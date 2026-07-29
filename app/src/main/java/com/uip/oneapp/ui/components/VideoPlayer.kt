package com.uip.oneapp.ui.components

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.StatusGreen
import com.uip.oneapp.ui.theme.StatusRed
import kotlinx.coroutines.delay

private const val TAG = "VideoPlayer"

/**
 * Ziel-Live-Offset für die Wiedergabe (ms). So weit hinter der Live-Kante darf ExoPlayer
 * höchstens zurückfallen, bevor es per leicht erhöhter Abspielgeschwindigkeit (siehe
 * [DefaultLivePlaybackSpeedControl] / [MediaItem.LiveConfiguration]) wieder aufholt — verhindert,
 * dass sich Latenz über die Zeit aufsummiert. Bewusst klein für einen Live-Monitor.
 *
 * `internal`, weil auch der produktive Live-Player ([FfmpegVideoPlayer]) dieselbe Live-Kante nutzt.
 */
internal const val LIVE_TARGET_OFFSET_MS = 200L

enum class PlayerState {
    IDLE, BUFFERING, READY, ERROR
}

/**
 * Hält für die Lebensdauer des Players einen WifiLock im Low-Latency-Modus — M2 (PERF-Doku
 * 2026-07-03): Das STA-Power-Save des Empfängergeräts bündelt eingehende RTP-Pakete zu Bursts
 * (gemessen: Ping-RTT 1,6→64 ms am Tab A9+) und addiert so 30–150 ms auf die Live-Latenz.
 * `WIFI_MODE_FULL_LOW_LATENCY` (API 29+, wirksam bei App im Vordergrund + Screen an — beim
 * Live-Monitor immer gegeben) schaltet es ab; darunter Fallback `FULL_HIGH_PERF`.
 * Auf der ONE selbst (lokaler LocalBitmap-Pfad, kein WLAN-Transport) harmlos.
 * `internal`, weil von beiden Playern ([VideoPlayer], [FfmpegVideoPlayer]) genutzt.
 */
@Composable
internal fun WifiLowLatencyLockEffect(key: Any?) {
    val context = LocalContext.current
    DisposableEffect(key) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        val lock = try {
            wifi?.createWifiLock(mode, "DrainQ:RtspLowLatency")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock nicht verfügbar: ${e.message}")
            null
        }
        onDispose {
            try { if (lock?.isHeld == true) lock.release() } catch (_: Exception) {}
        }
    }
}

/**
 * M5 (PERF-Doku 2026-07-03): Latenz-Trim für RTSP-Live. ExoPlayers Live-Speed-Control ist
 * bei RTSP ein No-op (keine Live-Timeline) — jeder WLAN-Schluckauf brennt sich deshalb als
 * DAUERHAFTER Zusatzversatz ein: Nach einem Stall kommt der Rückstau als Puffer an, die
 * Wiedergabe läuft aber mit 1,0x weiter und baut ihn nie ab. Dieser Effekt überwacht den
 * Puffer-Füllstand und spielt oberhalb von [TRIM_ENGAGE_MS] mit [TRIM_SPEED] ab, bis
 * [TRIM_RELEASE_MS] erreicht ist (Hysterese gegen Pendeln). Bei Pause inaktiv; nach dem
 * Fortsetzen räumt derselbe Mechanismus den Pause-Rückstau zur Live-Kante ab.
 * `internal`, weil von beiden Playern ([VideoPlayer], [FfmpegVideoPlayer]) genutzt.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun RtspLatencyTrimEffect(player: ExoPlayer) {
    LaunchedEffect(player) {
        var boosted = false
        while (true) {
            delay(400)
            try {
                if (!player.isPlaying) {
                    if (boosted) {
                        player.setPlaybackSpeed(1.0f)
                        boosted = false
                        // Sichtbar loggen: ein Trim-Abbruch durch Stall/Pause ist der Marker für
                        // Netz-Micro-Stalls (Rebuffer), nicht für ein sauberes Fertig-Trimmen.
                        Log.i(TAG, "Latenz-Trim AUS (Stall/Pause, state=${player.playbackState})")
                    }
                    continue
                }
                val buffered = player.totalBufferedDuration
                if (buffered > TRIM_REJOIN_MS) {
                    // Notbremse: Rückstand so groß, dass 1,1x minutenlang bräuchte. Sauberer
                    // Stream-Rejoin (neue RTSP-Session) — dank IDR-on-PLAY (M1) in ~100 ms
                    // wieder live. KEIN seek: unser Server kann kein PAUSE/Range (501).
                    Log.w(TAG, "Latenz-Trim: Puffer ${buffered}ms > ${TRIM_REJOIN_MS}ms -> Stream-Rejoin")
                    player.setPlaybackSpeed(1.0f)
                    boosted = false
                    player.stop()
                    player.prepare()
                    player.play()
                } else if (!boosted && buffered > TRIM_ENGAGE_MS) {
                    player.setPlaybackSpeed(TRIM_SPEED)
                    boosted = true
                    Log.i(TAG, "Latenz-Trim AN (Puffer ${buffered}ms)")
                } else if (boosted && buffered < TRIM_RELEASE_MS) {
                    player.setPlaybackSpeed(1.0f)
                    boosted = false
                    Log.i(TAG, "Latenz-Trim AUS (Puffer ${buffered}ms)")
                }
            } catch (e: Exception) {
                // Player released o. ä. — Effekt endet mit der Composition, hier nur nicht crashen.
                Log.w(TAG, "Latenz-Trim: ${e.message}")
                break
            }
        }
    }
}

/**
 * Puffer-Schwellen/Speed des Latenz-Trims (M5). Telemetrie 2026-07-03 (90-s-Fenster): Die
 * Anlieferung schwankt ±100 ms (GOP-Bursts + Funk) — Trimmen unter diesen Jitter-Boden lief
 * in den Underrun (state=2-Stalls im Sekundentakt, jeder Stall = Mikro-Freeze + Latenz zurück).
 * Daher: an > 250 ms, sanft mit 1,05x abbauen. RELEASE nach der R3-Messreihe (Median 226 ms,
 * sauberer Trim-Exit bei 101 ms ohne Folge-Stall) von 120 auf 100 ms gesenkt — bei steigender
 * Stall-Rate in schlechter Funkumgebung zurück auf 120.
 */
private const val TRIM_ENGAGE_MS = 250L
private const val TRIM_RELEASE_MS = 100L
private const val TRIM_SPEED = 1.05f

/** Ab diesem Rückstand lohnt Aufholen nicht mehr — Stream-Rejoin (M1 macht ihn ~100 ms schnell). */
private const val TRIM_REJOIN_MS = 5_000L

/**
 * Low-latency MediaCodec adapter: sets KEY_LOW_LATENCY on API 30+.
 * Hardware decoder outputs each frame immediately instead of batching,
 * saving 1-2 frame durations (~30-66ms at 30fps).
 */
@OptIn(UnstableApi::class)
internal class LowLatencyCodecAdapterFactory(
    private val delegate: DefaultMediaCodecAdapterFactory = DefaultMediaCodecAdapterFactory()
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                configuration.mediaFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                Log.d(TAG, "KEY_LOW_LATENCY=1 enabled")
                return delegate.createAdapter(configuration)
            } catch (e: Exception) {
                Log.w(TAG, "KEY_LOW_LATENCY not supported: ${e.message}")
                configuration.mediaFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 0)
            }
        }
        return delegate.createAdapter(configuration)
    }
}

/**
 * RenderersFactory that injects KEY_LOW_LATENCY codec adapter.
 * Overrides getCodecAdapterFactory() to use our low-latency wrapper.
 */
@OptIn(UnstableApi::class)
internal class LowLatencyRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
        return LowLatencyCodecAdapterFactory()
    }
}

/**
 * Build an ExoPlayer for near-realtime RTSP live stream playback.
 * Combines all available optimizations:
 * - Zero-buffer: render first frame instantly
 * - KEY_LOW_LATENCY: hardware decoder outputs frames immediately
 * - Async MediaCodec: overlaps decode + render pipeline
 * - TCP interleaved RTSP: avoids UDP jitter buffering
 */
@OptIn(UnstableApi::class)
private fun buildLowLatencyPlayer(context: Context): ExoPlayer {
    // 1. Absolute minimum buffering
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */                     0,
            /* maxBufferMs = */                     300,
            /* bufferForPlaybackMs = */             0,
            /* bufferForPlaybackAfterRebufferMs = */ 0
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // 2. Low-latency codec (KEY_LOW_LATENCY) + async MediaCodec queueing
    val renderersFactory = LowLatencyRenderersFactory(context)
        .forceEnableMediaCodecAsynchronousQueueing()

    // 3. Catch up to the live edge instead of accumulating latency. If playback drifts behind,
    //    ExoPlayer nudges the speed up toward the target offset and back to 1.0x once caught up.
    //    The target offset itself comes from MediaItem.LiveConfiguration (set below); here we only
    //    bound the speed adjustment. Effective only when the RTSP timeline reports a live window;
    //    otherwise a safe no-op (the server-side RTP-timestamp rebase is the primary latency fix).
    val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
        .setFallbackMinPlaybackSpeed(0.97f)
        .setFallbackMaxPlaybackSpeed(1.03f)
        .build()

    return ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setLoadControl(loadControl)
        .setLivePlaybackSpeedControl(liveSpeedControl)
        .build()
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    onConnected: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var playerState by remember { mutableStateOf(PlayerState.IDLE) }
    var errorMessage by remember { mutableStateOf("") }

    // M2: WLAN-Power-Save für die Dauer der Wiedergabe abschalten (Latenz-Bursts).
    WifiLowLatencyLockEffect(rtspUrl)

    val exoPlayer = remember(rtspUrl) {
        buildLowLatencyPlayer(context).apply {
            // 4. RTSP source with TCP interleaved - avoids UDP jitter buffering.
            //    The LiveConfiguration bounds the live-speed catch-up (see liveSpeedControl);
            //    it is honoured only if the RTSP window is live, harmless otherwise.
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(rtspUrl))
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                        .setMinPlaybackSpeed(0.97f)
                        .setMaxPlaybackSpeed(1.03f)
                        .build()
                )
                .build()
            val rtspSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(8_000)
                .createMediaSource(mediaItem)
            setMediaSource(rtspSource)

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> playerState = PlayerState.BUFFERING
                        Player.STATE_READY -> {
                            playerState = PlayerState.READY
                            onConnected()
                        }
                        Player.STATE_ENDED -> playerState = PlayerState.IDLE
                        Player.STATE_IDLE -> playerState = PlayerState.IDLE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerState = PlayerState.ERROR
                    errorMessage = error.message ?: "Unknown playback error"
                    onError(errorMessage)
                }
            })

            prepare()
            playWhenReady = true
        }
    }

    // M5: aufgestauten Puffer (WLAN-Stalls) zur Live-Kante abbauen statt ihn mitzuschleppen.
    RtspLatencyTrimEffect(exoPlayer)

    DisposableEffect(rtspUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { view ->
                // Bei rtspUrl-Wechsel entsteht ein neuer ExoPlayer, aber die factory läuft nicht
                // erneut — ohne update bliebe die View am released Alt-Player hängen (schwarz).
                if (view.player !== exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status overlay
        when (playerState) {
            PlayerState.IDLE, PlayerState.BUFFERING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (playerState == PlayerState.BUFFERING) S("buffering") else S("connecting_stream"),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            PlayerState.ERROR -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = StatusRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = S("stream_error"),
                        color = StatusRed,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = errorMessage,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            PlayerState.READY -> {
                // Stream is playing - show green indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "● ${S("live_indicator")}",
                        color = StatusGreen,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // URL label at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = rtspUrl,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

/**
 * @param cameraUnavailable Camera2-Umbau 2026-07-29 (AP-2): true, wenn
 * [com.uip.oneapp.network.VideoSource.Unavailable] anliegt — der Kameradienst-Selbststart
 * ist fehlgeschlagen. Zeigt eine eigene, verständliche Meldung statt des generischen
 * "kein Stream aktiv" (das würde einen echten Fehler wie einen normalen Leerlaufzustand
 * aussehen lassen — genau das soll AP-2 verhindern).
 */
@Composable
fun VideoPlayerPlaceholder(modifier: Modifier = Modifier, cameraUnavailable: Boolean = false) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (cameraUnavailable) Icons.Default.Error else Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (cameraUnavailable) com.uip.oneapp.ui.theme.StatusRed else Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (cameraUnavailable) S("camera_not_available_title") else S("no_stream_active"),
                color = if (cameraUnavailable) com.uip.oneapp.ui.theme.StatusRed else Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (cameraUnavailable) S("camera_not_available_hint") else S("enter_url_or_scan"),
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

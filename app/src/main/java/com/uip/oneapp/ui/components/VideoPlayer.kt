package com.uip.oneapp.ui.components

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
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

private const val TAG = "VideoPlayer"

enum class PlayerState {
    IDLE, BUFFERING, READY, ERROR
}

/**
 * Low-latency MediaCodec adapter: sets KEY_LOW_LATENCY on API 30+.
 * Hardware decoder outputs each frame immediately instead of batching,
 * saving 1-2 frame durations (~30-66ms at 30fps).
 */
@OptIn(UnstableApi::class)
private class LowLatencyCodecAdapterFactory(
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
private class LowLatencyRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
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

    return ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setLoadControl(loadControl)
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

    val exoPlayer = remember(rtspUrl) {
        buildLowLatencyPlayer(context).apply {
            // 3. RTSP source with TCP interleaved - avoids UDP jitter buffering
            val rtspSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(8_000)
                .createMediaSource(MediaItem.fromUri(Uri.parse(rtspUrl)))
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
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
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

@Composable
fun VideoPlayerPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = S("no_stream_active"),
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = S("enter_url_or_scan"),
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

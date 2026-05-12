package com.uip.oneapp.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.uip.oneapp.export.OsdSettings
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.StatusGreen
import com.uip.oneapp.ui.theme.StatusRed

private const val TAG = "FfmpegVideoPlayer"

/**
 * Primary player: ExoPlayer-backed RTSP stream with Canvas OSD overlay.
 *
 * Replaced the VLC backend (Phase 7) with ExoPlayer/Media3 for RTSP delivery.
 * Uses TextureView so the host can capture screenshots via [onTextureViewReady].
 * OSD is rendered only when [osdSettings].enableOsdBurnIn is true.
 * Recording is handled externally by FfmpegRtspRecorder — this component only displays.
 */
@OptIn(UnstableApi::class)
@Composable
fun FfmpegVideoPlayer(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    osdSettings: OsdSettings = OsdSettings(),
    osdLine1: String = "",
    osdLine2: String = "",
    findingFlash: String? = null,
    isPaused: Boolean = false,
    isFfmpegRecording: Boolean = false,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onTextureViewReady: (TextureView) -> Unit = {},
    onConnected: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var playerState by remember { mutableStateOf(PlayerState.IDLE) }
    var errorMessage by remember { mutableStateOf("") }
    // Track stream aspect ratio so we can letterbox/pillarbox correctly.
    // Default 16:9 until the decoder reports the real size — most cams stream
    // 1280x720 or 1920x1080. After STATE_READY this is overwritten with the
    // exact ratio reported by the codec.
    var videoAspect by remember { mutableStateOf(16f / 9f) }

    val exoPlayer = remember(rtspUrl) {
        buildLowLatencyPlayer(context).apply {
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
                        Player.STATE_ENDED, Player.STATE_IDLE -> playerState = PlayerState.IDLE
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    playerState = PlayerState.ERROR
                    errorMessage = error.message ?: "Playback error"
                    onError(errorMessage)
                    Log.e(TAG, "ExoPlayer error: ${error.message}")
                }
                override fun onVideoSizeChanged(size: VideoSize) {
                    // pixelWidthHeightRatio = stream's pixel-aspect (usually 1.0
                    // for square pixels). Multiply to get the actual display ratio.
                    if (size.width > 0 && size.height > 0) {
                        val par = if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f
                        videoAspect = (size.width * par) / size.height.toFloat()
                        Log.d(TAG, "Video size: ${size.width}x${size.height} par=$par -> aspect=$videoAspect")
                    }
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(Unit) {
        onPlayerReady(exoPlayer)
    }

    // Sync pause state to ExoPlayer
    LaunchedEffect(isPaused) {
        if (isPaused) exoPlayer.pause() else exoPlayer.play()
    }

    DisposableEffect(rtspUrl) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Aspect-preserving inner box: fits the largest centered rectangle
        // with the stream's native aspect ratio inside the available area.
        // Letterboxes (top/bottom) or pillarboxes (left/right) the surrounding
        // Box background which is already black.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(videoAspect, matchHeightConstraintsFirst = false),
            contentAlignment = Alignment.Center
        ) {
            // Video surface (TextureView for screenshot support).
            // Both Video + OSD share the same aspect-preserving box so the
            // overlay sticks to the picture, not the surrounding letterbox.
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        exoPlayer.setVideoTextureView(tv)
                        onTextureViewReady(tv)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // OSD Canvas overlay — sits on top of the actual picture area.
            OsdOverlay(
                settings = osdSettings,
                line1 = osdLine1,
                line2 = osdLine2,
                modifier = Modifier.fillMaxSize(),
                findingFlash = findingFlash,
                isPaused = isPaused
            )
        }

        // Status overlays
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
                // LIVE indicator (top-end)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isFfmpegRecording) {
                        val infiniteTransition = rememberInfiniteTransition(label = "rec")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "recAlpha"
                        )
                        Text(
                            text = "● ${S("recording_indicator_ffmpeg")}",
                            color = StatusRed.copy(alpha = alpha),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "|",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = "● ${S("live_indicator")}",
                        color = StatusGreen,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildLowLatencyPlayer(context: Context): ExoPlayer {
    // Aggressive low-latency LoadControl:
    //   max=100ms — drop anything older. 300ms was conservative and let the
    //   player accumulate up to ~10 frames at 30fps, which is what users
    //   perceived as "high latency".
    //   backBuffer disabled — we don't seek backward in a live stream, so
    //   keeping past frames around just costs memory.
    val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */                     0,
            /* maxBufferMs = */                     100,
            /* bufferForPlaybackMs = */             0,
            /* bufferForPlaybackAfterRebufferMs = */ 0
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .setBackBuffer(0, false)
        .build()
    val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .forceEnableMediaCodecAsynchronousQueueing()
    return ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setLoadControl(loadControl)
        .build()
}

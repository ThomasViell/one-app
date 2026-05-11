package com.uip.oneapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uip.oneapp.export.OsdSettings
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.StatusRed
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Integration player: VLC-backed live stream with OSD overlay.
 *
 * Phase 4: OsdOverlay Canvas layer on top of VlcVideoPlayer.
 * Phase 5: Added [isFfmpegRecording] to show REC indicator when FfmpegRtspRecorder is active
 *          (VLC is not recording in that mode — the recorder opens its own RTSP session).
 *
 * OSD is rendered only when [osdSettings].enableOsdBurnIn is true.
 */
@Composable
fun FfmpegVideoPlayer(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    osdSettings: OsdSettings = OsdSettings(),
    osdLine1: String = "",
    osdLine2: String = "",
    findingFlash: String? = null,
    isPaused: Boolean = false,
    recordingFilePath: String? = null,
    overlayText: String? = null,
    isFfmpegRecording: Boolean = false,
    onLayoutReady: (VLCVideoLayout) -> Unit = {},
    onMediaPlayerReady: (MediaPlayer) -> Unit = {},
    onConnected: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    Box(modifier = modifier) {
        VlcVideoPlayer(
            rtspUrl = rtspUrl,
            modifier = Modifier.fillMaxSize(),
            recordingFilePath = recordingFilePath,
            overlayText = overlayText,
            onLayoutReady = onLayoutReady,
            onMediaPlayerReady = onMediaPlayerReady,
            onConnected = onConnected,
            onError = onError
        )
        OsdOverlay(
            settings = osdSettings,
            line1 = osdLine1,
            line2 = osdLine2,
            modifier = Modifier.fillMaxSize(),
            findingFlash = findingFlash,
            isPaused = isPaused
        )

        // Recording indicator for FfmpegRtspRecorder mode (VLC is not recording in this path)
        if (isFfmpegRecording) {
            val infiniteTransition = rememberInfiniteTransition(label = "ffmpeg_rec")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ffmpegRecAlpha"
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "● ${S("recording_indicator_ffmpeg")}",
                    color = StatusRed.copy(alpha = alpha),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

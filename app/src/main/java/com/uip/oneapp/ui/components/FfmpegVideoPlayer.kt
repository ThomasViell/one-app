package com.uip.oneapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uip.oneapp.export.OsdSettings
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Integration player: VLC-backed live stream with OSD overlay.
 *
 * Phase 4: integrates OsdOverlay on top of VlcVideoPlayer to provide the live Canvas OSD layer.
 * Phase 5 will replace the VLC backend with an FFmpegKit pipeline for pixel-level burn-in recording.
 * The public API is stable so InspectionScreen needs no further changes in Phase 5.
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
    }
}

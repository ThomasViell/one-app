package com.uip.oneapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.StateFlow

/**
 * Player für den lokalen V4L2-Bitmap-Stream (DrainQ ONE-Local-Modus).
 *
 * Pendant zu FfmpegVideoPlayer für den `VideoSource.Rtsp`-Fall. Sammelt Bitmaps aus
 * einem StateFlow ein und rendert sie als Compose-Image. Aspect-Ratio: Crop (Video
 * füllt die ganze Box komplett — Standard für Inspektionsmonitor-Layout, sodass das
 * OSD-Overlay direkt über dem Live-Bild liegt statt im Letterbox-Bereich).
 *
 * Bezug: docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md, Phase P4.
 */
@Composable
fun LocalBitmapVideoPlayer(
    frameFlow: StateFlow<Bitmap?>,
    modifier: Modifier = Modifier
) {
    val frame by frameFlow.collectAsState()
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        val bm = frame
        if (bm != null) {
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Wenn frame == null: schwarzer Hintergrund, OSD/Status-Overlays vom Caller
        // werden vor diesem Composable in derselben Box positioniert.
    }
}

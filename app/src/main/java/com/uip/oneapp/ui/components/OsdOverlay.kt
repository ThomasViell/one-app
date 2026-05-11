package com.uip.oneapp.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdFlashPosition
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.export.OsdSettings
import com.uip.oneapp.ui.theme.OsdBarBackground
import com.uip.oneapp.ui.theme.OsdColorGray
import com.uip.oneapp.ui.theme.OsdColorYellow

/**
 * Compose Canvas OSD overlay — mirrors OsdRenderer.renderOnCanvas() for live display.
 *
 * Rendered as a transparent overlay on top of the video surface. No pixel allocation needed;
 * Compose's Canvas DrawScope handles transparency natively, unlike Android Bitmap-based rendering.
 */
@Composable
fun OsdOverlay(
    settings: OsdSettings,
    line1: String,
    line2: String,
    modifier: Modifier = Modifier,
    findingFlash: String? = null,
    isPaused: Boolean = false
) {
    if (!settings.enableOsdBurnIn) return

    val fontColorArgb = OsdRenderer.fontColorArgb(settings.fontColor)
    val grayArgb = OsdColorGray.toArgb()
    val yellowArgb = OsdColorYellow.toArgb()

    val textPaint = remember(settings.fontSize, settings.fontColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            color = fontColorArgb
        }
    }
    val grayPaint = remember(settings.fontSize) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            color = grayArgb
        }
    }
    val flashPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = yellowArgb } }
    val pausedPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = yellowArgb } }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val tsPx = OsdRenderer.textSizePx(h.toInt(), settings.fontSize)
        val topH = OsdRenderer.topBarHeight(tsPx).toFloat()
        val botH = OsdRenderer.bottomBarHeight(tsPx).toFloat()

        textPaint.textSize = tsPx
        grayPaint.textSize = tsPx * 0.85f
        flashPaint.textSize = tsPx
        pausedPaint.textSize = tsPx * 2.5f

        val bgAlpha = when (settings.background) {
            OsdBackground.Transparent -> 0f
            OsdBackground.SemiTransparent -> 0.5f
            OsdBackground.Solid -> 0.82f
        }
        val bgColor = OsdBarBackground.copy(alpha = bgAlpha)

        val safeL1 = OsdRenderer.asciiSafe(line1)
        val safeL2 = OsdRenderer.asciiSafe(line2)
        val safeFlash = OsdRenderer.asciiSafe(findingFlash)

        if (safeL1.isNotEmpty()) {
            drawRect(bgColor, Offset.Zero, Size(w, topH))
            drawContext.canvas.nativeCanvas.drawText(safeL1, 8f, topH - tsPx * 0.2f, textPaint)
        }

        if (safeL2.isNotEmpty()) {
            drawRect(bgColor, Offset(0f, h - botH), Size(w, botH))
            drawContext.canvas.nativeCanvas.drawText(safeL2, 8f, h - tsPx * 0.25f, grayPaint)
        }

        if (!safeFlash.isNullOrEmpty()) {
            val tb = Rect()
            flashPaint.getTextBounds(safeFlash, 0, safeFlash.length, tb)
            val bxW = tb.width() + 16f
            val bxH = tb.height() + 12f
            val (bx, by) = when (settings.findingFlashPosition) {
                OsdFlashPosition.Center -> Pair((w - bxW) / 2f, (h - bxH) / 2f)
                OsdFlashPosition.BelowLine1 -> Pair(4f, topH + 2f)
            }
            drawRect(Color.Black, Offset(bx, by), Size(bxW, bxH))
            drawContext.canvas.nativeCanvas.drawText(safeFlash, bx + 8f, by + tb.height() + 6f, flashPaint)
        }

        if (isPaused) {
            drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(w, h))
            val pausedText = "II PAUSED"
            val pb = Rect()
            pausedPaint.getTextBounds(pausedText, 0, pausedText.length, pb)
            drawContext.canvas.nativeCanvas.drawText(
                pausedText, (w - pb.width()) / 2f, (h + pb.height()) / 2f, pausedPaint
            )
        }
    }
}

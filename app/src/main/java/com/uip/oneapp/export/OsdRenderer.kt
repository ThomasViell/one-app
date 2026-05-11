package com.uip.oneapp.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.uip.oneapp.ui.theme.OsdBarBackground
import com.uip.oneapp.ui.theme.OsdColorGray
import com.uip.oneapp.ui.theme.OsdColorGreen
import com.uip.oneapp.ui.theme.OsdColorWhite
import com.uip.oneapp.ui.theme.OsdColorYellow
import java.nio.ByteBuffer

/**
 * Pixel-level OSD burn-in renderer — Android port of DrainQ.WPF OsdRenderer.cs.
 *
 * Input:  ARGB ByteArray (4 bytes/pixel, same layout as Bitmap.Config.ARGB_8888).
 * Output: same ByteArray mutated in-place with text bars drawn via Android Canvas.
 *
 * Color constants come from ui/theme/Color.kt; no hardcoded RGB literals here.
 * ASCII transliteration handles German umlauts so the system font renders them correctly.
 */
object OsdRenderer {

    // ── ASCII transliteration (mirrors OsdRenderer.cs AsciiSafe) ──

    internal fun asciiSafe(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return buildString(s.length + 4) {
            for (ch in s) {
                when (ch) {
                    'ä' -> append("ae")
                    'ö' -> append("oe")
                    'ü' -> append("ue")
                    'Ä' -> append("Ae")
                    'Ö' -> append("Oe")
                    'Ü' -> append("Ue")
                    'ß' -> append("ss")
                    '→' -> append("->")
                    '°' -> append("deg")
                    else -> if (ch.code <= 0x7E) append(ch) else append('?')
                }
            }
        }
    }

    // ── Internal coordinate helpers ──

    internal fun topBarHeight(textSizePx: Float): Int = (textSizePx * 1.6f).toInt().coerceAtLeast(20)

    internal fun bottomBarHeight(textSizePx: Float): Int = (textSizePx * 1.4f).toInt().coerceAtLeast(18)

    internal fun textSizePx(height: Int, fontSize: OsdFontSize): Float = when (fontSize) {
        OsdFontSize.Small  -> height * 0.022f
        OsdFontSize.Medium -> height * 0.028f
        OsdFontSize.Large  -> height * 0.039f
        OsdFontSize.Maxi   -> height * 0.056f
    }

    internal fun fontColorArgb(osdColor: OsdColor): Int = when (osdColor) {
        OsdColor.Green  -> OsdColorGreen.toArgb()
        OsdColor.White  -> OsdColorWhite.toArgb()
        OsdColor.Yellow -> OsdColorYellow.toArgb()
    }

    // ── Public render API ──

    /**
     * Burns OSD text into [argb] in-place.
     *
     * @param argb        Raw ARGB frame buffer (width × height × 4 bytes). Modified in place.
     * @param width       Frame width in pixels.
     * @param height      Frame height in pixels.
     * @param settings    OSD configuration.
     * @param line1       Top bar text (project header / mandatory DWA-M 149-5 fields).
     * @param line2       Bottom bar text (optional telemetry: meter, inclination, date).
     * @param findingFlash Optional flash label shown for a new observation entry.
     * @param isPaused    If true, renders a darkened "II PAUSED" overlay.
     */
    fun render(
        argb: ByteArray,
        width: Int,
        height: Int,
        settings: OsdSettings,
        line1: String,
        line2: String,
        findingFlash: String? = null,
        isPaused: Boolean = false
    ) {
        if (!settings.enableOsdBurnIn) return
        if (width <= 0 || height <= 0) return
        if (argb.size < width * height * 4) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argb))
            renderOnCanvas(Canvas(bitmap), width, height, settings, line1, line2, findingFlash, isPaused)
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(argb))
        } finally {
            bitmap.recycle()
        }
    }

    /** Overload that renders directly onto a pre-existing [Bitmap] (mutates it). */
    fun renderBitmap(
        bitmap: Bitmap,
        settings: OsdSettings,
        line1: String,
        line2: String,
        findingFlash: String? = null,
        isPaused: Boolean = false
    ) {
        if (!settings.enableOsdBurnIn) return
        renderOnCanvas(
            Canvas(bitmap), bitmap.width, bitmap.height,
            settings, line1, line2, findingFlash, isPaused
        )
    }

    // ── Core drawing logic ──

    private fun renderOnCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        settings: OsdSettings,
        line1: String,
        line2: String,
        findingFlash: String?,
        isPaused: Boolean
    ) {
        val safeL1 = asciiSafe(line1)
        val safeL2 = asciiSafe(line2)
        val safeFlash = asciiSafe(findingFlash)

        val tsPx = textSizePx(height, settings.fontSize)
        val topH = topBarHeight(tsPx)
        val botH = bottomBarHeight(tsPx)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = tsPx
            color = fontColorArgb(settings.fontColor)
        }
        val bgPaint = Paint().apply {
            color = OsdBarBackground.toArgb()
        }
        val grayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = tsPx * 0.85f
            color = OsdColorGray.toArgb()
        }

        // ── Top bar (line1) ──
        if (safeL1.isNotEmpty()) {
            if (settings.background != OsdBackground.Transparent) {
                val alpha = if (settings.background == OsdBackground.SemiTransparent) 128 else 210
                bgPaint.alpha = alpha
                canvas.drawRect(0f, 0f, width.toFloat(), topH.toFloat(), bgPaint)
            }
            canvas.drawText(safeL1, 8f, topH - tsPx * 0.2f, textPaint)
        }

        // ── Bottom bar (line2) ──
        if (safeL2.isNotEmpty()) {
            val botTop = (height - botH).toFloat()
            if (settings.background != OsdBackground.Transparent) {
                val alpha = if (settings.background == OsdBackground.SemiTransparent) 128 else 210
                bgPaint.alpha = alpha
                canvas.drawRect(0f, botTop, width.toFloat(), height.toFloat(), bgPaint)
            }
            canvas.drawText(safeL2, 8f, height - tsPx * 0.25f, grayPaint)
        }

        // ── Observation flash ──
        if (!safeFlash.isNullOrEmpty()) {
            val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = tsPx
                color = OsdColorYellow.toArgb()
            }
            val textBounds = Rect()
            flashPaint.getTextBounds(safeFlash, 0, safeFlash.length, textBounds)
            val boxW = textBounds.width() + 16
            val boxH = textBounds.height() + 12

            val (boxX, boxY) = when (settings.findingFlashPosition) {
                OsdFlashPosition.Center -> {
                    Pair((width - boxW) / 2, (height - boxH) / 2)
                }
                OsdFlashPosition.BelowLine1 -> {
                    Pair(4, topH + 2)
                }
            }

            val blackPaint = Paint().apply { color = android.graphics.Color.BLACK }
            canvas.drawRect(
                RectF(boxX.toFloat(), boxY.toFloat(), (boxX + boxW).toFloat(), (boxY + boxH).toFloat()),
                blackPaint
            )
            canvas.drawText(safeFlash, (boxX + 8).toFloat(), (boxY + textBounds.height() + 6).toFloat(), flashPaint)
        }

        // ── Paused overlay ──
        if (isPaused) {
            val dimPaint = Paint().apply {
                color = android.graphics.Color.argb(128, 0, 0, 0)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            val pausedText = "II PAUSED"
            val pausedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                textSize = tsPx * 2.5f
                color = OsdColorYellow.toArgb()
            }
            val pb = Rect()
            pausedPaint.getTextBounds(pausedText, 0, pausedText.length, pb)
            canvas.drawText(
                pausedText,
                ((width - pb.width()) / 2).toFloat(),
                ((height + pb.height()) / 2).toFloat(),
                pausedPaint
            )
        }
    }
}

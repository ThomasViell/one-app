package com.uip.oneapp.export

import android.graphics.Bitmap
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

// ─────────────────────────────────────────────────────────────────────────────
// Pure JVM logic tests (no Android framework required)
// ─────────────────────────────────────────────────────────────────────────────

// Hinweis: Die frühere asciiSafe-Transliteration wurde entfernt (CEO-Beschluss
// 2026-06-07) — OSD-Texte werden jetzt unverändert als Unicode gerendert. Die
// zugehörigen Transliteration-Tests entfallen; das Unicode-Rendering selbst wird
// durch den Robolectric-Visual-Test unten mit abgedeckt.

class OsdCoordinateTest {

    @Test fun `topBarHeight medium scale at 720p`() {
        val tsPx = OsdRenderer.textSizePx(720, OsdFontSize.Medium)  // 720 * 0.028 = 20.16
        val h = OsdRenderer.topBarHeight(tsPx)
        assertTrue("topBarHeight at 720p medium should be >= 20", h >= 20)
        assertTrue("topBarHeight at 720p medium should be <= 48", h <= 48)
    }

    @Test fun `maxi font larger than small font`() {
        val small = OsdRenderer.textSizePx(720, OsdFontSize.Small)
        val maxi  = OsdRenderer.textSizePx(720, OsdFontSize.Maxi)
        assertTrue("Maxi should be larger than Small", maxi > small)
    }

    @Test fun `textSizePx scales with frame height`() {
        val ts480 = OsdRenderer.textSizePx(480, OsdFontSize.Medium)
        val ts720 = OsdRenderer.textSizePx(720, OsdFontSize.Medium)
        assertEquals(ts480 * (720f / 480f), ts720, 0.01f)
    }

    @Test fun `fontColorArgb green is not zero`() {
        assertNotEquals(0, OsdRenderer.fontColorArgb(OsdColor.Green))
    }

    @Test fun `fontColorArgb colors are distinct`() {
        val g = OsdRenderer.fontColorArgb(OsdColor.Green)
        val w = OsdRenderer.fontColorArgb(OsdColor.White)
        val y = OsdRenderer.fontColorArgb(OsdColor.Yellow)
        assertNotEquals(g, w)
        assertNotEquals(g, y)
        assertNotEquals(w, y)
    }
}

class OsdSettingsDefaultsTest {

    @Test fun `defaults disable burnin`() = assertFalse(OsdSettings().enableOsdBurnIn)
    @Test fun `defaults show meter`() = assertTrue(OsdSettings().showMeterValue)
    @Test fun `defaults show date`() = assertTrue(OsdSettings().showDate)
    @Test fun `default font is medium`() = assertEquals(OsdFontSize.Medium, OsdSettings().fontSize)
    @Test fun `default color is green`() = assertEquals(OsdColor.Green, OsdSettings().fontColor)
    @Test fun `default background is semi-transparent`() = assertEquals(OsdBackground.SemiTransparent, OsdSettings().background)
    @Test fun `default flash is center`() = assertEquals(OsdFlashPosition.Center, OsdSettings().findingFlashPosition)
}

class OsdRenderGuardTest {

    @Test fun `render does nothing when disabled`() {
        val argb = ByteArray(4) { 0xAA.toByte() }
        val copy = argb.copyOf()
        OsdRenderer.render(argb, 1, 1, OsdSettings(enableOsdBurnIn = false), "line1", "line2")
        assertArrayEquals("disabled render must not mutate buffer", copy, argb)
    }

    @Test fun `render does nothing for empty frame`() {
        val argb = ByteArray(0)
        OsdRenderer.render(argb, 0, 0, OsdSettings(enableOsdBurnIn = true), "x", "y")
    }

    @Test fun `render does nothing if buffer too small`() {
        val argb = ByteArray(3)  // width=1 height=1 needs 4 bytes
        val copy = argb.copyOf()
        OsdRenderer.render(argb, 1, 1, OsdSettings(enableOsdBurnIn = true), "x", "y")
        assertArrayEquals(copy, argb)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Robolectric tests — require Android framework (Bitmap / Canvas)
// ─────────────────────────────────────────────────────────────────────────────

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class OsdRendererVisualTest {

    private fun grayFrame(w: Int, h: Int): ByteArray {
        val argb = ByteArray(w * h * 4)
        for (i in argb.indices step 4) {
            argb[i]     = 0xFF.toByte()  // A
            argb[i + 1] = 0x80.toByte()  // R
            argb[i + 2] = 0x80.toByte()  // G
            argb[i + 3] = 0x80.toByte()  // B  → gray
        }
        return argb
    }

    @Test fun `render top bar mutates pixels`() {
        val w = 320; val h = 240
        val argb = grayFrame(w, h)
        val settings = OsdSettings(enableOsdBurnIn = true, background = OsdBackground.SemiTransparent)
        OsdRenderer.render(argb, w, h, settings, "NSP3CT | 2026-05-11 | 42.5m", "")
        // At least one pixel in the top-bar area must have changed from 0x80
        val topH = OsdRenderer.topBarHeight(OsdRenderer.textSizePx(h, settings.fontSize))
        val topAreaMutated = (0 until topH).any { row ->
            val idx = row * w * 4
            argb[idx + 1] != 0x80.toByte() || argb[idx + 2] != 0x80.toByte() || argb[idx + 3] != 0x80.toByte()
        }
        assertTrue("Top bar pixels should have changed after render", topAreaMutated)
    }

    @Test fun `render bottom bar mutates pixels`() {
        val w = 320; val h = 240
        val argb = grayFrame(w, h)
        val settings = OsdSettings(enableOsdBurnIn = true)
        OsdRenderer.render(argb, w, h, settings, "", "Meter: 42.5m | 2026-05-11")
        val botH = OsdRenderer.bottomBarHeight(OsdRenderer.textSizePx(h, settings.fontSize))
        val botMutated = (h - botH until h).any { row ->
            val idx = row * w * 4
            argb[idx + 1] != 0x80.toByte() || argb[idx + 2] != 0x80.toByte() || argb[idx + 3] != 0x80.toByte()
        }
        assertTrue("Bottom bar pixels should have changed after render", botMutated)
    }

    @Test fun `render paused overlay darkens pixels`() {
        val w = 320; val h = 240
        val argb = grayFrame(w, h)
        val settings = OsdSettings(enableOsdBurnIn = true)
        OsdRenderer.render(argb, w, h, settings, "", "", isPaused = true)
        // The dimming pass (alpha=128 black) will make some pixels darker than 0x80
        val hasDarkerPixel = argb.indices.step(4).any { i ->
            (argb[i + 1].toInt() and 0xFF) < 0x80
        }
        assertTrue("Paused overlay should darken at least some pixels", hasDarkerPixel)
    }

    /** Visual diff artifact: saves a 1280×720 gray frame with full OSD to a PNG file. */
    @Test fun `visual output PNG smoke test`() {
        val w = 1280; val h = 720
        val argb = grayFrame(w, h)
        val settings = OsdSettings(
            enableOsdBurnIn = true,
            showMeterValue = true,
            showDate = true,
            fontSize = OsdFontSize.Medium,
            fontColor = OsdColor.Green,
            background = OsdBackground.SemiTransparent,
            findingFlashPosition = OsdFlashPosition.Center
        )
        // Unicode-Inhalte (Umlaute, Akzente) — werden seit 2026-06-07 direkt gerendert.
        OsdRenderer.render(
            argb, w, h, settings,
            line1 = "DrainQ ONE | Projekt: Müller GmbH | Start: SA1 | Ende: SA2 | Ø 300mm",
            line2 = "42.50m | 2026-05-11 | Wurzeleinwuchs übermäßig",
            findingFlash = "Riss längs"
        )

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(argb))

        val outDir = File(System.getProperty("java.io.tmpdir"), "drainq_osd_test")
        outDir.mkdirs()
        val outFile = File(outDir, "osd_visual_test_1280x720.png")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        assertTrue("PNG output file should exist: ${outFile.absolutePath}", outFile.exists())
        assertTrue("PNG output should not be empty", outFile.length() > 0)
        println("OSD Visual PNG: ${outFile.absolutePath}")
    }
}

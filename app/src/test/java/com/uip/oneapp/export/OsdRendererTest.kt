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

class OsdAsciiSafeTest {

    @Test fun `oe transliteration`() = assertEquals("oe", OsdRenderer.asciiSafe("ö"))
    @Test fun `ae transliteration`() = assertEquals("ae", OsdRenderer.asciiSafe("ä"))
    @Test fun `ue transliteration`() = assertEquals("ue", OsdRenderer.asciiSafe("ü"))
    @Test fun `Ae transliteration`() = assertEquals("Ae", OsdRenderer.asciiSafe("Ä"))
    @Test fun `Oe transliteration`() = assertEquals("Oe", OsdRenderer.asciiSafe("Ö"))
    @Test fun `Ue transliteration`() = assertEquals("Ue", OsdRenderer.asciiSafe("Ü"))
    @Test fun `ss transliteration`() = assertEquals("ss", OsdRenderer.asciiSafe("ß"))
    @Test fun `degree transliteration`() = assertEquals("deg", OsdRenderer.asciiSafe("°"))
    @Test fun `arrow transliteration`() = assertEquals("->", OsdRenderer.asciiSafe("→"))
    @Test fun `ascii passthrough`() = assertEquals("Hello World 123", OsdRenderer.asciiSafe("Hello World 123"))
    @Test fun `null returns empty`() = assertEquals("", OsdRenderer.asciiSafe(null))
    @Test fun `empty returns empty`() = assertEquals("", OsdRenderer.asciiSafe(""))
    @Test fun `mixed german string`() = assertEquals("Roehre: Oe-2 (Laenge: 80deg)", OsdRenderer.asciiSafe("Röhre: Ö-2 (Länge: 80°)"))
    @Test fun `non-ascii non-german becomes question mark`() = assertEquals("?", OsdRenderer.asciiSafe("Ѐ"))
}

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
    @Test fun `defaults hide inclination`() = assertFalse(OsdSettings().showInclination)
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
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])  // SDK 34
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
        OsdRenderer.render(
            argb, w, h, settings,
            line1 = "NSP3CT ONE | Projekt: Muster GmbH | Start: SA1 | Ende: SA2 | D: 300mm",
            line2 = "42.50m | 2026-05-11 | 0.0deg",
            findingFlash = "BAB 1.1"
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

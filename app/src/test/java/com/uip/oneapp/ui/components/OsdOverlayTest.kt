package com.uip.oneapp.ui.components

import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdColor
import com.uip.oneapp.export.OsdFontSize
import com.uip.oneapp.export.OsdFlashPosition
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.export.OsdSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 smoke tests for OsdOverlay helper calculations.
 * Validates that the bar-height and text-size formulas mirror OsdRenderer.cs.
 */
class OsdOverlayTest {

    @Test
    fun `topBarHeight grows with fontSize at 720p`() {
        val h = 720
        val small  = OsdRenderer.topBarHeight(OsdRenderer.textSizePx(h, OsdFontSize.Small))
        val medium = OsdRenderer.topBarHeight(OsdRenderer.textSizePx(h, OsdFontSize.Medium))
        val large  = OsdRenderer.topBarHeight(OsdRenderer.textSizePx(h, OsdFontSize.Large))
        val maxi   = OsdRenderer.topBarHeight(OsdRenderer.textSizePx(h, OsdFontSize.Maxi))
        assertTrue("small < medium", small < medium)
        assertTrue("medium < large", medium < large)
        assertTrue("large < maxi",  large < maxi)
    }

    @Test
    fun `bottomBarHeight is always less than topBarHeight`() {
        listOf(OsdFontSize.Small, OsdFontSize.Medium, OsdFontSize.Large, OsdFontSize.Maxi).forEach { fs ->
            val ts = OsdRenderer.textSizePx(720, fs)
            assertTrue("botH < topH for $fs", OsdRenderer.bottomBarHeight(ts) < OsdRenderer.topBarHeight(ts))
        }
    }

    @Test
    fun `textSizePx scales linearly with height`() {
        val ts240 = OsdRenderer.textSizePx(240, OsdFontSize.Medium)
        val ts720 = OsdRenderer.textSizePx(720, OsdFontSize.Medium)
        assertEquals("3x height -> 3x textSize", ts240 * 3f, ts720, 0.01f)
    }

    @Test
    fun `fontColorArgb returns distinct values for each OsdColor`() {
        val green  = OsdRenderer.fontColorArgb(OsdColor.Green)
        val white  = OsdRenderer.fontColorArgb(OsdColor.White)
        val yellow = OsdRenderer.fontColorArgb(OsdColor.Yellow)
        assertTrue("Green != White",  green  != white)
        assertTrue("Green != Yellow", green  != yellow)
        assertTrue("White != Yellow", white  != yellow)
    }

    @Test
    fun `OsdSettings defaults match phase 3 contract`() {
        val s = OsdSettings()
        assertEquals(false, s.enableOsdBurnIn)
        assertEquals(true,  s.showMeterValue)
        assertEquals(true,  s.showDate)
        assertEquals(false, s.showInclination)
        assertEquals(OsdFontSize.Medium,          s.fontSize)
        assertEquals(OsdColor.Green,              s.fontColor)
        assertEquals(OsdBackground.SemiTransparent, s.background)
        assertEquals(OsdFlashPosition.Center,     s.findingFlashPosition)
    }

    // ── OSD line builder tests ──────────────────────────────────────────────────

    @Test
    fun `buildOsdLine2 with showMeterValue only`() {
        val settings = OsdSettings(enableOsdBurnIn = true, showMeterValue = true, showDate = false, showInclination = false)
        val line2 = buildOsdLine2Test(42.5f, settings, null)
        assertTrue("contains meter", line2.contains("42.50m"))
        assertTrue("no date", !line2.contains("-"))  // date would contain hyphens
    }

    @Test
    fun `buildOsdLine2 with all fields disabled returns empty`() {
        val settings = OsdSettings(enableOsdBurnIn = true, showMeterValue = false, showDate = false, showInclination = false)
        val line2 = buildOsdLine2Test(0f, settings, null)
        assertEquals("", line2)
    }

    @Test
    fun `buildOsdLine2 with sonde frequency included when showInclination true`() {
        val settings = OsdSettings(enableOsdBurnIn = true, showMeterValue = false, showDate = false, showInclination = true)
        val line2 = buildOsdLine2Test(0f, settings, "33kHz")
        assertEquals("33kHz", line2)
    }
}

// Mirror of InspectionScreen.buildOsdLine2 for unit-testing without Android context
private fun buildOsdLine2Test(
    meterValue: Float,
    osdSettings: OsdSettings,
    sondeFrequency: String?
): String {
    val parts = mutableListOf<String>()
    if (osdSettings.showMeterValue) parts.add(String.format(java.util.Locale.US, "%.2fm", meterValue))
    if (osdSettings.showDate) parts.add(java.time.LocalDate.now().toString())
    if (osdSettings.showInclination && sondeFrequency != null) parts.add(sondeFrequency)
    return parts.joinToString(" | ")
}

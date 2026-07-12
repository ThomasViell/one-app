package com.uip.oneapp.ui.screens.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionControlsLightTest {

    @Test fun step_0_to_10()   { assertEquals(10,  nextLightStep(0)) }
    @Test fun step_30_to_40()  { assertEquals(40,  nextLightStep(30)) }
    @Test fun step_95_to_100() { assertEquals(100, nextLightStep(95)) }
    @Test fun step_100_to_0()  { assertEquals(0,   nextLightStep(100)) }

    @Test
    fun snapsKrummeWerte() {
        // Slider-Zwischenwerte auf nächste 10er-Stufe
        assertEquals(10, nextLightStep(3))
        assertEquals(50, nextLightStep(41))
        assertEquals(100, nextLightStep(91))
    }
}

class InspectionControlsSondeCodeTest {

    @Test fun off_to_33kHz()     { assertEquals(1, nextSondeCode("Off")) }
    @Test fun hz33_to_640Hz()    { assertEquals(2, nextSondeCode("33 kHz")) }
    @Test fun hz640_to_512Hz()   { assertEquals(3, nextSondeCode("640 Hz")) }
    @Test fun hz512_to_off()     { assertEquals(0, nextSondeCode("512 Hz")) }
    @Test fun null_to_33kHz()    { assertEquals(1, nextSondeCode(null)) }
    @Test fun blank_to_33kHz()   { assertEquals(1, nextSondeCode("")) }

    @Test
    fun zyklus_komplett() {
        // Vollständiger Umlauf aus Off
        var code = 0
        val labels = mutableListOf<Int>()
        repeat(4) {
            code = nextSondeCode(com.uip.oneapp.network.internal.SondeFrequency.name(code))
            labels += code
        }
        assertEquals(listOf(1, 2, 3, 0), labels)
    }
}

/**
 * Aktive Sonde-Frequenz im Popup hervorheben — beide Label-Formate der Dual-Mode-Branch.
 */
class InspectionControlsSondeTest {

    @Test
    fun directFormat_withSpaces_matchesActiveOption() {
        assertTrue(isSondeFrequencyActive(1, "33 kHz"))
        assertTrue(isSondeFrequencyActive(2, "640 Hz"))
        assertTrue(isSondeFrequencyActive(3, "512 Hz"))
        assertFalse(isSondeFrequencyActive(2, "33 kHz"))
        assertFalse(isSondeFrequencyActive(0, "33 kHz"))
    }

    @Test
    fun wifiFormat_withoutSpaces_matchesActiveOption() {
        assertTrue(isSondeFrequencyActive(1, "33kHz"))
        assertTrue(isSondeFrequencyActive(3, "512Hz"))
        assertFalse(isSondeFrequencyActive(2, "512Hz"))
    }

    @Test
    fun off_isActive_whenRxLabelNullOrBlank() {
        assertTrue(isSondeFrequencyActive(0, null))
        assertTrue(isSondeFrequencyActive(0, ""))
        assertTrue(isSondeFrequencyActive(0, "Off"))
        assertFalse(isSondeFrequencyActive(1, null))
    }

    @Test
    fun exactlyOneOption_isActive_perRxLabel() {
        val options = listOf(0, 1, 2, 3)
        listOf(null, "Off", "33 kHz", "33kHz", "640Hz", "512 Hz").forEach { rx ->
            assertEquals(
                "genau eine aktive Option für rx=$rx",
                1,
                options.count { isSondeFrequencyActive(it, rx) }
            )
        }
    }
}

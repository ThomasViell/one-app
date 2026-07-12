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

    // nextSondeCode(Int): Zyklus 0→1→2→3→0
    @Test fun code0_to_1()      { assertEquals(1, nextSondeCode(0)) }
    @Test fun code1_to_2()      { assertEquals(2, nextSondeCode(1)) }
    @Test fun code2_to_3()      { assertEquals(3, nextSondeCode(2)) }
    @Test fun code3_to_0()      { assertEquals(0, nextSondeCode(3)) }
    @Test fun unknown_to_1()    { assertEquals(1, nextSondeCode(-1)) }

    @Test
    fun zyklus_komplett() {
        var code = 0
        val result = mutableListOf<Int>()
        repeat(4) { code = nextSondeCode(code); result += code }
        assertEquals(listOf(1, 2, 3, 0), result)
    }

    // sondeCodeFromLabel: RX-Label → TX-Code (Initialbelegung)
    @Test fun label_33kHz_space()  { assertEquals(1, sondeCodeFromLabel("33 kHz")) }
    @Test fun label_33kHz_nospace(){ assertEquals(1, sondeCodeFromLabel("33kHz")) }
    @Test fun label_512Hz()        { assertEquals(3, sondeCodeFromLabel("512 Hz")) }
    @Test fun label_null()         { assertEquals(0, sondeCodeFromLabel(null)) }
    @Test fun label_off()          { assertEquals(0, sondeCodeFromLabel("Off")) }
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

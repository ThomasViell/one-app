package com.uip.oneapp.ui.screens.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Louis-Welle 1 QW1: Licht-Tasten-Zyklus deckelt bei 100 % (vorher 90 %).
 */
class InspectionControlsLightTest {

    @Test
    fun lightCycle_reaches100_andWraps() {
        assertEquals(30, nextLightLevel(0))
        assertEquals(60, nextLightLevel(30))
        assertEquals(100, nextLightLevel(60))
        assertEquals(0, nextLightLevel(100))
    }

    @Test
    fun lightCycle_snapsUpFromIntermediateValues() {
        // Der Slider kann Zwischenwerte setzen; der nächste Tipp springt zur nächsthöheren Stufe.
        assertEquals(30, nextLightLevel(15))
        assertEquals(60, nextLightLevel(45))
        // Regression zum Fix: bei 90 % nicht mehr auf 0 zurück, sondern auf 100 %.
        assertEquals(100, nextLightLevel(90))
    }

    @Test
    fun lightCycle_threeTapsFromOff_reach100() {
        // Prüfkriterium: nach 3× Licht-Tipp steht der Wert auf 100 %.
        var level = 0
        repeat(3) { level = nextLightLevel(level) }
        assertEquals(100, level)
    }
}

/**
 * Louis-Welle 1 QW2: Aktive Sonde-Frequenz wird im Popup hervorgehoben.
 * Prüft beide Label-Formate der Dual-Mode-Branch.
 */
class InspectionControlsSondeTest {

    @Test
    fun directFormat_withSpaces_matchesActiveOption() {
        // OneInternalHardwareService liefert "33 kHz"/"640 Hz"/"512 Hz" (SondeFrequency.name()).
        assertTrue(isSondeFrequencyActive(1, "33 kHz"))
        assertTrue(isSondeFrequencyActive(2, "640 Hz"))
        assertTrue(isSondeFrequencyActive(3, "512 Hz"))
        assertFalse(isSondeFrequencyActive(2, "33 kHz"))
        assertFalse(isSondeFrequencyActive(0, "33 kHz"))
    }

    @Test
    fun wifiFormat_withoutSpaces_matchesActiveOption() {
        // OneHardwareService liefert "33kHz"/"640Hz"/"512Hz" (OneRemoteProtocol.freqLabel()).
        assertTrue(isSondeFrequencyActive(1, "33kHz"))
        assertTrue(isSondeFrequencyActive(3, "512Hz"))
        assertFalse(isSondeFrequencyActive(2, "512Hz"))
    }

    @Test
    fun off_isActive_whenRxLabelNullOrBlank() {
        // WIFI/Remote meldet Off als null; DIRECT als "Off". Beide → Off-Option (Code 0) aktiv.
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

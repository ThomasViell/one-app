package com.uip.oneapp.ui.screens.inspection

import org.junit.Assert.assertEquals
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

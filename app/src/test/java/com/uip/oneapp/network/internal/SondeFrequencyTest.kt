package com.uip.oneapp.network.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AP4 (M8): Sichert, dass TX-Auswahl und RX-Anzeige der Sonde-Frequenz aus EINER Quelle kommen
 * und die OEM-Zuordnung tragen. Verhindert die frühere Inversion (UI sendete 512Hz↔33kHz vertauscht).
 */
class SondeFrequencyTest {

    @Test
    fun canonicalMapping_matchesOemControlArgs() {
        assertEquals("Off", SondeFrequency.name(0))
        assertEquals("33 kHz", SondeFrequency.name(1))
        assertEquals("640 Hz", SondeFrequency.name(2))
        assertEquals("512 Hz", SondeFrequency.name(3))
    }

    @Test
    fun selectableCodes_areOneTwoThree_inDisplayOrder() {
        assertEquals(listOf(1, 2, 3), SondeFrequency.selectableCodes)
    }

    @Test
    fun txCodeAndRxDisplay_areConsistentBySingleSource() {
        // Der Code, den die UI für ein Label sendet, ergibt bei der RX-Anzeige dasselbe Label —
        // beide Pfade nutzen SondeFrequency.name(). Roundtrip Label→Code→Label.
        SondeFrequency.selectableCodes.forEach { code ->
            val label = SondeFrequency.name(code)
            val rxCodeForLabel = SondeFrequency.selectableCodes.first { SondeFrequency.name(it) == label }
            assertEquals(code, rxCodeForLabel)
        }
    }

    @Test
    fun unknownCode_isLabeledNotCrashing() {
        assertTrue(SondeFrequency.name(9).contains("Unknown"))
    }
}

package com.uip.oneapp.kiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sichert die (Android-freie) Zielwahl von „App verlassen" ab (Kette kiosk-pflicht, 03.09.2026,
 * Plan E5): Aus den HOME-fähigen Aktivitäten wird die Systemoberfläche gewählt — eigenes Paket
 * und `FallbackHome` (Notfall-Home der Einrichtung, kein Bediener-Ziel) fallen raus. Bleibt kein
 * Kandidat, liefert `choose` null und der Kiosk bleibt aktiv.
 */
class LeaveAppTargetTest {

    private val launcher3 = "com.android.launcher3" to ".uioverrides.QuickstepLauncher"
    private val own = "com.uip.drainq.one" to "com.uip.oneapp.MainActivity"
    private val fallbackHome = "com.android.settings" to ".FallbackHome"

    @Test
    fun dreiKandidaten_wieAmGeraetGemessen_liefertLauncher3() {
        // Messung M0, 03.09.2026: query-activities HOME = launcher3, unsere App, FallbackHome.
        val chosen = LeaveAppTarget.choose(listOf(launcher3, own, fallbackHome), own.first)
        assertEquals(launcher3, chosen)
    }

    @Test
    fun nurEigenesPaketUndFallbackHome_liefertNull() {
        val chosen = LeaveAppTarget.choose(listOf(own, fallbackHome), own.first)
        assertNull(chosen)
    }

    @Test
    fun leereListe_liefertNull() {
        assertNull(LeaveAppTarget.choose(emptyList(), own.first))
    }

    @Test
    fun mehrereFremdeKandidaten_nimmtErsten() {
        val anderer = "com.example.home" to ".Home"
        val chosen = LeaveAppTarget.choose(listOf(launcher3, anderer, own), own.first)
        assertEquals(launcher3, chosen)
    }

    @Test
    fun eigenesPaketAnAndererStelle_wirdTrotzdemAusgefiltert() {
        val chosen = LeaveAppTarget.choose(listOf(own, launcher3), own.first)
        assertEquals(launcher3, chosen)
    }
}

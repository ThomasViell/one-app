package com.uip.oneapp.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Welle geraetezeit Z-1 / Plan Schritt 11: reines JUnit gegen die vier Logikfunktionen
 * von `DateTimeScreen.kt` — kein Compose (Compose-Semantik-Tests sind im JVM-Test nicht
 * lauffaehig, siehe OfflineMapsFlagTest), kein Pixel, kein Klick.
 *
 * Rot vor dem Bau (belege/b3_test_rot.txt), gruen danach (belege/b3_test_gruen.txt).
 */
class DateTimeScreenTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val utc: ZoneOffset = ZoneOffset.UTC

    // --- composeEpoch ---

    @Test
    fun composeEpoch_berlinWallClock_equalsUtcEpoch() {
        // Plan-Vorgabe: 10.09.2026 09:27 Europe/Berlin = 07:27 UTC.
        val berlinWallClock = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2))
        val expected = berlinWallClock.toEpochMilli()
        val actual = composeEpoch(LocalDate.of(2026, 9, 10), LocalTime.of(9, 27), berlin)
        assertEquals(expected, actual)
    }

    @Test
    fun composeEpoch_sameWallClockDifferentZone_otherEpoch() {
        val date = LocalDate.of(2026, 9, 10)
        val time = LocalTime.of(9, 27)
        assertNotEquals(composeEpoch(date, time, berlin), composeEpoch(date, time, utc))
    }

    // --- filterZones ---

    @Test
    fun filterZones_queryBerl_findsBerlin() {
        val zones = listOf("Europe/Berlin", "Europe/Paris", "UTC")
        assertEquals(listOf("Europe/Berlin"), filterZones(zones, "berl"))
    }

    @Test
    fun filterZones_caseInsensitive() {
        val zones = listOf("Europe/Berlin", "Europe/Paris")
        assertEquals(listOf("Europe/Berlin"), filterZones(zones, "BERL"))
    }

    @Test
    fun filterZones_emptyQuery_fullListSorted() {
        val zones = listOf("UTC", "Europe/Berlin", "Europe/Paris")
        assertEquals(listOf("Europe/Berlin", "Europe/Paris", "UTC"), filterZones(zones, ""))
    }

    @Test
    fun filterZones_noMatch_emptyList() {
        val zones = listOf("Europe/Berlin", "Europe/Paris")
        assertTrue(filterZones(zones, "nirgendwo").isEmpty())
    }

    @Test
    fun filterZones_stableOrder_acrossCalls() {
        val zones = listOf("Europe/Berlin", "UTC", "America/New_York", "Europe/Paris")
        assertEquals(filterZones(zones, ""), filterZones(zones, ""))
    }

    // --- zoneOffsetLabel ---

    @Test
    fun zoneOffsetLabel_summerAndWinter() {
        val summer = LocalDateTime.of(2026, 7, 1, 12, 0).toInstant(utc).toEpochMilli()
        val winter = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(utc).toEpochMilli()
        assertEquals("UTC+02:00", zoneOffsetLabel(berlin, summer))
        assertEquals("UTC+01:00", zoneOffsetLabel(berlin, winter))
    }

    @Test
    fun zoneOffsetLabel_negativeOffset() {
        val newYork = ZoneId.of("America/New_York")
        val winter = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(utc).toEpochMilli()
        assertEquals("UTC-05:00", zoneOffsetLabel(newYork, winter))
    }

    @Test
    fun zoneOffsetLabel_fractionalOffset() {
        // Kathmandu: +05:45 — prueft auch das Minuten-Padding.
        val kathmandu = ZoneId.of("Asia/Kathmandu")
        val epoch = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(utc).toEpochMilli()
        assertEquals("UTC+05:45", zoneOffsetLabel(kathmandu, epoch))
    }

    // --- formatForDisplay ---

    @Test
    fun formatForDisplay_de_usesGermanPattern() {
        val epoch = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        assertEquals("10.09.2026, 09:27", formatForDisplay(epoch, berlin, "de"))
    }

    @Test
    fun formatForDisplay_en_usesEnglishPattern() {
        val epoch = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        assertEquals("09/10/2026, 09:27", formatForDisplay(epoch, berlin, "en"))
    }
}

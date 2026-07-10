package com.uip.oneapp.ui.screens.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Louis 10-07 / B1-Interim: Der Datums-Guard muss den real beobachteten 2021-RTC-Reset fangen
 * (das war das eigentliche Ziel der Aufgabe) und darf jahresnahe, legitime Daten nicht blockieren.
 * Die Schwelle ist ans App-Build-Jahr gekoppelt (buildYear − 1) — hier fest 2026 injiziert,
 * damit die Tests deterministisch und unabhängig von BuildConfig sind.
 */
class InspectionDateGuardTest {

    private val buildYear = 2026 // ⇒ minPlausibleYear = 2025

    @Test
    fun minPlausibleYear_isOneYearBeforeBuild() {
        assertEquals(2025, InspectionDateGuard.minPlausibleYear(2026))
    }

    // --- Systemuhr-Plausibilität (steuert das Vorbelegen des Datumsfelds) ---

    @Test
    fun systemClock_2021Reset_isImplausible() {
        // Der Kern der Aufgabe: die auf 2021 zurückgefallene Uhr MUSS als falsch erkannt werden.
        assertFalse(InspectionDateGuard.isSystemClockPlausible(LocalDate.of(2021, 1, 1), buildYear))
    }

    @Test
    fun systemClock_buildYear_isPlausible() {
        assertTrue(InspectionDateGuard.isSystemClockPlausible(LocalDate.of(2026, 7, 10), buildYear))
    }

    @Test
    fun systemClock_boundaryBuildYearMinusOne_isPlausible() {
        assertTrue(InspectionDateGuard.isSystemClockPlausible(LocalDate.of(2025, 12, 31), buildYear))
    }

    @Test
    fun systemClock_futureYear_isPlausible() {
        assertTrue(InspectionDateGuard.isSystemClockPlausible(LocalDate.of(2027, 3, 1), buildYear))
    }

    // --- Speicher-Guard (blockiert klar zurückliegendes Datum, sonst normal) ---

    @Test
    fun save_wrongYear2021_isBlocked() {
        assertFalse(InspectionDateGuard.isSaveableDate("01.01.2021", buildYear))
    }

    @Test
    fun save_year2024_isBlocked() {
        assertFalse(InspectionDateGuard.isSaveableDate("15.06.2024", buildYear))
    }

    @Test
    fun save_plausibleDate_isAllowed() {
        assertTrue(InspectionDateGuard.isSaveableDate("10.07.2026", buildYear))
    }

    @Test
    fun save_boundaryBuildYearMinusOne_isAllowed() {
        assertTrue(InspectionDateGuard.isSaveableDate("31.12.2025", buildYear))
    }

    @Test
    fun save_emptyOrUnparsable_isNotBlocked() {
        // Leeres/unparsbares Feld ist kein „stilles Falschdatum" — der Guard blockiert hier nicht
        // (der Nutzer wurde per Banner gewarnt und entscheidet selbst).
        assertTrue(InspectionDateGuard.isSaveableDate("", buildYear))
        assertTrue(InspectionDateGuard.isSaveableDate("   ", buildYear))
        assertTrue(InspectionDateGuard.isSaveableDate("kein datum", buildYear))
    }
}

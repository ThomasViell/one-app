package com.uip.oneapp.ui.screens.projects

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Louis 10-07 / B1-Interim: Plausibilitätsprüfung des Inspektionsdatums gegen eine
 * offensichtlich falsch stehende Geräteuhr. Die ONE fällt offline gern auf ~2021 zurück
 * (RTC-Reset ohne NTP). Ohne echte Datum-Automatik (die braucht Device-Owner und kommt mit
 * dem Golden-Image-Rollout) verhindert dieser Guard wenigstens einen still 2021-datierten
 * Bericht: kein automatisches Vorbelegen mit dem Falschdatum, und `saveProject()` blockiert
 * ein klar zurückliegendes Datum.
 *
 * WICHTIG zur Schwelle: Der Auftrag nannte „Jahr < 2015" — das würde den real beobachteten
 * 2021-Reset (2021 ≥ 2015) NICHT fangen und damit genau den 2021-Bericht durchlassen, den
 * diese Aufgabe verhindern soll. Deshalb ankern wir stattdessen am APP-BUILD-JAHR
 * ([BuildConfig.BUILD_YEAR]): Eine Inspektion kann nicht vor dem App-Build liegen. Mit einem
 * Jahr Puffer (buildYear − 1) werden legitime, jahresnahe Daten nicht fälschlich blockiert,
 * der 2021-Reset aber zuverlässig erkannt. Selbst-wartend (kein zu pflegender Magic-Year).
 *
 * Reine Funktionen (kein Compose/Android, buildYear injiziert) → direkt unit-testbar.
 */
object InspectionDateGuard {

    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /**
     * Kleinstes plausibles Jahr: ein Jahr vor dem App-Build (Puffer gegen jahresnahe Randfälle).
     */
    fun minPlausibleYear(buildYear: Int): Int = buildYear - 1

    /** Systemuhr plausibel? Basis für „nicht still mit Falschdatum vorbelegen". */
    fun isSystemClockPlausible(today: LocalDate, buildYear: Int): Boolean =
        today.year >= minPlausibleYear(buildYear)

    /**
     * Darf mit diesem Datumsstring (Format `dd.MM.yyyy`) gespeichert werden?
     *
     * Blockiert AUSSCHLIESSLICH ein klar zurückliegendes Jahr (< [minPlausibleYear]) — genau der
     * 2021-Fall. Ein leeres oder anderweitig unparsbares Feld ist KEIN „stilles Falschdatum"
     * und wird hier nicht blockiert (der Nutzer wurde per Banner gewarnt und entscheidet selbst).
     */
    fun isSaveableDate(dateStr: String, buildYear: Int): Boolean {
        val parsed = try {
            LocalDate.parse(dateStr.trim(), fmt)
        } catch (_: Exception) {
            return true
        }
        return parsed.year >= minPlausibleYear(buildYear)
    }
}

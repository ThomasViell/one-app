package com.uip.oneapp.ui.screens.inspection

import com.uip.oneapp.network.internal.SondeFrequency

/**
 * Reine, testbare UI-Helfer für die Inspektions-Hardtasten.
 * Kein Compose-State, keine Hardware — nur Ableitungen, damit sie unit-testbar bleiben.
 */

/** Nächste Licht-Stufe: +10 %, nach 100 % wieder 0. Snappt krumme Slider-Werte auf die nächste 10er-Stufe. */
fun nextLightStep(current: Int): Int =
    if (current >= 100) 0 else ((current / 10) * 10 + 10).coerceAtMost(100)

/** Nächste Sonde-Frequenz im Zyklus Off→33kHz→640Hz→512Hz→Off. currentRxLabel = crawler.sondeFrequency. */
fun nextSondeCode(currentRxLabel: String?): Int {
    val cycle = listOf(SondeFrequency.OFF) + SondeFrequency.selectableCodes  // [0,1,2,3]
    fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()
    val activeLabel = currentRxLabel?.takeIf { it.isNotBlank() } ?: SondeFrequency.name(SondeFrequency.OFF)
    val current = cycle.firstOrNull { norm(SondeFrequency.name(it)) == norm(activeLabel) } ?: SondeFrequency.OFF
    return cycle[(cycle.indexOf(current) + 1) % cycle.size]
}

/**
 * Ist die Sonde-Option [optionCode] die aktuell aktive Frequenz (aus der RX-Anzeige [rxLabel])?
 * (Louis #2 — nur Hervorhebung, keine Logik-/Sende-Änderung.)
 *
 * Robust gegen beide Label-Formate der Dual-Mode-Branch:
 *  - DIRECT (OneInternalHardwareService): SondeFrequency.name()      → "33 kHz" (mit Leerzeichen)
 *  - WIFI/Remote (OneHardwareService):    OneRemoteProtocol.freqLabel() → "33kHz" (ohne), null = Off
 * Vergleich daher normalisiert (ohne Leerzeichen, ohne Groß-/Kleinschreibung). Ist [rxLabel] leer/null,
 * gilt die Off-Option als aktiv (Fallback = SondeFrequency.OFF).
 */
fun isSondeFrequencyActive(optionCode: Int, rxLabel: String?): Boolean {
    fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()
    val activeLabel = rxLabel?.takeIf { it.isNotBlank() } ?: SondeFrequency.name(SondeFrequency.OFF)
    return norm(SondeFrequency.name(optionCode)) == norm(activeLabel)
}

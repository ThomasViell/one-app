package com.uip.oneapp.ui.screens.inspection

import com.uip.oneapp.network.internal.SondeFrequency

/**
 * Reine, testbare UI-Helfer für die Inspektions-Hardtasten (Louis-Welle 1).
 * Kein Compose-State, keine Hardware — nur Ableitungen, damit sie unit-testbar bleiben.
 */

/** Licht-Tasten-Zyklus (Louis #4): 0 → 30 → 60 → 100 → 0. Deckelt jetzt bei 100 % statt 90 %. */
val LightCycle = intArrayOf(0, 30, 60, 100)

/** Nächste Stufe im Licht-Zyklus oberhalb des aktuellen Werts; nach der höchsten wieder 0. */
fun nextLightLevel(current: Int): Int = LightCycle.firstOrNull { it > current } ?: 0

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

package com.uip.oneapp.ui.screens.inspection

/**
 * Reine, testbare UI-Helfer für die Inspektions-Hardtasten (Louis-Welle 1).
 * Kein Compose-State, keine Hardware — nur Ableitungen, damit sie unit-testbar bleiben.
 */

/** Licht-Tasten-Zyklus (Louis #4): 0 → 30 → 60 → 100 → 0. Deckelt jetzt bei 100 % statt 90 %. */
val LightCycle = intArrayOf(0, 30, 60, 100)

/** Nächste Stufe im Licht-Zyklus oberhalb des aktuellen Werts; nach der höchsten wieder 0. */
fun nextLightLevel(current: Int): Int = LightCycle.firstOrNull { it > current } ?: 0

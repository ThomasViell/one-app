package com.uip.oneapp.ui.screens.inspection

/**
 * Parst die Metereingabe: Komma → Punkt, dann toFloat.
 * Gibt [fallback] zurück wenn der Text keine gültige Zahl ist,
 * oder null wenn beides fehlt — null blockiert das Speichern im Dialog.
 */
fun parseMeterInput(text: String, fallback: Float?): Float? =
    text.replace(",", ".").toFloatOrNull() ?: fallback

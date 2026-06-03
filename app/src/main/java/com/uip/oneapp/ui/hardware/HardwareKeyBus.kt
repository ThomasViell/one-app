package com.uip.oneapp.ui.hardware

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hardtasten der ONE. Belegung 1:1 wie die Original-App (F1–F8 / KeyCode 131–138):
 * F1 Licht, F2 Sonde, F3 Aufnahme, F4 Aufnahme-Stop, F5 Foto, F6 Galerie,
 * F7 Tag/Nacht, F8 Einstellungen.
 *
 * Die Einträge sind zugleich die gemeinsame Aktionsliste: Hardtaste und der
 * positionsgleiche Softbutton in der Inspektions-Leiste lösen dieselbe Aktion aus.
 */
enum class HwButton { POWER, LIGHT, SONDE, RECORD, RECORD_STOP, PHOTO, GALLERY, DAYNIGHT, SETTINGS }

/**
 * Reihenfolge = Original-Layout (untere Leiste, links → rechts):
 * Power, Licht(F1), Sonde(F2), Aufnahme(F3), Stop(F4), Foto(F5), Galerie(F6),
 * Tag/Nacht(F7), Einstellungen(F8). Power hat keine Hardtaste (nur Softbutton).
 */
val HwButtonOrder = listOf(
    HwButton.POWER, HwButton.LIGHT, HwButton.SONDE, HwButton.RECORD, HwButton.RECORD_STOP,
    HwButton.PHOTO, HwButton.GALLERY, HwButton.DAYNIGHT, HwButton.SETTINGS
)

object HardwareKeyBus {
    private val _events = MutableSharedFlow<HwButton>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    /** KeyCode 131..138 (F1..F8) → HwButton, sonst null. */
    fun fromKeyCode(keyCode: Int): HwButton? = when (keyCode) {
        131 -> HwButton.LIGHT
        132 -> HwButton.SONDE
        133 -> HwButton.RECORD
        134 -> HwButton.RECORD_STOP
        135 -> HwButton.PHOTO
        136 -> HwButton.GALLERY
        137 -> HwButton.DAYNIGHT
        138 -> HwButton.SETTINGS
        else -> null
    }

    fun emit(button: HwButton): Boolean = _events.tryEmit(button)
}

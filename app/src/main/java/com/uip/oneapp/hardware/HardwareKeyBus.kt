package com.uip.oneapp.hardware

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * HardwareKeyBus - globaler Event-Bus fuer die 8 programmierbaren Hardware-
 * Tasten unter dem ONE-Tablet-Display (KeyCodes 131-138).
 *
 * MainActivity.onKeyDown emittiert hier rein, die InspectionScreen-Composable
 * collectet via SharedFlow.
 */
object HardwareKeyBus {

    enum class Action {
        LIGHT,        // 131 — Slider-Overlay + cycle +10%
        LIGHT_LONG,   // 131 long
        SONDE,        // 132 — cycle 4 Frequenzen
        SONDE_LONG,   // 132 long
        REC_TOGGLE,   // 133 — Start/Stop kontextabhaengig
        PHOTO,        // 134 — Bitmap-Capture
        GALLERY,      // 135 — Project-Detail
        DAY_NIGHT,    // 136 — Schwarzweiss-Filter toggle
        DAMAGE,       // 137 — Schaden-Erfassen-Dialog
        SETTINGS      // 138 — Einstellungen
    }

    private val _events = MutableSharedFlow<Action>(extraBufferCapacity = 16)
    val events: SharedFlow<Action> = _events.asSharedFlow()

    fun emit(action: Action): Boolean = _events.tryEmit(action)
}

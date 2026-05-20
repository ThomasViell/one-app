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
        LIGHT,
        LIGHT_LONG,
        SONDE,
        SONDE_LONG,
        REC_START,
        REC_STOP,
        PHOTO,
        GALLERY,
        DAY_NIGHT,
        SETTINGS
    }

    private val _events = MutableSharedFlow<Action>(extraBufferCapacity = 16)
    val events: SharedFlow<Action> = _events.asSharedFlow()

    fun emit(action: Action): Boolean = _events.tryEmit(action)
}

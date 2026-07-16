package com.uip.oneapp.debugrig

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bus between [ScreenshotRigReceiver] (adb broadcast handler) and the Compose UI.
 * Debug-only — production code must NEVER import this class directly; access is gated via
 * BuildConfig.DEBUG guards at each call site.
 */
object ScreenshotRigBus {

    private val _navigate = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigate = _navigate.asSharedFlow()

    private val _uiState = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiState = _uiState.asSharedFlow()

    fun emitNavigate(route: String) {
        _navigate.tryEmit(route)
    }

    fun emitUiState(state: String) {
        _uiState.tryEmit(state)
    }
}

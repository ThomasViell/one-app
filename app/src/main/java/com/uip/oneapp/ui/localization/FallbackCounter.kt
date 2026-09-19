package com.uip.oneapp.ui.localization

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Z-6: Zaehlt jeden stillen Rueckfall in der `getString`-Kette (E-P5). Kein DataStore-Zugriff
 * hier im Nachschlagepfad — `SettingsViewModel` spiegelt die Zahlen beim Betreten der
 * Einstellungen bzw. beim Sprachwechsel in den Einstellungen-DataStore.
 */
object FallbackCounter {

    private const val RING_SIZE = 10

    private val _toEnglish = AtomicInteger(0)
    private val _toKeyName = AtomicInteger(0)

    private val _missingKeys = MutableStateFlow<List<String>>(emptyList())
    val missingKeys: StateFlow<List<String>> = _missingKeys.asStateFlow()

    val toEnglishCount: Int get() = _toEnglish.get()
    val toKeyNameCount: Int get() = _toKeyName.get()

    @Synchronized
    fun recordToEnglish(key: String) {
        _toEnglish.incrementAndGet()
        pushMissing(key)
    }

    @Synchronized
    fun recordToKeyName(key: String) {
        _toKeyName.incrementAndGet()
        pushMissing(key)
    }

    @Synchronized
    private fun pushMissing(key: String) {
        val next = (listOf(key) + _missingKeys.value).distinct().take(RING_SIZE)
        _missingKeys.value = next
    }

    @androidx.annotation.VisibleForTesting
    @Synchronized
    fun reset() {
        _toEnglish.set(0)
        _toKeyName.set(0)
        _missingKeys.value = emptyList()
    }
}

package com.uip.oneapp.ui.localization

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist, dass setLanguageAwait den DataStore-Write abwartet, bevor es zurückkehrt.
 * Testbar durch: Write → In-Memory-Reset (simuliert Kill) → init() liest zurück.
 * Der Kill-Pfad selbst ist nicht unit-testbar (Prozess-Kill beendet den Test-Prozess).
 */
@RunWith(AndroidJUnit4::class)
class LocalizationManagerTest {

    @Test
    fun setLanguageAwait_persistiert_dauerhaft_in_DataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Ausgangszustand sauber auf "de" setzen
        LocalizationManager.setLanguageAwait(context, "de")

        // Auf "en" wechseln — DataStore-Write muss vor Rückkehr abgeschlossen sein
        LocalizationManager.setLanguageAwait(context, "en")

        // In-Memory-Wert auf "de" zurücksetzen: simuliert Prozess-Kill + Neustart
        val field = LocalizationManager::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(LocalizationManager) as MutableStateFlow<String>).value = "de"
        assertEquals("de", LocalizationManager.currentLanguage.value) // Vorbedingung

        // init() liest aus DataStore — wenn "en" durable geschrieben wurde, setzt es "en"
        LocalizationManager.init(context)

        val result = withTimeout(2_000) { LocalizationManager.currentLanguage.first { it == "en" } }
        assertEquals("en", result)
    }
}

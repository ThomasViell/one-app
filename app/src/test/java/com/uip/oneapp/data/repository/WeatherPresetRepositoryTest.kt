package com.uip.oneapp.data.repository

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R-2 (Antwort des Beraters auf PLAN-Rueckfrage R-2, 19.09.2026): `WeatherPresetRepository.load`
 * parst seinen DataStore-Bestand ohne Fehlerbehandlung -- derselbe Absturzpfad wie
 * `DamagePresetRepository.parseStored` (A-3, Z-1). Ein beschaedigter Bestand wirft die
 * Ausnahme bis in den Aufrufer, auf dem Geraet ein Prozessabsturz beim Start des Repositorys.
 *
 * Rot-Beweis: am Ausgangskopf 24a18ee wirft `load()` (gerufen ueber `seedRawForTest`) die
 * Ausnahme ungefangen bis in den Test. Der Test-Konstruktor und `seedRawForTest` existierten
 * am Ausgangskopf nicht -- eigene Zeile im Rot-zuerst-Nachweis des Berichts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WeatherPresetRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var counter = 0

    private fun uniqueStoreName(): String = "test_weather_presets_${System.nanoTime()}_${counter++}"

    private fun newRepo(storeName: String = uniqueStoreName()): WeatherPresetRepository =
        WeatherPresetRepository(context, storeName)

    @Test
    fun corruptStored_fallsBackToDefaults() = runBlocking {
        val repo = newRepo()
        repo.presets.first { it.isNotEmpty() } // erster (leerer Alt-)Zustand ist geladen
        // Nutzerliste im Speicher statt ueber addPreset (das persistiert): zweites
        // Store-Schreiben scheitert unter Robolectric/Windows, belege/h1_platform_sonde.txt.
        repo.seedUserStateInMemoryForTest(listOf("Eigen"))
        repo.seedRawForTest("[{\"a\":1}]")
        val loaded = repo.presets.first { it == WeatherPresetRepository.getDefaultPresets() }
        assertEquals(WeatherPresetRepository.getDefaultPresets(), loaded)
    }
}

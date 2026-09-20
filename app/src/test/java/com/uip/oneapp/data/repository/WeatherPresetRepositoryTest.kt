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
 *
 * N-3 (Runde 2, Befund B-6): `[null]`-Bestand wurde bisher ALS Liste mit null-Element
 * gesetzt (fromJson liefert fuer `[null]` eine Liste mit einem null-String). Gleiche Form
 * wie die Schadensseite: filterNotNull(). Rot-Beweis am Kopf 24a18ee + Test-Hooks
 * (belege/n3_rot_raw.txt): beide Tests sind dort rot, weil das null-Element stehen bleibt.
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

    // N-3 (Runde 2, Befund B-6): seedRawForTest ruft load() direkt -- nach der Rueckkehr
    // steht der Endzustand fest, gewartet werden muss nur auf den Ladezustand davor.
    // Kein null-Element darf die Nutzerliste erreichen.
    @Test
    fun corruptStored_nullElementOnly_isFilteredToEmpty() = runBlocking {
        val repo = newRepo()
        repo.presets.first { it.isNotEmpty() } // erster Ladezustand (Standardliste) steht
        repo.seedRawForTest("[null]")
        assertEquals(
            "ein Bestand [null] muss auf die leere Liste zurueckfallen, nicht auf eine Liste mit null-Element",
            emptyList<String>(),
            repo.presets.value
        )
    }

    @Test
    fun corruptStored_mixedNullElements_areFiltered() = runBlocking {
        val repo = newRepo()
        repo.presets.first { it.isNotEmpty() } // erster Ladezustand (Standardliste) steht
        repo.seedRawForTest("[\"Regen\",null,\"Schnee\"]")
        assertEquals(listOf("Regen", "Schnee"), repo.presets.value)
    }
}

package com.uip.oneapp.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uip.oneapp.ui.localization.LocalizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.weatherStore by preferencesDataStore(name = "weather_presets")

private const val TAG = "WeatherPresetRepository"

class WeatherPresetRepository private constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.weatherStore)

    /**
     * Fuer Tests: eigener, per Aufruf frischer Dateipfad statt der App-weit einmaligen
     * `context.weatherStore`-Instanz -- derselbe Grund wie beim Test-Konstruktor von
     * `DamagePresetRepository` (DataStore haelt einen Dateihandle prozessweit offen;
     * mehrere Repository-Instanzen auf demselben physischen Pfad kollidieren in einem
     * Robolectric-Testklassenlauf beim Schreiben).
     */
    @androidx.annotation.VisibleForTesting
    constructor(context: Context, testStoreName: String) : this(
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(testStoreName) }
        )
    )

    companion object {
        private val KEY_PRESETS = stringPreferencesKey("weather_presets_json")
        private val DEFAULT_PRESET_KEYS = listOf(
            "weather_sunny", "weather_cloudy", "weather_partly_cloudy",
            "weather_rain", "weather_light_rain", "weather_heavy_rain",
            "weather_snow", "weather_fog", "weather_thunderstorm",
            "weather_dry", "weather_windy"
        )

        fun getDefaultPresets(): List<String> =
            DEFAULT_PRESET_KEYS.map { LocalizationManager.getString(it) }
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        try {
            val prefs = store.data.first()
            val json = prefs[KEY_PRESETS]
            if (json != null) {
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(json, type)
                // N-3 (Runde 2, Befund B-6): null-Elemente aussieben statt sie in die
                // Nutzerliste zu tragen -- gleiche Form wie
                // DamagePresetRepository.parseStored (dort legacy.filterNotNull(), A-3/Z-1).
                // Ein Bestand `[null]` haette sonst eine Liste MIT null-Element gesetzt.
                _presets.value = list.filterNotNull()
            } else {
                _presets.value = getDefaultPresets()
            }
        } catch (e: Exception) {
            // R-2 (ANTWORT AUF R-2): beschaedigter Bestand darf das Repository nicht
            // stoppen -- gleiche Form wie DamagePresetRepository.parseStored (A-3, Z-1).
            // Der Rueckfall wird nicht sofort persistiert; erst das naechste save()
            // schreibt wieder ein gueltiges Format.
            Log.w(TAG, "Wetter-Preset-Bestand unlesbar, Standardliste: ${e.javaClass.simpleName}")
            _presets.value = getDefaultPresets()
        }
    }

    private suspend fun save(list: List<String>) {
        store.edit { prefs ->
            prefs[KEY_PRESETS] = gson.toJson(list)
        }
        _presets.value = list
    }

    fun addPreset(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val current = _presets.value.toMutableList()
            current.add(name.trim())
            save(current)
        }
    }

    fun removePreset(index: Int) {
        scope.launch {
            val current = _presets.value.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                save(current)
            }
        }
    }

    fun updatePreset(index: Int, newName: String) {
        if (newName.isBlank()) return
        scope.launch {
            val current = _presets.value.toMutableList()
            if (index in current.indices) {
                current[index] = newName.trim()
                save(current)
            }
        }
    }

    fun resetToDefaults() {
        scope.launch {
            save(getDefaultPresets())
        }
    }

    /**
     * Nur fuer Tests (R-2, Rot-Beweis gegen den Ausgangskopf): schreibt rohen Bestand ueber
     * DIESELBE `store`-Instanz, die dieses Repository bereits besitzt, und laedt danach neu
     * (Muster `DamagePresetRepository.seedRawForTest`, RB-6).
     */
    @androidx.annotation.VisibleForTesting
    suspend fun seedRawForTest(json: String) {
        store.edit { prefs -> prefs[KEY_PRESETS] = json }
        load()
    }

    /**
     * Nur fuer Tests (R-2): haengt Nutzereintraege IN-MEMORY an die Standardliste, ohne den
     * DataStore anzufassen -- gleicher Grund wie bei
     * `DamagePresetRepository.seedUserStateInMemoryForTest` (belege/h1_platform_sonde.txt).
     */
    @androidx.annotation.VisibleForTesting
    fun seedUserStateInMemoryForTest(customTexts: List<String>) {
        _presets.value = getDefaultPresets() + customTexts
    }
}

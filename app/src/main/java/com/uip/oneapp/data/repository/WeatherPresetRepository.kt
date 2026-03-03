package com.uip.oneapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

class WeatherPresetRepository(private val context: Context) {

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
        val prefs = context.weatherStore.data.first()
        val json = prefs[KEY_PRESETS]
        if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(json, type)
            _presets.value = list
        } else {
            _presets.value = getDefaultPresets()
        }
    }

    private suspend fun save(list: List<String>) {
        context.weatherStore.edit { prefs ->
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
}

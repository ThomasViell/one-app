package com.uip.oneapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        val DEFAULT_PRESETS = listOf(
            "Sonnig", "Bewölkt", "Leicht bewölkt", "Regen", "Leichter Regen",
            "Starker Regen", "Schnee", "Nebel", "Gewitter", "Trocken", "Windig"
        )
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _presets = MutableStateFlow(DEFAULT_PRESETS)
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
            save(DEFAULT_PRESETS)
        }
    }
}

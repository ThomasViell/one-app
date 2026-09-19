package com.uip.oneapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val Context.damageStore by preferencesDataStore(name = "damage_presets")

/**
 * Z-8: eine Standardbezeichnung traegt ihren Schluessel (`key`, folgt der Sprache); eine
 * vom Anwender ergaenzte oder editierte Bezeichnung traegt ihren Text (`text`, bleibt stehen).
 * Genau eines der beiden Felder ist belegt.
 */
internal data class PresetEntry(val key: String? = null, val text: String? = null) {
    fun resolve(lang: String): String = key?.let { LocalizationManager.getString(it, lang) } ?: text.orEmpty()
}

private data class StoredEntryV2(val key: String? = null, val text: String? = null)
private data class StoredV2(val v: Int, val entries: List<StoredEntryV2>)

class DamagePresetRepository private constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.damageStore)

    /**
     * Fuer Tests: eigener, per Aufruf frischer Dateipfad statt der App-weit einmaligen
     * `context.damageStore`-Instanz. androidx DataStore haelt einen Dateihandle fuer die
     * gesamte Lebensdauer offen; mehrere Repository-Instanzen auf demselben physischen Pfad
     * innerhalb eines Robolectric-Testklassenlaufs (derselbe temporaere Sandbox-Ordner ueber
     * alle Testmethoden hinweg) kollidieren sonst beim Schreiben (Windows: "Unable to rename").
     */
    @androidx.annotation.VisibleForTesting
    constructor(context: Context, testStoreName: String) : this(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(testStoreName) }
        )
    )

    companion object {
        private val KEY_PRESETS = stringPreferencesKey("damage_presets_json")
        private val DEFAULT_PRESET_KEYS = listOf(
            "damage_type_crack",
            "damage_type_fracture",
            "damage_type_roots",
            "damage_type_deposit",
            "damage_type_blockage",
            "damage_type_sag",
            "damage_type_other"
        )

        fun getDefaultPresets(): List<String> =
            DEFAULT_PRESET_KEYS.map { LocalizationManager.getString(it) }

        private fun defaultEntries(): List<PresetEntry> = DEFAULT_PRESET_KEYS.map { PresetEntry(key = it) }
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _entries = MutableStateFlow<List<PresetEntry>>(emptyList())
    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    init {
        scope.launch { load() }
        scope.launch {
            // Reagiert auf einen Sprachwechsel, der ausserhalb dieses Repositorys ausgeloest
            // wurde (Settings-Dropdown) -- Standardbezeichnungen folgen der Sprache (Z-8).
            LocalizationManager.currentLanguage.collect { lang ->
                _presets.value = _entries.value.map { it.resolve(lang) }
            }
        }
    }

    private fun recompute() {
        _presets.value = _entries.value.map { it.resolve(LocalizationManager.currentLanguage.value) }
    }

    private suspend fun load() {
        val prefs = store.data.first()
        val json = prefs[KEY_PRESETS]
        _entries.value = if (json == null) defaultEntries() else parseStored(json)
        recompute()
    }

    /**
     * v2: `{"v":2,"entries":[{"key":"..."}|{"text":"..."}]}`. v1 (Altbestand vor Z-8):
     * JSON-Array reiner Texte -- ein Text, der einem Standardwert in irgendeiner der 35
     * Map-Sprachen oder im Portal-Paket gleicht, wird zu `{key}`; alles andere `{text}`
     * (R-2, PLAN_NACHTRAG). Ein nicht erkanntes v1-Format faellt auf die Standardliste
     * zurueck, statt abzustuerzen.
     */
    private fun parseStored(json: String): List<PresetEntry> {
        if (json.trimStart().startsWith("[")) {
            val type = object : TypeToken<List<String>>() {}.type
            val legacy: List<String> = gson.fromJson(json, type)
            return migrateLegacy(legacy)
        }
        val stored = gson.fromJson(json, StoredV2::class.java)
        return stored.entries.map { PresetEntry(key = it.key, text = it.text) }
    }

    private fun migrateLegacy(list: List<String>): List<PresetEntry> = list.map { text ->
        val matchedKey = DEFAULT_PRESET_KEYS.firstOrNull { key -> text in LocalizationManager.allValuesForKey(key) }
        if (matchedKey != null) PresetEntry(key = matchedKey) else PresetEntry(text = text)
    }

    private suspend fun persist(entries: List<PresetEntry>) {
        val json = gson.toJson(StoredV2(v = 2, entries = entries.map { StoredEntryV2(it.key, it.text) }))
        store.edit { prefs -> prefs[KEY_PRESETS] = json }
        _entries.value = entries
        recompute()
    }

    fun addPreset(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val current = _entries.value.toMutableList()
            current.add(PresetEntry(text = name.trim()))
            persist(current)
        }
    }

    fun removePreset(index: Int) {
        scope.launch {
            val current = _entries.value.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                persist(current)
            }
        }
    }

    fun updatePreset(index: Int, newName: String) {
        if (newName.isBlank()) return
        scope.launch {
            val current = _entries.value.toMutableList()
            if (index in current.indices) {
                // Editieren macht aus einer Standardbezeichnung ein Nutzerdatum (Z-8):
                // sie bleibt ab jetzt stehen und folgt der Sprache nicht mehr.
                current[index] = PresetEntry(text = newName.trim())
                persist(current)
            }
        }
    }

    fun resetToDefaults() {
        scope.launch {
            persist(defaultEntries())
        }
    }

    /** Beendet den Hintergrund-Sammler auf Sprachwechsel (Aufraeumen in Tests). */
    fun close() {
        scope.cancel()
    }

    /**
     * Nur fuer Tests (Migrationstest, RB-6): schreibt rohen v1-Altbestand ueber DIESELBE
     * `store`-Instanz, die dieses Repository bereits besitzt, und laedt danach neu.
     * Root-Cause-Fix (Regel 5): eine fruehere Fassung erzeugte zum Seeding eine ZWEITE,
     * unabhaengige `DataStore`-Instanz auf demselben Dateipfad (per
     * `PreferenceDataStoreFactory.create`) -- genau die von androidx DataStore verbotene
     * Konstellation ("multiple instances of DataStore for this file",
     * SingleProcessDataStore.kt:433), die den Testlauf zuverlaessig zum Haengen brachte.
     * Mit nur einer Instanz je Dateipfad tritt der Konflikt nicht mehr auf.
     */
    @androidx.annotation.VisibleForTesting
    suspend fun seedRawForTest(json: String) {
        store.edit { prefs -> prefs[KEY_PRESETS] = json }
        load()
    }
}

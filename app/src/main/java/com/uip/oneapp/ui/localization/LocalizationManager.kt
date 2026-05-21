package com.uip.oneapp.ui.localization

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uip.oneapp.network.l10n.L10nApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Context.langStore by preferencesDataStore(name = "language_prefs")

private const val TAG = "LocalizationManager"
private const val CACHE_DIR = "l10n"
private const val BUNDLE_DE = "l10n_de"
private const val BUNDLE_EN = "l10n_en"

data class AppLanguage(
    val code: String,
    val name: String,
    val flag: String,
    val isBundle: Boolean = false,
    val isCached: Boolean = false,
    val sizeBytes: Long = 0L
)

object LocalizationManager {

    private val KEY_LANGUAGE = stringPreferencesKey("app_language")

    private val _currentLanguage = MutableStateFlow("de")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<AppLanguage>>(bundleLanguages())
    val availableLanguages: StateFlow<List<AppLanguage>> = _availableLanguages.asStateFlow()

    private var deBundle: Map<String, String> = emptyMap()
    private var enBundle: Map<String, String> = emptyMap()
    internal val cachedLocales = mutableMapOf<String, Map<String, String>>()
    internal val localLastModified = mutableMapOf<String, String>()

    internal var api: L10nApi? = null
    private val gson = Gson()

    fun init(context: Context, overrideApi: L10nApi? = null) {
        api = overrideApi ?: L10nApi(portalUrl(context))

        deBundle = loadRawBundle(context, BUNDLE_DE)
        enBundle = loadRawBundle(context, BUNDLE_EN)
        cachedLocales["de"] = deBundle
        cachedLocales["en"] = enBundle

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.langStore.data.first()
            val saved = prefs[KEY_LANGUAGE] ?: "de"
            _currentLanguage.value = saved

            if (saved != "de" && saved != "en") {
                val f = cacheFile(context, saved)
                if (f.exists()) {
                    val map = parseJsonFile(f)
                    if (map.isNotEmpty()) cachedLocales[saved] = map
                }
            }
            updateCachedFlags(context)
            fetchAvailableLanguages(context)
        }
    }

    fun setLanguage(context: Context, code: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (code == "de" || code == "en" || cachedLocales.containsKey(code)) {
                applyLanguage(context, code)
            } else {
                val ok = downloadLocale(context, code)
                if (ok) applyLanguage(context, code)
            }
        }
    }

    suspend fun downloadLocale(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        val result = api?.getTranslations(code) ?: return@withContext false
        if (result.translations.isEmpty()) return@withContext false
        val jsonStr = gson.toJson(result.translations)
        val file = cacheFile(context, code)
        file.writeText(jsonStr)
        cachedLocales[code] = result.translations
        result.lastModified?.let { localLastModified[code] = it }
        updateCachedFlags(context)
        true
    }

    suspend fun refreshCurrent(context: Context) = withContext(Dispatchers.IO) {
        val code = _currentLanguage.value
        if (code == "de" || code == "en") return@withContext
        val lastMod = localLastModified[code]
        val result = api?.getTranslations(code, ifModifiedSince = lastMod) ?: return@withContext
        if (result.translations.isEmpty()) return@withContext
        val jsonStr = gson.toJson(result.translations)
        cacheFile(context, code).writeText(jsonStr)
        cachedLocales[code] = result.translations
        result.lastModified?.let { localLastModified[code] = it }
    }

    fun deleteLocale(context: Context, code: String) {
        if (code == "de" || code == "en") return
        cacheFile(context, code).delete()
        cachedLocales.remove(code)
        localLastModified.remove(code)
        if (_currentLanguage.value == code) {
            _currentLanguage.value = "de"
            CoroutineScope(Dispatchers.IO).launch {
                context.langStore.edit { it[KEY_LANGUAGE] = "de" }
            }
        }
        updateCachedFlags(context)
    }

    fun t(key: String, vararg args: Any): String {
        val lang = _currentLanguage.value
        val raw = cachedLocales[lang]?.get(key)
            ?: cachedLocales["en"]?.get(key)
            ?: cachedLocales["de"]?.get(key)
            ?: key
        return if (args.isEmpty()) raw else substituteArgs(raw, args)
    }

    fun getString(key: String): String = t(key)

    fun getString(key: String, langCode: String): String =
        cachedLocales[langCode]?.get(key) ?: cachedLocales["de"]?.get(key) ?: key

    private fun fetchAvailableLanguages(context: Context) {
        runCatching {
            val locales = api?.getLocales() ?: return
            if (locales.isEmpty()) return
            val langs = locales.map { info ->
                val isBundle = info.code == "de" || info.code == "en"
                val isCached = isBundle || cacheFile(context, info.code).exists()
                AppLanguage(info.code, info.displayName, flagEmoji(info.code), isBundle, isCached, info.bytesSize)
            }
            _availableLanguages.value = langs
        }.getOrElse { e ->
            Log.d(TAG, "fetchAvailableLanguages: bundle fallback (${e.message})")
        }
    }

    private fun applyLanguage(context: Context, code: String) {
        _currentLanguage.value = code
        CoroutineScope(Dispatchers.IO).launch {
            context.langStore.edit { it[KEY_LANGUAGE] = code }
        }
    }

    private fun updateCachedFlags(context: Context) {
        _availableLanguages.value = _availableLanguages.value.map { lang ->
            lang.copy(isCached = lang.isBundle || cacheFile(context, lang.code).exists())
        }
    }

    internal fun cacheFile(context: Context, code: String): File {
        val dir = File(context.filesDir, CACHE_DIR)
        dir.mkdirs()
        return File(dir, "$code.json")
    }

    private fun loadRawBundle(context: Context, resName: String): Map<String, String> {
        return runCatching {
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId == 0) return emptyMap()
            context.resources.openRawResource(resId).bufferedReader().use { parseJsonString(it.readText()) }
        }.getOrElse { e ->
            Log.e(TAG, "loadRawBundle $resName failed: ${e.message}")
            emptyMap()
        }
    }

    internal fun parseJsonFile(file: File): Map<String, String> =
        runCatching { parseJsonString(file.readText()) }.getOrDefault(emptyMap())

    internal fun parseJsonString(json: String): Map<String, String> {
        val type = object : TypeToken<Map<String, String>>() {}.type
        return runCatching { gson.fromJson<Map<String, String>>(json, type) }.getOrDefault(emptyMap())
    }

    private fun substituteArgs(template: String, args: Array<out Any>): String {
        var result = template
        Regex("\\{(\\w+)\\}").findAll(template).toList().forEachIndexed { index, match ->
            if (index < args.size) result = result.replace(match.value, args[index].toString())
        }
        return result
    }

    private fun portalUrl(context: Context): String {
        return runCatching {
            com.uip.oneapp.BuildConfig.L10N_PORTAL_URL.ifBlank { null }
        }.getOrNull() ?: DEFAULT_PORTAL_URL
    }

    internal fun bundleLanguages() = listOf(
        AppLanguage("de", "Deutsch", "🇩🇪", isBundle = true, isCached = true),
        AppLanguage("en", "English", "🇬🇧", isBundle = true, isCached = true)
    )

    private fun flagEmoji(code: String): String = FLAG_MAP[code] ?: code.uppercase()

    private val FLAG_MAP = mapOf(
        "de" to "🇩🇪", "en" to "🇬🇧", "no" to "🇳🇴", "it" to "🇮🇹",
        "nl" to "🇳🇱", "fr" to "🇫🇷", "es" to "🇪🇸", "pt" to "🇵🇹",
        "pl" to "🇵🇱", "cs" to "🇨🇿", "sk" to "🇸🇰", "sl" to "🇸🇮",
        "hr" to "🇭🇷", "hu" to "🇭🇺", "ro" to "🇷🇴", "bg" to "🇧🇬",
        "el" to "🇬🇷", "da" to "🇩🇰", "sv" to "🇸🇪", "fi" to "🇫🇮",
        "et" to "🇪🇪", "lv" to "🇱🇻", "lt" to "🇱🇹", "ga" to "🇮🇪",
        "mt" to "🇲🇹", "ar" to "🇸🇦", "ru" to "🇷🇺", "tr" to "🇹🇷",
        "sr" to "🇷🇸", "sq" to "🇦🇱", "zh" to "🇨🇳", "ja" to "🇯🇵",
        "ko" to "🇰🇷", "id" to "🇮🇩", "th" to "🇹🇭"
    )

    private const val DEFAULT_PORTAL_URL = ""
}

@Suppress("unused")
@Composable
fun S(key: String): String {
    @Suppress("UNUSED_VARIABLE")
    val lang by LocalizationManager.currentLanguage.collectAsState()
    return LocalizationManager.t(key)
}

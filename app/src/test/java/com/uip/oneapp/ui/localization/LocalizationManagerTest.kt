package com.uip.oneapp.ui.localization

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.network.l10n.L10nApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class LocalizationManagerTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
        resetManagerState()
    }

    @After
    fun teardown() {
        server.shutdown()
        resetManagerState()
    }

    private fun resetManagerState() {
        LocalizationManager.cachedLocales.clear()
        LocalizationManager.localLastModified.clear()
        LocalizationManager.api = null
        currentLanguageFlow().value = "de"
        val avail = LocalizationManager::class.java.getDeclaredField("_availableLanguages")
        avail.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (avail.get(LocalizationManager) as MutableStateFlow<List<AppLanguage>>).value =
            LocalizationManager.bundleLanguages()
    }

    private fun currentLanguageFlow(): MutableStateFlow<String> {
        val f = LocalizationManager::class.java.getDeclaredField("_currentLanguage")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return f.get(LocalizationManager) as MutableStateFlow<String>
    }

    private fun setCurrentLanguage(code: String) {
        currentLanguageFlow().value = code
    }

    // --- Fallback Chain Tests ---

    @Test
    fun `t() returns value from current locale when available`() {
        LocalizationManager.cachedLocales["de"] = mapOf("hello" to "Hallo")
        LocalizationManager.cachedLocales["en"] = mapOf("hello" to "Hello")
        setCurrentLanguage("de")
        assertEquals("Hallo", LocalizationManager.t("hello"))
    }

    @Test
    fun `t() falls back to EN when key missing in current locale`() {
        LocalizationManager.cachedLocales["pl"] = mapOf("other_key" to "Inny")
        LocalizationManager.cachedLocales["en"] = mapOf("hello" to "Hello")
        LocalizationManager.cachedLocales["de"] = mapOf("hello" to "Hallo")
        setCurrentLanguage("pl")
        assertEquals("Hello", LocalizationManager.t("hello"))
    }

    @Test
    fun `t() falls back to DE when key missing in current and EN`() {
        LocalizationManager.cachedLocales["pl"] = emptyMap()
        LocalizationManager.cachedLocales["en"] = emptyMap()
        LocalizationManager.cachedLocales["de"] = mapOf("hello" to "Hallo")
        setCurrentLanguage("pl")
        assertEquals("Hallo", LocalizationManager.t("hello"))
    }

    @Test
    fun `t() returns key as last resort when missing everywhere`() {
        LocalizationManager.cachedLocales["de"] = emptyMap()
        LocalizationManager.cachedLocales["en"] = emptyMap()
        setCurrentLanguage("de")
        assertEquals("missing_key", LocalizationManager.t("missing_key"))
    }

    @Test
    fun `t() substitutes named placeholder args by index`() {
        LocalizationManager.cachedLocales["de"] = mapOf("and_more" to "... und {count} weitere")
        setCurrentLanguage("de")
        assertEquals("... und 5 weitere", LocalizationManager.t("and_more", 5))
    }

    @Test
    fun `t() substitutes elapsed placeholder`() {
        LocalizationManager.cachedLocales["en"] = mapOf("rec" to "REC {elapsed}")
        LocalizationManager.cachedLocales["de"] = mapOf("rec" to "REC {elapsed}")
        setCurrentLanguage("en")
        assertEquals("REC 01:23", LocalizationManager.t("rec", "01:23"))
    }

    @Test
    fun `t() returns template unchanged when no args`() {
        LocalizationManager.cachedLocales["de"] = mapOf("template" to "Hello {name}")
        setCurrentLanguage("de")
        assertEquals("Hello {name}", LocalizationManager.t("template"))
    }

    // --- getString() backward compatibility ---

    @Test
    fun `getString() delegates to t()`() {
        LocalizationManager.cachedLocales["de"] = mapOf("key1" to "Wert1")
        setCurrentLanguage("de")
        assertEquals("Wert1", LocalizationManager.getString("key1"))
    }

    @Test
    fun `getString() with explicit langCode returns that locale value`() {
        LocalizationManager.cachedLocales["de"] = mapOf("key1" to "Deutsch")
        LocalizationManager.cachedLocales["en"] = mapOf("key1" to "English")
        setCurrentLanguage("de")
        assertEquals("English", LocalizationManager.getString("key1", "en"))
    }

    // --- JSON Parse Tests ---

    @Test
    fun `parseJsonString parses flat JSON map correctly`() {
        val json = """{"key1":"Value 1","key2":"Value 2"}"""
        val result = LocalizationManager.parseJsonString(json)
        assertEquals("Value 1", result["key1"])
        assertEquals("Value 2", result["key2"])
        assertEquals(2, result.size)
    }

    @Test
    fun `parseJsonString returns empty map for invalid JSON`() {
        val result = LocalizationManager.parseJsonString("not json at all")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseJsonFile reads and parses file correctly`() {
        val f = File(context.filesDir, "test_locale.json")
        f.writeText("""{"hello":"Hola"}""")
        val result = LocalizationManager.parseJsonFile(f)
        assertEquals("Hola", result["hello"])
        f.delete()
    }

    @Test
    fun `parseJsonFile returns empty map for missing file`() {
        val f = File(context.filesDir, "nonexistent_xyz.json")
        val result = LocalizationManager.parseJsonFile(f)
        assertTrue(result.isEmpty())
    }

    // --- Cache File Tests ---

    @Test
    fun `cacheFile returns file in l10n subdirectory`() {
        val f = LocalizationManager.cacheFile(context, "pl")
        assertTrue(f.absolutePath.contains("l10n"))
        assertEquals("pl.json", f.name)
    }

    // --- Download Flow Tests ---

    @Test
    fun `downloadLocale fetches from server and caches result`() = runTest {
        val json = """{"hello":"Hola","back":"Atrás"}"""
        server.enqueue(
            MockResponse().setBody(json).setResponseCode(200)
                .addHeader("Last-Modified", "Wed, 21 May 2026 10:00:00 GMT")
        )
        LocalizationManager.api = L10nApi(server.url("/").toString())

        val ok = LocalizationManager.downloadLocale(context, "es")

        assertTrue(ok)
        val cached = LocalizationManager.cachedLocales["es"]
        assertNotNull(cached)
        assertEquals("Hola", cached?.get("hello"))
        val request = server.takeRequest()
        assertTrue(request.path?.contains("/api/translations/es.json") == true)
        assertEquals("Wed, 21 May 2026 10:00:00 GMT", LocalizationManager.localLastModified["es"])
    }

    @Test
    fun `downloadLocale returns false on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        LocalizationManager.api = L10nApi(server.url("/").toString())
        val ok = LocalizationManager.downloadLocale(context, "zz")
        assertFalse(ok)
    }

    @Test
    fun `downloadLocale returns false for empty JSON body`() = runTest {
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        LocalizationManager.api = L10nApi(server.url("/").toString())
        val ok = LocalizationManager.downloadLocale(context, "zz")
        assertFalse(ok)
    }

    @Test
    fun `refreshCurrent skips bundle languages`() = runTest {
        setCurrentLanguage("de")
        LocalizationManager.api = L10nApi(server.url("/").toString())
        LocalizationManager.refreshCurrent(context)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refreshCurrent sends If-Modified-Since header`() = runTest {
        LocalizationManager.cachedLocales["pl"] = mapOf("hello" to "Cześć")
        LocalizationManager.localLastModified["pl"] = "Wed, 21 May 2026 10:00:00 GMT"
        setCurrentLanguage("pl")
        server.enqueue(MockResponse().setResponseCode(304))
        LocalizationManager.api = L10nApi(server.url("/").toString())

        LocalizationManager.refreshCurrent(context)

        val req = server.takeRequest()
        assertEquals("Wed, 21 May 2026 10:00:00 GMT", req.getHeader("If-Modified-Since"))
    }

    @Test
    fun `refreshCurrent updates cache on 200 response`() = runTest {
        LocalizationManager.cachedLocales["pl"] = mapOf("hello" to "Stara wersja")
        setCurrentLanguage("pl")
        server.enqueue(MockResponse().setBody("""{"hello":"Nowa wersja"}""").setResponseCode(200))
        LocalizationManager.api = L10nApi(server.url("/").toString())

        LocalizationManager.refreshCurrent(context)

        assertEquals("Nowa wersja", LocalizationManager.cachedLocales["pl"]?.get("hello"))
    }

    // --- deleteLocale Tests ---

    @Test
    fun `deleteLocale removes cache file and falls back to de`() = runTest {
        val cacheFile = LocalizationManager.cacheFile(context, "fr")
        cacheFile.writeText("""{"hello":"Bonjour"}""")
        LocalizationManager.cachedLocales["fr"] = mapOf("hello" to "Bonjour")
        setCurrentLanguage("fr")

        LocalizationManager.deleteLocale(context, "fr")

        assertFalse(cacheFile.exists())
        assertNull(LocalizationManager.cachedLocales["fr"])
        assertEquals("de", LocalizationManager.currentLanguage.value)
    }

    @Test
    fun `deleteLocale does not remove DE bundle`() {
        LocalizationManager.cachedLocales["de"] = mapOf("hello" to "Hallo")
        LocalizationManager.deleteLocale(context, "de")
        assertNotNull(LocalizationManager.cachedLocales["de"])
    }

    @Test
    fun `deleteLocale does not change language when other locale is active`() {
        LocalizationManager.cachedLocales["fr"] = mapOf("a" to "b")
        setCurrentLanguage("de")
        LocalizationManager.deleteLocale(context, "fr")
        assertEquals("de", LocalizationManager.currentLanguage.value)
    }

    // --- Bundle Language Tests ---

    @Test
    fun `bundleLanguages returns exactly DE and EN`() {
        val langs = LocalizationManager.bundleLanguages()
        assertEquals(2, langs.size)
        assertTrue(langs.all { it.isBundle && it.isCached })
        assertEquals(setOf("de", "en"), langs.map { it.code }.toSet())
    }

    @Test
    fun `availableLanguages initial state contains DE and EN`() {
        val langs = LocalizationManager.availableLanguages.value
        assertTrue(langs.any { it.code == "de" })
        assertTrue(langs.any { it.code == "en" })
    }
}

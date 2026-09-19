package com.uip.oneapp.ui.localization

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Z-2/Z-3: Zwischenspeicher fuer nachgeladene Sprachpakete ueberlebt einen Prozessneustart
 * (neue Instanz liest, was eine vorherige geschrieben hat). Geraetegebundenes Ueberleben
 * (Flugmodus, echter Neustart) ist damit NICHT hergestellt (H-4) -- das prueft nur, dass
 * die Ablage dateibasiert und nicht prozessgebunden ist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocalePackStoreTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun save_thenNewInstanceReads_isFieldEqual() {
        val store1 = LocalePackStore(context())
        val values = mapOf("k1" to "v1", "CANCEL" to "Abbrechen")
        val meta = LocalePackMeta(etag = "\"abc\"", lastModified = "Mon, 13 Jul 2026 17:56:12 GMT", bytes = 42, fetchedAt = 12345L)
        store1.save("pl", values, meta)

        val store2 = LocalePackStore(context())
        val loaded = store2.load("pl")
        val loadedMeta = store2.loadMeta("pl")

        assertEquals(values, loaded)
        assertEquals(meta.etag, loadedMeta?.etag)
        assertEquals(meta.lastModified, loadedMeta?.lastModified)
        assertEquals(meta.bytes, loadedMeta?.bytes)
        assertEquals(meta.fetchedAt, loadedMeta?.fetchedAt)
    }

    @Test
    fun delete_removesDataAndMeta() {
        val store = LocalePackStore(context())
        store.save("pl", mapOf("k" to "v"), LocalePackMeta(null, null, 1, 0L))
        assertTrue(store.listLoaded().contains("pl"))

        store.delete("pl")

        assertNull(store.load("pl"))
        assertNull(store.loadMeta("pl"))
        assertTrue(!store.listLoaded().contains("pl"))
    }

    @Test
    fun listLoaded_reflectsSavedCodes() {
        val store = LocalePackStore(context())
        store.save("pl", mapOf("k" to "v"), LocalePackMeta(null, null, 1, 0L))
        store.save("cs", mapOf("k" to "v"), LocalePackMeta(null, null, 1, 0L))

        assertEquals(listOf("cs", "pl"), store.listLoaded())
    }

    @Test
    fun sizeOf_reflectsWrittenBytes() {
        val store = LocalePackStore(context())
        store.save("pl", mapOf("k" to "v"), LocalePackMeta(null, null, 1, 0L))

        assertTrue(store.sizeOf("pl") > 0)
        assertEquals(0L, store.sizeOf("unbekannt"))
    }

    @Test
    fun localesList_survivesNewInstance() {
        val store1 = LocalePackStore(context())
        store1.saveLocalesList(listOf("de", "en", "pl"))

        val store2 = LocalePackStore(context())
        assertEquals(listOf("de", "en", "pl"), store2.loadLocalesList())
    }
}

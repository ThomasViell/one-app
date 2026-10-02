package com.uip.oneapp.update

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Zwei Pakete aus einem Bau (CEO 01.10.2026): Das Tablet-Paket holt Updates aus dem
 * Portal-Produkt "one-tablet", das ONE-Paket unverändert aus "one". Kanal bleibt "beta",
 * applicationId bleibt für beide gleich. Läuft in beiden Varianten.
 */
// Plain Application, damit OneApp nicht während des Robolectric-Setups Koin startet.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PaketBuildConfigTest {

    private lateinit var config: UpdateConfig

    private val expectedProxyUrl: String
        get() = when (BuildConfig.FLAVOR) {
            "one" -> "https://license.drainq.com/api/software/one/"
            "tablet" -> "https://license.drainq.com/api/software/one-tablet/"
            else -> throw AssertionError("Unbekannte Produktvariante: ${BuildConfig.FLAVOR}")
        }

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        config = UpdateConfig(context)
        // Keine Override-Prefs: gemessen wird der Auslieferungswert aus BuildConfig.
        config.reset()
    }

    @After
    fun tearDown() {
        config.reset()
    }

    @Test
    fun flavor_istOneOderTablet() {
        assertTrue(
            "BuildConfig.FLAVOR=${BuildConfig.FLAVOR}, erwartet one oder tablet",
            BuildConfig.FLAVOR in setOf("one", "tablet")
        )
    }

    @Test
    fun updateProxyUrl_jePaket() {
        assertEquals(
            "UPDATE_PROXY_URL der Variante ${BuildConfig.FLAVOR}",
            expectedProxyUrl,
            BuildConfig.UPDATE_PROXY_URL
        )
    }

    @Test
    fun updateChannel_bleibtBeta() {
        assertEquals("UPDATE_CHANNEL der Variante ${BuildConfig.FLAVOR}", "beta", BuildConfig.UPDATE_CHANNEL)
    }

    @Test
    fun applicationId_gleichFuerBeidePakete() {
        assertEquals(
            "APPLICATION_ID der Variante ${BuildConfig.FLAVOR}",
            "com.uip.drainq.one",
            BuildConfig.APPLICATION_ID
        )
    }

    @Test
    fun manifestUrl_ohneOverride_zeigtAufProduktDesPakets() {
        val expected = when (BuildConfig.FLAVOR) {
            "one" -> "https://license.drainq.com/api/software/one/releases.beta.json"
            "tablet" -> "https://license.drainq.com/api/software/one-tablet/releases.beta.json"
            else -> throw AssertionError("Unbekannte Produktvariante: ${BuildConfig.FLAVOR}")
        }
        assertEquals("UpdateConfig.manifestUrl der Variante ${BuildConfig.FLAVOR}", expected, config.manifestUrl)
    }
}

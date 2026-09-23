package com.uip.oneapp.ui.localization

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C-3 (Z-4, Welle l10n-auflagen): Messung des synchronen Startpfads -- Ladezeit von
 * `loadBundledAssets` (H-5), Groesse der beiden eingecheckten Asset-Pakete
 * (`H5_DE_BYTES`/`H5_EN_BYTES`) und der Nichtleer-Schutz: `getString("app_name", "en")`
 * muss AUS DEM PAKET kommen. PLAN 4.4: `damage_type_crack` unterscheidet nicht
 * (en.json und EN-Map-Block tragen denselben Wert) -- gewechselt auf `app_name`, dort
 * galt bis 23.09.2026 Paket "DrainQ.ONE" gegen Map "ONE.APP" (gemessen 19.09.2026).
 * W-33f (E-6): Z-1 hat beide auf den Portalwert "DrainQ.ONE" angeglichen; der
 * Nichtleer-Schutz greift seitdem ueber ein eingespeistes Probe-Paket (Klasse, nicht
 * Instanz -- Rot-Beweis des alten Tests: belege/p3_alter_test_rot.txt). Die Klasse wird
 * von den Kommentaren in `LocalizationManager.kt` zitiert und existierte am
 * Ausgangskopf nicht (Befund C-3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class L10nBundleLoadTimeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun assetBytes(code: String): Int =
        context.assets.open("l10n/$code.json").use { it.readBytes().size }

    @Test
    fun loadBundledAssets_isFast() {
        // Kalt gemessen: genau der Aufruf, den init() im Startpfad macht (C-3, H-5).
        val t0 = System.nanoTime()
        LocalizationManager.loadBundledAssets(context)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        println("H5_BUNDLE_LOAD_MS=$ms")
        assertTrue("H-5: Asset-Laden muss unter 50 ms bleiben ($ms ms)", ms < 50.0)
    }

    @Test
    fun bundledAssets_haveExpectedSizes() {
        // N-5 (Runde 2, Befund B-4): der Test hatte keine Zusicherung -- behalten und
        // scharf gemacht statt geloescht: die beiden Zahlen sind der einzige Beleg fuer
        // die Paketgroessen im C-3-Nachweis (H5_DE_BYTES/H5_EN_BYTES), loeschen wuerde
        // den Beleg ersatzlos entfernen. Gemessen am Ausgangskopf ebcf283
        // (belege/n0_basis_ebcf283.txt): 17732/16661. Eine Aenderung am Paket (mehr
        // Schluessel, Zeichensatz) muss hier bewusst nachgezogen werden.
        println("H5_DE_BYTES=${assetBytes("de")}")
        println("H5_EN_BYTES=${assetBytes("en")}")
        assertEquals("l10n/de.json muss 17732 Bytes tragen (N-5)", 17732, assetBytes("de"))
        assertEquals("l10n/en.json muss 16661 Bytes tragen (N-5)", 16661, assetBytes("en"))
    }

    @Test
    fun appName_en_comesFromPackNotMap() {
        LocalizationManager.loadBundledAssets(context)
        val fromPack = LocalizationManager.getString("app_name", "en")
        val assetValue = JSONObject(
            context.assets.open("l10n/en.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ).getString("app_name")
        assertEquals(
            "getString muss den Wert aus dem Asset-Paket liefern, nicht aus dem Map-Block (C-3)",
            assetValue, fromPack
        )
        // Nichtleer-Schutz (W-33f, E-6): nicht mehr ueber eine zufaellige Portal/Map-Abweichung
        // (die jede Angleichungswelle beseitigt — Z-1 hat app_name angeglichen), sondern ueber
        // ein eingespeistes Probe-Paket: Pack-Sieger muss das Probe-Paket sein, nach dem
        // Leeren des Pakets darf es nicht mehr gelten (Klasse, nicht Instanz).
        LocalizationManager.injectPack("en", mapOf("app_name" to "PROBE-PAKET"))
        val fromProbePack = LocalizationManager.getString("app_name", "en")
        assertEquals(
            "Paket vor Map: das eingespeiste Probe-Paket muss gewinnen",
            "PROBE-PAKET", fromProbePack
        )
        LocalizationManager.injectPack("en", emptyMap())
        val fromMap = LocalizationManager.getString("app_name", "en")
        assertNotEquals(
            "Nach dem Leeren des Pakets darf nicht mehr das Probe-Paket gelten — Erhebung nichtleer",
            fromProbePack, fromMap
        )
    }
}

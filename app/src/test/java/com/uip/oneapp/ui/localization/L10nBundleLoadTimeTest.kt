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
 * gilt Paket "DrainQ.ONE" gegen Map "ONE.APP" (gemessen 19.09.2026). Die Klasse wird
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
        println("H5_DE_BYTES=${assetBytes("de")}")
        println("H5_EN_BYTES=${assetBytes("en")}")
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
        // Nichtleer-Schutz: Paket und Map muessen sich unterscheiden, sonst belegt die
        // Gleichheit oben nichts (PLAN 4.4 -- sonst Schluessel wechseln).
        LocalizationManager.injectPack("en", emptyMap())
        val fromMap = LocalizationManager.getString("app_name", "en")
        assertNotEquals(
            "app_name unterscheidet sich nicht zwischen en.json und EN-Map-Block -- Schluessel wechseln (PLAN 4.4)",
            fromPack, fromMap
        )
    }
}

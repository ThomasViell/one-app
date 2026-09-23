package com.uip.oneapp.ui.localization

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C-3 (Z-4, Welle l10n-auflagen): Messung des synchronen Startpfads -- Ladezeit von
 * `loadBundledAssets` (H-5), Groesse der beiden eingecheckten Asset-Pakete
 * (`H5_DE_BYTES`/`H5_EN_BYTES`) und der Ladebeweis.
 *
 * Geschichte des Ladebeweises: bis 23.09.2026 ueber die Ungleichheit `app_name` Paket
 * "DrainQ.ONE" gegen Map "ONE.APP" (gemessen 19.09.2026); W-33f Z-1 hat beide angeglichen
 * (Rot-Beweis des alten Tests: _ketten/mt-b4/belege/p3_alter_test_rot.txt), der Ersatz
 * ueber ein eingespeistes Probe-Paket umging den Ladeweg (Planpruefung 23.09.2026,
 * Befunde 1/2). Seit der Nachbesserung mt-b4-nb (N-1/N-2) gilt: Ladebeweis ueber das
 * GANZE Paket gegen den reinen Map-Weg, Rueckfall mit exaktem Sollwert.
 *
 * Belegpfade `_ketten/<welle>/belege/...` meinen den Kettenordner C:\Projekte\_ketten\,
 * nicht das Repo (Auflage P-2 der Vorrunde).
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
        // (_ketten/l10n-auflagen/belege/n0_basis_ebcf283.txt): 17732/16661. Eine Aenderung am Paket (mehr
        // Schluessel, Zeichensatz) muss hier bewusst nachgezogen werden.
        println("H5_DE_BYTES=${assetBytes("de")}")
        println("H5_EN_BYTES=${assetBytes("en")}")
        assertEquals("l10n/de.json muss 17732 Bytes tragen (N-5)", 17732, assetBytes("de"))
        assertEquals("l10n/en.json muss 16661 Bytes tragen (N-5)", 16661, assetBytes("en"))
    }

    /**
     * N-1 (W-33f Nachbesserung): Ladebeweis. Vor dem Laden werden Paket, Asset-EN und
     * Injektionen geleert, dann antwortet nur der Map-Weg; mindestens ein Paketschluessel
     * muss dort anders antworten als das Paket (am Kopf 9a80089 gemessen: de 21, en 55 --
     * 16 bzw. 42 Schluessel gibt es nur im Paket, 5 bzw. 13 tragen in der Map einen anderen
     * Wert; _ketten/mt-b4-nb/plan_messung/m1_ausgabe.txt). Danach laeuft der echte Ladeweg
     * `loadBundledAssets` (Asset oeffnen, parsen, Paket setzen), und JEDER Paketschluessel
     * muss den Paketwert liefern. Leerer Rumpf, falscher Dateiname, Parsefehler: rot mit
     * Zahl und Beispielen (Mutationsbelege _ketten/mt-b4-nb/belege/n1_*.txt).
     * Der Beweis haengt an keinem einzelnen Schluessel: eine Angleichung Map -> Portal
     * aendert nur Werte gemeinsamer Schluessel, die Nur-Paket-Schluessel bleiben
     * unterscheidend. Verschwinden ALLE Unterschiede, wird die erste Zusicherung laut rot
     * -- der Test wird dann bewusst angepasst, nie still gruen.
     */
    private fun assertBundledPackIsLoaded(code: String) {
        val asset = JSONObject(
            context.assets.open("l10n/$code.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        )
        val keys = asset.keys().asSequence().toList()
        assertTrue("Paket $code direkt gelesen, aber nur ${keys.size} Schluessel (>= 100 erwartet)", keys.size >= 100)

        LocalizationManager.clearInjectedLanguage("de")
        LocalizationManager.clearInjectedLanguage("en")
        LocalizationManager.resetBundleEn()
        LocalizationManager.clearPack("de")
        LocalizationManager.clearPack("en")
        val unterscheidend = keys.filter { LocalizationManager.getString(it, code) != asset.getString(it) }
        println("N1_UNTERSCHEIDEND_$code=${unterscheidend.size}")
        assertTrue(
            "Kein Schluessel unterscheidet Paket $code vom Map-Weg -- der Ladebeweis ist so nicht " +
                "mehr fuehrbar; Test bewusst anpassen statt gruen lassen (N-1)",
            unterscheidend.isNotEmpty()
        )

        LocalizationManager.loadBundledAssets(context)

        val falsch = keys.filter { LocalizationManager.getString(it, code) != asset.getString(it) }
        assertTrue(
            "Paket $code nicht geladen (N-1): ${falsch.size} von ${keys.size} Schluesseln liefern nicht " +
                "den Paketwert, z. B. " + falsch.take(3).joinToString { k ->
                    "$k='${LocalizationManager.getString(k, code)}' statt '${asset.getString(k)}'"
                },
            falsch.isEmpty()
        )
    }

    @Test
    fun bundledPack_de_isLoadedByLoadBundledAssets() {
        assertBundledPackIsLoaded("de")
    }

    @Test
    fun bundledPack_en_isLoadedByLoadBundledAssets() {
        assertBundledPackIsLoaded("en")
    }

    /**
     * N-2 (W-33f Nachbesserung): Paket vor Map, und nach dem Leeren des Pakets EXAKT der
     * Map-Wert -- nicht nur "ungleich Probe". Schluessel `wifi_no_networks`: hat einen
     * Verbraucher (NetworkScreen), steht in Map-en seit Z-1 auf dem Portalwert und faellt
     * damit weder einer Tot-Schluessel- noch einer Angleichungswelle zum Opfer. Sollwert
     * gemessen am Kopf 9a80089, LocalizationManager.kt Zeile 1668. Schluesselname oder
     * anderer Wert: rot (Mutationsbelege _ketten/mt-b4-nb/belege/n2_*.txt).
     */
    @Test
    fun emptiedPack_en_fallsBackToExactMapValue() {
        LocalizationManager.clearInjectedLanguage("en")
        LocalizationManager.loadBundledAssets(context)
        LocalizationManager.injectPack("en", mapOf("wifi_no_networks" to "PROBE-PAKET"))
        assertEquals(
            "Paket vor Map: das eingespeiste Probe-Paket muss gewinnen",
            "PROBE-PAKET", LocalizationManager.getString("wifi_no_networks", "en")
        )
        LocalizationManager.clearPack("en")
        assertEquals(
            "Nach dem Leeren des Pakets muss exakt der Map-en-Wert kommen (N-2)",
            "No networks found (or location permission missing)",
            LocalizationManager.getString("wifi_no_networks", "en")
        )
        LocalizationManager.loadBundledAssets(context) // Rueckstellung fuer nachfolgende Tests
    }
}

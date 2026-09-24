package com.uip.oneapp.ui.localization

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Z-4/Z-6 (RB-3): Die `getString`-Kette muss auf Englisch zurueckfallen, nicht auf
 * Deutsch (AUFTRAG.md Abschnitt 1, Punkt 4). E-P4: pack[lang] -> Asset-EN -> Map[lang]
 * -> Map[en] -> Schluesselname.
 */
class LocalizationManagerChainTest {

    @After
    fun tearDown() {
        LocalizationManager.clearPack("pl")
        LocalizationManager.clearPack("xx")
        LocalizationManager.resetBundleEn()
        LocalizationManager.clearInjectedLanguage("xx")
    }

    @Test
    fun getString_missingInLanguage_fallsBackToEnglishNotGerman() {
        // "pl" hat den Schluessel "k" nicht im Paket; Asset-EN hat "k" = "E";
        // Map-de haette (ueber den injizierten Block) "D" geliefert -- das ist der
        // Fehler von heute (Rueckfall auf Deutsch statt Englisch).
        LocalizationManager.injectBundleEn(mapOf("k" to "E"))
        LocalizationManager.injectLanguage("de", mapOf("k" to "D"))
        val result = LocalizationManager.getString("k", "pl")
        LocalizationManager.clearInjectedLanguage("de")
        assertEquals("Rueckfall muss Englisch sein, nicht Deutsch", "E", result)
    }

    @Test
    fun getString_missingEverywhere_returnsKeyName() {
        // W-33e: "hardware_osd" ist mit der Welle entfernt (toter Schluessel). Ersatz ist
        // "update_not_configured" — in der de-Map, nicht in der en-Map, nicht in en.json:
        // der einzige KNOWN_EN_GAPS-Eintrag (BundleGapTest). Fuer eine Sprache ohne Paket
        // und ohne Map-Eintrag darf NICHT die deutsche Map als letzte Stufe dienen
        // (heutiger Fehler) -- das Ergebnis muss der Schluesselname sein.
        val key = "update_not_configured"
        val result = LocalizationManager.getString(key, "xx")
        assertEquals(key, result)
    }

    @Test
    fun getString_orderIsPackThenBundleEnThenMapLangThenMapEn() {
        val key = "chain_order_probe_key"
        LocalizationManager.injectPack("xx", mapOf(key to "aus_pack"))
        LocalizationManager.injectBundleEn(mapOf(key to "aus_bundle_en"))
        LocalizationManager.injectLanguage("xx", mapOf(key to "aus_map_lang"))
        LocalizationManager.injectLanguage("en", mapOf(key to "aus_map_en"))

        assertEquals("aus_pack", LocalizationManager.getString(key, "xx"))

        LocalizationManager.clearPack("xx")
        assertEquals("aus_bundle_en", LocalizationManager.getString(key, "xx"))

        LocalizationManager.resetBundleEn()
        assertEquals("aus_map_lang", LocalizationManager.getString(key, "xx"))

        LocalizationManager.clearInjectedLanguage("xx")
        assertEquals("aus_map_en", LocalizationManager.getString(key, "xx"))

        LocalizationManager.clearInjectedLanguage("en")
    }

    @Test
    fun getString_germanUiNeverServedForNonGerman() {
        // Sicherung (muss vorher wie nachher gruen sein): fuer lang=en darf kein Wert aus
        // deTranslations() kommen. Stichprobe von Schluesseln, deren de/en-Werte sich im
        // realen Blockinhalt tatsaechlich unterscheiden (sonst waere ein Treffer Zufall).
        val expectedGerman = mapOf(
            "delete_project_confirm" to "Endgültig löschen",
            // W-33e: "restart_now" ist entfernt (tot); Ersatz "download" — de-Map
            // "Herunterladen", en-Map "Download", Verbraucher OfflineMapsScreen.
            "download" to "Herunterladen"
        )
        expectedGerman.forEach { (key, germanValue) ->
            assertEquals(germanValue, LocalizationManager.getString(key, "de"))
            assertFalse(
                "Schluessel $key liefert fuer Englisch den deutschen Wert",
                LocalizationManager.getString(key, "en") == germanValue
            )
        }
    }
}

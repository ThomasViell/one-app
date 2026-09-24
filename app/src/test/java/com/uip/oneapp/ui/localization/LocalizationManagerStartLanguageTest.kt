package com.uip.oneapp.ui.localization

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C-2 (Z-3, Welle l10n-auflagen) + N-1 (Runde 2, Befund B-1): die gespeicherte Sprache muss
 * stehen, bevor das erste Bild gezeichnet wird — ohne den Hauptfaden zu blockieren.
 *
 * Runde 1 las `init` die Sprache synchron per `runBlocking` (Startpfad-Blockade: der erste,
 * kalte Lesevorgang ist am Entwicklungsrechner mit 129,04/151,00 ms gemessen, PRUEFBERICHT_A
 * SONDE-C — ueber H-5). Runde 2: das Lesen bleibt im IO-Faden; MainActivity haelt den
 * System-Splash ueber `setKeepOnScreenCondition` zurueck, bis `languageSettled` steht.
 * Damit erscheint zu keinem Zeitpunkt eine andere Sprache als die gespeicherte (Z-3 bleibt
 * erfuellt), und `onCreate` blockiert nicht mehr (N-1).
 *
 * Rot-zuerst (N-1): der Rot-Lauf lief am Runde-2-Ausgangskopf `ebcf283` mit einer
 * Rot-Variante dieser Testklasse (`N1StartLanguageRedVariantTest`,
 * belege/n1_rot_raw.txt — Laufprotokoll mit beiden roten Tests; die Varianten-Quelle
 * wurde beim Beweis-Rueckbau entfernt, eigene Zeile im Rot-zuerst-Nachweis des
 * Berichts): `init_returnsWithoutBlocking` (init muss unter 50 ms zurueckkehren)
 * und ein Messtest des ersten Lesevorgangs (unter 50 ms) waren dort rot — genau die
 * Blockade, die der Pruefer gemessen hat. Die Endfassung misst statt dessen: init unter
 * 50 ms auf dem Aufrufer-Faden, den ersten Lesevorgang gegen den Gurtel
 * [LocalizationManager.START_LANGUAGE_READ_TIMEOUT_MS] (der erste Lesevorgang dauert
 * inhärent ueber 50 ms — er laeuft jetzt im IO-Faden) und die warme Wiederholung gegen
 * H-5 (50 ms). Beide Zahlen stehen im Beleg mit Bezeichnung (N-1-Belegpflicht).
 *
 * Volllauf-Fund (19.09.2026, Runde 1, eigene Zeile im Bericht): der Seed schrieb zunaechst
 * ueber den Prozess-Singleton `Context.langStore` — im Volllauf scheiterte es am zweiten
 * Schreiben auf dieselbe Datei (Robolectric/Windows, belege/h1_platform_sonde.txt). Deshalb
 * setzt jeder Test die Umleitung `setLanguageStoreOverrideForTest` auf eine frische Instanz
 * mit frischer Datei (Muster: Test-Konstruktor von `DamagePresetRepository`). N-7 (Befund
 * B-7): das Feld selbst ist privat, gesetzt wird nur ueber diese benannte Test-Schnittstelle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocalizationManagerStartLanguageTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var counter = 0

    private fun freshStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                context.preferencesDataStoreFile("test_language_prefs_${System.nanoTime()}_${counter++}")
            }
        )

    /** N-1: ein Speicher, dessen Datenfluss nie liefert — steht fuer blockierendes Medium. */
    private fun blockedStore(): DataStore<Preferences> = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { awaitCancellation() }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            emptyPreferences()
    }

    @Before
    fun setUp() {
        LocalizationManager.setLanguageStoreOverrideForTest(freshStore())
    }

    @After
    fun tearDown() {
        LocalizationManager.setLanguageStoreOverrideForTest(null)
    }

    @Test
    fun storedLanguage_isSetBeforeSplashRelease() = runBlocking {
        LocalizationManager.seedStoredLanguageForTest(context, "en")
        LocalizationManager.resetInMemoryLanguageForTest()
        LocalizationManager.init(context, refreshPortal = false)
        // Der Splash wird erst freigegeben, wenn languageSettled steht — und genau dann
        // muss die gespeicherte Sprache anliegen (C-2/Z-3 in der N-1-Bauform).
        LocalizationManager.languageSettled.first { it }
        assertEquals("en", LocalizationManager.currentLanguage.value)
        // Die gespeicherte Sprache muss auch in den Lookups anliegen: "download"
        // unterscheidet de/en (de "Herunterladen", en "Download", gemessen 19.09.2026).
        // Ein Schluessel mit gleichem Wert in beiden Paketen taugt dafuer nicht — das
        // fruehere Gegenbeispiel "app_name" ist mit W-33e entfernt (toter Schluessel).
        // Volllauf-Fund (19.09.2026): die Vorfassung erwartete hier "en" aus "app_name" —
        // unerfuellbar, sobald init das echte en.json einhaengt; benannt statt still, der
        // Test lief bis dahin nur kompiliert.
        assertEquals("Download", LocalizationManager.getString("download"))
    }

    @Test
    fun noStoredLanguage_fallsBackToDe() = runBlocking {
        LocalizationManager.resetInMemoryLanguageForTest()
        LocalizationManager.init(context, refreshPortal = false)
        LocalizationManager.languageSettled.first { it }
        assertEquals("de", LocalizationManager.currentLanguage.value)
    }

    @Test
    fun init_returnsWithoutBlocking() = runBlocking {
        // N-1 (B-1): init darf den Aufrufer-Faden (im Startpfad: den Hauptfaden) nicht
        // blockieren — H-5, 50 ms. Der erste Lesevorgang laeuft im IO-Faden.
        LocalizationManager.resetInMemoryLanguageForTest()
        val t0 = System.nanoTime()
        LocalizationManager.init(context, refreshPortal = false)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        println("N1_INIT_MS=$ms")
        // Aufraeumen: den gestarteten IO-Lesevorgang zu Ende laufen lassen, bevor der
        // Test endet (sonst schreibt er waehrend des naechsten Tests).
        LocalizationManager.languageSettled.first { it }
        assertTrue("H-5: init muss unter 50 ms zurueckkehren (gemessen $ms ms)", ms < 50.0)
    }

    @Test
    fun readStoredLanguage_firstRead_vsWarmRepeat() = runBlocking {
        // N-1-Belegpflicht: gemessen wird der ERSTE Lesevorgang — frische Store-Instanz,
        // nie zuvor beruehrt, genau der Vorgang, den der Startpfad jetzt im IO-Faden macht.
        // Die warme Wiederholung (H-5-relevant) wird separat ausgewiesen, mit Bezeichnung.
        LocalizationManager.setLanguageStoreOverrideForTest(freshStore())
        val t0 = System.nanoTime()
        val first = LocalizationManager.readStoredLanguage(context)
        val firstMs = (System.nanoTime() - t0) / 1_000_000.0
        assertEquals("de", first)
        println("Z3_FIRST_READ_MS=$firstMs")
        assertTrue(
            "erster Lesevorgang muss unter dem Start-Gurtel bleiben, sonst faellt der Start still auf de zurueck ($firstMs ms)",
            firstMs < LocalizationManager.START_LANGUAGE_READ_TIMEOUT_MS
        )

        val times = (1..20).map {
            val t1 = System.nanoTime()
            LocalizationManager.readStoredLanguage(context)
            (System.nanoTime() - t1) / 1_000_000.0
        }.sorted()
        val median = (times[9] + times[10]) / 2.0
        val max = times.last()
        println("Z3_WARM_READ_MS_MEDIAN=$median")
        println("Z3_WARM_READ_MS_MAX=$max")
        assertTrue(
            "H-5: warme Wiederholung des Sprach-Lesens muss unter 50 ms bleiben (max=$max ms)",
            max < 50.0
        )
    }

    @Test
    fun readStoredLanguage_timesOutOnBlockedStore() {
        // N-1-Gurtel: liefert der Speicher nie, muss readStoredLanguage nach
        // START_LANGUAGE_READ_TIMEOUT_MS auf "de" zurueckfallen statt ewig zu haengen.
        // Ein ECHT blockierendes Dateisystem ist damit nicht hergestellt (L-213) — der
        // nie emittierende Datenfluss ist der naechste erreichbare Ersatz.
        LocalizationManager.setLanguageStoreOverrideForTest(blockedStore())
        val t0 = System.nanoTime()
        val result = runBlocking { LocalizationManager.readStoredLanguage(context) }
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        println("Z3_TIMEOUT_MS=$ms")
        assertEquals("de", result)
        assertTrue(
            "Gurtel muss gerissen sein (nicht frueher zurueckgekehrt): $ms ms",
            ms >= LocalizationManager.START_LANGUAGE_READ_TIMEOUT_MS
        )
        assertTrue(
            "Gurtel muss nach Ablauf wirklich zurueckkehren: $ms ms",
            ms < LocalizationManager.START_LANGUAGE_READ_TIMEOUT_MS + 500
        )
    }
}

package com.uip.oneapp.ui.localization

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C-2 (Z-3, Welle l10n-auflagen): die gespeicherte Sprache muss stehen, bevor das erste Bild
 * gezeichnet wird. Am Ausgangskopf las `init` den langStore erst im IO-Faden -- nach einem
 * Neustart mit gespeicherter Fremdsprache erschien zuerst ein deutsches Bild (Aufblitzen),
 * bevor der IO-Faden den Flow korrigierte.
 *
 * Rot-zuerst (PLAN 4.3, H-4): die Rot-Laeufe liefen am Ausgangskopf 24a18ee MIT dieser
 * Testklasse und den zwei Test-Hooks `seedStoredLanguageForTest`/`resetInMemoryLanguageForTest`
 * (beide @VisibleForTesting, kein Produktcode) -- die Hooks existieren am Ausgangskopf nicht,
 * ohne sie ist der Datei-private `Context.langStore` aus dem Test nicht erreichbar; eine zweite
 * DataStore-Instanz auf derselben Datei ist verboten (Fund der Vorwelle, RB-6). Das steht als
 * eigene Zeile im Rot-zuerst-Nachweis des Berichts.
 *
 * Der Messtest `readStoredLanguage_isFast` kam erst mit dem Produktbau dazu (3d): er ruft die
 * neue Funktion `readStoredLanguage` auf, die es am Ausgangskopf nicht gibt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocalizationManagerStartLanguageTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun storedLanguage_isSetWhenInitReturns() = runBlocking {
        LocalizationManager.seedStoredLanguageForTest(context, "en")
        LocalizationManager.resetInMemoryLanguageForTest()
        LocalizationManager.init(context, refreshPortal = false)
        // Ohne Warten: die Sprache muss stehen, wenn init zurueckkehrt (C-2).
        assertEquals("en", LocalizationManager.currentLanguage.value)
    }

    @Test
    fun noStoredLanguage_fallsBackToDe() {
        LocalizationManager.resetInMemoryLanguageForTest()
        LocalizationManager.init(context, refreshPortal = false)
        assertEquals("de", LocalizationManager.currentLanguage.value)
    }

    @Test
    fun readStoredLanguage_isFast() {
        // H-3/H-5: 20 Wiederholungen des synchronen Lesens auf dem Hauptfaden; Median und
        // Maximum landen als Z3_READ_MS_MEDIAN=/Z3_READ_MS_MAX= im System-Out der XML.
        val times = (1..20).map {
            val t0 = System.nanoTime()
            LocalizationManager.readStoredLanguage(context)
            (System.nanoTime() - t0) / 1_000_000.0
        }.sorted()
        val median = (times[9] + times[10]) / 2.0
        val max = times.last()
        println("Z3_READ_MS_MEDIAN=$median")
        println("Z3_READ_MS_MAX=$max")
        assertTrue("H-5: Lesen der gespeicherten Sprache muss unter 50 ms bleiben (max=$max ms)", max < 50.0)
    }
}

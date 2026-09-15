@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.uip.oneapp.ui.screens.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.system.SystemTimeSetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Welle geraetezeit Z-1 / Plan Schritt 11: reines JUnit gegen die vier Logikfunktionen
 * von `DateTimeScreen.kt` — kein Compose (Compose-Semantik-Tests sind im JVM-Test nicht
 * lauffaehig, siehe OfflineMapsFlagTest), kein Pixel, kein Klick.
 *
 * Rot vor dem Bau (belege/b3_test_rot.txt), gruen danach (belege/b3_test_gruen.txt).
 *
 * Robolectric erst durch die Runde-3-Ergaenzung (N-2) noetig: `persistDiagnostic_*` unten
 * braucht einen echten `Context` fuer den `preferencesDataStore` von `SettingsViewModel`.
 * Plain `Application`, damit Koin nicht startet (Muster wie `SystemTimeSetterTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DateTimeScreenTest {

    /**
     * NACHBESSERUNG Runde 3, N-2: `viewModelScope` haengt an `Dispatchers.Main.immediate`.
     * Robolectrics Standard-`LooperMode.PAUSED` fuehrt darauf gepostete Fortsetzungen NICHT
     * von selbst aus (gemessen: ohne dies bleibt eine ueber `viewModelScope.launch` gestartete
     * Koroutine liegen, bis ein Test sie explizit ueber `ShadowLooper` anstoesst — brueckig und
     * racy gegenueber echten Datei-Schreibvorgaengen). `Dispatchers.setMain` mit einem
     * `UnconfinedTestDispatcher` ist der von Google fuer `viewModelScope`-Tests vorgesehene Weg:
     * `Dispatchers.Main`/`.immediate` fuehren Koroutinen dann eager aus, ohne Robolectric-Looper.
     */
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * NACHBESSERUNG Runde 3, N-2: eigenes `SettingsViewModel` je Testfall, echter
     * Robolectric-`Application`-Context, `HardwareMode.DIRECT` (beliebig — hier ohne Wirkung).
     * `timeSetter` bleibt `lazy` und wird von diesen Tests nie beruehrt — kein echter
     * `AlarmManager`-Zugriff.
     */
    private fun newViewModel(): SettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return SettingsViewModel(
            context = context,
            weatherPresetRepository = WeatherPresetRepository(context),
            damagePresetRepository = DamagePresetRepository(context),
            hardwareMode = HardwareMode.DIRECT,
        )
    }


    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val utc: ZoneOffset = ZoneOffset.UTC

    // --- composeEpoch ---

    @Test
    fun composeEpoch_berlinWallClock_equalsUtcEpoch() {
        // Plan-Vorgabe: 10.09.2026 09:27 Europe/Berlin = 07:27 UTC.
        val berlinWallClock = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2))
        val expected = berlinWallClock.toEpochMilli()
        val actual = composeEpoch(LocalDate.of(2026, 9, 10), LocalTime.of(9, 27), berlin)
        assertEquals(expected, actual)
    }

    @Test
    fun composeEpoch_sameWallClockDifferentZone_otherEpoch() {
        val date = LocalDate.of(2026, 9, 10)
        val time = LocalTime.of(9, 27)
        assertNotEquals(composeEpoch(date, time, berlin), composeEpoch(date, time, utc))
    }

    // --- filterZones ---

    @Test
    fun filterZones_queryBerl_findsBerlin() {
        val zones = listOf("Europe/Berlin", "Europe/Paris", "UTC")
        assertEquals(listOf("Europe/Berlin"), filterZones(zones, "berl"))
    }

    @Test
    fun filterZones_caseInsensitive() {
        val zones = listOf("Europe/Berlin", "Europe/Paris")
        assertEquals(listOf("Europe/Berlin"), filterZones(zones, "BERL"))
    }

    @Test
    fun filterZones_emptyQuery_fullListSorted() {
        val zones = listOf("UTC", "Europe/Berlin", "Europe/Paris")
        assertEquals(listOf("Europe/Berlin", "Europe/Paris", "UTC"), filterZones(zones, ""))
    }

    @Test
    fun filterZones_noMatch_emptyList() {
        val zones = listOf("Europe/Berlin", "Europe/Paris")
        assertTrue(filterZones(zones, "nirgendwo").isEmpty())
    }

    @Test
    fun filterZones_stableOrder_acrossCalls() {
        val zones = listOf("Europe/Berlin", "UTC", "America/New_York", "Europe/Paris")
        assertEquals(filterZones(zones, ""), filterZones(zones, ""))
    }

    // --- zoneOffsetLabel ---

    @Test
    fun zoneOffsetLabel_summerAndWinter() {
        val summer = LocalDateTime.of(2026, 7, 1, 12, 0).toInstant(utc).toEpochMilli()
        val winter = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(utc).toEpochMilli()
        assertEquals("UTC+02:00", zoneOffsetLabel(berlin, summer))
        assertEquals("UTC+01:00", zoneOffsetLabel(berlin, winter))
    }

    @Test
    fun zoneOffsetLabel_negativeOffset() {
        val newYork = ZoneId.of("America/New_York")
        val winter = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(utc).toEpochMilli()
        assertEquals("UTC-05:00", zoneOffsetLabel(newYork, winter))
    }

    @Test
    fun zoneOffsetLabel_fractionalOffset() {
        // Kathmandu: +05:45 — prueft auch das Minuten-Padding.
        val kathmandu = ZoneId.of("Asia/Kathmandu")
        val epoch = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(utc).toEpochMilli()
        assertEquals("UTC+05:45", zoneOffsetLabel(kathmandu, epoch))
    }

    // --- formatForDisplay ---

    @Test
    fun formatForDisplay_de_usesGermanPattern() {
        val epoch = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        assertEquals("10.09.2026, 09:27", formatForDisplay(epoch, berlin, "de"))
    }

    @Test
    fun formatForDisplay_en_usesEnglishPattern() {
        val epoch = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        assertEquals("09/10/2026, 09:27", formatForDisplay(epoch, berlin, "en"))
    }

    // --- handleDateTimeResult (Runde 5, N-1/B-7) ---
    //
    // Vor der Aenderung stand die Meldung (showMessage, hier per delay(10_000) nachgestellte
    // SnackbarDuration.Long) VOR den Folgehandlungen und wurde abgewartet — dieser Test war rot,
    // weil onApplied() erst nach der vollen Meldedauer griff. Rohausgaben: belege/b11_n1_test_rot.txt,
    // belege/b12_n1_test_gruen.txt.

    @Test
    fun handleDateTimeResult_applied_updatesImmediately_withoutAwaitingMessageDuration() = runTest {
        var onAppliedCalled = false
        var messageShown = false

        handleDateTimeResult(
            scope = this,
            result = SystemTimeSetter.Result.Applied,
            msg = "irrelevant",
            showMessage = { delay(10_000); messageShown = true },
            autoTimeActive = { false },
            onShowAutoDialog = {},
            onApplied = { onAppliedCalled = true },
        )

        // Sofort nach dem Aufruf: die Folgehandlung ist gelaufen, die Meldung steht noch aus.
        assertTrue(onAppliedCalled)
        assertFalse(messageShown)

        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(messageShown)
    }

    @Test
    fun handleDateTimeResult_notAppliedWithAutoTimeActive_offersAutoDialogImmediately() = runTest {
        var showAutoDialogCalled = false

        handleDateTimeResult(
            scope = this,
            result = SystemTimeSetter.Result.NotApplied(read = "07:15", expected = "09:15"),
            msg = "irrelevant",
            showMessage = { delay(10_000) },
            autoTimeActive = { true },
            onShowAutoDialog = { showAutoDialogCalled = true },
            onApplied = {},
        )

        assertTrue(showAutoDialogCalled)
    }

    @Test
    fun handleDateTimeResult_notAppliedWithAutoTimeInactive_doesNotOfferAutoDialog() = runTest {
        var showAutoDialogCalled = false

        handleDateTimeResult(
            scope = this,
            result = SystemTimeSetter.Result.NotApplied(read = "07:15", expected = "09:15"),
            msg = "irrelevant",
            showMessage = { delay(10_000) },
            autoTimeActive = { false },
            onShowAutoDialog = { showAutoDialogCalled = true },
            onApplied = {},
        )

        assertFalse(showAutoDialogCalled)
    }

    // --- diagnosticLines (Welle zeitseite-nachzug Z-4 + PLAN_NACHTRAG B-1/B-2) ---

    @Test
    fun diagnosticLines_countMatchesRecords() {
        val records = listOf(
            SystemTimeSetter.CallRecord("setDateTime", "angefordert=123", "123", "456", true, false, "Applied"),
            SystemTimeSetter.CallRecord("setZone", "zone=Europe/Nirgendwo", null, null, null, null, "InvalidZone"),
        )
        assertEquals(2, diagnosticLines(records, "de").size)
    }

    @Test
    fun diagnosticLines_unreadableAuto_showsQuestionMark() {
        val records = listOf(
            SystemTimeSetter.CallRecord("setDateTime", "angefordert=123", "123", null, null, null, "pending"),
        )
        val line = diagnosticLines(records, "de").first()
        assertTrue(line.contains("auto_time=?"))
        assertTrue(line.contains("auto_time_zone=?"))
    }

    @Test
    fun diagnosticLines_containsBothTimeForms() {
        val epoch = LocalDateTime.of(2026, 9, 10, 9, 27).toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        val records = listOf(
            SystemTimeSetter.CallRecord("setDateTime", "angefordert=$epoch", epoch.toString(), epoch.toString(), true, false, "Applied"),
        )
        val line = diagnosticLines(records, "de").first()
        // technischer Wert (Epochenmillisekunden) UND die lesbare formatForDisplay-Form
        assertTrue(line.contains(epoch.toString()))
        assertTrue(line.contains(formatForDisplay(epoch, ZoneId.systemDefault(), "de")))
    }

    @Test
    fun diagnosticLines_readNull_showsDash() {
        val records = listOf(
            SystemTimeSetter.CallRecord("setDateTime", "angefordert=123", null, null, true, true, "NeedsConsent"),
        )
        val line = diagnosticLines(records, "de").first()
        assertTrue(line.contains("-"))
    }

    @Test
    fun diagnosticLines_usesGivenLabels() {
        val records = listOf(
            SystemTimeSetter.CallRecord("setDateTime", "angefordert=123", "123", "123", true, false, "Applied"),
        )
        val labels = DiagnosticLabels(requested = "REQ", read1 = "R1", read2 = "R2", autoTime = "AT", autoZone = "AZ", result = "ERG")
        val line = diagnosticLines(records, "de", labels).first()
        assertTrue(line.contains("REQ=angefordert=123"))
        assertTrue(line.contains("ERG=Applied"))
    }

    // --- persistDiagnostic/loadPersistedDiagnostic (NACHBESSERUNG Runde 3, N-2 — Befund E-2) ---
    //
    // Die fuenf Tests oben pruefen ausschliesslich `diagnosticLines` (die Darstellung). Dieser
    // Test prueft die Aussage von B-1 selbst (N-2 Punkt 1): den Persistenzpfad in
    // `SettingsViewModel` — geschrieben, dann wiedergelesen, feldgleich (Regel 36 — jedes Feld
    // einzeln geprueft, nicht nur eines). N-2 Punkt 2 („ueberlebt den Abbruch") ist NICHT
    // hergestellt — Begruendung direkt im Anschluss an diesen Test.

    /**
     * `androidx.datastore` schreibt per Datei-Umbenennung (`.tmp` → Zieldatei). Unter Windows
     * blockiert ein noch nicht freigegebenes Datei-Handle (z.B. vom vorangegangenen Lesevorgang
     * derselben Testmethode) diese Umbenennung kurzzeitig — gemessen als
     * `java.io.IOException: Unable to rename ... multiple instances of DataStore`, obwohl
     * tatsaechlich nur EINE Instanz existiert (Testinfrastruktur-Rauschen, kein Produktivbefund).
     * Kurzer Retry mit Wartezeit, NICHT Teil des Produktivpfads.
     */
    private suspend fun retryOnWindowsRenameRace(block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                block()
                return
            } catch (e: java.io.IOException) {
                attempt++
                if (attempt >= 5) throw e
                delay(100)
            }
        }
    }

    @Test
    fun persistDiagnostic_writtenThenRead_isFieldEqual() = runBlocking {
        val viewModel = newViewModel()
        val record = SystemTimeSetter.CallRecord(
            funName = "setZoneAndTime",
            requested = "angefordert=1234567",
            read1 = "1234567",
            read2 = "1234999",
            autoTime = false,
            autoZone = true,
            resultBranch = "Overwritten",
            timestampMs = 999_888L,
        )

        retryOnWindowsRenameRace { viewModel.persistDiagnostic(record) }
        val loaded = viewModel.loadPersistedDiagnostic()

        assertNotNull(loaded)
        assertEquals(record.funName, loaded?.funName)
        assertEquals(record.requested, loaded?.requested)
        assertEquals(record.read1, loaded?.read1)
        assertEquals(record.read2, loaded?.read2)
        assertEquals(record.autoTime, loaded?.autoTime)
        assertEquals(record.autoZone, loaded?.autoZone)
        assertEquals(record.resultBranch, loaded?.resultBranch)
        assertEquals(record.timestampMs, loaded?.timestampMs)
        Unit
    }

    // --- N-2 Punkt 2 „ueberlebt den Abbruch" — NICHT HERGESTELLT (Regel 3/L-213) ---
    //
    // Gemessen, mehrfach, mit wechselnden Gegenmassnahmen (UnconfinedTestDispatcher fuer
    // `Dispatchers.Main`, `ShadowLooper.idle()`, reale Wartezeiten bis 2 s, Retry-Schleifen bis
    // 8 Versuche, `System.gc()`): ein ZWEITER Schreibvorgang auf dieselbe `preferencesDataStore`-
    // Datei (`app_settings`) im selben Testprozess scheitert auf diesem Windows-Messrechner
    // UNTER ROBOLECTRIC REPRODUZIERBAR — deterministisch, nicht transient — mit
    // `java.io.IOException: Unable to rename ...tmp. This likely means that there are multiple
    // instances of DataStore for this file.`, UNABHAENGIG davon, ob ueber `recordDiagnostic`
    // (asynchron, `NonCancellable`) oder direkt/synchron ueber `persistDiagnostic` geschrieben
    // wird, und UNABHAENGIG von `ViewModelStore.clear()` (also unabhaengig vom eigentlichen
    // Pruefgegenstand dieser Runde). `internal val Context.settingsStore by
    // preferencesDataStore(...)` ist laut DataStore-Dokumentation als PROZESSWEITES Singleton
    // gedacht — genau dieses Singleton macht einen zweiten, isolierten Schreib-/Lesezyklus
    // gegen dieselbe Datei innerhalb EINES Testprozesses in dieser Umgebung nicht sicher
    // herstellbar.
    //
    // Damit greift NACHBESSERUNG.md woertlich: „Braucht der Test dafuer eine Einspeisestelle
    // (Ablage als Schnittstelle statt direkt am Context): das ist eine Strukturaenderung und
    // damit Rueckfrage an den CEO, kein stillschweigender Umbau." Der Bauer hat diese
    // Strukturaenderung NICHT vorgenommen — sie ist Rueckfrage, siehe BERICHT.md Runde-3-Anhang.
    // N-2 Punkt 1 (oben, `persistDiagnostic_writtenThenRead_isFieldEqual`) ist deshalb der EINE
    // der zwei geforderten Tests, der in dieser Runde hergestellt wurde;
    // der zweite bleibt fachlich unbewiesen durch Automatisierung — die Korrektheit von
    // `NonCancellable` in `recordDiagnostic` stuetzt sich auf die dokumentierte
    // Kotlin-Coroutines-Semantik (structured concurrency: ein per `NonCancellable`-Kontext
    // gestarteter Kind-Job haengt nicht mehr strukturell am abgebrochenen Eltern-Job), nicht auf
    // einen automatisierten Rot/Gruen-Beweis.
}

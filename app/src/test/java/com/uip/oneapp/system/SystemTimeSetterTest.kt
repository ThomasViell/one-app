package com.uip.oneapp.system

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Welle geraetezeit Z-1 / Plan Schritt 10, erweitert Welle zeitseite-nachzug (Z-1/Z-2/Z-4):
 * Rot vor dem Bau (belege/b1_z2_rot.txt, belege/b3_z1_rot.txt), danach gruen
 * (belege/b2_z2_gruen.txt, belege/b4_z1_gruen.txt).
 *
 * `AlarmManager` ist im JVM ein Stub — der Test faehrt deshalb einen Fake-Port und prueft
 * Validierung, Ausnahme-Abbildung und die Rueckleseproben (zweite Probe: Z-1b,
 * `SECOND_READ_BACK_DELAY_MS` = 10 s, ueber `runTest`/virtuelle Zeit ohne echte Wartezeit,
 * Plan 1.7). Robolectric nur, damit `android.util.Log` funktioniert (Nachtrag 2 Punkt 4: die
 * `DqZeit`-Zeile ist Teil der Klasse); Plain Application, damit Koin nicht startet (Muster
 * UpdateServiceTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SystemTimeSetterTest {

    private val buildYear = 2026 // minPlausibleYear = 2025

    /** 01.03.2025 00:00 UTC — klar plausibel, per java.time berechnet statt handgezaehlt. */
    private val baseEpochMs: Long =
        LocalDate.of(2025, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 01.01.2021 00:00 UTC — der 2021-Reset-Fall. */
    private val epoch2021: Long =
        LocalDate.of(2021, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private class FakePort : SystemClockPort {
        // „clock" statt „time": `var time` kollidiert auf JVM-Ebene mit `fun setTime`.
        var clock: Long = 0
        var zone: String = "UTC"
        var autoTime: Boolean = false
        var autoZone: Boolean = false
        /** true = Geraet uebernimmt den Wert; false = still verworfen (NotApplied-Fall). */
        var applyTime: Boolean = true
        var applyZone: Boolean = true
        var applyAutoWrite: Boolean = true
        /** Simulierte Verarbeitungszeit zwischen Setzen und Ruecklesen. */
        var driftMs: Long = 0
        var setTimeError: (() -> Nothing)? = null
        var setZoneError: (() -> Nothing)? = null
        var autoWriteError: (() -> Nothing)? = null
        /** Welle zeitseite-nachzug Z-2: `autoTimeEnabled`/`autoTimeZoneEnabled` werfen. */
        var autoReadError: (() -> Nothing)? = null
        /**
         * Welle zeitseite-nachzug Z-1b: naechste Ruecklesewerte in Reihenfolge — leer,
         * faellt auf `clock + driftMs` zurueck (bisheriges Verhalten).
         */
        val readBackQueue: ArrayDeque<Long> = ArrayDeque()
        val callLog = mutableListOf<String>()

        override fun setTime(epochMs: Long) {
            callLog += "setTime($epochMs)"
            setTimeError?.invoke()
            if (applyTime) clock = epochMs
        }

        override fun setTimeZone(zoneId: String) {
            callLog += "setTimeZone($zoneId)"
            setZoneError?.invoke()
            if (applyZone) zone = zoneId
        }

        override fun currentTimeMillis(): Long =
            if (readBackQueue.isNotEmpty()) readBackQueue.removeFirst() else clock + driftMs

        override fun currentZoneId(): String = zone

        override fun autoTimeEnabled(): Boolean {
            autoReadError?.invoke()
            return autoTime
        }

        override fun autoTimeZoneEnabled(): Boolean {
            autoReadError?.invoke()
            return autoZone
        }

        override fun setAutoTimeEnabled(enabled: Boolean) {
            callLog += "setAutoTimeEnabled($enabled)"
            autoWriteError?.invoke()
            if (applyAutoWrite) {
                autoTime = enabled
                autoZone = enabled
            }
        }
    }

    private fun setter(port: FakePort) = SystemTimeSetter(port, buildYear)

    @Before
    fun clearLog() {
        ShadowLog.reset()
    }

    /** Letzte `DqZeit`-Zeile aus dem Robolectric-Log, oder null wenn keine geschrieben wurde. */
    private fun lastDqZeitLine(): String? =
        ShadowLog.getLogs().lastOrNull { it.tag == SystemTimeSetter.TAG }?.msg

    // --- setDateTime ---

    @Test
    fun setDateTime_validEpoch_applied() = runTest {
        val port = FakePort()
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setDateTime(epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
        assertEquals(listOf("setTime($epoch)"), port.callLog)
        assertEquals(epoch, port.clock)
    }

    @Test
    fun setDateTime_readBackWithinTolerance_applied() = runTest {
        // Geraet uebernimmt, die Ruecklese haengt 2 s hinterher — innerhalb der Toleranz.
        val port = FakePort()
        port.driftMs = 2_000
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setDateTime(epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
    }

    @Test
    fun setDateTime_readBackBeyondTolerance_notApplied() = runTest {
        // Geraet meldet einen Wert 6 s neben dem gesetzten — das darf kein Erfolg sein.
        // Die erste Probe entscheidet hier schon; die zweite laeuft nicht mehr an.
        val port = FakePort()
        port.driftMs = 6_000
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setDateTime(epoch)
        assertEquals(
            SystemTimeSetter.Result.NotApplied((epoch + 6_000).toString(), epoch.toString()),
            result,
        )
    }

    @Test
    fun setDateTime_discardedWithoutException_notApplied() = runTest {
        // HYPOTHESE 2.1: Der Zeitdetektor verwirft die Vorgabe STILL (kein Fehler, keine
        // Wirkung) — nur die Rueckleseprobe entlarvt das. Fake stellt es nach: setTime
        // kehrt zurueck, die Uhr bleibt stehen.
        val port = FakePort()
        port.applyTime = false
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(
            SystemTimeSetter.Result.NotApplied(port.clock.toString(), (baseEpochMs + 3_600_000).toString()),
            result,
        )
    }

    @Test
    fun setDateTime_epochBeforePlausibleYear_invalidTime_andNoCall() = runTest {
        val port = FakePort()
        val result = setter(port).setDateTime(epoch2021)
        assertEquals(SystemTimeSetter.Result.InvalidTime, result)
        assertTrue(port.callLog.isEmpty())
    }

    @Test
    fun setDateTime_securityException_denied() = runTest {
        val port = FakePort()
        port.setTimeError = { throw SecurityException("SET_TIME verweigert") }
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: SET_TIME verweigert"), result)
    }

    @Test
    fun setDateTime_otherException_denied() = runTest {
        // Jede nicht vorhergesagte Ausnahme wird als Denied gemeldet — kein stiller Pfad.
        val port = FakePort()
        port.setTimeError = { throw IllegalStateException("kaputt") }
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.IllegalStateException: kaputt"), result)
    }

    // --- setDateTime: zweite Ruecklese (Welle zeitseite-nachzug Z-1b) ---

    @Test
    fun setDateTime_secondReadConsistent_applied() = runTest {
        // Beide Lesungen liefern denselben Wert — nichts hat die Uhr zwischen den Proben
        // angefasst. Reihenfolge der Erfolgsmessung Z-1 aus dem Auftrag.
        val port = FakePort()
        val epoch = baseEpochMs + 3_600_000
        port.readBackQueue.addAll(listOf(epoch, epoch))
        val result = setter(port).setDateTime(epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
    }

    @Test
    fun setDateTime_secondReadRevertedToOldTime_overwritten() = runTest {
        // Auftrag, Erfolgsmessung Z-1 — Louis' Fall 2 als Test: erste Lesung bestaetigt den
        // gesetzten Wert, die zweite (10 s spaeter) liefert die alte Zeit zurueck. VOR dem Bau
        // lieferte setDateTime hier `Applied` (nur ein Lesezugriff) — Rot-Beweis b3_z1_rot.txt.
        val port = FakePort()
        port.autoTime = true
        port.autoZone = false
        val epoch = baseEpochMs + 3_600_000
        port.readBackQueue.addAll(listOf(epoch, baseEpochMs))
        val result = setter(port).setDateTime(epoch)
        assertEquals(
            SystemTimeSetter.Result.Overwritten(epoch.toString(), baseEpochMs.toString(), epoch.toString(), true, false),
            result,
        )
    }

    @Test
    fun setDateTime_secondReadPending_onProgressCarriesFirstReadOnly() = runTest {
        // PLAN_NACHTRAG B-2: der Zwischenstand nach der ersten Ruecklese muss VOR dem
        // `delay` (zweite Probe) beim Aufrufer ankommen — sonst ueberlebt er einen
        // Coroutine-Abbruch waehrend der Wartezeit nicht.
        val port = FakePort()
        port.autoTime = false
        port.autoZone = false
        val epoch = baseEpochMs + 3_600_000
        val progress = mutableListOf<SystemTimeSetter.CallRecord>()
        setter(port).setDateTime(epoch) { progress += it }
        assertEquals(2, progress.size)
        assertEquals("pending", progress[0].resultBranch)
        assertNull(progress[0].read2)
        assertEquals(epoch.toString(), progress[0].read1)
        assertEquals("Applied", progress[1].resultBranch)
    }

    // --- setZone ---

    @Test
    fun setZone_validZone_applied() {
        val port = FakePort()
        val result = setter(port).setZone("Europe/Berlin")
        assertEquals(SystemTimeSetter.Result.Applied, result)
        assertEquals(listOf("setTimeZone(Europe/Berlin)"), port.callLog)
        assertEquals("Europe/Berlin", port.zone)
    }

    @Test
    fun setZone_invalidZone_invalidZone_andNoCall() {
        val port = FakePort()
        val result = setter(port).setZone("Europe/Nirgendwo")
        assertEquals(SystemTimeSetter.Result.InvalidZone("Europe/Nirgendwo"), result)
        assertTrue(port.callLog.isEmpty())
    }

    @Test
    fun setZone_discardedWithoutException_notApplied() {
        val port = FakePort()
        port.applyZone = false
        val result = setter(port).setZone("Europe/Berlin")
        assertEquals(SystemTimeSetter.Result.NotApplied("UTC", "Europe/Berlin"), result)
    }

    @Test
    fun setZone_securityException_denied() {
        val port = FakePort()
        port.setZoneError = { throw SecurityException("SET_TIME_ZONE verweigert") }
        val result = setter(port).setZone("Europe/Berlin")
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: SET_TIME_ZONE verweigert"), result)
    }

    // --- setZoneAndTime (E-4: Zone VOR Zeit) ---

    @Test
    fun setZoneAndTime_setsZoneFirstThenTime() = runTest {
        val port = FakePort()
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
        // Reihenfolge: wuerde erst die Uhr und dann die Zone gesetzt, haette der
        // Zonenwechsel die eben gesetzte Wanduhr verschoben (E-4).
        assertEquals(listOf("setTimeZone(Europe/Berlin)", "setTime($epoch)"), port.callLog)
    }

    @Test
    fun setZoneAndTime_zoneFails_timeNotCalled() = runTest {
        val port = FakePort()
        port.setZoneError = { throw SecurityException("SET_TIME_ZONE verweigert") }
        val result = setter(port).setZoneAndTime("Europe/Berlin", baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: SET_TIME_ZONE verweigert"), result)
        // Die Wanduhr in der falschen Zone waere ein Falsch-Ergebnis — kein setTime.
        assertEquals(listOf("setTimeZone(Europe/Berlin)"), port.callLog)
    }

    @Test
    fun setZoneAndTime_invalidZone_timeNotCalled() = runTest {
        val port = FakePort()
        val result = setter(port).setZoneAndTime("Europe/Nirgendwo", baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.InvalidZone("Europe/Nirgendwo"), result)
        assertTrue(port.callLog.isEmpty())
    }

    @Test
    fun setZoneAndTime_timeNotAppliedAfterZoneApplied_notApplied() = runTest {
        val port = FakePort()
        port.clock = baseEpochMs // Geraeteuhr steht auf einem plausiblen alten Wert
        port.applyTime = false
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.NotApplied(baseEpochMs.toString(), epoch.toString()), result)
        assertEquals(listOf("setTimeZone(Europe/Berlin)", "setTime($epoch)"), port.callLog)
    }

    // --- setZoneAndTime: Automatik VOR dem Setzen geklaert (Welle zeitseite-nachzug Z-1a) ---

    @Test
    fun setZoneAndTime_autoTimeOn_doesNotSetWithoutConsent() = runTest {
        // Auftrag Z-1a: die Automatik wird VOR dem Setzen geklaert. VOR dem Bau haette dieser
        // Test versucht zu setzen (Precheck existierte nicht) — Rot-Beweis b3_z1_rot.txt.
        val port = FakePort()
        port.autoTime = true
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.NeedsConsent(true, false), result)
        assertTrue("kein Portzugriff vor der Bestaetigung erwartet: ${port.callLog}", port.callLog.isEmpty())
    }

    @Test
    fun setZoneAndTime_autoTimeZoneOn_doesNotSetWithoutConsent() = runTest {
        val port = FakePort()
        port.autoZone = true
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.NeedsConsent(false, true), result)
        assertTrue(port.callLog.isEmpty())
    }

    @Test
    fun setZoneAndTime_autoTimeOff_setsNormally() = runTest {
        val port = FakePort()
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
        assertEquals(listOf("setTimeZone(Europe/Berlin)", "setTime($epoch)"), port.callLog)
    }

    // --- precheck (Z-1a) ---

    @Test
    fun precheck_autoOff_ready() {
        val port = FakePort()
        assertEquals(SystemTimeSetter.Precheck.Ready, setter(port).precheck())
    }

    @Test
    fun precheck_autoTimeOn_autoActive() {
        val port = FakePort()
        port.autoTime = true
        val result = setter(port).precheck()
        assertTrue(result is SystemTimeSetter.Precheck.AutoActive)
        assertEquals(SystemTimeSetter.AutoState(true, false), (result as SystemTimeSetter.Precheck.AutoActive).state)
    }

    @Test
    fun precheck_bothUnreadable_unreadable() {
        val port = FakePort()
        port.autoReadError = { throw SecurityException("READ verweigert") }
        assertEquals(SystemTimeSetter.Precheck.Unreadable, setter(port).precheck())
    }

    // --- disableAutoTime / autoTimeState (Nachtrag 2 Punkt 5, Welle zeitseite-nachzug Z-2) ---

    @Test
    fun disableAutoTime_writesAndReadsBack_applied() {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = true
        val result = setter(port).disableAutoTime()
        assertEquals(SystemTimeSetter.Result.Applied, result)
        assertEquals(listOf("setAutoTimeEnabled(false)"), port.callLog)
    }

    @Test
    fun disableAutoTime_writeDenied_denied() {
        val port = FakePort()
        port.autoWriteError = { throw SecurityException("WRITE_SECURE_SETTINGS verweigert") }
        val result = setter(port).disableAutoTime()
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: WRITE_SECURE_SETTINGS verweigert"), result)
    }

    @Test
    fun disableAutoTime_writeSilentlyDiscarded_notApplied() {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = true
        port.applyAutoWrite = false
        val result = setter(port).disableAutoTime()
        assertEquals(
            SystemTimeSetter.Result.NotApplied(
                "auto_time=true, auto_time_zone=true",
                "auto_time=false, auto_time_zone=false",
            ),
            result,
        )
    }

    // --- DqZeit-Zeile traegt auto_time/auto_time_zone in JEDEM Result-Zweig (Runde 4 N-1, B-8) ---

    @Test
    fun logLine_appliedBranch_carriesReadAutoTimeValues() = runTest {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = false
        setter(port).setDateTime(baseEpochMs + 3_600_000)
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=true") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=false") == true)
    }

    @Test
    fun logLine_deniedBranch_carriesReadAutoTimeValues() = runTest {
        val port = FakePort()
        port.autoTime = false
        port.autoZone = true
        port.setTimeError = { throw SecurityException("SET_TIME verweigert") }
        setter(port).setDateTime(baseEpochMs + 3_600_000)
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=false") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=true") == true)
    }

    @Test
    fun logLine_notAppliedBranch_carriesReadAutoTimeValues() = runTest {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = true
        port.applyTime = false
        setter(port).setDateTime(baseEpochMs + 3_600_000)
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=true") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=true") == true)
    }

    @Test
    fun logLine_invalidZoneBranch_carriesReadAutoTimeValues() {
        val port = FakePort()
        port.autoTime = false
        port.autoZone = false
        setter(port).setZone("Europe/Nirgendwo")
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=false") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=false") == true)
    }

    @Test
    fun logLine_invalidTimeBranch_carriesReadAutoTimeValues() = runTest {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = false
        setter(port).setDateTime(epoch2021)
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=true") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=false") == true)
    }

    @Test
    fun logLine_autoReadThrows_carriesQuestionMark_notCrash() = runTest {
        // Welle zeitseite-nachzug Z-2 — Rot-Beweis b1_z2_rot.txt: VOR dem Bau verliess die
        // IllegalStateException `logResult`, hier als Testbild des Prozessabbruchs aus B-B2.
        val port = FakePort()
        port.autoReadError = { throw IllegalStateException("settings tot") }
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Applied, result)
        val line = lastDqZeitLine()
        assertTrue("Zeile ohne '?': $line", line?.contains("auto_time=?") == true)
        assertTrue("Zeile ohne '?': $line", line?.contains("auto_time_zone=?") == true)
    }

    @Test
    fun logResult_portThrowsInCatchPath_yieldsDeniedNotCrash() = runTest {
        // Auftrag Z-2, B-B2: ein Wurf aus dem Lesezugriff INNERHALB eines catch-Blocks darf
        // die urspruengliche Denied-Meldung nicht verschlucken — vor dem Bau verliess die
        // IllegalStateException `setDateTime` und der Test endete mit Ausnahme.
        val port = FakePort()
        port.setTimeError = { throw SecurityException("SET_TIME verweigert") }
        port.autoReadError = { throw IllegalStateException("settings tot") }
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: SET_TIME verweigert"), result)
    }

    @Test
    fun autoTimeState_portThrows_returnsNullNotCrash() {
        val port = FakePort()
        port.autoReadError = { throw IllegalStateException("settings tot") }
        val state = setter(port).autoTimeState()
        assertNull(state.autoTime)
        assertNull(state.autoZone)
        assertTrue(!state.active)
    }

    @Test
    fun autoTimeState_reflectsPortState() {
        val port = FakePort()
        assertTrue(!setter(port).autoTimeState().active)
        port.autoTime = true
        assertTrue(setter(port).autoTimeState().active)
        port.autoTime = false
        port.autoZone = true
        assertTrue(setter(port).autoTimeState().active)
    }
}

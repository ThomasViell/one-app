package com.uip.oneapp.system

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Welle geraetezeit Z-1 / Plan Schritt 10: Rot vor dem Bau (belege/b2_test_rot.txt),
 * dann gruen (belege/b2_test_gruen.txt).
 *
 * `AlarmManager` ist im JVM ein Stub — der Test faehrt deshalb einen Fake-Port und prueft
 * Validierung, Ausnahme-Abbildung und die Rueckleseprobe (Plan 2.1: Zeit
 * `|gelesen − gesetzt| < 5 s`, Zone exakt). Robolectric nur, damit `android.util.Log`
 * funktioniert (Nachtrag 2 Punkt 4: die `DqZeit`-Zeile ist Teil der Klasse); Plain
 * Application, damit Koin nicht startet (Muster UpdateServiceTest).
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

        override fun currentTimeMillis(): Long = clock + driftMs
        override fun currentZoneId(): String = zone
        override fun autoTimeEnabled(): Boolean = autoTime
        override fun autoTimeZoneEnabled(): Boolean = autoZone

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
    fun setDateTime_validEpoch_applied() {
        val port = FakePort()
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setDateTime(epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
        assertEquals(listOf("setTime($epoch)"), port.callLog)
        assertEquals(epoch, port.clock)
    }

    @Test
    fun setDateTime_readBackWithinTolerance_applied() {
        // Geraet uebernimmt, die Ruecklese haengt 2 s hinterher — innerhalb der Toleranz.
        val port = FakePort()
        port.driftMs = 2_000
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setDateTime(epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
    }

    @Test
    fun setDateTime_readBackBeyondTolerance_notApplied() {
        // Geraet meldet einen Wert 6 s neben dem gesetzten — das darf kein Erfolg sein.
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
    fun setDateTime_discardedWithoutException_notApplied() {
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
    fun setDateTime_epochBeforePlausibleYear_invalidTime_andNoCall() {
        val port = FakePort()
        val result = setter(port).setDateTime(epoch2021)
        assertEquals(SystemTimeSetter.Result.InvalidTime, result)
        assertTrue(port.callLog.isEmpty())
    }

    @Test
    fun setDateTime_securityException_denied() {
        val port = FakePort()
        port.setTimeError = { throw SecurityException("SET_TIME verweigert") }
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: SET_TIME verweigert"), result)
    }

    @Test
    fun setDateTime_otherException_denied() {
        // Jede nicht vorhergesagte Ausnahme wird als Denied gemeldet — kein stiller Pfad.
        val port = FakePort()
        port.setTimeError = { throw IllegalStateException("kaputt") }
        val result = setter(port).setDateTime(baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.IllegalStateException: kaputt"), result)
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
    fun setZoneAndTime_setsZoneFirstThenTime() {
        val port = FakePort()
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.Applied, result)
        // Reihenfolge: wuerde erst die Uhr und dann die Zone gesetzt, haette der
        // Zonenwechsel die eben gesetzte Wanduhr verschoben (E-4).
        assertEquals(listOf("setTimeZone(Europe/Berlin)", "setTime($epoch)"), port.callLog)
    }

    @Test
    fun setZoneAndTime_zoneFails_timeNotCalled() {
        val port = FakePort()
        port.setZoneError = { throw SecurityException("SET_TIME_ZONE verweigert") }
        val result = setter(port).setZoneAndTime("Europe/Berlin", baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.Denied("java.lang.SecurityException: SET_TIME_ZONE verweigert"), result)
        // Die Wanduhr in der falschen Zone waere ein Falsch-Ergebnis — kein setTime.
        assertEquals(listOf("setTimeZone(Europe/Berlin)"), port.callLog)
    }

    @Test
    fun setZoneAndTime_invalidZone_timeNotCalled() {
        val port = FakePort()
        val result = setter(port).setZoneAndTime("Europe/Nirgendwo", baseEpochMs + 3_600_000)
        assertEquals(SystemTimeSetter.Result.InvalidZone("Europe/Nirgendwo"), result)
        assertTrue(port.callLog.isEmpty())
    }

    @Test
    fun setZoneAndTime_timeNotAppliedAfterZoneApplied_notApplied() {
        val port = FakePort()
        port.clock = baseEpochMs // Geraeteuhr steht auf einem plausiblen alten Wert
        port.applyTime = false
        val epoch = baseEpochMs + 3_600_000
        val result = setter(port).setZoneAndTime("Europe/Berlin", epoch)
        assertEquals(SystemTimeSetter.Result.NotApplied(baseEpochMs.toString(), epoch.toString()), result)
        assertEquals(listOf("setTimeZone(Europe/Berlin)", "setTime($epoch)"), port.callLog)
    }

    // --- disableAutoTime / autoTimeActive (Nachtrag 2 Punkt 5) ---

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
    fun logLine_appliedBranch_carriesReadAutoTimeValues() {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = false
        setter(port).setDateTime(baseEpochMs + 3_600_000)
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=true") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=false") == true)
    }

    @Test
    fun logLine_deniedBranch_carriesReadAutoTimeValues() {
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
    fun logLine_notAppliedBranch_carriesReadAutoTimeValues() {
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
    fun logLine_invalidTimeBranch_carriesReadAutoTimeValues() {
        val port = FakePort()
        port.autoTime = true
        port.autoZone = false
        setter(port).setDateTime(epoch2021)
        val line = lastDqZeitLine()
        assertTrue("Zeile fehlt oder ohne auto_time: $line", line?.contains("auto_time=true") == true)
        assertTrue("Zeile ohne auto_time_zone: $line", line?.contains("auto_time_zone=false") == true)
    }

    @Test
    fun autoTimeActive_reflectsPortState() {
        val port = FakePort()
        assertTrue(!setter(port).autoTimeActive())
        port.autoTime = true
        assertTrue(setter(port).autoTimeActive())
        port.autoTime = false
        port.autoZone = true
        assertTrue(setter(port).autoTimeActive())
    }
}

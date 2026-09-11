package com.uip.oneapp.system

import android.app.AlarmManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.screens.projects.InspectionDateGuard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.math.abs

/**
 * Welle geraetezeit Z-1: Setzt Systemzeit und Zeitzone der ONE.
 *
 * Die App laeuft plattformsigniert als `android.uid.system` (ADR-0005); `SET_TIME` und
 * `SET_TIME_ZONE` im Manifest sind die Grundlage. Ob die Aufrufe am Geraet wirklich
 * durchgehen oder der Zeitdetektor sie bei eingeschalteter Automatik still verwirft
 * (Plan 2.1, HYPOTHESE), ist ohne Geraet nicht gemessen (Nachtrag 2): deshalb gilt
 * Rueckleseprobe vor jedem Erfolg, eine Log-Zeile je Aufruf (Tag `DqZeit`), und kein
 * Ergebnis ohne sichtbare Meldung am Bildschirm. Der Feldlauf bei Louis liefert die
 * Antwort, die M-1 geliefert haette.
 *
 * Baujahr ist injizierbar (Default [BuildConfig.BUILD_YEAR]) — dieselbe Technik wie
 * [InspectionDateGuard], die lesend wiederverwendet wird (Plan E-3).
 */
class SystemTimeSetter(
    private val port: SystemClockPort,
    private val buildYear: Int = BuildConfig.BUILD_YEAR,
) {

    /** Plan Schritt 10 / Nachtrag 2 Punkt 1: jeder Zweig wird am Bildschirm sichtbar. */
    sealed class Result {
        /** Rueckleseprobe bestaetigt den gesetzten Wert. */
        data object Applied : Result()
        /** Aufruf verweigert oder fehlgeschlagen; [cause] nennt die Ausnahme. */
        data class Denied(val cause: String) : Result()
        /** Rueckleseprobe abweichend: [read] = gelesen, [expected] = angefordert. */
        data class NotApplied(val read: String, val expected: String) : Result()
        /** Zone nicht in der Geraeteliste (E-2). */
        data class InvalidZone(val zoneId: String) : Result()
        /** Zeit vor dem kleinsten plausiblen Jahr — genau der 2021-Reset-Fall (E-3). */
        data object InvalidTime : Result()
    }

    /**
     * Wanduhr setzen (`AlarmManager.setTime`). Erfolg erst nach Rueckleseprobe
     * (`|gelesen − gesetzt| < 5 s`, Plan 2.1) — ein still verworfenes `void` darf nie
     * als Erfolg erscheinen (Nachtrag 2 Punkt 3).
     */
    fun setDateTime(epochMs: Long): Result {
        // E-3: Datum vor dem kleinsten plausiblen Jahr abweisen — der 2021-Reset-Fall.
        // Jahrespruefung in UTC (deterministisch, zonenunabhaengig testbar); die
        // Neujahrs-Randlage (lokales Jahr schon neu, UTC-Jahr noch alt) tritt nur fuer
        // Zonen oestlich von UTC am Jahreswechsel auf und endet in einer sichtbaren
        // Fehlermeldung, nie in einem stillen Falschwert.
        if (!InspectionDateGuard.isSystemClockPlausible(
                LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC),
                buildYear,
            )
        ) {
            return logResult("setDateTime", "angefordert=$epochMs", null, Result.InvalidTime)
        }
        return try {
            port.setTime(epochMs)
            val read = port.currentTimeMillis()
            val result = if (abs(read - epochMs) < READ_BACK_TOLERANCE_MS) {
                Result.Applied
            } else {
                Result.NotApplied(read.toString(), epochMs.toString())
            }
            logResult("setDateTime", "angefordert=$epochMs", "gelesen=$read", result)
        } catch (e: SecurityException) {
            logResult("setDateTime", "angefordert=$epochMs", null, Result.Denied(e.toString()))
        } catch (e: Exception) {
            // Jede nicht vorhergesagte Ausnahme wird sichtbar gemeldet, nie verschluckt.
            logResult("setDateTime", "angefordert=$epochMs", null, Result.Denied(e.toString()))
        }
    }

    /**
     * Zeitzone setzen (`AlarmManager.setTimeZone`). Nur Zonen aus der Geraeteliste
     * (`ZoneId.getAvailableZoneIds()`, E-2); Rueckleseprobe vergleicht exakt.
     */
    fun setZone(zoneId: String): Result {
        if (zoneId !in ZoneId.getAvailableZoneIds()) {
            return logResult("setZone", "angefordert=$zoneId", null, Result.InvalidZone(zoneId))
        }
        return try {
            port.setTimeZone(zoneId)
            val read = port.currentZoneId()
            val result = if (read == zoneId) Result.Applied else Result.NotApplied(read, zoneId)
            logResult("setZone", "angefordert=$zoneId", "gelesen=$read", result)
        } catch (e: SecurityException) {
            logResult("setZone", "angefordert=$zoneId", null, Result.Denied(e.toString()))
        } catch (e: Exception) {
            logResult("setZone", "angefordert=$zoneId", null, Result.Denied(e.toString()))
        }
    }

    /**
     * Kombinierter Aufruf, Reihenfolge ZONE VOR ZEIT (E-4): die Wanduhr wird fuer die
     * gewaehlte Zone berechnet; wuerde erst die Uhr und dann die Zone gesetzt, haette
     * der Zonenwechsel die eben gesetzte Wanduhr verschoben. Schlaegt die Zone fehl,
     * wird die Zeit gar nicht erst gesetzt (kein stiller Pfad, kein Falsch-Ergebnis).
     */
    fun setZoneAndTime(zoneId: String, epochMs: Long): Result {
        val zoneResult = setZone(zoneId)
        return if (zoneResult is Result.Applied) setDateTime(epochMs) else zoneResult
    }

    /**
     * Zeitautomatik abschalten — NUR nach ausdruecklicher Bestaetigung der Bedienung
     * (Nachtrag 2 Punkt 5): schreibt `AUTO_TIME=0` und `AUTO_TIME_ZONE=0`, liest beide
     * zurueck.
     */
    fun disableAutoTime(): Result {
        return try {
            port.setAutoTimeEnabled(false)
            val autoTime = port.autoTimeEnabled()
            val autoZone = port.autoTimeZoneEnabled()
            val result = if (!autoTime && !autoZone) {
                Result.Applied
            } else {
                Result.NotApplied(
                    "auto_time=$autoTime, auto_time_zone=$autoZone",
                    "auto_time=false, auto_time_zone=false",
                )
            }
            logResult("disableAutoTime", "angefordert=false", "gelesen=$autoTime/$autoZone", result)
        } catch (e: SecurityException) {
            logResult("disableAutoTime", "angefordert=false", null, Result.Denied(e.toString()))
        } catch (e: Exception) {
            logResult("disableAutoTime", "angefordert=false", null, Result.Denied(e.toString()))
        }
    }

    /** Ist die Zeitautomatik an? Der Bildschirm bietet das Abschalten nur dann an. */
    fun autoTimeActive(): Boolean = port.autoTimeEnabled() || port.autoTimeZoneEnabled()

    private fun logResult(funName: String, requested: String, read: String?, result: Result): Result {
        // Nachtrag 2 Punkt 4: EINE Log.i-Zeile je Aufruf, Tag DqZeit, mit angefordert,
        // gelesen und Ergebnis-Zweig — die Feldmessung greift sie ohne Neubau ab.
        Log.i(TAG, "$funName: $requested ${read ?: "gelesen=n/a"} ergebnis=$result")
        return result
    }

    companion object {
        /** Nachtrag 2 Punkt 4: Log-Tag, unter dem die Feldmessung die Zeilen abgreift. */
        const val TAG = "DqZeit"
        const val READ_BACK_TOLERANCE_MS = 5_000L
    }
}

/**
 * Schmaler Port ueber AlarmManager/Settings/Systemzeit — im JVM-Test per Fake stellbar
 * (Regel 20). Gegenueber Plan 1.5 um die Auto-Zeit-Zugriffe erweitert (Nachtrag 2 Punkt 5:
 * das Abschalten der Automatik braucht Lese- und Schreibzugriff auf die Settings).
 */
interface SystemClockPort {
    fun setTime(epochMs: Long)
    fun setTimeZone(zoneId: String)
    fun currentTimeMillis(): Long
    fun currentZoneId(): String
    fun autoTimeEnabled(): Boolean
    fun autoTimeZoneEnabled(): Boolean
    fun setAutoTimeEnabled(enabled: Boolean)
}

/** Android-Implementierung des Ports (AlarmManager, Settings.Global, Systemuhr). */
class AndroidClockPort(context: Context) : SystemClockPort {

    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager? =
        appContext.getSystemService(AlarmManager::class.java)

    override fun setTime(epochMs: Long) {
        checkNotNull(alarmManager) { "AlarmManager nicht vorhanden" }.setTime(epochMs)
    }

    override fun setTimeZone(zoneId: String) {
        checkNotNull(alarmManager) { "AlarmManager nicht vorhanden" }.setTimeZone(zoneId)
    }

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun currentZoneId(): String {
        // Plan-Risiko „Cache": TimeZone.getDefault() haelt die beim Prozessstart gelesene
        // Zone fest — nach setTimeZone laese die Ruecklese sonst die ALTE Zone (falsches
        // NotApplied). setDefault(null) leert den Cache, der naechste Aufruf liest neu.
        TimeZone.setDefault(null)
        return TimeZone.getDefault().id
    }

    override fun autoTimeEnabled(): Boolean =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.AUTO_TIME, 0) == 1

    override fun autoTimeZoneEnabled(): Boolean =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1

    override fun setAutoTimeEnabled(enabled: Boolean) {
        val value = if (enabled) 1 else 0
        Settings.Global.putInt(appContext.contentResolver, Settings.Global.AUTO_TIME, value)
        Settings.Global.putInt(appContext.contentResolver, Settings.Global.AUTO_TIME_ZONE, value)
    }
}

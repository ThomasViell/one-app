package com.uip.oneapp.system

import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.screens.projects.InspectionDateGuard
import kotlinx.coroutines.delay
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
        /** Rueckleseprobe bestaetigt den gesetzten Wert (beide Lesungen, Welle zeitseite-nachzug Z-1b). */
        data object Applied : Result()
        /** Aufruf verweigert oder fehlgeschlagen; [cause] nennt die Ausnahme. */
        data class Denied(val cause: String) : Result()
        /** Rueckleseprobe abweichend: [read] = gelesen, [expected] = angefordert. */
        data class NotApplied(val read: String, val expected: String) : Result()
        /** Zone nicht in der Geraeteliste (E-2). */
        data class InvalidZone(val zoneId: String) : Result()
        /** Zeit vor dem kleinsten plausiblen Jahr — genau der 2021-Reset-Fall (E-3). */
        data object InvalidTime : Result()
        /**
         * Welle zeitseite-nachzug Z-1b: die erste Ruecklese lag in der Toleranz, die zweite
         * — [SECOND_READ_BACK_DELAY_MS] spaeter — weicht wieder ab. Das ist Louis' Fall 2 als
         * Ergebniszweig: gesetzt, aber von der Zeitautomatik ueberschrieben (oder aus
         * unbekanntem Grund), bevor die App es als dauerhaft melden durfte.
         *
         * NACHBESSERUNG Runde 2, N-1: [expected] traegt seit dieser Runde den ueber die
         * monotone Referenz ([SystemClockPort.elapsedRealtime]) berechneten Erwartungswert
         * (`read1 + verstrichene Zeit`), NICHT mehr `epochMs` — eine normal weiterlaufende
         * Wanduhr haette nach der Wartezeit genau diesen Wert zeigen muessen.
         */
        data class Overwritten(
            val firstRead: String,
            val secondRead: String,
            val expected: String,
            val autoTime: Boolean?,
            val autoZone: Boolean?,
        ) : Result()
        /**
         * Welle zeitseite-nachzug Z-1a: die Automatik ist an — VOR jedem Portzugriff erkannt
         * (Precheck). Es wurde nichts gesetzt; der Bildschirm muss die Bedienung fragen, bevor
         * irgendetwas geschieht (Nachtrag 2 Punkt 5: nie ungefragt abschalten).
         */
        data class NeedsConsent(val autoTime: Boolean?, val autoZone: Boolean?) : Result()
        /** Bedienung hat den Vorab-Dialog abgebrochen — eigener Zweig statt Stille (B-6-Lehre). */
        data object ConsentCancelled : Result()
    }

    /** Welle zeitseite-nachzug Z-2/E-5: `null` heisst „nicht lesbar", nie stillschweigend `false`. */
    data class AutoState(val autoTime: Boolean?, val autoZone: Boolean?) {
        val active: Boolean get() = autoTime == true || autoZone == true
    }

    /**
     * Ergebnis der Automatik-Vorabpruefung (Z-1a) — VOR jedem Setzen ausgewertet.
     *
     * [Unreadable] blockiert NICHT (NACHBESSERUNG Runde 2, N-3): ein Lesefehler an den
     * Einstellungen darf eine stellbare Uhr nicht sperren, `setZoneAndTime` faehrt fort wie
     * bei [Ready]. Unsichtbar bleibt der Fall trotzdem nicht — die anschliessenden
     * [CallRecord]s tragen `autoTime=null`/`autoZone=null` weiter (E-5, „?" statt erratenem
     * `false"), und `DateTimeScreen` erkennt genau dieses Muster im zuletzt abgelegten
     * Datensatz, um dem Bediener zu sagen, dass die Uhr spaeter ueberschrieben werden koennte.
     */
    sealed class Precheck {
        data object Ready : Precheck()
        data class AutoActive(val state: AutoState) : Precheck()
        data object Unreadable : Precheck()
    }

    /**
     * Ein Aufruf-Datensatz fuer die Diagnosezeile ohne adb (Z-4) und die dauerhafte Ablage
     * (PLAN_NACHTRAG B-1). [read2]/[resultBranch]=`"pending"` heisst: nur die erste Ruecklese
     * liegt vor (PLAN_NACHTRAG B-2) — die zweite ergaenzt denselben Datensatz.
     */
    data class CallRecord(
        val funName: String,
        val requested: String,
        val read1: String?,
        val read2: String?,
        val autoTime: Boolean?,
        val autoZone: Boolean?,
        val resultBranch: String,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    /** Automatik-Zustand, gegen Lesefehler abgesichert (Z-2) — nie eine geworfene Ausnahme. */
    fun autoTimeState(): AutoState = AutoState(
        autoTime = runCatching { port.autoTimeEnabled() }.getOrNull(),
        autoZone = runCatching { port.autoTimeZoneEnabled() }.getOrNull(),
    )

    /** Z-1a: wird VOR jedem Setzen aufgerufen — kein Portzugriff zum Setzen ohne dieses Urteil. */
    fun precheck(): Precheck {
        val state = autoTimeState()
        return when {
            state.autoTime == null && state.autoZone == null -> Precheck.Unreadable
            state.active -> Precheck.AutoActive(state)
            else -> Precheck.Ready
        }
    }

    /**
     * Wanduhr setzen (`AlarmManager.setTime`). Erfolg erst nach ZWEI Rueckleseproben
     * (Z-1b): die erste unmittelbar (`|gelesen − gesetzt| < 5 s`), die zweite
     * [SECOND_READ_BACK_DELAY_MS] spaeter — ein Zeitdetektor, der Sekunden bis Minuten
     * nach dem Setzen ueberschreibt, wird sonst nicht bemerkt (Auftrag Z-1, Louis Fall 2).
     * [onProgress] liefert den Zwischenstand nach der ersten Ruecklese (PLAN_NACHTRAG B-2) —
     * er laeuft VOR dem `delay`, also auch dann, wenn die Coroutine danach abgebrochen wird.
     */
    suspend fun setDateTime(epochMs: Long, onProgress: (CallRecord) -> Unit = {}): Result {
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
            val read1 = port.currentTimeMillis()
            if (abs(read1 - epochMs) >= READ_BACK_TOLERANCE_MS) {
                val result = Result.NotApplied(read1.toString(), epochMs.toString())
                val state = autoTimeState()
                onProgress(
                    CallRecord(
                        "setDateTime", "angefordert=$epochMs", read1.toString(), null,
                        state.autoTime, state.autoZone, "NotApplied",
                    ),
                )
                return logResult("setDateTime", "angefordert=$epochMs", "gelesen=$read1", result)
            }
            val mono1 = port.elapsedRealtime()
            val pendingState = autoTimeState()
            onProgress(
                CallRecord(
                    "setDateTime", "angefordert=$epochMs", read1.toString(), null,
                    pendingState.autoTime, pendingState.autoZone, "pending",
                ),
            )
            delay(SECOND_READ_BACK_DELAY_MS)
            val read2 = port.currentTimeMillis()
            val mono2 = port.elapsedRealtime()
            // NACHBESSERUNG Runde 2, N-1: die Ersatzformel aus Runde 1 (E-4-Richtigstellung,
            // `read2` direkt gegen `read1`) ist am Geraet FALSCH — die Wanduhr laeuft in den
            // SECOND_READ_BACK_DELAY_MS Wartesekunden normal weiter, `read2` liegt dann rund
            // SECOND_READ_BACK_DELAY_MS ueber `read1`, die Toleranz wird ueberschritten und
            // JEDER erfolgreiche Setzvorgang haette „Overwritten" gemeldet (Rot-Beweis dieser
            // Runde). Referenz ist jetzt [SystemClockPort.elapsedRealtime] — sie laeuft
            // unabhaengig von jedem Uhrsprung und laesst sich nicht stellen. `expected` ist der
            // Wert, den eine normal weiterlaufende Wanduhr nach der GEMESSENEN (nicht
            // angenommenen) Wartezeit zeigen muesste; weicht `read2` davon um mehr als
            // READ_BACK_TOLERANCE_MS ab, hat ein Zeitdetektor eingegriffen — egal ob zurueck
            // (Louis Fall 2) oder vor.
            val expected = read1 + (mono2 - mono1)
            val branch: String
            val finalState = autoTimeState()
            val result = if (abs(read2 - expected) < READ_BACK_TOLERANCE_MS) {
                branch = "Applied"
                Result.Applied
            } else {
                branch = "Overwritten"
                Result.Overwritten(read1.toString(), read2.toString(), expected.toString(), finalState.autoTime, finalState.autoZone)
            }
            onProgress(
                CallRecord(
                    "setDateTime", "angefordert=$epochMs", read1.toString(), read2.toString(),
                    finalState.autoTime, finalState.autoZone, branch,
                ),
            )
            logResult("setDateTime", "angefordert=$epochMs", "gelesen1=$read1 gelesen2=$read2", result)
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
     *
     * Z-1a: [precheck] laeuft ZUERST, vor jedem Portzugriff — ist die Automatik an, wird
     * WEDER Zone NOCH Zeit gesetzt (Nachtrag 2 Punkt 5: nie ungefragt). Der Aufrufer
     * (ViewModel) muss dann erst die Bestaetigung einholen und ruft danach erneut auf.
     */
    suspend fun setZoneAndTime(zoneId: String, epochMs: Long, onProgress: (CallRecord) -> Unit = {}): Result {
        when (val pre = precheck()) {
            is Precheck.AutoActive -> {
                onProgress(
                    CallRecord(
                        "setZoneAndTime", "zone=$zoneId zeit=$epochMs", null, null,
                        pre.state.autoTime, pre.state.autoZone, "NeedsConsent",
                    ),
                )
                return Result.NeedsConsent(pre.state.autoTime, pre.state.autoZone)
            }
            Precheck.Unreadable, Precheck.Ready -> Unit
        }
        val zoneResult = setZone(zoneId)
        if (zoneResult !is Result.Applied) {
            val state = autoTimeState()
            onProgress(
                CallRecord(
                    "setZoneAndTime", "zone=$zoneId zeit=$epochMs", null, null,
                    state.autoTime, state.autoZone, zoneResult::class.simpleName ?: "Unbekannt",
                ),
            )
            return zoneResult
        }
        return setDateTime(epochMs, onProgress)
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

    private fun logResult(funName: String, requested: String, read: String?, result: Result): Result {
        // Nachtrag 2 Punkt 4: EINE Log.i-Zeile je Aufruf, Tag DqZeit, mit angefordert,
        // gelesen und Ergebnis-Zweig — die Feldmessung greift sie ohne Neubau ab.
        // Runde 4 N-1 (B-8): auto_time/auto_time_zone in JEDEM Zweig, auch bei Applied —
        // sonst unterscheidet das Log nicht, ob A-2 (Automatik hat ueberschrieben) oder
        // ein anderer Grund hinter einem NotApplied steht.
        // Welle zeitseite-nachzug Z-2: beide Lesezugriffe abgesichert — ein Lesefehler an
        // dieser Stelle darf die eigentliche Meldung (in [result]) nicht mehr verschlucken;
        // die Zeile traegt dann `?` statt eines erratenen `false` (E-5).
        val state = autoTimeState()
        Log.i(
            TAG,
            "$funName: $requested ${read ?: "gelesen=n/a"} auto_time=${state.autoTime?.toString() ?: "?"} " +
                "auto_time_zone=${state.autoZone?.toString() ?: "?"} ergebnis=$result",
        )
        return result
    }

    companion object {
        /** Nachtrag 2 Punkt 4: Log-Tag, unter dem die Feldmessung die Zeilen abgreift. */
        const val TAG = "DqZeit"
        const val READ_BACK_TOLERANCE_MS = 5_000L
        /** Z-1b: Wartezeit bis zur zweiten Ruecklese. R-3: unter der 15-s-Schwelle des Auftrags. */
        const val SECOND_READ_BACK_DELAY_MS = 10_000L
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
    /**
     * NACHBESSERUNG Runde 2, N-1: monotone Referenz fuer die zweite Ruecklese
     * (`android.os.SystemClock.elapsedRealtime()` am Geraet) — laeuft unabhaengig von jedem
     * Uhrsprung weiter und laesst sich nicht stellen. Die Wanduhr ([currentTimeMillis]) kann
     * diese Rolle nicht uebernehmen, sie ist der Gegenstand der Pruefung.
     */
    fun elapsedRealtime(): Long
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
        // ANNAHME — am Geraet nicht gemessen, Feldlauf Louis 14.09.
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

    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

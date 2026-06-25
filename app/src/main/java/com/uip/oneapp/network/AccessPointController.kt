package com.uip.oneapp.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

// HINWEIS: Diese Datei trennt bewusst zwei Schichten:
//  - [AccessPointController] / [AccessPointSpec] = Android-frei, unit-getestet
//    (KEIN android.util.Log → sonst „not mocked" in reinen JVM-Tests).
//  - [AndroidLohsStarter] = die einzige Android-/Log-behaftete Schicht (Geräte-Test).

/**
 * Reine, Android-freie Start-Vorbedingung des Tablet-Hotspots (Dual-Modus, Welle 3a):
 * der Hotspot kommt **nur im [HardwareMode.DIRECT]** hoch (App läuft auf der ONE).
 *
 * Hinweis zur STA/AP-Exklusivität: Auf der ONE (ein `wlan0`) kann nicht gleichzeitig eine
 * WLAN-Verbindung (STA) und ein Hotspot (AP) laufen. Anders als beim früheren @SystemApi-
 * Tethering-Pfad ist das hier **kein** Start-Blocker: der öffentliche
 * [WifiManager.startLocalOnlyHotspot] regelt das selbst und legt eine aktive STA-Verbindung
 * für die Hotspot-Dauer still — exakt das gewünschte On-Demand-Verhalten (CEO-Entscheid:
 * Hotspot per Schalter). Daher reduziert sich das Gate auf die Modus-Prüfung.
 */
object AccessPointSpec {

    /** Ergebnis der reinen Start-Vorprüfung. */
    enum class GateResult { OK, NOT_DIRECT_MODE }

    /** Start nur im DIRECT-Modus erlaubt. */
    fun gate(mode: HardwareMode): GateResult =
        if (mode == HardwareMode.DIRECT) GateResult.OK else GateResult.NOT_DIRECT_MODE
}

/** Beobachtbarer Zustand des Tablet-Hotspots — Quelle der Wahrheit für die Pairing-UI. */
sealed interface ApState {
    /** Hotspot aus. */
    data object Idle : ApState

    /** Start angestoßen, warten auf `onStarted` der Plattform. */
    data object Starting : ApState

    /**
     * Hotspot läuft; [ssid] + [passphrase] sind die **von der Plattform generierten**
     * Zugangsdaten (LocalOnlyHotspot vergibt sie pro Sitzung — per-Gerät-Geheimnis ohne
     * harte Kodierung). Werden als WIFI-QR ([WifiQr.encode]) gezeigt.
     */
    data class Active(val ssid: String, val passphrase: String) : ApState

    /** Start abgelehnt (falscher Modus). */
    data class Blocked(val gate: AccessPointSpec.GateResult) : ApState

    /** Start fehlgeschlagen (Plattform-Fehlercode, fehlende Berechtigung, Exception). */
    data class Failed(val reason: String) : ApState
}

/** Eine laufende Hotspot-Sitzung; [stop] gibt die Plattform-Reservation frei. */
fun interface HotspotSession {
    fun stop()
}

/**
 * Plattform-Seam für den Hotspot-Start. Trennt die (testbare) Zustandslogik des
 * [AccessPointController] vom Android-/Privileg-behafteten LocalOnlyHotspot-Aufruf —
 * im Test wird ein Fake injiziert (vgl. `interfaceProvider` in [OneRemoteServer]).
 */
interface HotspotStarter {
    /**
     * Startet den Hotspot. Meldet das Ergebnis über genau einen der Callbacks:
     *  - [onActive] mit generierter SSID + Passphrase, sobald der AP läuft,
     *  - [onFailed] mit einem kurzen Grund (Fehlercode/Exception),
     *  - [onStopped] wenn die Plattform den AP von sich aus beendet.
     * Liefert die [HotspotSession] zum Stoppen.
     */
    fun start(
        onActive: (ssid: String, passphrase: String) -> Unit,
        onFailed: (reason: String) -> Unit,
        onStopped: () -> Unit,
    ): HotspotSession
}

/**
 * **Tablet-Hotspot-Host** (Dual-Modus, Welle 3a) — spannt im DIRECT-Modus auf der ONE einen
 * WLAN-Hotspot auf, dem ein Tablet ohne Büro-WLAN beitritt. On-Demand per Schalter
 * (CEO-Entscheid 1); die Zugangsdaten werden per QR gekoppelt (CEO-Entscheid 2).
 *
 * **Umsetzung über die ÖFFENTLICHE API** [WifiManager.startLocalOnlyHotspot] (Android 8+,
 * kein System-Privileg) statt des früheren @SystemApi-Tethering-Pfads: die Plattform
 * generiert SSID + WPA2-Passphrase und gibt sie über die Reservation zurück — wir lesen sie
 * aus und zeigen sie als WIFI-QR. Eine eigene (gebrandete) SSID ließe sich nur über die
 * @SystemApi-Variante (`startLocalOnlyHotspot(SoftApConfiguration, …)`) setzen — bewusst
 * nicht genutzt; der QR macht die System-SSID für die Bedienung irrelevant.
 *
 * Die heikle Zustandslogik (Idempotenz, Gate, Übergänge) ist Android-frei und unit-getestet;
 * der eigentliche Plattform-Aufruf liegt hinter [HotspotStarter] ([AndroidLohsStarter]).
 *
 * **TODO(device, Welle 5):** Abnahme auf der ONE — Hotspot an → Tablet scannt QR → joint →
 * Video/Telemetrie/Steuerung wie über Büro-WLAN. LocalOnlyHotspot setzt aktivierte
 * Standortdienste + erteilte Standortberechtigung voraus (Pairing-Screen fragt sie an).
 */
class AccessPointController(
    private val mode: HardwareMode,
    private val starter: HotspotStarter,
) {
    companion object {
        /**
         * Erwartetes Gateway des LocalOnlyHotspot (Android-Konvention). Informativ — das Tablet
         * lernt die echte IP ohnehin aus der UDP-Discovery (Quelladresse, [OneRemoteServer]).
         */
        const val LOHS_GATEWAY_IP = "192.168.49.1"
    }

    private val _state = MutableStateFlow<ApState>(ApState.Idle)
    val state: StateFlow<ApState> = _state.asStateFlow()

    @Volatile
    private var session: HotspotSession? = null

    /**
     * Monoton steigende Sitzungs-Generation. [WifiManager.startLocalOnlyHotspot] meldet
     * **asynchron** (Main-Handler, Hochfahren dauert hunderte ms–s); ein [stop] oder erneuter
     * [start] dazwischen darf nicht von einem späten Callback einer überholten Sitzung
     * überschrieben werden. Jeder Callback trägt seine `gen` und wirkt nur, wenn sie noch aktuell
     * ist. (Die Reservation der abgebrochenen Sitzung schließt [HotspotStarter]/[AndroidLohsStarter]
     * selbst — siehe dortige Cancel-Behandlung — damit kein Hotspot „hängen" bleibt.)
     */
    @Volatile
    private var generation = 0

    /**
     * Bringt den Hotspot hoch — gated auf DIRECT-Modus. Idempotent: ein erneuter Aufruf während
     * [ApState.Starting]/[ApState.Active] ist ein No-op.
     */
    fun start() {
        when (_state.value) {
            is ApState.Starting, is ApState.Active -> return // idempotent
            else -> Unit
        }
        val gate = AccessPointSpec.gate(mode)
        if (gate != AccessPointSpec.GateResult.OK) {
            _state.value = ApState.Blocked(gate)
            return
        }
        val gen = ++generation
        _state.value = ApState.Starting
        session = starter.start(
            onActive = { ssid, passphrase ->
                if (gen == generation) _state.value = ApState.Active(ssid, passphrase)
            },
            onFailed = { reason ->
                if (gen == generation) {
                    session = null
                    _state.value = ApState.Failed(reason)
                }
            },
            onStopped = {
                if (gen == generation) {
                    session = null
                    // Plattform-seitiger Stopp (z. B. WLAN aus) → zurück auf Idle, sofern nicht
                    // ohnehin schon ein Fehler ansteht.
                    if (_state.value !is ApState.Failed) _state.value = ApState.Idle
                }
            },
        )
    }

    /**
     * Stoppt den Hotspot und gibt die Reservation frei. Idempotent. Bumpt die [generation], sodass
     * noch ausstehende Callbacks der laufenden Sitzung folgenlos bleiben (verhindert das „Active
     * nach explizitem Stop"-Szenario, wenn der Toggle während [ApState.Starting] auf AUS geht).
     */
    fun stop() {
        generation++
        try { session?.stop() } catch (_: Throwable) {}
        session = null
        _state.value = ApState.Idle
    }
}

/**
 * Android-Implementierung von [HotspotStarter] über [WifiManager.startLocalOnlyHotspot]
 * (öffentlich, kein System-Privileg). Liest die generierte SSID/Passphrase aus der Reservation
 * (API 30+: [android.net.wifi.SoftApConfiguration]; darunter: deprecated
 * `android.net.wifi.WifiConfiguration`).
 */
class AndroidLohsStarter(context: Context) : HotspotStarter {

    private companion object { const val TAG = "AndroidLohsStarter" }

    private val appContext = context.applicationContext

    override fun start(
        onActive: (ssid: String, passphrase: String) -> Unit,
        onFailed: (reason: String) -> Unit,
        onStopped: () -> Unit,
    ): HotspotSession {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) {
            onFailed("no-wifi-service")
            return HotspotSession { }
        }

        // Reservation thread-sicher festhalten (Callbacks laufen auf dem Main-Looper).
        val reservationHolder = arrayOfNulls<WifiManager.LocalOnlyHotspotReservation>(1)
        // Wurde stop() gerufen, BEVOR der (asynchrone) onStarted ankam? Dann gibt es noch keine
        // Reservation zum Schließen — wir merken uns den Abbruch und schließen sie nach, sobald
        // sie eintrifft (sonst bliebe der Hotspot „hängen", da der einzige Handle verloren ginge).
        val cancelled = AtomicBoolean(false)

        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                if (cancelled.get()) {
                    Log.i(TAG, "Hotspot hochgefahren, aber bereits abgebrochen — Reservation schließen")
                    try { reservation.close() } catch (_: Throwable) {}
                    onStopped()
                    return
                }
                reservationHolder[0] = reservation
                val (ssid, passphrase) = readCredentials(reservation)
                if (!ssid.isNullOrEmpty()) {
                    Log.i(TAG, "Hotspot aktiv (SSID=$ssid)") // Passphrase NICHT loggen.
                    onActive(ssid, passphrase.orEmpty())
                } else {
                    Log.w(TAG, "Hotspot gestartet, aber keine Zugangsdaten lesbar")
                    onFailed("no-credentials")
                }
            }

            override fun onFailed(reason: Int) {
                Log.w(TAG, "Hotspot-Start fehlgeschlagen: ${failureName(reason)}")
                onFailed(failureName(reason))
            }

            override fun onStopped() {
                Log.i(TAG, "Hotspot von der Plattform gestoppt")
                onStopped()
            }
        }

        return try {
            Log.i(TAG, "Starte LocalOnlyHotspot …")
            wifi.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
            HotspotSession {
                cancelled.set(true)
                try { reservationHolder[0]?.close() } catch (_: Throwable) {}
                reservationHolder[0] = null
            }
        } catch (e: Throwable) {
            // SecurityException (fehlende Standortberechtigung) / IllegalStateException etc.
            Log.w(TAG, "startLocalOnlyHotspot warf ${e.javaClass.simpleName}: ${e.message}")
            onFailed(e.javaClass.simpleName)
            HotspotSession { }
        }
    }

    /** Liest SSID + Passphrase aus der Reservation — API-Level-abhängig. */
    @Suppress("DEPRECATION")
    private fun readCredentials(
        reservation: WifiManager.LocalOnlyHotspotReservation,
    ): Pair<String?, String?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val soft = try { reservation.softApConfiguration } catch (_: Throwable) { null }
            if (soft != null) {
                val ssid = try { soft.ssid } catch (_: Throwable) { null }
                val pass = try { soft.passphrase } catch (_: Throwable) { null }
                if (!ssid.isNullOrEmpty()) return ssid to pass
            }
        }
        // API 26–29 (und Fallback): WifiConfiguration trägt SSID/PSK in Anführungszeichen.
        val cfg = try { reservation.wifiConfiguration } catch (_: Throwable) { null }
        if (cfg != null) {
            val ssid = cfg.SSID?.removeSurrounding("\"")
            val pass = cfg.preSharedKey?.removeSurrounding("\"")
            if (!ssid.isNullOrEmpty()) return ssid to pass
        }
        return null to null
    }

    private fun failureName(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "ERROR_NO_CHANNEL"
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "ERROR_GENERIC"
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "ERROR_INCOMPATIBLE_MODE"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "ERROR_TETHERING_DISALLOWED"
        else -> "ERROR_$reason"
    }
}

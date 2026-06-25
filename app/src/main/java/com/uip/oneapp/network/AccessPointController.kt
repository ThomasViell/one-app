package com.uip.oneapp.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// HINWEIS: Diese Datei trennt bewusst zwei Schichten:
//  - [AccessPointController] / [AccessPointSpec] = Android-frei, unit-getestet
//    (KEIN android.util.Log → sonst „not mocked" in reinen JVM-Tests).
//  - [AndroidSoftApStarter] (eigene Datei) = die einzige Android-/Privileg-/Reflection-behaftete
//    Schicht (Geräte-Test). Sie spannt den gebrandeten SoftAP auf.

/**
 * Grund-Kennung „Werks-Image-Privileg fehlt" — gemeldet von [AndroidSoftApStarter], wenn der
 * privilegierte SoftAP-Pfad mangels System-Berechtigung scheitert. Die [PairingScreen]-UI
 * übersetzt genau diese Kennung in den verständlichen Hinweis statt einer Roh-Fehlermeldung.
 */
const val REASON_PRIVILEGE = "needs-privilege"

/**
 * Reine, Android-freie Start-Vorbedingung des Tablet-Hotspots (Dual-Modus, Welle 3a):
 * der Hotspot kommt **nur im [HardwareMode.DIRECT]** hoch (App läuft auf der ONE).
 *
 * Hinweis zur STA/AP-Exklusivität: Auf der ONE (ein `wlan0`) kann nicht gleichzeitig eine
 * WLAN-Verbindung (STA) und ein Hotspot (AP) laufen. Der privilegierte SoftAP-Pfad
 * ([AndroidSoftApStarter]) legt eine aktive STA-Verbindung für die Hotspot-Dauer still — exakt
 * das gewünschte On-Demand-Verhalten (CEO-Entscheid: Hotspot per Schalter). Daher reduziert sich
 * das Gate auf die Modus-Prüfung.
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

    /** Start angestoßen, warten auf die Aktiv-Meldung der Plattform. */
    data object Starting : ApState

    /**
     * Hotspot läuft; [ssid] + [passphrase] sind die **gebrandeten, persistenten** Zugangsdaten
     * (feste SSID `DrainQ-ONE-<serial>` + einmalig erzeugtes Geheimnis, [SoftApSpec]). Werden als
     * WIFI-QR ([WifiQr.encode]) gezeigt.
     */
    data class Active(val ssid: String, val passphrase: String) : ApState

    /** Start abgelehnt (falscher Modus). */
    data class Blocked(val gate: AccessPointSpec.GateResult) : ApState

    /**
     * Start fehlgeschlagen. [reason] trägt entweder [REASON_PRIVILEGE] (Werks-Image-Privileg
     * fehlt) oder einen Plattform-/Reflection-Fehlernamen; die UI rendert beides passend.
     */
    data class Failed(val reason: String) : ApState
}

/** Eine laufende Hotspot-Sitzung; [stop] gibt die Plattform-Ressource frei. */
fun interface HotspotSession {
    fun stop()
}

/**
 * Plattform-Seam für den Hotspot-Start. Trennt die (testbare) Zustandslogik des
 * [AccessPointController] vom Android-/Privileg-behafteten SoftAP-Aufruf — im Test wird ein Fake
 * injiziert (vgl. `interfaceProvider` in [OneRemoteServer]).
 */
interface HotspotStarter {
    /**
     * Startet den Hotspot. Meldet das Ergebnis über genau einen der Callbacks:
     *  - [onActive] mit der gebrandeten SSID + Passphrase, sobald der AP läuft,
     *  - [onFailed] mit einem kurzen Grund (Fehlercode/Privileg-Mangel/Exception),
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
 * **Umsetzung über den privilegierten SoftAP-Pfad** ([AndroidSoftApStarter]): feste, gebrandete
 * SSID + persistentes Geheimnis, **ohne Standortberechtigung** (Kunden-No-Go). Möglich, weil die
 * ONE-App im Werks-Image privilegiert ist (s. `docs/SOFTAP_WERKS_PRIVILEG.md`). Der frühere
 * `startLocalOnlyHotspot`-Pfad (Standort-Prompt + plattform-gewürfelte SSID) ist entfernt.
 *
 * Die heikle Zustandslogik (Idempotenz, Gate, Übergänge) ist Android-frei und unit-getestet;
 * der eigentliche Plattform-Aufruf liegt hinter [HotspotStarter] ([AndroidSoftApStarter]).
 *
 * **TODO(device, Welle 5):** Abnahme auf der ONE — Hotspot an (OHNE Standort-Prompt, gebrandete
 * SSID) → Tablet scannt QR → joint → Video/Telemetrie/Steuerung wie über Büro-WLAN.
 */
class AccessPointController(
    private val mode: HardwareMode,
    private val starter: HotspotStarter,
) {
    companion object {
        /**
         * Erwartetes Gateway des SoftAP (Android-Tethering-Konvention `192.168.43.1`, identisch zu
         * [OneHardwareConfig.targetIp]). Informativ — das Tablet lernt die echte IP ohnehin aus der
         * UDP-Discovery (Quelladresse, [OneRemoteServer]).
         */
        const val SOFTAP_GATEWAY_IP = "192.168.43.1"
    }

    private val _state = MutableStateFlow<ApState>(ApState.Idle)
    val state: StateFlow<ApState> = _state.asStateFlow()

    @Volatile
    private var session: HotspotSession? = null

    /**
     * Monoton steigende Sitzungs-Generation. Der Plattform-Start meldet **asynchron** (Hochfahren
     * dauert hunderte ms–s); ein [stop] oder erneuter [start] dazwischen darf nicht von einem
     * späten Callback einer überholten Sitzung überschrieben werden. Jeder Callback trägt seine
     * `gen` und wirkt nur, wenn sie noch aktuell ist. (Die Ressource der abgebrochenen Sitzung gibt
     * [HotspotStarter]/[AndroidSoftApStarter] selbst frei — siehe dortiges `stop`.)
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
     * Stoppt den Hotspot und gibt die Ressource frei. Idempotent. Bumpt die [generation], sodass
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

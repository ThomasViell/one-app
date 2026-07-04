package com.uip.oneapp.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Schmaler Seam auf den [WifiController] für den Auto-Reconnect — im Test ein Fake
 * (kein Android). [WifiController] implementiert das Interface direkt.
 */
interface AutoConnectWifi {
    /** WLAN-Scan (nutzt den vorhandenen Berechtigungsstand; ohne Berechtigung leer/Cache). */
    suspend fun scan(): List<WifiNetwork>

    /** App-gebundene Verbindung via WifiNetworkSpecifier (siehe [WifiController]). */
    fun connectViaRequest(
        ssid: String,
        password: String,
        secured: Boolean,
        onAvailable: () -> Unit,
        onUnavailable: () -> Unit,
        onLost: () -> Unit,
    )

    /** SSID des aktuell verbundenen WLANs, `null` wenn unbekannt/nicht verbunden. */
    fun currentWifiSsid(): String?

    /**
     * Prozess an das aktive WLAN binden (Trigger C: bereits im richtigen Netz).
     * [onLost] feuert einmalig, wenn genau dieses Netz später wegfällt — sonst wäre der
     * Suggestion-/System-Join-Pfad blind für Abrisse (kein Specifier-Callback vorhanden).
     */
    fun bindToCurrentWifi(onLost: () -> Unit): Boolean
}

/** Beobachtbare Phase des Auto-Reconnects (für die "Bekannte ONE"-Sektion im NetworkScreen). */
enum class AutoConnectPhase {
    /** Kein Versuch aktiv/nötig. */
    IDLE,

    /** Scan nach bekannter ONE läuft. */
    SCANNING,

    /** Verbindungsversuch läuft (inkl. Backoff vor dem Single-Retry). */
    CONNECTING,

    /** Mit einer bekannten ONE verbunden. */
    CONNECTED,

    /**
     * Verbindung verloren und der EINE automatische Wiederholungsversuch ist verbraucht —
     * die UI zeigt den Banner "Verbindung zur ONE verloren" mit Retry-Button ([retry]).
     */
    LOST,
}

data class AutoConnectState(
    val phase: AutoConnectPhase = AutoConnectPhase.IDLE,
    val ssid: String = "",
)

/**
 * Zentrale Auto-Reconnect-Logik (Auto-Reconnect W1) — nur aktiv im [HardwareMode.WIFI]
 * (Tablet). Eine einmal per QR gekoppelte ONE ([KnownOneStore]) wird ohne erneuten
 * QR-Scan wiederverbunden:
 *
 *  - **Trigger A — App-Start:** einmalig nach [startDelayMs], wenn nicht verbunden:
 *    Scan → bekannte ONE in Reichweite → [AutoConnectWifi.connectViaRequest] mit den
 *    gespeicherten Credentials. Ab Android 11 überspringt die Plattform den
 *    Specifier-Dialog oft für zuvor genehmigte Netze → real häufig 0 Taps, sonst 1 Tap.
 *  - **Trigger B — Verbindungsabriss:** genau EIN automatischer Versuch nach
 *    [retryBackoffMs]; danach [AutoConnectPhase.LOST] (UI-Banner, kein Dialog-Spam).
 *  - **Trigger C — bereits im richtigen Netz:** hängt das System schon in einer
 *    bekannten `DrainQ-ONE-*`-SSID, wird KEIN Specifier-Request gestellt, sondern nur
 *    der Prozess gebunden ([AutoConnectWifi.bindToCurrentWifi]) und die Hardware-Kette
 *    gestartet.
 *
 * Nach JEDEM erfolgreichen Join feuert [startHardwareChain] (Discovery/probeEndpoints +
 * startPolling; die RTSP-VideoSource published der OneHardwareService selbst).
 *
 * Nebenläufigkeit: Alle Plattform-Callbacks werden in den [scope] gehoppt und über ein
 * Generation-Token ([gen]) entwertet, wenn inzwischen ein neuer Versuch/manueller Connect
 * läuft (Muster LOHS-Stop-Race, Welle 3a). [scope] muss single-threaded dispatchen
 * (Main bzw. TestDispatcher).
 *
 * Bewusst ohne android.util.Log (JVM-testbar) — Diagnose über [log].
 */
class OneAutoConnector(
    private val wifi: AutoConnectWifi,
    private val store: KnownOneStore,
    private val mode: HardwareMode,
    private val scope: CoroutineScope,
    private val autoConnectEnabled: suspend () -> Boolean,
    private val startHardwareChain: suspend () -> Unit,
    private val startDelayMs: Long = START_DELAY_MS,
    private val retryBackoffMs: Long = RETRY_BACKOFF_MS,
    private val log: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(AutoConnectState())
    val state: StateFlow<AutoConnectState> = _state.asStateFlow()

    private var started = false
    private var attemptJob: Job? = null

    /** Genau EIN automatischer Wiederverbindungsversuch nach Abriss (kein Dialog-Spam). */
    private var retryUsed = false

    /** Generation-Token: entwertet Callbacks überholter Verbindungsversuche. */
    private var gen = 0

    /** Trigger A: einmalig beim App-Start aufrufen (OneApp), nur im WiFi-/Tablet-Modus wirksam. */
    fun start() {
        if (mode != HardwareMode.WIFI || started) return
        started = true
        // Über launchAttempt (nicht nur scope.launch): so ist der Start-Versuch als
        // attemptJob abbrechbar, wenn zwischenzeitlich ein manueller Connect/QR-Join
        // dazwischenkommt — sonst würde ein noch laufender Scan danach connectViaRequest
        // feuern und die manuelle Specifier-Verbindung ersetzen (Race).
        launchAttempt(afterMs = startDelayMs, force = false, lostSsid = null)
    }

    /**
     * Manueller Retry aus dem UI-Banner — bewusster Nutzer-Tap, ignoriert den Auto-Toggle.
     * Ein LOST-Kontext bleibt erhalten: scheitert auch der manuelle Versuch, bleibt der
     * Banner stehen (statt still auf IDLE zu fallen).
     */
    fun retry() {
        retryUsed = false
        val lostSsid = _state.value
            .takeIf { it.phase == AutoConnectPhase.LOST }
            ?.ssid?.takeIf { it.isNotEmpty() }
        launchAttempt(afterMs = 0, force = true, lostSsid = lostSsid)
    }

    /**
     * Race-Schutz: Der Nutzer hat einen manuellen Connect gestartet (NetworkViewModel).
     * Laufende Auto-Versuche abbrechen und deren späte Callbacks entwerten — der
     * [WifiController] ersetzt die Specifier-Anfrage ohnehin (cancelRequest).
     * Läuft wie ALLE Zustands-Mutationen im [scope] (Main.immediate: vom Main-Thread aus
     * inline, also noch VOR dem nachfolgenden connectViaRequest des Aufrufers).
     */
    fun noteManualConnectStarted() {
        scope.launch {
            gen++
            attemptJob?.cancel()
            attemptJob = null
            if (_state.value.phase == AutoConnectPhase.SCANNING ||
                _state.value.phase == AutoConnectPhase.CONNECTING
            ) {
                _state.value = AutoConnectState()
            }
        }
    }

    /**
     * Erfolgreicher Join von außen (manueller/QR-Weg über das NetworkViewModel) —
     * Zustand synchronisieren, Timestamp setzen, Hardware-Kette starten. Wird aus dem
     * ConnectivityThread-Callback gerufen → in den [scope] hoppen (keine Mutation fremder
     * Threads an gen/attemptJob/_state).
     */
    fun noteExternalJoin(ssid: String) {
        if (!KnownOneStore.isOneSsid(ssid)) return
        scope.launch {
            gen++
            attemptJob?.cancel()
            attemptJob = null
            retryUsed = false
            _state.value = AutoConnectState(AutoConnectPhase.CONNECTED, ssid)
            store.touch(ssid)
            runCatching { startHardwareChain() }
        }
    }

    /** Verbindungsabriss von außen gemeldet (manueller/QR-Weg) — Trigger B. */
    fun noteExternalLoss(ssid: String) {
        scope.launch { handleLoss(ssid) }
    }

    // ===== interne Zustandsmaschine (läuft immer im scope) =====

    private fun launchAttempt(afterMs: Long, force: Boolean, lostSsid: String?) {
        gen++
        attemptJob?.cancel()
        attemptJob = scope.launch {
            if (afterMs > 0) delay(afterMs)
            attempt(force, lostSsid)
        }
    }

    /**
     * Ein kompletter Verbindungsversuch. [lostSsid] != null markiert den Abriss-Retry
     * (Trigger B): Fehlschlag endet dann in [AutoConnectPhase.LOST] statt still in IDLE.
     */
    private suspend fun attempt(force: Boolean, lostSsid: String?) {
        if (mode != HardwareMode.WIFI) return
        // Bereits verbunden (z. B. externer QR-Join während des Start-Delays): nichts tun —
        // ein Specifier-Request würde die bestehende Verbindung ersetzen.
        if (_state.value.phase == AutoConnectPhase.CONNECTED) return
        if (!force && !autoConnectEnabled()) {
            log("Auto-Reconnect deaktiviert (Setting)")
            _state.value = AutoConnectState()
            return
        }

        // Trigger C: System hängt bereits in einer bekannten ONE-SSID → kein Specifier-Dialog,
        // nur Prozess binden und mit Discovery weitermachen. Bind-Erfolg ist Bedingung:
        // eine veraltete SSID-Meldung ohne reales WLAN-Netz darf nicht CONNECTED setzen.
        // Der onLost-Watcher macht auch den Suggestion-/System-Join-Pfad abriss-sensitiv
        // (Trigger B) — per Generation-Token entwertet, wenn längst neu verbunden wurde.
        val current = wifi.currentWifiSsid()
        if (current != null && store.get(current) != null) {
            val g = gen
            val bound = wifi.bindToCurrentWifi(
                onLost = { scope.launch { if (g == gen) handleLoss(current) } }
            )
            if (bound) {
                log("Bereits im bekannten Netz $current — Prozess gebunden (kein Dialog)")
                handleJoined(current)
                return
            }
        }

        val known = store.all()
        if (known.isEmpty()) {
            _state.value = AutoConnectState()
            return
        }

        _state.value = AutoConnectState(AutoConnectPhase.SCANNING, lostSsid ?: "")
        val scanResults = wifi.scan()
        val match = store.bestMatch(scanResults)
        if (match == null) {
            log("Keine bekannte ONE in Reichweite (${scanResults.size} Netze gescannt)")
            _state.value = if (lostSsid != null) {
                AutoConnectState(AutoConnectPhase.LOST, lostSsid)
            } else {
                AutoConnectState()
            }
            return
        }

        log("Verbinde automatisch mit ${match.ssid}")
        _state.value = AutoConnectState(AutoConnectPhase.CONNECTING, match.ssid)
        val g = gen
        wifi.connectViaRequest(
            ssid = match.ssid,
            password = match.passphrase,
            secured = match.secured,
            onAvailable = { scope.launch { if (g == gen) handleJoined(match.ssid) } },
            onUnavailable = { scope.launch { if (g == gen) handleUnavailable(match.ssid, lostSsid) } },
            onLost = { scope.launch { if (g == gen) handleLoss(match.ssid) } },
        )
    }

    private suspend fun handleJoined(ssid: String) {
        retryUsed = false
        _state.value = AutoConnectState(AutoConnectPhase.CONNECTED, ssid)
        store.touch(ssid)
        runCatching { startHardwareChain() }
    }

    private fun handleUnavailable(ssid: String, lostSsid: String?) {
        log("Auto-Connect zu $ssid nicht möglich (abgelehnt/nicht erreichbar)")
        _state.value = if (lostSsid != null) {
            AutoConnectState(AutoConnectPhase.LOST, lostSsid)
        } else {
            // App-Start-Versuch gescheitert (z. B. Dialog weggetippt): still bleiben,
            // kein zweiter Dialog — der Nutzer verbindet über die "Bekannte ONE"-Sektion.
            AutoConnectState()
        }
    }

    /** Trigger B: genau EIN automatischer Wiederverbindungsversuch mit kurzem Backoff. */
    private fun handleLoss(ssid: String) {
        if (!KnownOneStore.isOneSsid(ssid)) return
        // Späte onLost-Callbacks nach bereits neuem Verbindungszustand ignorieren.
        val phase = _state.value.phase
        if (phase == AutoConnectPhase.SCANNING || phase == AutoConnectPhase.CONNECTING) return
        if (retryUsed) {
            log("Verbindung zu $ssid erneut verloren — kein weiterer Auto-Versuch (Banner)")
            _state.value = AutoConnectState(AutoConnectPhase.LOST, ssid)
            return
        }
        retryUsed = true
        log("Verbindung zu $ssid verloren — EIN Auto-Retry in ${retryBackoffMs} ms")
        _state.value = AutoConnectState(AutoConnectPhase.CONNECTING, ssid)
        launchAttempt(afterMs = retryBackoffMs, force = false, lostSsid = ssid)
    }

    companion object {
        /** Leichter Start-Delay: App-Start (Koin/UI) nicht mit Scan/Dialog überfahren. */
        const val START_DELAY_MS = 3_000L

        /** Kurzer Backoff vor dem einen Abriss-Retry (ONE-Reboot/AP-Neustart abwarten). */
        const val RETRY_BACKOFF_MS = 3_000L
    }
}

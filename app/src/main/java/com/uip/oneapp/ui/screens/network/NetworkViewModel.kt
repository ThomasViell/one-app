package com.uip.oneapp.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.network.AutoConnectPhase
import com.uip.oneapp.network.AutoConnectState
import com.uip.oneapp.network.ConnectionType
import com.uip.oneapp.network.ConnectivityMonitor
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.network.KnownOneStore
import com.uip.oneapp.network.OneAutoConnector
import com.uip.oneapp.network.OnlineStatus
import com.uip.oneapp.network.WifiController
import com.uip.oneapp.network.WifiNetwork
import com.uip.oneapp.network.WifiPath
import com.uip.oneapp.network.WifiQr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Verbindungs-Phase für klares UI-Feedback (kein stilles Scheitern). */
enum class ConnectPhase { IDLE, CONNECTING, CONNECTED, FAILED }

/** Anzeige-Status einer bekannten ONE in der "Bekannte ONE"-Sektion. */
enum class KnownOneStatus { CONNECTED, IN_RANGE, NOT_FOUND, UNKNOWN }

data class NetworkUiState(
    val online: OnlineStatus = OnlineStatus(),
    val wifiEnabled: Boolean = false,
    val path: WifiPath = WifiPath.REQUEST,
    val scanning: Boolean = false,
    val networks: List<WifiNetwork> = emptyList(),
    val connectPhase: ConnectPhase = ConnectPhase.IDLE,
    val connectSsid: String = "",
    /** Lokalisierungs-Key für den Fehlergrund (nur bei [ConnectPhase.FAILED]). */
    val failReasonKey: String? = null,
    /** Tablet (WiFi-Modus)? → blendet die "Mit ONE verbinden"-QR-Kopplung ein (Welle 3a). */
    val isTablet: Boolean = false,
    /** Bekannte (gekoppelte) ONEs — nur SSIDs, KEINE Passphrasen im UI-State (W1). */
    val knownOnes: List<String> = emptyList(),
    /** Zustand des Auto-Reconnects (Banner "Verbindung verloren" bei [AutoConnectPhase.LOST]). */
    val autoState: AutoConnectState = AutoConnectState(),
) {
    /** Tethering (USB/Bluetooth) ist aktiv, wenn die aktive Verbindung darüber läuft. */
    val tetheringActive: Boolean
        get() = online.online &&
            (online.type == ConnectionType.USB_TETHER || online.type == ConnectionType.BLUETOOTH)

    /**
     * In-App-WLAN-Picker zeigen? Nur als Device-Owner (Kiosk/LockTask), wo die
     * Android-WLAN-Einstellungen evtl. gesperrt sind. Sonst ist der Settings-Sprung
     * die primäre Aktion.
     */
    val inAppPicker: Boolean get() = path == WifiPath.PRIVILEGED

    /**
     * Status einer bekannten ONE: verbunden > in Reichweite (letzter Scan) > nicht
     * gefunden (Scan lieferte Netze, aber diese nicht) > unbekannt (noch kein Scan-Wissen).
     */
    fun knownOneStatus(ssid: String): KnownOneStatus = when {
        autoState.phase == AutoConnectPhase.CONNECTED && autoState.ssid == ssid ->
            KnownOneStatus.CONNECTED
        connectPhase == ConnectPhase.CONNECTED && connectSsid == ssid ->
            KnownOneStatus.CONNECTED
        networks.any { it.ssid == ssid } -> KnownOneStatus.IN_RANGE
        networks.isNotEmpty() -> KnownOneStatus.NOT_FOUND
        else -> KnownOneStatus.UNKNOWN
    }
}

class NetworkViewModel(
    private val connectivityMonitor: ConnectivityMonitor,
    private val wifiController: WifiController,
    private val knownOneStore: KnownOneStore,
    private val autoConnector: OneAutoConnector,
    hardwareMode: HardwareMode,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NetworkUiState(
            wifiEnabled = wifiController.isWifiEnabled(),
            path = wifiController.detectPath(),
            isTablet = hardwareMode == HardwareMode.WIFI,
        )
    )
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    init {
        connectivityMonitor.refresh()
        viewModelScope.launch {
            connectivityMonitor.status.collect { status ->
                _uiState.value = _uiState.value.copy(online = status)
            }
        }
        // Auto-Reconnect-Zustand (W1) in den UI-State spiegeln (Banner + Status-Chips).
        viewModelScope.launch {
            autoConnector.state.collect { auto ->
                _uiState.value = _uiState.value.copy(autoState = auto)
            }
        }
        refreshKnownOnes()
        // Tablet mit gekoppelter ONE: einmal opportunistisch scannen, damit die
        // "Bekannte ONE"-Sektion Reichweite anzeigen kann. Ohne Scan-Berechtigung
        // liefert scan() leer/Cache → Status bleibt schlicht "unbekannt" (kein Crash).
        if (_uiState.value.isTablet && _uiState.value.knownOnes.isNotEmpty()) {
            scan()
        }
    }

    /** Bekannte ONEs aus dem Store in den UI-State laden (nur SSIDs). */
    private fun refreshKnownOnes() {
        _uiState.value = _uiState.value.copy(knownOnes = knownOneStore.all().map { it.ssid })
    }

    /** Scan starten (Berechtigungen müssen vorab erteilt sein — siehe Screen). */
    fun scan() {
        if (_uiState.value.scanning) return
        _uiState.value = _uiState.value.copy(
            scanning = true,
            wifiEnabled = wifiController.isWifiEnabled(),
        )
        viewModelScope.launch {
            val nets = wifiController.scan()
            _uiState.value = _uiState.value.copy(scanning = false, networks = nets)
        }
    }

    /**
     * Verbinden — pfadabhängig. Privilegiert: WifiManager direkt. Sonst: WifiNetworkSpecifier
     * via requestNetwork (System-Dialog). Erfolgspfad persistiert die Credentials im
     * [KnownOneStore] — ausschließlich für gebrandete `DrainQ-ONE-*`-SSIDs (W1), zusätzlich
     * als [android.net.wifi.WifiNetworkSuggestion] für den Null-Tap-Rejoin der Plattform (W2).
     */
    fun connect(network: WifiNetwork, password: String) {
        // Race-Schutz: laufende Auto-Versuche abbrechen — der manuelle Connect gewinnt
        // (connectViaRequest ersetzt die Specifier-Anfrage ohnehin).
        autoConnector.noteManualConnectStarted()
        _uiState.value = _uiState.value.copy(
            connectPhase = ConnectPhase.CONNECTING,
            connectSsid = network.ssid,
            failReasonKey = null,
        )
        when (_uiState.value.path) {
            WifiPath.PRIVILEGED -> viewModelScope.launch {
                val ok = wifiController.connectPrivileged(network.ssid, password, network.secured)
                _uiState.value = if (ok) {
                    onJoinSucceeded(network, password)
                    _uiState.value.copy(connectPhase = ConnectPhase.CONNECTED)
                } else {
                    _uiState.value.copy(connectPhase = ConnectPhase.FAILED, failReasonKey = "net_fail_generic")
                }
            }
            WifiPath.REQUEST -> wifiController.connectViaRequest(
                ssid = network.ssid,
                password = password,
                secured = network.secured,
                onAvailable = {
                    onJoinSucceeded(network, password)
                    _uiState.value = _uiState.value.copy(connectPhase = ConnectPhase.CONNECTED)
                },
                onUnavailable = {
                    _uiState.value = _uiState.value.copy(
                        connectPhase = ConnectPhase.FAILED, failReasonKey = "net_fail_unavailable"
                    )
                },
                onLost = {
                    // Nur als Verlust melden, wenn vorher verbunden war.
                    if (_uiState.value.connectPhase == ConnectPhase.CONNECTED) {
                        _uiState.value = _uiState.value.copy(
                            connectPhase = ConnectPhase.FAILED, failReasonKey = "net_fail_lost"
                        )
                    }
                    // Trigger B (W1): AutoConnector übernimmt den einen Wiederverbindungs-
                    // versuch; für Nicht-ONE-SSIDs intern ein No-op.
                    autoConnector.noteExternalLoss(network.ssid)
                },
            )
        }
    }

    /**
     * Erfolgspfad beider Verbinde-Wege (W1): ONE-Credentials persistieren (nur
     * `DrainQ-ONE-*`), den AutoConnector synchronisieren (startet die Hardware-Kette)
     * und die Known-Liste aktualisieren.
     */
    private fun onJoinSucceeded(network: WifiNetwork, password: String) {
        if (!KnownOneStore.isOneSsid(network.ssid)) return
        val security = if (network.secured) WifiQr.SECURITY_WPA else WifiQr.SECURITY_OPEN
        knownOneStore.save(network.ssid, password, security)
        autoConnector.noteExternalJoin(network.ssid)
        refreshKnownOnes()
    }

    /**
     * Tablet-Kopplung (Welle 3a): verarbeitet ein gescanntes WIFI-QR-Payload. Parst SSID/Passwort
     * ([WifiQr.parse]) und tritt über denselben pfadabhängigen Weg wie [connect] bei. `null`
     * (Scan abgebrochen) ist ein No-op; ein nicht-WLAN-Code meldet einen klaren Fehler.
     */
    fun joinFromQr(payload: String?) {
        if (payload == null) return
        val creds = WifiQr.parse(payload)
        if (creds == null) {
            _uiState.value = _uiState.value.copy(
                connectPhase = ConnectPhase.FAILED,
                connectSsid = "",
                failReasonKey = "connect_one_invalid_qr",
            )
            return
        }
        connectToSsid(creds.ssid, creds.passphrase, creds.secured)
    }

    /** Verbindet zu einem nicht gescannten/per QR gelieferten Netz (Re-Use des [connect]-Pfads). */
    fun connectToSsid(ssid: String, password: String, secured: Boolean) {
        connect(WifiNetwork(ssid = ssid, bssid = "", level = 0, rssi = 0, secured = secured), password)
    }

    /** "Verbinden" aus der "Bekannte ONE"-Sektion — nutzt die gespeicherten Credentials (W1). */
    fun connectKnown(ssid: String) {
        val known = knownOneStore.get(ssid) ?: return
        connectToSsid(known.ssid, known.passphrase, known.secured)
    }

    /** "Vergessen": löscht Kopplung rückstandsfrei (Store, W2 zusätzlich die Suggestion). */
    fun forgetKnown(ssid: String) {
        knownOneStore.forget(ssid)
        refreshKnownOnes()
    }

    /** Retry-Button des "Verbindung verloren"-Banners (W1, Trigger B verbraucht). */
    fun retryAutoConnect() {
        autoConnector.retry()
    }

    fun refreshStatus() {
        connectivityMonitor.refresh()
        _uiState.value = _uiState.value.copy(wifiEnabled = wifiController.isWifiEnabled())
        refreshKnownOnes()
    }

    // BEWUSST kein cancelRequest() in onCleared() (F2-Befund 2026-07-03): Die Specifier-
    // Verbindung zur ONE MUSS die Navigation überleben — der Nutzer scannt den QR im
    // Netzwerk-Screen und wechselt danach zur Inspektion. cancelRequest() hier riss die
    // app-gebundene Verbindung (inkl. bindProcessToNetwork) genau in dem Moment ab.
    // Lebensdauer der Verbindung = WifiController-Single (ersetzt beim nächsten Connect,
    // endet mit dem Prozess); explizites Trennen bleibt über cancelRequest() möglich.
}

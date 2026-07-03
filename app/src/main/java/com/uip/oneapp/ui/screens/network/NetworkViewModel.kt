package com.uip.oneapp.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.network.ConnectionType
import com.uip.oneapp.network.ConnectivityMonitor
import com.uip.oneapp.network.HardwareMode
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
}

class NetworkViewModel(
    private val connectivityMonitor: ConnectivityMonitor,
    private val wifiController: WifiController,
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
     * via requestNetwork (System-Dialog). Passwort wird NICHT gespeichert.
     */
    fun connect(network: WifiNetwork, password: String) {
        _uiState.value = _uiState.value.copy(
            connectPhase = ConnectPhase.CONNECTING,
            connectSsid = network.ssid,
            failReasonKey = null,
        )
        when (_uiState.value.path) {
            WifiPath.PRIVILEGED -> viewModelScope.launch {
                val ok = wifiController.connectPrivileged(network.ssid, password, network.secured)
                _uiState.value = if (ok) {
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
                },
            )
        }
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

    fun refreshStatus() {
        connectivityMonitor.refresh()
        _uiState.value = _uiState.value.copy(wifiEnabled = wifiController.isWifiEnabled())
    }

    // BEWUSST kein cancelRequest() in onCleared() (F2-Befund 2026-07-03): Die Specifier-
    // Verbindung zur ONE MUSS die Navigation überleben — der Nutzer scannt den QR im
    // Netzwerk-Screen und wechselt danach zur Inspektion. cancelRequest() hier riss die
    // app-gebundene Verbindung (inkl. bindProcessToNetwork) genau in dem Moment ab.
    // Lebensdauer der Verbindung = WifiController-Single (ersetzt beim nächsten Connect,
    // endet mit dem Prozess); explizites Trennen bleibt über cancelRequest() möglich.
}

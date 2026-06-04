package com.uip.oneapp.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.network.ConnectionType
import com.uip.oneapp.network.ConnectivityMonitor
import com.uip.oneapp.network.OnlineStatus
import com.uip.oneapp.network.WifiController
import com.uip.oneapp.network.WifiNetwork
import com.uip.oneapp.network.WifiPath
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NetworkUiState(
            wifiEnabled = wifiController.isWifiEnabled(),
            path = wifiController.detectPath(),
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

    fun refreshStatus() {
        connectivityMonitor.refresh()
        _uiState.value = _uiState.value.copy(wifiEnabled = wifiController.isWifiEnabled())
    }

    override fun onCleared() {
        super.onCleared()
        wifiController.cancelRequest()
    }
}

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

data class NetworkUiState(
    val online: OnlineStatus = OnlineStatus(),
    val wifiEnabled: Boolean = false,
    val path: WifiPath = WifiPath.SUGGESTION,
    val scanning: Boolean = false,
    val networks: List<WifiNetwork> = emptyList(),
    val connecting: Boolean = false,
    /** Lokalisierungs-Key für eine transiente Snackbar-Meldung. */
    val messageKey: String? = null,
) {
    /** Tethering (USB/Bluetooth) ist aktiv, wenn die aktive Verbindung darüber läuft. */
    val tetheringActive: Boolean
        get() = online.online &&
            (online.type == ConnectionType.USB_TETHER || online.type == ConnectionType.BLUETOOTH)
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
            messageKey = null,
        )
        viewModelScope.launch {
            val nets = wifiController.scan()
            _uiState.value = _uiState.value.copy(scanning = false, networks = nets)
        }
    }

    /**
     * Verbinden. Versucht je nach erkanntem Pfad den privilegierten Weg, sonst den
     * System-Vorschlag. Passwort wird NICHT gespeichert.
     */
    fun connect(network: WifiNetwork, password: String) {
        _uiState.value = _uiState.value.copy(connecting = true, messageKey = null)
        viewModelScope.launch {
            val key = when (_uiState.value.path) {
                WifiPath.PRIVILEGED -> {
                    val ok = wifiController.connectPrivileged(network.ssid, password, network.secured)
                    if (ok) "net_connect_started"
                    else if (wifiController.suggest(network.ssid, password, network.secured)) "net_suggestion_added"
                    else "net_connect_failed"
                }
                WifiPath.SUGGESTION -> {
                    if (wifiController.suggest(network.ssid, password, network.secured)) "net_suggestion_added"
                    else "net_connect_failed"
                }
            }
            _uiState.value = _uiState.value.copy(connecting = false, messageKey = key)
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(messageKey = null)
    }

    fun refreshStatus() {
        connectivityMonitor.refresh()
        _uiState.value = _uiState.value.copy(wifiEnabled = wifiController.isWifiEnabled())
    }
}

package com.uip.oneapp.ui.screens.connection

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.network.DiscoveredHost
import com.uip.oneapp.network.DeviceType
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.NetworkDiscoveryService
import com.uip.oneapp.network.OneHardwareState
import com.uip.oneapp.network.RtspStreamTester
import com.uip.oneapp.network.TwoHardwareService
import com.uip.oneapp.ui.screens.settings.SettingsViewModel
import com.uip.oneapp.ui.screens.settings.settingsStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.uip.oneapp.network.RtspTestResult
import com.uip.oneapp.network.WifiInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connection_settings")

data class ConnectionUiState(
    val wifiInfo: WifiInfo = WifiInfo(),
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val manualRtspUrl: String = "",
    val activeRtspUrl: String = "",
    val streamStatus: StreamStatus = StreamStatus.IDLE,
    val rtspTestResults: List<RtspTestResult> = emptyList(),
    val isTestingRtsp: Boolean = false,
    val testingIp: String = "",
    val logMessages: List<String> = emptyList(),
    val hardwareState: OneHardwareState = OneHardwareState(),
    val isProbing: Boolean = false
)

enum class StreamStatus {
    IDLE, CONNECTING, CONNECTED, ERROR
}

class ConnectionViewModel(
    private val networkDiscovery: NetworkDiscoveryService,
    private val rtspTester: RtspStreamTester,
    private val hardwareService: HardwareService,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ConnectionVM"
        private val KEY_LAST_RTSP_URL = stringPreferencesKey("last_rtsp_url")
        private const val ONE_RTSP_PORT = 8554
        private const val ONE_RTSP_PATH = "/1234"

        fun buildRtspUrl(ip: String): String = "rtsp://$ip:$ONE_RTSP_PORT$ONE_RTSP_PATH"
    }

    init {
        // Load saved URL
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            val savedUrl = prefs[KEY_LAST_RTSP_URL] ?: ""
            if (savedUrl.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(manualRtspUrl = savedUrl)
                hardwareService.lastRtspUrl = savedUrl
                addLog("Gespeicherte RTSP-URL: $savedUrl")
            }
        }
        refreshWifiStatus()
        // Collect hardware state updates and auto-connect RTSP when IP is discovered
        viewModelScope.launch {
            hardwareService.hardwareState.collect { hwState ->
                val oldIp = _uiState.value.hardwareState.connectionStatus.discoveredIp
                _uiState.value = _uiState.value.copy(hardwareState = hwState)

                // When controller IP is discovered, auto-set RTSP URL
                val newIp = hwState.connectionStatus.discoveredIp
                if (newIp.isNotEmpty() && newIp != oldIp) {
                    val rtspUrl = buildRtspUrl(newIp)
                    addLog("RTSP-URL aktualisiert: $rtspUrl")
                    _uiState.value = _uiState.value.copy(manualRtspUrl = rtspUrl)
                    // Auto-connect to stream if not already connected
                    if (_uiState.value.activeRtspUrl.isEmpty()) {
                        connectToStream(rtspUrl)
                    }
                }
            }
        }
        // Collect hardware log messages
        viewModelScope.launch {
            hardwareService.logMessages.collect { hwLogs ->
                hwLogs.firstOrNull()?.let { latestLog ->
                    val currentLogs = _uiState.value.logMessages
                    if (currentLogs.isEmpty() || currentLogs.first() != latestLog) {
                        addLog("[HW] $latestLog")
                    }
                }
            }
        }
        // Auto-probe hardware on startup
        viewModelScope.launch {
            delay(2000)
            if (!hardwareService.isConnected) {
                Log.d(TAG, "Auto-probe hardware on startup")
                probeHardware()
            }
        }
    }

    fun refreshWifiStatus() {
        networkDiscovery.refreshWifiInfo()
        viewModelScope.launch {
            networkDiscovery.wifiInfo.collect { info ->
                _uiState.value = _uiState.value.copy(wifiInfo = info)
            }
        }
    }

    fun startNetworkScan() {
        viewModelScope.launch {
            addLog("Starte Netzwerk-Scan...")
            networkDiscovery.refreshWifiInfo()

            // Collect scan state
            launch {
                networkDiscovery.isScanning.collect { scanning ->
                    _uiState.value = _uiState.value.copy(isScanning = scanning)
                }
            }
            launch {
                networkDiscovery.scanProgress.collect { progress ->
                    _uiState.value = _uiState.value.copy(scanProgress = progress)
                }
            }
            launch {
                networkDiscovery.discoveredHosts.collect { hosts ->
                    _uiState.value = _uiState.value.copy(discoveredHosts = hosts)
                }
            }

            val hosts = networkDiscovery.scanNetwork()
            addLog("Scan abgeschlossen: ${hosts.size} Geräte gefunden")
            hosts.forEach { host ->
                val portInfo = if (host.openPorts.isNotEmpty()) " - Ports: ${host.openPorts.joinToString()}" else ""
                addLog("  ${host.ip}${portInfo}")
            }
        }
    }

    fun testRtspOnHost(host: DiscoveredHost) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingRtsp = true,
                testingIp = host.ip,
                rtspTestResults = emptyList()
            )
            addLog("Teste RTSP auf ${host.ip}...")

            val ports = host.openPorts.ifEmpty { listOf(554, 8554) }
            val results = rtspTester.testAllUrls(host.ip, ports)
            _uiState.value = _uiState.value.copy(
                rtspTestResults = results,
                isTestingRtsp = false
            )

            val working = results.filter { it.reachable }
            if (working.isNotEmpty()) {
                addLog("${working.size} erreichbare RTSP-URLs gefunden:")
                working.forEach { r ->
                    addLog("  ${r.url} [${r.responseCode}]")
                }
            } else {
                addLog("Keine RTSP-Streams auf ${host.ip} gefunden")
            }
        }
    }

    fun updateManualUrl(url: String) {
        _uiState.value = _uiState.value.copy(manualRtspUrl = url)
    }

    fun testManualUrl() {
        val url = _uiState.value.manualRtspUrl.trim()
        if (url.isEmpty()) return

        viewModelScope.launch {
            addLog("Teste manuelle URL: $url")
            _uiState.value = _uiState.value.copy(isTestingRtsp = true)
            val result = rtspTester.testUrl(url)
            _uiState.value = _uiState.value.copy(isTestingRtsp = false)

            if (result.reachable) {
                addLog("URL erreichbar! Response: ${result.responseCode}")
            } else {
                addLog("URL nicht erreichbar: ${result.error}")
            }
        }
    }

    fun connectToStream(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                activeRtspUrl = url,
                streamStatus = StreamStatus.CONNECTING
            )
            hardwareService.lastRtspUrl = url
            addLog("Verbinde mit Stream: $url")

            // Save URL
            context.dataStore.edit { prefs ->
                prefs[KEY_LAST_RTSP_URL] = url
            }
        }
    }

    fun onStreamConnected() {
        _uiState.value = _uiState.value.copy(streamStatus = StreamStatus.CONNECTED)
        addLog("Stream verbunden!")
    }

    fun onStreamError(error: String) {
        _uiState.value = _uiState.value.copy(streamStatus = StreamStatus.ERROR)
        addLog("Stream-Fehler: $error")
    }

    fun disconnectStream() {
        _uiState.value = _uiState.value.copy(
            activeRtspUrl = "",
            streamStatus = StreamStatus.IDLE
        )
        addLog("Stream getrennt")
    }

    fun cycleLightPower() {
        if (!hardwareService.isConnected) {
            addLog("Licht: Keine TCP-Verbindung")
            return
        }
        hardwareService.cycleLightPower()
    }

    fun cycleFrequency() {
        if (!hardwareService.isConnected) {
            addLog("Sonde: Keine TCP-Verbindung")
            return
        }
        hardwareService.cycleFrequency()
    }

    fun resetMeterAbsolute() {
        if (!hardwareService.isConnected) {
            addLog("Meter: Keine TCP-Verbindung")
            return
        }
        hardwareService.resetMeterAbsolute()
    }

    fun resetMeterRelative() {
        if (!hardwareService.isConnected) {
            addLog("Meter: Keine TCP-Verbindung")
            return
        }
        hardwareService.resetMeterRelative()
    }

    fun probeHardware() {
        if (hardwareService.isConnected) {
            addLog("Hardware bereits verbunden")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProbing = true)
            addLog("Starte Hardware-Probe...")
            try {
                val status = hardwareService.probeEndpoints()
                if (status.cableControllerReachable || status.crawlerControllerReachable) {
                    addLog("Hardware gefunden! Starte Polling...")
                    hardwareService.startPolling()
                } else {
                    addLog("Kein Hardware-Controller erreichbar")
                }
            } catch (e: Exception) {
                addLog("Hardware-Probe Fehler: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isProbing = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't destroy the singleton hardware service - it must stay alive for InspectionScreen
    }

    private fun addLog(message: String) {
        Log.d(TAG, message)
        val current = _uiState.value.logMessages.toMutableList()
        current.add(0, message)
        if (current.size > 50) current.removeLast()
        _uiState.value = _uiState.value.copy(logMessages = current)
    }
}

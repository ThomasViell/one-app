package com.uip.oneapp.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hardware service for the DrainQ TWO (NSP3CT HMX Suite) camera system.
 * Communicates via HTTP CGI REST calls instead of TCP binary protocol.
 *
 * Key differences from ONE:
 * - Camera at 172.169.10.65 (configurable)
 * - RTSP on port 554 with authentication (admin:bmw12345)
 * - Control via HTTP CGI endpoints on port 80
 * - No built-in cable meter controller (meter via MQTT or manual - Phase 2)
 * - PTZ support (zoom, focus) via CGI
 */
class TwoHardwareService(
    private val config: TwoHardwareConfig = TwoHardwareConfig()
) : HardwareService {

    companion object {
        private const val TAG = "TwoHardwareService"
        private const val HTTP_TIMEOUT_MS = 3000

        // PTZ command codes (from APK analysis)
        private const val PTZ_STOP = 0
        private const val PTZ_ZOOM_TELE = 11
        private const val PTZ_ZOOM_WIDE = 12
        private const val PTZ_FOCUS_NEAR = 13
        private const val PTZ_FOCUS_FAR = 14
    }

    private val _hardwareState = MutableStateFlow(OneHardwareState())
    override val hardwareState: StateFlow<OneHardwareState> = _hardwareState.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    override val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    @Volatile
    override var lastRtspUrl: String = ""

    override val isConnected: Boolean
        get() = _hardwareState.value.connectionStatus.tcpConnected

    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private var pollingJob: Job? = null

    // Local light state (IRCut mode: 2=day, 3=night/IR)
    @Volatile
    private var irCutMode: Int = 2

    // Software offset for absolute distance (no hardware meter on TWO)
    @Volatile
    private var absoluteDistanceOffset: Float = 0f

    // ===== Probe & Discovery =====

    override suspend fun probeEndpoints(): HardwareConnectionStatus = withContext(Dispatchers.IO) {
        addLog("Starte TWO-Discovery (HTTP ${config.cameraIp})...")

        val reachable = testHttpConnection()

        val status = if (reachable) {
            addLog("TWO-Kamera gefunden: ${config.cameraIp}")

            // Try to get system info
            val sysInfo = httpGet("/cgi-bin/magicBox.cgi?action=getSystemInfo")
            if (sysInfo != null) {
                addLog("System-Info: ${sysInfo.take(100)}")
            }

            HardwareConnectionStatus(
                cableControllerReachable = false,  // TWO has no cable controller
                crawlerControllerReachable = true,
                cableControllerIp = "",
                crawlerControllerIp = config.cameraIp,
                lastProbeAttemptMs = System.currentTimeMillis(),
                probeCompleted = true,
                tcpConnected = true,
                discoveredIp = config.cameraIp
            )
        } else {
            addLog("WARNUNG: TWO-Kamera nicht erreichbar unter ${config.cameraIp}")
            HardwareConnectionStatus(
                lastProbeAttemptMs = System.currentTimeMillis(),
                probeCompleted = true
            )
        }

        _hardwareState.value = _hardwareState.value.copy(
            connectionStatus = status,
            crawlerController = CrawlerControllerState(
                lightAvailable = reachable,
                lightOn = null,
                laserOn = null,
                sondeFrequency = null,
                lastUpdateMs = System.currentTimeMillis()
            )
        )
        status
    }

    private fun testHttpConnection(): Boolean {
        return try {
            val url = URL("http://${config.cameraIp}/cgi-bin/magicBox.cgi?action=getSystemInfo")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.setRequestProperty("Authorization", buildBasicAuth())
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (e: Exception) {
            addLog("HTTP-Test: ${e.message}")
            false
        }
    }

    // ===== Polling =====

    override fun startPolling() {
        ensureScope()
        if (pollingJob?.isActive == true) {
            addLog("Polling läuft bereits")
            return
        }

        addLog("Starte TWO-Polling (HTTP-Keepalive)...")
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    // Poll camera status periodically
                    val reachable = testHttpConnection()
                    val currentStatus = _hardwareState.value.connectionStatus
                    if (reachable != currentStatus.tcpConnected) {
                        _hardwareState.value = _hardwareState.value.copy(
                            connectionStatus = currentStatus.copy(tcpConnected = reachable)
                        )
                        if (reachable) {
                            addLog("TWO-Kamera verbunden")
                        } else {
                            addLog("TWO-Kamera Verbindung verloren")
                        }
                    }

                    // Optionally read temperature
                    val tempInfo = httpGet("/cgi/sys_get?Channel=1&Group=TempInfo")
                    if (tempInfo != null) {
                        Log.d(TAG, "Temp: $tempInfo")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    addLog("Polling-Fehler: ${e.message}")
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _hardwareState.value = _hardwareState.value.copy(
            connectionStatus = _hardwareState.value.connectionStatus.copy(tcpConnected = false)
        )
    }

    override fun destroy() {
        stopPolling()
        scopeJob.cancel()
    }

    // ===== Light Control (via IR-Cut mode) =====

    override fun cycleLightPower() {
        ensureScope()
        scope.launch {
            // Toggle between day mode (2) and night/IR mode (3)
            irCutMode = if (irCutMode == 2) 3 else 2
            val success = httpGet("/cgi-bin/set_ircut?ircutmode=$irCutMode&time=night&daynight=1&uid=")
            if (success != null) {
                val label = if (irCutMode == 3) "Nacht/IR" else "Tag"
                addLog("Licht-Modus: $label")
                _hardwareState.value = _hardwareState.value.copy(
                    crawlerController = _hardwareState.value.crawlerController.copy(
                        lightOn = irCutMode == 3,
                        frontLightPower = if (irCutMode == 3) 100 else 0
                    )
                )
            } else {
                addLog("Licht-Steuerung fehlgeschlagen")
            }
        }
    }

    override fun sendLightPower(power: Int) {
        // TWO doesn't have variable light power, only IR-Cut toggle
        cycleLightPower()
    }

    // ===== Sonde/Frequency - Not available on TWO =====

    override fun cycleFrequency() {
        addLog("Sonde: Nicht verfügbar bei TWO")
    }

    override fun sendFrequency(frequency: Int) {
        addLog("Sonde: Nicht verfügbar bei TWO")
    }

    // ===== Video Overlay (OSD) =====

    override fun sendVideoOverlay(text: String?) {
        ensureScope()
        scope.launch {
            when {
                text == null -> {
                    // Restore default OSD
                    httpGet("/cgi-bin/osd?enable=1&uid=")
                    addLog("Video-Overlay: Standard (AN)")
                }
                text.isEmpty() -> {
                    // Disable OSD
                    httpGet("/cgi-bin/osd?enable=0&uid=")
                    addLog("Video-Overlay: AUS")
                }
                else -> {
                    // Set custom OSD text
                    val encoded = java.net.URLEncoder.encode(text, "UTF-8")
                    httpGet("/cgi-bin/userosd?title_index=0&title_text=$encoded&uid=")
                    addLog("Video-Overlay: \"$text\"")
                }
            }
        }
    }

    // ===== Meter/Distance =====

    override fun resetMeterAbsolute() {
        val currentReading = _hardwareState.value.cableController
        val rawDistance = (currentReading.meterReading ?: 0f) + absoluteDistanceOffset
        absoluteDistanceOffset = rawDistance
        _hardwareState.value = _hardwareState.value.copy(
            cableController = currentReading.copy(meterReading = 0f)
        )
        addLog("Absolut-Meter auf 0 gesetzt (Offset: ${String.format("%.2f", rawDistance)}m)")
    }

    override fun resetMeterRelative() {
        _hardwareState.value = _hardwareState.value.copy(
            cableController = _hardwareState.value.cableController.copy(currentDistance = 0f)
        )
        addLog("Relativ-Meter auf 0 gesetzt")
    }

    // ===== TWO-specific: PTZ Control =====

    fun zoomIn() {
        sendPtzCommand("ZoomTele")
    }

    fun zoomOut() {
        sendPtzCommand("ZoomWide")
    }

    fun focusNear() {
        sendPtzCommand("FocusNear")
    }

    fun focusFar() {
        sendPtzCommand("FocusFar")
    }

    fun ptzStop() {
        ensureScope()
        scope.launch {
            httpGet("/cgi-bin/ptz.cgi?action=stop&channel=0&code=ZoomTele")
            addLog("PTZ Stop")
        }
    }

    private fun sendPtzCommand(code: String) {
        ensureScope()
        scope.launch {
            val result = httpGet("/cgi-bin/ptz.cgi?action=start&channel=0&code=$code&arg1=0&arg2=0&arg3=0")
            if (result != null) {
                addLog("PTZ: $code")
            } else {
                addLog("PTZ-Fehler: $code")
            }
        }
    }

    // ===== TWO-specific: Camera Image Settings =====

    fun setCameraImage(brightness: Int = 128, contrast: Int = 128, saturation: Int = 126, sharpness: Int = 128) {
        ensureScope()
        scope.launch {
            httpGet("/cgi-bin/image?brightness=$brightness&contrast=$contrast&saturation=$saturation&sharpness=$sharpness&hlc=0&blc=0&nr2d=179&nr3d=78&uid=")
            addLog("Kamera-Bild: B=$brightness C=$contrast S=$saturation")
        }
    }

    fun setWdr(enabled: Boolean, value: Int = 128) {
        ensureScope()
        scope.launch {
            val enable = if (enabled) 1 else 0
            httpGet("/cgi-bin/set_wdr?enable=$enable&wdr_value=$value&uid=")
            addLog("WDR: ${if (enabled) "AN ($value)" else "AUS"}")
        }
    }

    // ===== HTTP Helper =====

    private fun httpGet(path: String): String? {
        return try {
            val url = URL("${config.buildCgiBaseUrl()}$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.setRequestProperty("Authorization", buildBasicAuth())
            conn.requestMethod = "GET"

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                Log.w(TAG, "HTTP $code for $path")
                null
            }
            conn.disconnect()
            body
        } catch (e: Exception) {
            Log.w(TAG, "HTTP error for $path: ${e.message}")
            null
        }
    }

    private fun buildBasicAuth(): String {
        val credentials = "${config.cameraUser}:${config.cameraPassword}"
        val encoded = android.util.Base64.encodeToString(
            credentials.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "Basic $encoded"
    }

    private fun ensureScope() {
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
    }

    private fun addLog(message: String) {
        Log.d(TAG, message)
        val current = _logMessages.value.toMutableList()
        current.add(0, message)
        if (current.size > 50) current.removeLast()
        _logMessages.value = current
    }
}

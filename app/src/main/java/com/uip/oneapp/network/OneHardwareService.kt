package com.uip.oneapp.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

/**
 * **ONE-Remote** — WiFi-Transport-Implementierung des [HardwareService] (Dual-Modus,
 * Welle 1). Tablet = Client gegen die ONE-Einheit:
 *   - **Video:** RTSP-Stream der ONE → published als [VideoSource.Rtsp] (+ [lastRtspUrl]);
 *     Player (ExoPlayer/Media3) und Recording ([FfmpegRtspRecorder]) am App-Layer unverändert.
 *   - **Steuerung/Telemetrie:** TCP/JSON zum Bominwell `DeviceService` (Port 12345);
 *     Licht/Sonde/Meter/OSD über [OneRemoteProtocol] + [OneHardwareModels] (`SdkSendData`).
 *
 * Wiederhergestellt aus Commit `8da3b98` (am 19.05. in `13b1384` durch Migration A geleert)
 * und an das aktuelle [HardwareService]-Interface angeglichen — Drift seit `8da3b98` ist
 * allein das neue [videoSource]-Property; Paket-/Parsing-Logik liegt jetzt rein in
 * [OneRemoteProtocol] (unit-getestet).
 *
 * Der Direkt-Modus ([com.uip.oneapp.network.internal.OneInternalHardwareService]) bleibt
 * Default; diese Klasse wird nur über den versteckten Dev-Schalter (`one_transport=remote`,
 * siehe `di/AppModule`) gewählt.
 */
class OneHardwareService(
    config: OneHardwareConfig = OneHardwareConfig()
) : HardwareService {

    companion object {
        private const val TAG = "OneHardwareService"
        // Poll-Granularität des blockierenden Socket-Reads; die eigentliche Stillstands-
        // Schwelle für den Reconnect ist config.tcpReadTimeoutMs.
        private const val SOCKET_POLL_TIMEOUT_MS = 2000
    }

    private var config: OneHardwareConfig = config

    private val _hardwareState = MutableStateFlow(OneHardwareState())
    override val hardwareState: StateFlow<OneHardwareState> = _hardwareState.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    override val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // VideoSource wird konsistent zu lastRtspUrl gepflegt (vgl. TwoHardwareService).
    private val _videoSource = MutableStateFlow<VideoSource>(VideoSource.None)
    override val videoSource: StateFlow<VideoSource> = _videoSource.asStateFlow()

    override var lastRtspUrl: String = ""
        set(value) {
            field = value
            _videoSource.value = if (value.isNotEmpty()) VideoSource.Rtsp(value) else VideoSource.None
        }

    @Volatile
    private var tcpJob: Job? = null
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val gson = Gson()

    @Volatile
    private var tcpSocket: Socket? = null

    // Lokale Licht-/Sonde-Spiegelung (Controller meldet den Licht-Status nicht zurück).
    @Volatile
    private var currentLightPower: Int = 0
    @Volatile
    private var currentFrequency: Int = 0

    // Software-Offset für den Absolut-Reset (Hardware kennt keinen Absolut-Reset).
    @Volatile
    private var absoluteDistanceOffset: Float = 0f

    override val isConnected: Boolean
        get() = tcpJob?.isActive == true && _hardwareState.value.connectionStatus.tcpConnected

    // ===== Probe & Discovery =====

    override suspend fun probeEndpoints(): HardwareConnectionStatus = probeEndpoints(this.config)

    /** Test-/Override-Variante: erlaubt das Einspeisen einer abweichenden [config]. */
    suspend fun probeEndpoints(
        config: OneHardwareConfig = this.config
    ): HardwareConnectionStatus = withContext(Dispatchers.IO) {
        if (isConnected) {
            addLog("Bereits verbunden, überspringe Probe")
            return@withContext _hardwareState.value.connectionStatus
        }

        this@OneHardwareService.config = config

        // Schritt 1: Direkt-Test der konfigurierten Ziel-IP (ONE-als-AP, Default 192.168.43.1).
        addLog("Teste ONE-Control direkt: ${config.targetIp}:${config.tcpPort}...")
        var discoveredIp: String? =
            if (testTcpConnection(config.targetIp, config.tcpPort)) {
                addLog("ONE-Control erreichbar unter ${config.targetIp}")
                config.targetIp
            } else null

        // Schritt 2: Fallback auf optionale UDP-Broadcast-Discovery.
        if (discoveredIp == null) {
            addLog("Direkt nicht erreichbar — versuche UDP-Discovery (:${config.broadcastPort})...")
            discoveredIp = discoverViaUdpBroadcast(config)?.also {
                addLog("ONE-Controller per Broadcast gefunden: $it")
            }
        }

        val status = if (discoveredIp != null) {
            // RTSP-URL optimistisch setzen → videoSource=Rtsp; Player/Recorder am App-Layer
            // ziehen daraus. (Erreichbarkeitstest des Streams: RtspStreamTester am App-Layer.)
            lastRtspUrl = config.buildRtspUrl(discoveredIp)
            addLog("RTSP-URL: $lastRtspUrl")
            HardwareConnectionStatus(
                cableControllerReachable = true,
                crawlerControllerReachable = true,
                cableControllerIp = discoveredIp,
                crawlerControllerIp = discoveredIp,
                lastProbeAttemptMs = System.currentTimeMillis(),
                probeCompleted = true,
                discoveredIp = discoveredIp
            )
        } else {
            addLog("WARNUNG: Kein ONE-Controller gefunden!")
            HardwareConnectionStatus(
                lastProbeAttemptMs = System.currentTimeMillis(),
                probeCompleted = true
            )
        }

        _hardwareState.value = _hardwareState.value.copy(connectionStatus = status)
        status
    }

    private fun discoverViaUdpBroadcast(config: OneHardwareConfig): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(config.broadcastPort))
            socket.soTimeout = config.discoveryTimeoutMs

            addLog("Warte auf UDP-Broadcast auf Port ${config.broadcastPort}...")
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            val receivedIp = String(buffer, 0, packet.length, Charsets.UTF_8).trim()
            addLog("Broadcast empfangen: '$receivedIp' von ${packet.address.hostAddress}")

            if (receivedIp.isNotEmpty() && isValidIp(receivedIp)) receivedIp
            else packet.address?.hostAddress
        } catch (e: Exception) {
            addLog("UDP-Discovery: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }

    private fun isValidIp(ip: String): Boolean =
        ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))

    private fun testTcpConnection(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3000)
                true
            }
        } catch (e: Exception) {
            addLog("TCP-Test $ip:$port - ${e.message}")
            false
        }
    }

    // ===== Polling =====

    /**
     * Persistente TCP-Verbindung zum DeviceService mit Auto-Reconnect. Liest den
     * kontinuierlichen JSON-Push-Stream (Telemetrie) und hält die Steuer-Verbindung offen.
     */
    override fun startPolling() {
        ensureScope()
        if (tcpJob?.isActive == true) {
            addLog("TCP-Verbindung läuft bereits")
            return
        }

        val ip = _hardwareState.value.connectionStatus.discoveredIp
        if (ip.isEmpty()) {
            addLog("Polling nicht gestartet - kein Controller gefunden")
            return
        }

        addLog("Starte TCP-Verbindung zu $ip:${config.tcpPort}...")
        tcpJob = scope.launch {
            while (isActive) {
                try {
                    connectAndRead(ip, config.tcpPort)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) addLog("TCP-Fehler: ${e.message}")
                }
                if (isActive) {
                    addLog("Reconnect in 5s...")
                    updateConnectionState(false)
                    delay(5000)
                }
            }
        }
    }

    /**
     * Bildet das SDK-`DeviceClient`-Verhalten ab: persistenter Socket, 1024-Byte-Reads,
     * 5-s-Stillstands-Timeout → Disconnect/Retry, periodischer Keepalive.
     */
    private suspend fun connectAndRead(ip: String, port: Int) {
        var socket: Socket? = null
        var keepaliveJob: Job? = null
        try {
            socket = Socket(ip, port).apply {
                // Steuerbefehle nicht in Nagle's Algorithmus hängen lassen.
                tcpNoDelay = true
                // SO_LINGER kurz: close() schickt sofort RST/FIN statt auf ungesendete
                // Bytes zu warten → keine CLOSE_WAIT-Stapel am Server (DeviceService
                // schließt seine Server-Sockets nicht selbsttätig).
                setSoLinger(true, 0)
            }
            tcpSocket = socket
            addLog("TCP verbunden mit $ip:$port")
            updateConnectionState(true)

            val inputStream = socket.getInputStream()
            val buffer = ByteArray(1024)
            val jsonBuffer = StringBuilder()
            var lastDataTime = System.currentTimeMillis()

            socket.soTimeout = SOCKET_POLL_TIMEOUT_MS

            // Initial-Befehl, um den Datenstrom des Controllers zu triggern.
            val outputStream = socket.getOutputStream()
            try {
                val initPacket = OneRemoteProtocol.baseCommandPacket(currentLightPower, currentFrequency)
                outputStream.write(initPacket)
                outputStream.flush()
                addLog("Initial-Befehl gesendet (raw ${initPacket.size} bytes)")
            } catch (e: Exception) {
                addLog("Initial-Befehl Fehler: ${e.message}")
            }

            // Periodischer Keepalive — alterniert raw-Bytes und JSON-Wrap (SDK-Format).
            keepaliveJob = scope.launch {
                delay(500)
                var counter = 0
                while (isActive) {
                    try {
                        val pkt = OneRemoteProtocol.baseCommandPacket(currentLightPower, currentFrequency)
                        if (counter % 2 == 0) {
                            outputStream.write(pkt)
                        } else {
                            val sendData = SdkSendData(sendCommand = OneRemoteProtocol.packetAsIntList(pkt))
                            outputStream.write(gson.toJson(sendData).toByteArray(Charsets.UTF_8))
                        }
                        outputStream.flush()
                        counter++
                    } catch (_: Exception) {
                        break
                    }
                    delay(500)
                }
            }

            while (currentCoroutineContext().isActive) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        addLog("TCP-Verbindung geschlossen vom Server")
                        break
                    }
                    if (bytesRead > 0) {
                        lastDataTime = System.currentTimeMillis()
                        jsonBuffer.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                        for (json in OneRemoteProtocol.drainJsonObjects(jsonBuffer)) {
                            parseJsonObject(json)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    if (System.currentTimeMillis() - lastDataTime >= config.tcpReadTimeoutMs) {
                        addLog("Keine Daten seit ${config.tcpReadTimeoutMs}ms, reconnect...")
                        break
                    }
                }
            }
        } finally {
            keepaliveJob?.cancel()
            // Sauberer Half-Close: shutdownOutput() schickt FIN → kein CLOSE_WAIT am Server.
            try { socket?.shutdownOutput() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
            tcpSocket = null
            updateConnectionState(false)
            addLog("TCP-Verbindung getrennt")
        }
    }

    /** Dekodiert einen JSON-Push und faltet die Telemetrie in den HardwareState. */
    private fun parseJsonObject(json: String) {
        try {
            val sendData = gson.fromJson(json, SdkSendData::class.java) ?: return
            val now = System.currentTimeMillis()
            val telemetry = OneRemoteProtocol.telemetryFrom(sendData.miniPushInfo)

            val cableState = if (telemetry != null) {
                CableControllerState(
                    meterReading = OneRemoteProtocol.applyAbsoluteOffset(telemetry.rawDistance, absoluteDistanceOffset),
                    currentDistance = telemetry.currentDistance,
                    batteryLevel = telemetry.battery,
                    lastUpdateMs = now
                )
            } else {
                _hardwareState.value.cableController
            }

            // Sonde-Frequenz wird gemeldet; Licht-Status NICHT (firmware-seitig 0) → lokal halten.
            val crawlerState = if (telemetry != null) {
                currentFrequency = telemetry.frequency
                val existingCrawler = _hardwareState.value.crawlerController
                CrawlerControllerState(
                    lightOn = existingCrawler.lightOn,
                    lightAvailable = existingCrawler.lightAvailable,
                    frontLightPower = existingCrawler.frontLightPower,
                    laserOn = telemetry.frequency > 0,
                    sondeFrequency = telemetry.freqLabel,
                    lastUpdateMs = now
                )
            } else {
                _hardwareState.value.crawlerController
            }

            _hardwareState.value = _hardwareState.value.copy(
                cableController = cableState,
                crawlerController = crawlerState
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse error: ${e.message}")
        }
    }

    private fun updateConnectionState(connected: Boolean) {
        val current = _hardwareState.value
        val updatedConn = current.connectionStatus.copy(tcpConnected = connected)
        // Mit TCP-Verbindung wird die Lichtsteuerung verfügbar.
        val updatedCrawler = if (connected) {
            current.crawlerController.copy(lightAvailable = true)
        } else {
            current.crawlerController
        }
        _hardwareState.value = current.copy(
            connectionStatus = updatedConn,
            crawlerController = updatedCrawler
        )
    }

    // ===== Light Control =====

    override fun sendLightPower(power: Int) {
        val clampedPower = power.coerceIn(0, 100)
        ensureScope()
        scope.launch {
            val packet = OneRemoteProtocol.baseCommandPacket(clampedPower, currentFrequency)
            if (sendCommandToController(packet)) {
                currentLightPower = clampedPower
                addLog("Licht gesetzt: ${if (clampedPower == 0) "AUS" else "$clampedPower%"}")
                updateLocalLightState()
            }
        }
    }

    override fun cycleLightPower() {
        sendLightPower(OneRemoteProtocol.nextLightPower(currentLightPower))
    }

    // ===== Sonde/Frequency Control =====

    override fun sendFrequency(frequency: Int) {
        val clampedFreq = frequency.coerceIn(0, 3)
        ensureScope()
        scope.launch {
            val packet = OneRemoteProtocol.baseCommandPacket(currentLightPower, clampedFreq)
            if (sendCommandToController(packet)) {
                currentFrequency = clampedFreq
                val label = OneRemoteProtocol.freqLabel(clampedFreq)
                addLog("Sonde gesetzt: ${label ?: "AUS"}")
                updateLocalSondeState(clampedFreq, label)
            }
        }
    }

    override fun cycleFrequency() {
        sendFrequency(OneRemoteProtocol.nextFrequency(currentFrequency))
    }

    /**
     * Sendet ein Steuer-Paket über die bestehende TCP-Verbindung. SDK-Gson-Wrap:
     * `SendData` mit der Paket-Int-Liste im Feld `sendCommand`.
     */
    private fun sendCommandToController(packet: ByteArray): Boolean {
        val socket = tcpSocket ?: return false
        return try {
            val sendData = SdkSendData(sendCommand = OneRemoteProtocol.packetAsIntList(packet))
            val json = gson.toJson(sendData)
            socket.getOutputStream().write(json.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            Log.d(TAG, "Command sent: ${sendData.sendCommand?.takeLast(13)}")
            true
        } catch (e: Exception) {
            addLog("Sende-Fehler: ${e.message}")
            false
        }
    }

    // ===== Video Overlay Control =====

    /**
     * Legacy-Signatur (text-basiert) bleibt für Backwards-Compat: `text == null` → OSD AN,
     * `text == ""` → OSD AUS, sonst → AN (Custom-Text wird im Hardware-Schema nicht gerendert).
     */
    override fun sendVideoOverlay(text: String?) {
        setHardwareOsdVisible(visible = (text == null))
    }

    /**
     * Direkter ON/OFF-Toggle des Hardware-OSD (DeviceService Port 12345). Schlüsselfeld
     * `isShowOSD` triggert `BitmapOsdUtil.setShowHeadOsd()` / `setNonRecordOsd()`.
     */
    fun setHardwareOsdVisible(visible: Boolean) {
        ensureScope()
        scope.launch {
            val packet = OneRemoteProtocol.baseCommandPacket(currentLightPower, currentFrequency)
            val overlay = SdkVideoOverlay(
                isShowOSD = visible,
                modeON_OFF = if (visible) 0 else 1,
                osdHeadStrArr = emptyList(),
                osdNormalStrArr = emptyList()
            )
            val sendData = SdkSendData(
                videoOverlay = overlay,
                sendCommand = OneRemoteProtocol.packetAsIntList(packet)
            )
            val json = gson.toJson(sendData)
            Log.d(TAG, "VideoOverlay JSON: $json")

            val socket = tcpSocket
            if (socket != null) {
                try {
                    socket.getOutputStream().write(json.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    addLog("Hardware-OSD: ${if (visible) "AN" else "AUS"}")
                } catch (e: Exception) {
                    addLog("Hardware-OSD Fehler: ${e.message}")
                }
            } else {
                addLog("Hardware-OSD: Keine TCP-Verbindung")
            }
        }
    }

    // ===== Meter/Distance Reset =====

    /**
     * Absolut-Reset über Software-Offset (Hardware kennt keinen Absolut-Reset): der
     * rohe Hardware-Wert läuft weiter, wir subtrahieren den Offset für die Anzeige.
     */
    override fun resetMeterAbsolute() {
        val currentReading = _hardwareState.value.cableController
        val rawDistance = OneRemoteProtocol.offsetForAbsoluteReset(
            currentReading.meterReading ?: 0f, absoluteDistanceOffset
        )
        absoluteDistanceOffset = rawDistance
        _hardwareState.value = _hardwareState.value.copy(
            cableController = currentReading.copy(meterReading = 0f)
        )
        addLog("Absolut-Meter auf 0 gesetzt (Offset: ${String.format("%.2f", rawDistance)}m)")
    }

    /** Relativ-Reset (currentDistance/Strecke) = SDK `setJiMi(CLEAR_DISTANCE_ON)`. */
    override fun resetMeterRelative() {
        sendMeterReset(label = "relativ")
    }

    private fun sendMeterReset(label: String) {
        ensureScope()
        scope.launch {
            val packet = OneRemoteProtocol.baseCommandPacket(currentLightPower, currentFrequency)
            // SDK setJiMi(1): MiniPushInfo(currentDistance=1.0f) = CLEAR_DISTANCE_ON (Reset-Signal,
            // NICHT der Ziel-Meterwert).
            val sendData = SdkSendData(
                miniPushInfo = SdkMiniPushInfo(currentDistance = 1.0f),
                sendCommand = OneRemoteProtocol.packetAsIntList(packet)
            )
            val json = gson.toJson(sendData)
            Log.d(TAG, "Meter-Reset ($label) JSON: $json")

            val socket = tcpSocket
            if (socket != null) {
                try {
                    socket.getOutputStream().write(json.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    addLog("Meter $label Reset gesendet")
                } catch (e: Exception) {
                    addLog("Meter-Reset Fehler: ${e.message}")
                }
            } else {
                addLog("Meter-Reset: Keine TCP-Verbindung")
            }
        }
    }

    private fun updateLocalLightState() {
        val current = _hardwareState.value
        val updatedCrawler = current.crawlerController.copy(
            lightOn = currentLightPower > 0,
            lightAvailable = true,
            frontLightPower = if (currentLightPower > 0) currentLightPower else null
        )
        _hardwareState.value = current.copy(crawlerController = updatedCrawler)
    }

    private fun updateLocalSondeState(frequency: Int, label: String?) {
        val current = _hardwareState.value
        val updatedCrawler = current.crawlerController.copy(
            laserOn = frequency > 0,
            sondeFrequency = label
        )
        _hardwareState.value = current.copy(crawlerController = updatedCrawler)
    }

    // ===== Lifecycle =====

    override fun stopPolling() {
        tcpJob?.cancel()
        tcpJob = null
        try { tcpSocket?.close() } catch (_: Exception) {}
        tcpSocket = null
    }

    private fun ensureScope() {
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
    }

    override fun destroy() {
        stopPolling()
        scopeJob.cancel()
    }

    private fun addLog(message: String) {
        Log.d(TAG, message)
        val current = _logMessages.value.toMutableList()
        current.add(0, message)
        if (current.size > 50) current.removeLast()
        _logMessages.value = current
    }
}

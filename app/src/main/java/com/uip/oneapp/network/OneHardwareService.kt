package com.uip.oneapp.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

class OneHardwareService : HardwareService {

    companion object {
        private const val TAG = "OneHardwareService"

        // SDK protocol constants (from decompiled MiniPushControlHelper)
        private val HEADER = byteArrayOf(
            0xFA.toByte(), 0xAF.toByte(), 0x00, 0x10, 0x00, 0x01
        )
        private val LIGHT_POWER_CYCLE = intArrayOf(0, 30, 60, 90)
        private val FREQUENCY_CYCLE = intArrayOf(0, 1, 2, 3) // OFF, 33kHz, 640Hz, 512Hz
    }

    private val _hardwareState = MutableStateFlow(OneHardwareState())
    override val hardwareState: StateFlow<OneHardwareState> = _hardwareState.asStateFlow()

    @Volatile
    private var tcpJob: Job? = null
    private var config = OneHardwareConfig()
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val gson = Gson()

    @Volatile
    private var tcpSocket: Socket? = null

    // Last known RTSP URL (set by ConnectionViewModel, read by InspectionScreen)
    @Volatile
    override var lastRtspUrl: String = ""

    // Local light state tracking (controller doesn't report light status)
    @Volatile
    private var currentLightPower: Int = 0

    // Current sonde frequency to preserve when sending light commands
    @Volatile
    private var currentFrequency: Int = 0

    // Software offset for absolute distance reset (hardware doesn't support absolute reset)
    @Volatile
    private var absoluteDistanceOffset: Float = 0f

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    override val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    override val isConnected: Boolean
        get() = tcpJob?.isActive == true && _hardwareState.value.connectionStatus.tcpConnected

    /**
     * Discover ONE controller via UDP broadcast on port 8555,
     * then connect via TCP on port 12345 to receive sensor data.
     */
    override suspend fun probeEndpoints(): HardwareConnectionStatus = probeEndpoints(this.config)

    suspend fun probeEndpoints(
        config: OneHardwareConfig = this.config
    ): HardwareConnectionStatus = withContext(Dispatchers.IO) {
        // Don't re-probe if already connected
        if (isConnected) {
            addLog("Bereits verbunden, überspringe Probe")
            return@withContext _hardwareState.value.connectionStatus
        }

        this@OneHardwareService.config = config
        addLog("Starte ONE-Discovery (UDP:${config.broadcastPort})...")

        // Step 1: Listen for UDP broadcast from ONE controller
        var discoveredIp = discoverViaUdpBroadcast(config)

        // Step 2: Fallback to known IP if no broadcast received
        if (discoveredIp == null) {
            addLog("Kein Broadcast empfangen, teste Fallback ${config.fallbackIp}...")
            if (testTcpConnection(config.fallbackIp, config.tcpPort)) {
                discoveredIp = config.fallbackIp
                addLog("Fallback-IP ${config.fallbackIp} erreichbar!")
            }
        }

        val status = if (discoveredIp != null) {
            addLog("ONE-Controller gefunden: $discoveredIp")
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

            if (receivedIp.isNotEmpty() && isValidIp(receivedIp)) {
                receivedIp
            } else {
                packet.address?.hostAddress
            }
        } catch (e: Exception) {
            addLog("UDP-Discovery: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }

    private fun isValidIp(ip: String): Boolean {
        return ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
    }

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

    /**
     * Connect to ONE controller via TCP and continuously read sensor data.
     * Uses persistent connection with auto-reconnect.
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
                    if (isActive) {
                        addLog("TCP-Fehler: ${e.message}")
                    }
                }
                // Always wait before reconnecting (like SDK's Thread.sleep(delayedTime))
                if (isActive) {
                    addLog("Reconnect in 5s...")
                    updateConnectionState(false)
                    delay(5000)
                }
            }
        }
    }

    /**
     * Matches SDK DeviceClient behavior exactly:
     * - Socket(ip, port) constructor (no separate connect)
     * - 1024 byte read buffer
     * - 5 second no-data timeout -> disconnect and retry
     * - No soTimeout or keepAlive settings
     */
    private suspend fun connectAndRead(ip: String, port: Int) {
        var socket: Socket? = null
        var keepaliveJob: Job? = null
        try {
            socket = Socket(ip, port)
            tcpSocket = socket
            addLog("TCP verbunden mit $ip:$port")
            updateConnectionState(true)

            val inputStream = socket.getInputStream()
            val buffer = ByteArray(1024)
            val jsonBuffer = StringBuilder()
            var lastDataTime = System.currentTimeMillis()

            // Set a read timeout so we can check for stalls
            socket.soTimeout = 2000

            // Send initial raw command bytes to trigger data stream from controller
            val outputStream = socket.getOutputStream()
            try {
                val initCmd = buildBaseCommand(currentLightPower, currentFrequency)
                val initPacket = buildPacket(initCmd)
                // Try raw bytes first (some controllers expect binary protocol)
                outputStream.write(initPacket)
                outputStream.flush()
                addLog("Initial-Befehl gesendet (raw ${initPacket.size} bytes)")
            } catch (e: Exception) {
                addLog("Initial-Befehl Fehler: ${e.message}")
            }

            // Start periodic keepalive - alternating raw bytes and JSON format
            keepaliveJob = scope.launch {
                delay(500)
                var counter = 0
                while (isActive) {
                    try {
                        val cmd = buildBaseCommand(currentLightPower, currentFrequency)
                        val pkt = buildPacket(cmd)
                        if (counter % 2 == 0) {
                            // Raw binary
                            outputStream.write(pkt)
                        } else {
                            // JSON wrapped (SDK format)
                            val intList = pkt.map { it.toInt() }
                            val sendData = SdkSendData(sendCommand = intList)
                            val json = gson.toJson(sendData)
                            outputStream.write(json.toByteArray(Charsets.UTF_8))
                        }
                        outputStream.flush()
                        counter++
                    } catch (_: Exception) { break }
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
                        val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        jsonBuffer.append(chunk)
                        processJsonBuffer(jsonBuffer)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Check if we've had no data for too long
                    if (System.currentTimeMillis() - lastDataTime >= 5000) {
                        addLog("Keine Daten seit 5s, reconnect...")
                        break
                    }
                    // Otherwise just continue reading
                }
            }
        } finally {
            keepaliveJob?.cancel()
            try { socket?.close() } catch (_: Exception) {}
            tcpSocket = null
            updateConnectionState(false)
            addLog("TCP-Verbindung getrennt")
        }
    }

    /**
     * Extract and process complete JSON objects from the buffer.
     * TCP can deliver partial or concatenated JSON, so we track braces.
     */
    private fun processJsonBuffer(buffer: StringBuilder) {
        while (buffer.isNotEmpty()) {
            val start = buffer.indexOf('{')
            if (start == -1) {
                buffer.clear()
                return
            }
            if (start > 0) buffer.delete(0, start)

            // Find matching closing brace
            var depth = 0
            var end = -1
            for (i in buffer.indices) {
                when (buffer[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }

            if (end == -1) return // Incomplete JSON, wait for more data

            val json = buffer.substring(0, end + 1)
            buffer.delete(0, end + 1)

            parseJsonObject(json)
        }
    }

    private fun parseJsonObject(json: String) {
        try {
            val sendData = gson.fromJson(json, SdkSendData::class.java) ?: return
            val now = System.currentTimeMillis()

            val info = sendData.miniPushInfo
            val cableState = if (info != null) {
                CableControllerState(
                    meterReading = info.distance - absoluteDistanceOffset,
                    currentDistance = info.currentDistance,
                    batteryLevel = info.battery,
                    lastUpdateMs = now
                )
            } else {
                _hardwareState.value.cableController
            }

            // Sonde frequency: reported correctly via miniPushInfo.frequency
            // Light status: NOT reported by ONE firmware (light/power always 0)
            // We preserve locally tracked light state from sendLightPower()
            val crawlerState = if (info != null) {
                // Track current frequency so we can preserve it when sending light commands
                currentFrequency = info.frequency

                val freqLabel = when (info.frequency) {
                    1 -> "33kHz"
                    2 -> "640Hz"
                    3 -> "512Hz"
                    else -> null
                }
                val existingCrawler = _hardwareState.value.crawlerController
                CrawlerControllerState(
                    // Preserve local light state (controller doesn't report it)
                    lightOn = existingCrawler.lightOn,
                    lightAvailable = existingCrawler.lightAvailable,
                    frontLightPower = existingCrawler.frontLightPower,
                    laserOn = info.frequency > 0,
                    sondeFrequency = freqLabel,
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
        // When TCP connects, light control becomes available
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

    /**
     * Build the 12-byte BaseCommand matching SDK's MiniPushControlHelper.
     * Layout: [length, 0x01, 0x00, lightPower, frequency, 0x00 x7]
     */
    private fun buildBaseCommand(lightPower: Int, frequency: Int): ByteArray {
        val cmd = ByteArray(12)
        cmd[0] = 0x0D.toByte() // Will be overwritten by sendCommandWithChecksum
        cmd[1] = 0x01
        cmd[2] = 0x00
        cmd[3] = lightPower.coerceIn(0, 100).toByte()
        cmd[4] = frequency.coerceIn(0, 3).toByte()
        // cmd[5..11] = 0x00 (already zero-initialized)
        return cmd
    }

    /**
     * Apply checksum and prepend header, matching SDK's sendCommandWithChecksum.
     * Sets command[0] = command.length + 1, then XORs all command bytes.
     */
    private fun buildPacket(command: ByteArray): ByteArray {
        command[0] = (command.size + 1).toByte()
        var checksum = 0
        for (b in command) {
            checksum = checksum xor (b.toInt() and 0xFF)
        }
        return HEADER + command + checksum.toByte()
    }

    /**
     * Send a command packet to the controller via the existing TCP connection.
     * The SDK wraps the byte array in a SendData JSON and writes raw UTF-8 to the socket.
     */
    private fun sendCommandToController(packet: ByteArray): Boolean {
        val socket = tcpSocket ?: return false
        return try {
            // Convert to signed int list for JSON (matches SDK Gson serialization)
            val intList = packet.map { it.toInt() }
            val sendData = SdkSendData(
                miniPushInfo = null,
                videoOverlay = null,
                sendCommand = intList
            )
            val json = gson.toJson(sendData)
            socket.getOutputStream().write(json.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            Log.d(TAG, "Command sent: ${intList.takeLast(13)}")
            true
        } catch (e: Exception) {
            addLog("Sende-Fehler: ${e.message}")
            false
        }
    }

    /**
     * Set light power directly (0 = OFF, 1-100 = ON with brightness).
     * Updates local state since controller doesn't report light status.
     */
    override fun sendLightPower(power: Int) {
        val clampedPower = power.coerceIn(0, 100)
        ensureScope()
        scope.launch {
            val command = buildBaseCommand(clampedPower, currentFrequency)
            val packet = buildPacket(command)
            if (sendCommandToController(packet)) {
                currentLightPower = clampedPower
                addLog("Licht gesetzt: ${if (clampedPower == 0) "AUS" else "$clampedPower%"}")
                updateLocalLightState()
            }
        }
    }

    /**
     * Cycle through light power levels: 0 → 30 → 60 → 90 → 0
     * Matches SDK's changeLightPower() behavior.
     */
    override fun cycleLightPower() {
        val currentIndex = LIGHT_POWER_CYCLE.indexOf(currentLightPower)
        val nextIndex = if (currentIndex == -1) 1 else (currentIndex + 1) % LIGHT_POWER_CYCLE.size
        sendLightPower(LIGHT_POWER_CYCLE[nextIndex])
    }

    // ===== Sonde/Frequency Control =====

    /**
     * Set sonde frequency (0=OFF, 1=33kHz, 2=640Hz, 3=512Hz).
     */
    override fun sendFrequency(frequency: Int) {
        val clampedFreq = frequency.coerceIn(0, 3)
        ensureScope()
        scope.launch {
            val command = buildBaseCommand(currentLightPower, clampedFreq)
            val packet = buildPacket(command)
            if (sendCommandToController(packet)) {
                currentFrequency = clampedFreq
                val label = when (clampedFreq) {
                    1 -> "33kHz"; 2 -> "640Hz"; 3 -> "512Hz"; else -> null
                }
                addLog("Sonde gesetzt: ${label ?: "AUS"}")
                updateLocalSondeState(clampedFreq, label)
            }
        }
    }

    /**
     * Cycle through sonde frequencies: OFF → 33kHz → 640Hz → 512Hz → OFF
     */
    override fun cycleFrequency() {
        val nextIndex = (currentFrequency + 1) % FREQUENCY_CYCLE.size
        sendFrequency(FREQUENCY_CYCLE[nextIndex])
    }

    // ===== Video Overlay Control =====

    /**
     * Schaltet das Hardware-OSD live an/aus.
     *
     * Implementiert das echte BWELL/MiniPush-Protokoll (rueckwirkend dekompiliert
     * aus DeviceService.kt:230-252 der minipush-APK):
     * Senden eines vollstaendigen VideoOverlay-Objekts an Port 12345.
     *
     * Schluesselfeld ist [isShowOSD]:
     *  - true  → DeviceService ruft BitmapOsdUtil.setShowHeadOsd()   (LIVE AN)
     *  - false → DeviceService ruft BitmapOsdUtil.setNonRecordOsd()  (LIVE AUS)
     *
     * Vorherige Implementation hat nur `{ text: "" }` gesendet — das war ein
     * Feld das im echten Schema gar nicht existiert; es hatte deshalb null Wirkung.
     *
     * Legacy-Signatur (text-basiert) bleibt fuer Backwards-Compat aktiv:
     *   text == null  → OSD AN
     *   text == ""    → OSD AUS
     *   text == "..." → OSD AN (Custom-Text wird im aktuellen Schema nicht unterstuetzt)
     */
    override fun sendVideoOverlay(text: String?) {
        setHardwareOsdVisible(visible = (text == null))
    }

    /** Direkter ON/OFF-Toggle fuer das Hardware-OSD. */
    fun setHardwareOsdVisible(visible: Boolean) {
        ensureScope()
        scope.launch {
            val command = buildBaseCommand(currentLightPower, currentFrequency)
            val packet = buildPacket(command)
            val intList = packet.map { it.toInt() }

            // Echtes BWELL-Schema. Die uebrigen Felder (FontColor, Pos, Strings)
            // werden vom DeviceService nur in die Prefs persistiert; sie aendern
            // nichts live. Wir senden bewusst Default-Werte mit, damit das
            // Schema vollstaendig ist und der Gson-Parser nichts vermisst.
            val overlay = SdkVideoOverlay(
                isShowOSD = visible,
                modeON_OFF = if (visible) 0 else 1,
                osdHeadStrArr = emptyList(),
                osdNormalStrArr = emptyList()
            )
            val sendData = SdkSendData(
                miniPushInfo = null,
                videoOverlay = overlay,
                sendCommand = intList
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
    // SDK ControlArgs: CLEAR_DISTANCE_OFF=0, CLEAR_DISTANCE_ON=1
    // setJiMi(CLEAR_DISTANCE_ON) sends MiniPushInfo(currentDistance=1.0f) as reset signal

    /**
     * Reset the absolute distance display to 0.
     * Uses a software offset since the hardware doesn't support absolute reset.
     * The raw hardware value continues counting, we subtract the offset for display.
     */
    override fun resetMeterAbsolute() {
        val currentReading = _hardwareState.value.cableController
        val rawDistance = (currentReading.meterReading ?: 0f) + absoluteDistanceOffset
        absoluteDistanceOffset = rawDistance
        // Immediately update displayed value to 0
        _hardwareState.value = _hardwareState.value.copy(
            cableController = currentReading.copy(meterReading = 0f)
        )
        addLog("Absolut-Meter auf 0 gesetzt (Offset: ${String.format("%.2f", rawDistance)}m)")
    }

    /**
     * Reset the relative distance counter (currentDistance/Strecke) to 0.
     * Matches SDK's setJiMi(ControlArgs.CLEAR_DISTANCE_ON) exactly.
     */
    override fun resetMeterRelative() {
        sendMeterReset(label = "relativ")
    }

    /**
     * Send meter reset command matching SDK's setJiMi(1).
     * Creates SendData with miniPushInfo(currentDistance=1.0) + command bytes.
     * The value 1 = CLEAR_DISTANCE_ON (signal to reset, NOT the meter value).
     */
    private fun sendMeterReset(label: String) {
        ensureScope()
        scope.launch {
            val command = buildBaseCommand(currentLightPower, currentFrequency)
            val packet = buildPacket(command)
            val intList = packet.map { it.toInt() }

            // SDK's setJiMi(1): creates MiniPushInfo with all defaults, sets currentDistance=1.0f
            // Value 1 = CLEAR_DISTANCE_ON (reset signal), NOT the target meter value
            val miniPushInfo = SdkMiniPushInfo(
                currentDistance = 1.0f  // CLEAR_DISTANCE_ON
            )
            val sendData = SdkSendData(
                miniPushInfo = miniPushInfo,
                videoOverlay = null,
                sendCommand = intList
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

    /** Update the hardware state with locally tracked light values. */
    private fun updateLocalLightState() {
        val current = _hardwareState.value
        val updatedCrawler = current.crawlerController.copy(
            lightOn = if (currentLightPower > 0) true else false,
            lightAvailable = true,
            frontLightPower = if (currentLightPower > 0) currentLightPower else null
        )
        _hardwareState.value = current.copy(crawlerController = updatedCrawler)
    }

    /** Update the hardware state with locally tracked sonde frequency. */
    private fun updateLocalSondeState(frequency: Int, label: String?) {
        val current = _hardwareState.value
        val updatedCrawler = current.crawlerController.copy(
            laserOn = frequency > 0,
            sondeFrequency = label
        )
        _hardwareState.value = current.copy(crawlerController = updatedCrawler)
    }

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

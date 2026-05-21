package com.uip.oneapp.network.internal

import android.util.Log
import com.uip.oneapp.network.CableControllerState
import com.uip.oneapp.network.CrawlerControllerState
import com.uip.oneapp.network.HardwareConnectionStatus
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.OneHardwareState
import com.uip.oneapp.network.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Direkt-lokale Implementierung des HardwareService — DrainQ.ONE läuft auf der
 * BWELL/Bominwell ONE-Hardware selbst und spricht die Schiebekamera direkt an:
 *   - Steuerung: serielle Schnittstelle /dev/ttyS5 @ 9600 baud
 *   - Live-Video: V4L2 über /dev/video0 (MACROSILICON MS2109)
 *
 * Ersetzt OneHardwareService (TCP/JSON-Variante für Slave-Monitor-Setup auf
 * Samsung-Tablet). Aus dem Smoke-Test (one-smoketest) verifiziert.
 *
 * Voraussetzung: rooted Tablet + chmod 666 auf /dev/ttyS5 und /dev/video0
 * (siehe Phase P7 Permission-Strategie).
 *
 * Bezug: docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md, Phase P3.
 */
class OneInternalHardwareService(
    private val serialDevicePath: String = "/dev/ttyS5"
) : HardwareService {

    companion object {
        private const val TAG = "OneInternalHW"
        private const val RX_POLL_INTERVAL_MS = 10L
        private const val UI_PUBLISH_INTERVAL_MS = 33L
    }

    // ── State ──────────────────────────────────────────────────────

    private val _hardwareState = MutableStateFlow(OneHardwareState())
    override val hardwareState: StateFlow<OneHardwareState> = _hardwareState.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    override val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // lastRtspUrl ist im lokal-Modus inaktiv — videoSource wird stattdessen als
    // LocalBitmap published.
    override var lastRtspUrl: String = ""

    private val _videoSource = MutableStateFlow<VideoSource>(VideoSource.None)
    override val videoSource: StateFlow<VideoSource> = _videoSource.asStateFlow()

    // ── Hardware-Komponenten ───────────────────────────────────────

    private val camera = V4L2Camera()
    private val meter = LinearMeterCalculator()

    // Serial-Port — geöffnet via JNI mit O_RDWR|O_NOCTTY+cfmakeraw (nicht FileOutputStream+stty)
    @Volatile private var serialPfd: ParcelFileDescriptor? = null
    @Volatile private var outputStream: OutputStream? = null
    @Volatile private var inputStream: InputStream? = null

    // Cached steuer-Werte (jedes Set sendet den vollständigen Base-Frame)
    @Volatile private var curPower: Int = 0      // Sonde an/aus (0/1)
    @Volatile private var curFreq: Int = 0       // 0=Off, 1=512Hz, 2=640Hz, 3=33kHz
    @Volatile private var curLight: Int = 0      // 0..100

    @Volatile private var lastRawDistanceMm: Int = 0
    @Volatile private var distanceOffsetMm: Int = 0

    private var scope: CoroutineScope? = null
    private var rxJob: Job? = null

    private var connected: Boolean = false

    override val isConnected: Boolean
        get() = connected

    // ── Lifecycle ──────────────────────────────────────────────────

    override suspend fun probeEndpoints(): HardwareConnectionStatus = withContext(Dispatchers.IO) {
        val videoFile = File("/dev/video0")
        val serialFile = File(serialDevicePath)
        val videoOk = videoFile.exists() && videoFile.canRead()
        val serialOk = serialFile.exists() && serialFile.canRead() && serialFile.canWrite()

        val status = HardwareConnectionStatus(
            cableControllerReachable = serialOk,
            crawlerControllerReachable = videoOk,
            cableControllerIp = "local",
            crawlerControllerIp = "local",
            lastProbeAttemptMs = System.currentTimeMillis(),
            probeCompleted = true,
            tcpConnected = serialOk && videoOk,
            discoveredIp = "local"
        )
        _hardwareState.update { it.copy(connectionStatus = status) }
        addLog("probeEndpoints: video=$videoOk serial=$serialOk")
        status
    }

    // startPolling() darf den aufrufenden Thread (oft Main) NICHT blockieren.
    // JNI-open + IO-Operationen laufen in setupAndPoll() auf dem IO-Dispatcher.
    override fun startPolling() {
        if (rxJob != null) return
        addLog("startPolling: dispatching IO setup")
        val coScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = coScope
        rxJob = coScope.launch { setupAndPoll() }
    }

    private suspend fun setupAndPoll() {
        try {
            val file = File(serialDevicePath)
            if (!file.canRead() || !file.canWrite()) {
                addLog("ERROR: serial port not RW-accessible — chmod 666 missing?")
                _hardwareState.update {
                    it.copy(connectionStatus = it.connectionStatus.copy(tcpConnected = false))
                }
                return
            }
            // JNI-Open: O_RDWR | O_NOCTTY + cfmakeraw() + tcsetattr() auf demselben fd.
            // Ersetzt stty+FileOutputStream — stty schloss den fd, was den Treiber die
            // termios zurücksetzen ließ; FileOutputStream öffnete dann mit OPOST aktiv
            // und transformierte 0x0D (LL-Byte) per ONLCR in 0x0D 0x0A → Frame kaputt.
            val fd = SerialPortNative.openSerial(serialDevicePath, 9600)
            if (fd < 0) {
                addLog("ERROR: SerialPortNative.openSerial($serialDevicePath) failed")
                _hardwareState.update {
                    it.copy(connectionStatus = it.connectionStatus.copy(tcpConnected = false))
                }
                return
            }
            val pfd = ParcelFileDescriptor.adoptFd(fd)
            serialPfd = pfd
            // FileInputStream/FileOutputStream auf FileDescriptor: kein fd-Ownership,
            // close() schließt nicht den fd — Schließen passiert über pfd.close().
            outputStream = FileOutputStream(pfd.fileDescriptor)
            inputStream = FileInputStream(pfd.fileDescriptor)
            connected = true

            // Dev_Open: Kamerakopf aktivieren — Hardware ignoriert alle Steuerbefehle
            // solange power=0 (Dev_Close). Entspricht setPower(Dev_Open) im Bominwell-Original.
            curPower = 1
            sendBase()

            // Kamera-Capture starten und als VideoSource publishen
            try {
                camera.start()
                _videoSource.value = VideoSource.LocalBitmap(camera.frame)
            } catch (e: Exception) {
                addLog("camera.start failed (non-fatal): ${e.message}")
                Log.w(TAG, "camera.start failed", e)
            }

            _hardwareState.update {
                it.copy(connectionStatus = it.connectionStatus.copy(tcpConnected = true))
            }

            // TX-Heartbeat und RX-Loop parallel starten.
            // Die Bominwell-AIO-App sendet den Base-Frame kontinuierlich (~100ms Intervall).
            // Ohne Heartbeat ignoriert die Hardware nach kurzer Zeit neue Light-Werte.
            scope?.launch { txHeartbeat() }
            rxLoop()
        } catch (e: Exception) {
            addLog("startPolling failed: ${e.message}")
            Log.e(TAG, "startPolling failed", e)
        }
    }

    // Sendet den aktuellen Base-Frame alle 100 ms — Hardware erwartet kontinuierliche
    // Befehle (wie Bominwell MiniPushControlHelper-Timer). Verhindert Reset auf Defaults.
    private suspend fun txHeartbeat() {
        Log.i(TAG, "txHeartbeat: started (100ms)")
        while (scope?.isActive == true) {
            sendBase()
            try { delay(100L) } catch (_: Exception) { return }
        }
        Log.i(TAG, "txHeartbeat: stopped")
    }

    override fun stopPolling() {
        rxJob?.cancel()
        rxJob = null
        scope?.cancel()
        scope = null
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { serialPfd?.close() } catch (_: Exception) {}
        outputStream = null
        inputStream = null
        serialPfd = null
        connected = false
        camera.stop()
        _videoSource.value = VideoSource.None
        _hardwareState.update {
            it.copy(connectionStatus = it.connectionStatus.copy(tcpConnected = false))
        }
        addLog("stopPolling: closed")
    }

    override fun destroy() {
        stopPolling()
    }

    // ── Common controls ────────────────────────────────────────────

    override fun cycleLightPower() {
        // Identisch MiniPushControlHelper.changeLightPower: 0→33→66→100→0
        val cycle = intArrayOf(0, 33, 66, 100)
        val cur = curLight
        val next = cycle.firstOrNull { it > cur } ?: cycle[0]
        sendLightPower(next)
    }

    override fun sendLightPower(power: Int) {
        curLight = power.coerceIn(0, 100)
        curPower = 1  // Dev_Open: device must be active to accept light commands
        // sendBase() wird NICHT direkt aufgerufen — txHeartbeat schickt den aktuellen
        // Wert alle 100ms. Kein blockierendes write() auf dem Main Thread (ANR-Schutz).
    }

    override fun cycleFrequency() {
        val next = (curFreq + 1) % 4
        sendFrequency(next)
    }

    override fun sendFrequency(frequency: Int) {
        curFreq = frequency.coerceIn(0, 3)
        // curPower bleibt 1 (Dev_Open) — Sonde-Status wird nur über curFreq gesteuert,
        // nicht über curPower. power=0 (Dev_Close) würde den Kamerakopf komplett schließen
        // und danach Licht- und Frequenzbefehle ignorieren.
        // sendBase() nicht direkt — txHeartbeat übernimmt (ANR-Schutz).
    }

    override fun resetMeterAbsolute() {
        distanceOffsetMm = lastRawDistanceMm
        // Direktes State-Update ohne RX-Loop-Warten
        _hardwareState.update {
            it.copy(cableController = it.cableController.copy(
                meterReading = 0f,
                currentDistance = 0f
            ))
        }
        addLog("Meterzähler auf 0 gesetzt (offset=$distanceOffsetMm mm)")
    }

    override fun resetMeterRelative() {
        // Im lokal-Modus haben wir keine Trennung absolut/relativ — beides macht das Gleiche
        resetMeterAbsolute()
    }

    override fun sendVideoOverlay(text: String?) {
        // No-op: im lokal-Modus rendert die DrainQ-UI das OSD selbst per Compose-Canvas-
        // Burn-in (siehe FfmpegRtspRecorder/OsdOverlay). Hardware-OSD wird nicht benötigt.
    }

    // ── Serial-Pfad ────────────────────────────────────────────────

    // TX-Logging auf 1/s gedrosselt (Heartbeat würde sonst 10 Zeilen/s produzieren).
    @Volatile private var lastTxLogMs = 0L

    private fun sendBase() {
        val frame = OneFrameCodec.baseCommand(
            power = curPower,
            light = curLight,
            frequency = curFreq
        )
        val now = System.currentTimeMillis()
        if (now - lastTxLogMs >= 1000L) {
            val hex = frame.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            Log.d(TAG, "TX[power=$curPower,light=$curLight,freq=$curFreq]: $hex")
            lastTxLogMs = now
        }
        try {
            outputStream?.write(frame)
            outputStream?.flush()
        } catch (e: Exception) {
            addLog("TX failed: ${e.message}")
        }
    }

    /**
     * RX-Loop: 10 ms-Polling auf serielle, parsen via OneFrameCodec, 30-Hz-Throttle
     * für UI-Updates. Identisch zur Smoke-Test-Implementation.
     */
    private suspend fun rxLoop() {
        val buffer = ByteArray(2048)
        val ins = inputStream ?: return
        var pendingState: OneHardwareState? = null
        var lastPublishMs = 0L

        // Streaming-Akkumulator — die Hardware sendet kontinuierlich, jeder read() ist NICHT
        // garantiert Frame-aligned. Wir akkumulieren bis zu 16 KB, suchen nach FA AF-Magic,
        // parsen Sub-Frames bis zum nächsten Magic, behalten den unvollständigen Tail.
        var rxAcc = ByteArray(0)
        val ACC_MAX = 16384

        // DEBUG-Heartbeat — entfernen wenn Meterzähler-Diagnose abgeschlossen
        var dbgBytesRead = 0L
        var dbgFramesParsed = 0L
        var dbgMeterFrames = 0L
        var dbgStatusFrames = 0L
        val dbgGroupCounts = mutableMapOf<Int, Int>()
        var dbgFirstFrameSample: String? = null
        var dbgG22LastLogMs = 0L
        var dbgLastHbMs = System.currentTimeMillis()
        Log.i(TAG, "rxLoop: started, polling /dev/ttyS5 every ${RX_POLL_INTERVAL_MS}ms")

        while (scope?.isActive == true) {
            try {
                val available = ins.available()
                if (available > 0) {
                    val n = ins.read(buffer, 0, minOf(available, buffer.size))
                    if (n > 0) {
                        dbgBytesRead += n
                        // Akkumulator anhängen, oben kappen
                        val merged = if (rxAcc.size + n <= ACC_MAX) {
                            ByteArray(rxAcc.size + n).also {
                                System.arraycopy(rxAcc, 0, it, 0, rxAcc.size)
                                System.arraycopy(buffer, 0, it, rxAcc.size, n)
                            }
                        } else {
                            // Akkumulator zu groß → letzte ACC_MAX Bytes behalten
                            val keepOld = maxOf(0, ACC_MAX - n)
                            ByteArray(keepOld + n).also {
                                System.arraycopy(rxAcc, rxAcc.size - keepOld, it, 0, keepOld)
                                System.arraycopy(buffer, 0, it, keepOld, n)
                            }
                        }
                        rxAcc = merged

                        // Frames aus Akku extrahieren (kann mehrere Bursts enthalten)
                        var pos = 0
                        val allFrames = mutableListOf<OneFrameCodec.RxSubFrame>()
                        while (pos < rxAcc.size - 1) {
                            // Magic FA AF suchen
                            val magicIdx = findMagic(rxAcc, pos)
                            if (magicIdx < 0) { pos = rxAcc.size - 1; break }
                            // Mindestens 6 Byte Magic-Header brauchen
                            if (magicIdx + 6 > rxAcc.size) { pos = magicIdx; break }
                            // Sub-Frames ab magicIdx+6 parsen, bis nächstes FA AF oder unvollständig
                            var subPos = magicIdx + 6
                            while (subPos + 1 < rxAcc.size) {
                                // Stopp bei nächstem Magic (= nächster Burst)
                                if (rxAcc[subPos] == 0xFA.toByte() && rxAcc[subPos + 1] == 0xAF.toByte()) break
                                val length = rxAcc[subPos].toInt() and 0xFF
                                if (length < 2) { subPos++; continue } // bad length, skip
                                if (subPos + length > rxAcc.size) break // unvollständig — auf nächsten read warten
                                val group = rxAcc[subPos + 1].toInt() and 0xFF
                                val payload = IntArray(length - 2)
                                for (k in payload.indices) payload[k] = rxAcc[subPos + 2 + k].toInt() and 0xFF
                                allFrames.add(OneFrameCodec.RxSubFrame(group, payload))
                                subPos += length
                            }
                            pos = subPos
                        }
                        // Tail behalten (alles ab pos, inkl. evtl. unvollständigem Sub-Frame)
                        rxAcc = if (pos > 0 && pos <= rxAcc.size) rxAcc.copyOfRange(pos, rxAcc.size) else rxAcc

                        if (allFrames.isNotEmpty()) {
                            dbgFramesParsed += allFrames.size
                            for (f in allFrames) {
                                dbgGroupCounts[f.group] = (dbgGroupCounts[f.group] ?: 0) + 1
                                when (f.group) {
                                    OneFrameCodec.GROUP_METER -> dbgMeterFrames++
                                    OneFrameCodec.GROUP_STATUS -> dbgStatusFrames++
                                }
                            }
                            if (dbgFirstFrameSample == null) {
                                val f0 = allFrames[0]
                                dbgFirstFrameSample = "group=${f0.group} (0x${"%02X".format(f0.group)}) len=${f0.payload.size} payload=${f0.payload.take(8).joinToString(",") { "%02X".format(it) }}"
                            }
                            // Group 22 (0x16) = Meter-Frame — alle Bytes throttled loggen
                            val nowDbg = System.currentTimeMillis()
                            for (f in allFrames) {
                                if (f.group == OneFrameCodec.GROUP_METER && nowDbg - dbgG22LastLogMs >= 500) {
                                    val hex = f.payload.joinToString(" ") { "%02X".format(it) }
                                    val be32_0 = (f.payload.getOrNull(0) ?: 0) shl 24 or
                                        ((f.payload.getOrNull(1) ?: 0) shl 16) or
                                        ((f.payload.getOrNull(2) ?: 0) shl 8) or
                                        (f.payload.getOrNull(3) ?: 0)
                                    Log.i(TAG, "G22 (${f.payload.size}B): $hex  | be32[0..3]=$be32_0 mm")
                                    dbgG22LastLogMs = nowDbg
                                }
                            }
                            pendingState = foldFrames(pendingState ?: _hardwareState.value, allFrames)
                        }
                    }
                }
            } catch (e: Exception) {
                if (scope?.isActive != true) return
                Log.w(TAG, "RX read failed: ${e.message}")
            }

            val now = System.currentTimeMillis()
            val ps = pendingState
            if (ps != null && (now - lastPublishMs) >= UI_PUBLISH_INTERVAL_MS) {
                _hardwareState.value = ps
                pendingState = null
                lastPublishMs = now
            }

            // DEBUG-Heartbeat alle 2s
            if (now - dbgLastHbMs >= 2000) {
                val groups = dbgGroupCounts.entries.sortedByDescending { it.value }
                    .joinToString(",") { "${it.key}(0x${"%02X".format(it.key)})=${it.value}" }
                Log.i(TAG, "rxLoop HB: bytes=$dbgBytesRead frames=$dbgFramesParsed meter=$dbgMeterFrames status=$dbgStatusFrames acc=${rxAcc.size} groups=[$groups]")
                if (dbgFirstFrameSample != null) Log.i(TAG, "rxLoop sample: $dbgFirstFrameSample")
                dbgBytesRead = 0; dbgFramesParsed = 0; dbgMeterFrames = 0; dbgStatusFrames = 0
                dbgGroupCounts.clear()
                dbgFirstFrameSample = null
                dbgLastHbMs = now
            }

            try { delay(RX_POLL_INTERVAL_MS) } catch (_: Exception) { return }
        }
        Log.w(TAG, "rxLoop: exited (scope inactive)")
    }

    /** Sucht FA AF im Buffer ab Offset. -1 wenn nicht gefunden. */
    private fun findMagic(buf: ByteArray, from: Int): Int {
        var i = from
        while (i < buf.size - 1) {
            if (buf[i] == 0xFA.toByte() && buf[i + 1] == 0xAF.toByte()) return i
            i++
        }
        return -1
    }

    /**
     * Folds RX-Frames in den drainq.one-OneHardwareState (CableControllerState +
     * CrawlerControllerState + ConnectionStatus).
     */
    private fun foldFrames(base: OneHardwareState, frames: List<OneFrameCodec.RxSubFrame>): OneHardwareState {
        var s = base
        val nowMs = System.currentTimeMillis()
        for (f in frames) {
            s = when (f.group) {
                OneFrameCodec.GROUP_STATUS -> if (f.payload.size >= 2) {
                    s.copy(crawlerController = s.crawlerController.copy(
                        frontLightPower = f.payload[1],
                        sondeFrequency = if (f.payload.size >= 3) freqName(f.payload[2]) else s.crawlerController.sondeFrequency,
                        lastUpdateMs = nowMs
                    ))
                } else s

                OneFrameCodec.GROUP_METER -> if (f.payload.size >= 4) {
                    val mm = (f.payload[0] shl 24) or (f.payload[1] shl 16) or
                             (f.payload[2] shl 8) or f.payload[3]
                    val mmLin = meter.calculateWithDiscount(mm).toInt()
                    lastRawDistanceMm = mmLin
                    val displayMm = mmLin - distanceOffsetMm
                    val meters = displayMm / 1000.0f
                    s.copy(cableController = s.cableController.copy(
                        meterReading = meters,
                        currentDistance = meters,
                        rawDistanceValue = mmLin,
                        lastUpdateMs = nowMs
                    ))
                } else s

                OneFrameCodec.GROUP_CAMERA -> if (f.payload.size >= 5) {
                    val mv = (f.payload[0] shl 24) or (f.payload[1] shl 16) or
                             (f.payload[2] shl 8) or f.payload[3]
                    val voltage = mv / 1000.0f
                    val batteryPct = ((voltage / 12.6f) * 100f).toInt().coerceIn(0, 100)
                    s.copy(cableController = s.cableController.copy(
                        batteryLevel = batteryPct,
                        lastUpdateMs = nowMs
                    ))
                } else s

                OneFrameCodec.GROUP_VERSION -> s  // Firmware-Version aktuell nicht in OneHardwareState

                else -> s
            }
        }
        return s
    }

    private fun freqName(byte: Int): String = when (byte) {
        0 -> "Off"
        1 -> "512 Hz"
        2 -> "640 Hz"
        3 -> "33 kHz"
        else -> "Unknown ($byte)"
    }

    private fun addLog(msg: String) {
        Log.i(TAG, msg)
        _logMessages.update { (it + msg).takeLast(100) }
    }
}

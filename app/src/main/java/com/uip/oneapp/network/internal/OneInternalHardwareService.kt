package com.uip.oneapp.network.internal

import android.util.Log
import com.uip.oneapp.BuildConfig
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
import java.io.File

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
    private val serialDevicePath: String = "/dev/ttyS5",
    // Dual-Modus W3d-Video: der V4L2-Frame-Fan-out. Per DI als geteilte Single injiziert,
    // damit derselbe Frame-Strom auch den RTSP-Encoder bedient (siehe OneVideoServer) — ohne
    // /dev/video0 ein zweites Mal zu öffnen. Default = eigener Bus (Test/Standalone).
    private val cameraBus: CameraFrameBus = CameraFrameBus()
) : HardwareService {

    companion object {
        private const val TAG = "OneInternalHW"
        private const val RX_POLL_INTERVAL_MS = 10L
        private const val UI_PUBLISH_INTERVAL_MS = 33L
        // Akkumulator-Obergrenze: schützt gegen unbegrenztes Wachsen bei Dauer-Müll
        // (nie mehr als ein paar Frames Rückstand sinnvoll).
        private const val RX_ACC_MAX = 4096
        // Debounce für den Kamerakopf-Marker: erst nach N gleichen Frames übernehmen.
        private const val CAM_ID_DEBOUNCE = 3
        // DEBUG-only: rohes Frame-/Sub-Frame-Logging für Geräte-Diagnose (Kamerakopf-
        // Marker). An den echten Build-Flag gekoppelt: nur in Debug-Builds aktiv, NIE im
        // Release (KRITIS/Logging-Hygiene — kein Roh-/Beweisdaten-Logging in Produktion).
        private val DEBUG_RX_FRAMES = BuildConfig.DEBUG
        init { System.loadLibrary("v4l2bridge") }
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

    private val meter = LinearMeterCalculator()

    // Serieller Port (UART) — nativ verwaltet (open/termios/read/write in
    // v4l2bridge.c), kein su, kein FileInputStream.available()-Problem.
    private var serialFd: Int = -1

    // Cached steuer-Werte (jedes Set sendet den vollständigen Base-Frame)
    @Volatile private var curPower: Int = 0      // Sonde an/aus (0/1)
    @Volatile private var curFreq: Int = 0       // OEM ControlArgs: 0=Off,1=33kHz,2=640Hz,3=512Hz (siehe SondeFrequency)
    @Volatile private var curLight: Int = 0      // 0..200

    @Volatile private var lastRawDistanceMm: Int = 0
    @Volatile private var distanceOffsetMm: Int = 0

    // Debounce-State für den Kamerakopf-Marker (GROUP_CAMERA payload[4]). Nur im
    // rxLoop/foldFrames (einzelne Coroutine) berührt — kein @Volatile nötig.
    private var camIdCandidate: Int = Int.MIN_VALUE
    private var camIdCandidateCount: Int = 0
    private var camIdStable: Int? = null

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

    override fun startPolling() {
        if (rxJob != null) return
        addLog("startPolling: opening $serialDevicePath + /dev/video0")

        try {
            // Nativ öffnen + termios konfigurieren (9600 8N1 Raw). Ersetzt den
            // rohen FileStream-Pfad ohne termios (Ursache der HW-Regression) und
            // braucht kein su.
            serialFd = nativeOpenSerial(serialDevicePath, 9600)
            if (serialFd < 0) {
                addLog("ERROR: nativeOpenSerial($serialDevicePath) fehlgeschlagen — Device vorhanden & beschreibbar?")
                _hardwareState.update {
                    it.copy(connectionStatus = it.connectionStatus.copy(tcpConnected = false))
                }
                return
            }
            connected = true

            val coScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = coScope
            rxJob = coScope.launch { rxLoop() }

            // Kamera-Capture starten und als VideoSource publishen. Frames kommen über den
            // Fan-out-Bus (gleiche StateFlow wie zuvor camera.frame) — additiv, lokaler Pfad
            // unverändert; derselbe Strom speist parallel den RTSP-Encoder (W3c).
            cameraBus.start()
            _videoSource.value = VideoSource.LocalBitmap(cameraBus.frames)

            _hardwareState.update {
                it.copy(connectionStatus = it.connectionStatus.copy(tcpConnected = true))
            }
        } catch (e: Exception) {
            addLog("startPolling failed: ${e.message}")
            Log.e(TAG, "startPolling failed", e)
        }
    }

    override fun stopPolling() {
        rxJob?.cancel()
        rxJob = null
        scope?.cancel()
        scope = null
        if (serialFd >= 0) nativeCloseSerial(serialFd)
        serialFd = -1
        connected = false
        cameraBus.stop()
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
        // Stufen wie in der Original-App (changeLightPower): 0 → 30 → 60 → 90 → 100.
        val cycle = intArrayOf(0, 30, 60, 90, 100)
        val cur = curLight
        val next = cycle.firstOrNull { it > cur } ?: cycle[0]
        sendLightPower(next)
    }

    override fun sendLightPower(power: Int) {
        // Hardware-Wertebereich 0–100 (Frame-Byte 3), nicht 0–255.
        curLight = power.coerceIn(0, 100)
        sendBase()
    }

    override fun cycleFrequency() {
        // Zyklus 0→1→2→3→0 (OEM: 0=Off,1=33kHz,2=640Hz,3=512Hz; siehe SondeFrequency)
        val next = (curFreq + 1) % 4
        // Sonde-Power koppelt mit Frequenz: 0=Off, sonst an
        curPower = if (next == 0) 0 else 1
        sendFrequency(next)
    }

    override fun sendFrequency(frequency: Int) {
        curFreq = frequency.coerceIn(0, 3)
        // Power-Status mit setzen: 0=Off, sonst an
        curPower = if (curFreq == 0) 0 else 1
        sendBase()
    }

    override fun resetMeterAbsolute() {
        distanceOffsetMm = lastRawDistanceMm
        meter.reset()  // Filter-/Glättungshistorie leeren, damit der Nullpunkt sauber sitzt (W3)
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

    private fun sendBase() {
        val fd = serialFd
        if (fd < 0) return
        val frame = OneFrameCodec.baseCommand(
            power = curPower,
            light = curLight,
            frequency = curFreq
        )
        val w = nativeWriteSerial(fd, frame, frame.size)
        addLog("TX p=$curPower l=$curLight f=$curFreq -> " +
               frame.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } + " (w=$w)")
    }

    // ── JNI: serielle Schnittstelle (libv4l2bridge.so) ──────────────
    private external fun nativeOpenSerial(path: String, baud: Int): Int
    private external fun nativeReadSerial(fd: Int, buf: ByteArray): Int
    private external fun nativeWriteSerial(fd: Int, data: ByteArray, len: Int): Int
    private external fun nativeCloseSerial(fd: Int)

    /**
     * RX-Loop: 10 ms-Polling auf serielle, parsen via OneFrameCodec, 30-Hz-Throttle
     * für UI-Updates. Identisch zur Smoke-Test-Implementation.
     */
    private suspend fun rxLoop() {
        val buffer = ByteArray(2048)
        // Persistenter Akkumulator: nativeReadSerial liefert Frames zerstückelt oder
        // mehrere pro Read. Wir hängen an, drainen vollständige Frames und behalten den
        // unvollständigen Rest. Behebt den früheren Pro-Chunk-FA-AF-Bug, der Gruppe
        // 23/24 systematisch verwarf (Fortsetzungs-Chunk ohne Magic -> verworfen).
        var acc = ByteArray(0)
        var pendingState: OneHardwareState? = null
        var lastPublishMs = 0L

        while (scope?.isActive == true) {
            val fd = serialFd
            if (fd < 0) return
            try {
                // nativeReadSerial blockiert bis ~100 ms (VTIME), liefert 0 bei Timeout.
                val n = nativeReadSerial(fd, buffer)
                if (n > 0) {
                    acc = if (acc.isEmpty()) buffer.copyOf(n) else acc + buffer.copyOf(n)
                    val res = OneFrameCodec.drainRxFrames(acc, acc.size)
                    if (res.consumed > 0) {
                        if (DEBUG_RX_FRAMES) {
                            val done = acc.copyOf(res.consumed)
                            Log.i(TAG, "RXFRAME=" + done.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
                        }
                        acc = if (res.consumed >= acc.size) ByteArray(0)
                              else acc.copyOfRange(res.consumed, acc.size)
                    }
                    // Schutz gegen unbegrenztes Wachsen bei Dauer-Müll ohne gültiges Magic.
                    if (acc.size > RX_ACC_MAX) acc = ByteArray(0)
                    if (res.frames.isNotEmpty()) {
                        pendingState = foldFrames(pendingState ?: _hardwareState.value, res.frames)
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

            try { delay(RX_POLL_INTERVAL_MS) } catch (_: Exception) { return }
        }
    }

    /**
     * Folds RX-Frames in den drainq.one-OneHardwareState (CableControllerState +
     * CrawlerControllerState + ConnectionStatus).
     */
    private fun foldFrames(base: OneHardwareState, frames: List<OneFrameCodec.RxSubFrame>): OneHardwareState {
        var s = base
        val nowMs = System.currentTimeMillis()
        for (f in frames) {
            // DEBUG-Diagnose: welche Serial-Groups kommen wirklich an (insb. Group 23/24)?
            if (DEBUG_RX_FRAMES) {
                Log.i(TAG, "RX grp=${f.group} len=${f.payload.size} payload=${f.payload.joinToString(" "){ "%02X".format(it and 0xFF) }}")
            }
            s = when (f.group) {
                OneFrameCodec.GROUP_STATUS -> if (f.payload.size >= 9) {
                    s.copy(crawlerController = s.crawlerController.copy(
                        frontLightPower = f.payload[1],
                        sondeFrequency = SondeFrequency.name(f.payload[2]),
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
                    // Gruppe 23 ist NICHT die Akku-Spannung (frühere RE-Fehlannahme): die
                    // Bytes [0..3]/[5] sind zwei schwankende Analog-Kanäle (~0x0200 vs ~0x011D).
                    // Akku kommt aus dem Android-System (ACTION_BATTERY_CHANGED) — daher hier
                    // KEIN batteryLevel-Write mehr (sonst Müll-Überschreibung der OSD-Spannung).
                    // payload[4] = Kamerakopf-Marker (C10=0x01/C18=0x02). Debounce: erst nach
                    // CAM_ID_DEBOUNCE gleichen Frames übernehmen (schützt gegen Transienten beim
                    // Umstecken). Roh-Byte wird publiziert; Enum-Mapping (CameraHead) in der UI.
                    val raw = f.payload[4] and 0xFF
                    if (raw == camIdCandidate) {
                        camIdCandidateCount++
                    } else {
                        camIdCandidate = raw
                        camIdCandidateCount = 1
                    }
                    if (camIdCandidateCount >= CAM_ID_DEBOUNCE) camIdStable = raw
                    s.copy(cableController = s.cableController.copy(
                        cameraId = camIdStable,
                        lastUpdateMs = nowMs
                    ))
                } else s

                OneFrameCodec.GROUP_VERSION -> s  // Firmware-Version aktuell nicht in OneHardwareState

                else -> s
            }
        }
        return s
    }

    private fun addLog(msg: String) {
        Log.i(TAG, msg)
        _logMessages.update { (it + msg).takeLast(100) }
    }
}

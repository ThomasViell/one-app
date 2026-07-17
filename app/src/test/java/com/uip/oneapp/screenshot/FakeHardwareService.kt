package com.uip.oneapp.screenshot

import android.graphics.BitmapFactory
import android.util.Log
import com.uip.oneapp.network.CableControllerState
import com.uip.oneapp.network.CrawlerControllerState
import com.uip.oneapp.network.HardwareConnectionStatus
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.OneHardwareState
import com.uip.oneapp.network.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * W-H4: Fake-HardwareService für synthetische Screenshots.
 *
 * Gibt VideoSource.LocalBitmap mit dem echten Rohr-Kamerabild (pipe_frame.png) zurück.
 * InspectionScreen wählt damit LocalBitmapVideoPlayer — ExoPlayer wird nie gestartet.
 * C18 / 72 % / 0,72 m als Fake-Telemetrie (DemoDataSeeder-Vorgabe).
 */
class FakeHardwareService(pipeFramePath: String? = null) : HardwareService {

    private val fakeState = OneHardwareState(
        cableController = CableControllerState(
            meterReading = 0.72f,
            currentDistance = 0.72f,
            batteryLevel = 72,
            cameraId = 2,  // C18 = 0x02
            lastUpdateMs = System.currentTimeMillis()
        ),
        crawlerController = CrawlerControllerState(
            frontLightPower = 80,
            sondeFrequency = "512 Hz",
            lightOn = true,
            lightAvailable = true,
            lastUpdateMs = System.currentTimeMillis()
        ),
        connectionStatus = HardwareConnectionStatus(
            cableControllerReachable = true,
            crawlerControllerReachable = true,
            probeCompleted = true,
            // Kein discoveredIp: verhindert RTSP-Auto-Connect in ConnectionViewModel (ExoPlayer würde crashen)
            discoveredIp = "",
            tcpConnected = true
        )
    )

    private val _hardwareState = MutableStateFlow(fakeState)
    override val hardwareState: StateFlow<OneHardwareState> = _hardwareState.asStateFlow()

    override val logMessages: StateFlow<List<String>> = MutableStateFlow(emptyList<String>()).asStateFlow()
    override val isConnected: Boolean = true
    override var lastRtspUrl: String = ""

    // pipe_frame.png als statischer Bitmap-Flow für den Video-Bereich
    private val _frameFlow = MutableStateFlow<android.graphics.Bitmap?>(null)
    private val _videoSource = MutableStateFlow<VideoSource>(VideoSource.LocalBitmap(_frameFlow))
    override val videoSource: StateFlow<VideoSource> = _videoSource.asStateFlow()

    init {
        // Lade pipe_frame.png aus tools/manual/assets/ (Repo-relativ vom Arbeitsverzeichnis)
        val candidates = listOf(
            pipeFramePath,
            "tools/manual/assets/pipe_frame.png",
            "../tools/manual/assets/pipe_frame.png",
            "app/../tools/manual/assets/pipe_frame.png",
        )
        for (p in candidates) {
            if (p == null) continue
            val f = File(p)
            if (f.exists()) {
                try {
                    _frameFlow.value = BitmapFactory.decodeFile(f.absolutePath)
                    Log.d("FakeHardwareService", "pipe_frame geladen: $p")
                    break
                } catch (e: Exception) {
                    Log.w("FakeHardwareService", "Laden fehlgeschlagen: $p — ${e.message}")
                }
            }
        }
    }

    override suspend fun probeEndpoints(): HardwareConnectionStatus = fakeState.connectionStatus
    override fun startPolling() {}
    override fun stopPolling() {}
    override fun destroy() {}
    override fun cycleLightPower() {}
    override fun sendLightPower(power: Int) {}
    override fun cycleFrequency() {}
    override fun sendFrequency(frequency: Int) {}
    override fun resetMeterAbsolute() {}
    override fun resetMeterRelative() {}
    override fun sendVideoOverlay(text: String?) {}
}

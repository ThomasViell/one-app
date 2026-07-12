package com.uip.oneapp.spike

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.uip.oneapp.network.internal.V4L2Camera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SPIKE (W3c) — Wegwerf-Code. NICHT für Produktion. Nur auf spike/video-rtsp.
 *
 * Standalone-Entry-Point: öffnet /dev/video0 direkt (V4L2Camera), zeigt den Feed
 * lokal (Referenz für Latenzvergleich) UND streamt ihn als RTSP/H.264 auf :8554.
 *
 * Start (Produktions-App vorher beenden, da /dev/video0 nur EINEN Besitzer hat):
 *   adb shell am force-stop com.uip.drainq.one
 *   adb shell am start -n com.uip.drainq.one.spike/com.uip.oneapp.spike.SpikeRtspActivity
 *   adb forward tcp:8554 tcp:8554
 *   ffplay -rtsp_transport tcp -fflags nobuffer -flags low_delay rtsp://127.0.0.1:8554/cam
 */
class SpikeRtspActivity : ComponentActivity() {

    private lateinit var camera: V4L2Camera
    private lateinit var encoder: SpikeH264Encoder
    private lateinit var server: SpikeRtspServer
    private lateinit var imageView: ImageView
    private lateinit var statusView: TextView

    @Volatile private var latest: Bitmap? = null
    @Volatile private var encRunning = false
    @Volatile private var fps = 0.0
    private var encThread: Thread? = null

    private var width = 1280
    private var height = 720
    private var bitRate = 4_000_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        width = intent.getIntExtra("w", 1280)
        height = intent.getIntExtra("h", 720)
        bitRate = intent.getIntExtra("bitrate", 4_000_000)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        statusView = TextView(this).apply {
            setTextColor(Color.GREEN)
            setBackgroundColor(0x88000000.toInt())
            textSize = 14f
            setPadding(16, 16, 16, 16)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
            )
        }
        root.addView(imageView)
        root.addView(statusView)
        setContentView(root)

        server = SpikeRtspServer(port = 8554, streamPath = "cam")
        encoder = SpikeH264Encoder(
            bitRate = bitRate,
            onAccessUnit = server::onAccessUnit,
            onConfig = { sps, pps -> server.setParameterSets(sps, pps) },
        )
        server.start()
        encoder.start()

        camera = V4L2Camera(devicePath = "/dev/video0", width = width, height = height)
        camera.start()

        // Vorschau (lokale Referenz für den Latenzvergleich)
        lifecycleScope.launch {
            camera.frame.collect { bm ->
                if (bm != null) {
                    latest = bm
                    imageView.setImageBitmap(bm)
                }
            }
        }

        // Encode-Loop in eigenem Thread (greift jeweils das neueste Bitmap ab → droppt bei Rückstau)
        encRunning = true
        encThread = Thread({ encodeLoop() }, "spike-encode").apply { start() }

        // Status-HUD
        lifecycleScope.launch {
            while (isActive) {
                val camState = camera.state.value
                statusView.text = buildString {
                    append("DrainQ ONE — RTSP-Spike (W3c)\n")
                    append("req ${width}x$height  enc=${encoder.actualSize}  open=${camState.open} stream=${camState.streaming}\n")
                    append("cam frames=${camState.frameCount}  enc=${encoder.encodedFrames}  fps≈${"%.1f".format(fps)}\n")
                    append("rtsp: ${server.lastStatus}  playing=${server.isPlaying()}\n")
                    append("rtsp://127.0.0.1:8554/cam (adb forward tcp:8554 tcp:8554)")
                    camState.lastError?.let { append("\nERR: $it") }
                }
                delay(500)
            }
        }
    }

    private fun encodeLoop() {
        var last: Bitmap? = null
        var count = 0
        var t0 = System.nanoTime()
        while (encRunning) {
            val bm = latest
            if (bm != null && bm !== last) {
                last = bm
                if (encoder.encode(bm)) {
                    count++
                    val now = System.nanoTime()
                    val dt = (now - t0) / 1_000_000_000.0
                    if (dt >= 1.0) {
                        fps = count / dt
                        count = 0
                        t0 = now
                    }
                }
            } else {
                try { Thread.sleep(2) } catch (_: InterruptedException) { return }
            }
        }
    }

    override fun onDestroy() {
        encRunning = false
        try { encThread?.join(500) } catch (_: Exception) {}
        try { camera.stop() } catch (_: Exception) {}
        try { encoder.stop() } catch (_: Exception) {}
        try { server.stop() } catch (_: Exception) {}
        super.onDestroy()
    }
}

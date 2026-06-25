package com.uip.oneapp.network.video

import android.graphics.Bitmap
import android.util.Log
import com.uip.oneapp.network.OneHardwareConfig
import com.uip.oneapp.network.internal.CameraFrameBus

/**
 * **ONE-Video-Server** (Dual-Modus, Welle 3c) — macht den lokalen Kamera-Feed der ONE als
 * RTSP/H.264 fürs Tablet verfügbar. Verdrahtet die bewiesene Spike-Pipeline produktiv:
 *
 * ```
 * CameraFrameBus.frames  (EIN /dev/video0, Welle 3d-Fan-out)
 *   → H264Encoder        (MediaCodec/HW-AVC, lazy aus echter Bitmap-Größe)
 *   → RtspVideoServer    (RTSP + RTP/H.264 über TCP-interleaved, :rtspPort/streamPath)
 * ```
 *
 * **Reiner Frame-Konsument:** liest ausschließlich [CameraFrameBus.frames] (`.value`) und
 * öffnet `/dev/video0` **nicht** selbst — das Gerät hat genau einen Besitzer (die lokale
 * Anzeige via `OneInternalHardwareService`). Ist die lokale Anzeige nicht aktiv (keine Frames),
 * idlet der Encoder; der lokale Direkt-Modus-Videopfad bleibt davon unberührt.
 *
 * **Lebenszyklus:** wie [com.uip.oneapp.network.OneRemoteServer] nur im DIRECT-Modus von
 * `OneApp` gestartet (Gate = HardwareMode-Single), parallel dazu. Stop = Prozessende (Kiosk).
 *
 * TODO(perf): Zero-Copy (V4L2-MJPEG → HW-Decoder → Surface → HW-Encoder) statt
 *   BitmapFactory + CPU-YUV — hebt FPS auf volle 30 und senkt Latenz/CPU (Spike: ~27 fps).
 * TODO(device): On-Device-Abnahme (lokale Anzeige läuft weiter UND Tablet zeigt Video, Welle 5).
 */
class OneVideoServer(
    private val cameraBus: CameraFrameBus,
    private val config: OneHardwareConfig = OneHardwareConfig(),
) {
    companion object { private const val TAG = "OneVideoServer" }

    @Volatile private var running = false
    @Volatile private var server: RtspVideoServer? = null
    @Volatile private var encoder: H264Encoder? = null
    private var encodeThread: Thread? = null

    val isRunning: Boolean get() = running
    fun isStreaming(): Boolean = server?.isPlaying() == true
    fun rtspStatus(): String = server?.lastStatus ?: "stopped"

    /** Idempotent: startet RTSP-Server, Encoder und den Encode-Thread. */
    fun start() {
        if (running) return
        running = true
        val srv = RtspVideoServer(
            port = config.rtspPort,
            // OneHardwareConfig.rtspPath ist "/1234"; der Server-Pfad ist ohne führenden Slash.
            streamPath = config.rtspPath.removePrefix("/"),
        )
        val enc = H264Encoder(
            onAccessUnit = srv::onAccessUnit,
            onConfig = { sps, pps -> srv.setParameterSets(sps, pps) },
        )
        server = srv
        encoder = enc
        srv.start()
        enc.start()
        encodeThread = Thread({ encodeLoop(enc) }, "one-video-encode").apply { isDaemon = true; start() }
        Log.i(TAG, "gestartet — RTSP :${config.rtspPort}${config.rtspPath} (TCP-interleaved)")
    }

    /** Stoppt Encode-Thread, Encoder und Server; gibt den RTSP-Port frei. */
    fun stop() {
        running = false
        encodeThread?.interrupt()
        try { encodeThread?.join(500) } catch (_: Exception) {}
        encodeThread = null
        encoder?.stop(); encoder = null
        server?.stop(); server = null
        Log.i(TAG, "gestoppt")
    }

    /**
     * Greift jeweils das NEUESTE Bitmap aus dem Fan-out ab und kodiert es; bei Encoder-Rückstau
     * werden Zwischenframes verworfen (StateFlow ist konflatierend + `!== last`-Guard) — das ist
     * die gewünschte Drop-on-Backpressure-Charakteristik aus dem Spike.
     */
    private fun encodeLoop(enc: H264Encoder) {
        var last: Bitmap? = null
        while (running) {
            val bm = cameraBus.frames.value
            if (bm != null && bm !== last) {
                last = bm
                try {
                    enc.encode(bm)
                } catch (e: Exception) {
                    Log.w(TAG, "encode-Fehler (übersprungen): ${e.message}")
                }
            } else {
                try { Thread.sleep(2) } catch (_: InterruptedException) { return }
            }
        }
    }
}

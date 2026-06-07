package com.uip.oneapp.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.system.Os
import android.util.Log
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.export.OsdSettings
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * Lokal-Aufnahme (#15) in ECHTZEIT: Im V4L2/LocalBitmap-Modus gibt es keinen RTSP-Stream.
 * Wir schreiben die Live-Frames (RGBA) fortlaufend in eine FIFO, die FFmpegKit (libx264
 * ultrafast) parallel zu MP4 encodiert. Dadurch ist der Stopp quasi sofort fertig
 * (nur noch flushen) — kein nachgelagertes Voll-Encoding mehr.
 *
 * Bewusst ohne MediaCodec (kein geräteabhängiges Farbformat-Risiko auf der RK3588).
 */
class LocalBitmapRecorder(private val context: Context) {

    enum class State { IDLE, RECORDING, PAUSED, FINISHING }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeJob: Job? = null
    private var session: FFmpegSession? = null
    private var fifo: File? = null

    /** Aufnahme läuft (auch wenn gerade pausiert) — Datei ist offen. */
    val isRecording: Boolean get() = _state.value == State.RECORDING || _state.value == State.PAUSED

    val isPaused: Boolean get() = _state.value == State.PAUSED

    /**
     * Pause (CEO-Beschluss 2026-06-07, wie Original-App): Die Frame-Zufuhr an FFmpeg
     * stoppt, die FIFO und die MP4 bleiben offen. Die Pausenzeit fehlt im Video —
     * beim Fortsetzen läuft DIESELBE Datei nahtlos weiter (eine durchgehende MP4).
     */
    fun pause() {
        if (_state.value == State.RECORDING) _state.value = State.PAUSED
    }

    fun resume() {
        if (_state.value == State.PAUSED) _state.value = State.RECORDING
    }

    /**
     * @param sdResolution true → Ausgabe auf 720x576 skalieren (M1, SD); false → native Auflösung.
     * @param osdSettings  != null und enableOsdBurnIn → OSD wird pro Frame eingebrannt (M3).
     *                     Die Zeilen werden über die Provider live abgefragt (Meter/Datum/Flash).
     */
    fun start(
        outputPath: String,
        frameFlow: StateFlow<Bitmap?>,
        fps: Int = 12,
        sdResolution: Boolean = false,
        osdSettings: OsdSettings? = null,
        typeface: Typeface? = null,
        osdLine1Provider: () -> String = { "" },
        osdLine2Provider: () -> String = { "" },
        findingProvider: () -> String? = { null }
    ): Boolean {
        if (_state.value != State.IDLE) return false
        if (frameFlow.value == null) return false
        val f = fps.coerceIn(5, 30)
        val burnIn = osdSettings != null && osdSettings.enableOsdBurnIn

        val fifoFile = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mjpeg")
        try {
            if (fifoFile.exists()) fifoFile.delete()
            Os.mkfifo(fifoFile.absolutePath, 432) // 0660
        } catch (e: Exception) {
            Log.e(TAG, "mkfifo failed", e); return false
        }
        fifo = fifoFile

        // FFmpeg liest die FIFO als JPEG-Strom (image2pipe) und encodiert live nach H.264.
        // JPEG trägt die korrekten Farben (kein Roh-Pixel-Format-Risiko). crop => gerade Maße.
        // M1: SD => zusätzlich auf 720x576 skalieren; HD => nur gerade Maße sicherstellen.
        val vf = if (sdResolution) "crop=trunc(iw/2)*2:trunc(ih/2)*2,scale=720:576"
                 else "crop=trunc(iw/2)*2:trunc(ih/2)*2"
        val cmd = "-f image2pipe -framerate $f -i ${fifoFile.absolutePath} " +
                  "-vf $vf -c:v libx264 -preset ultrafast -pix_fmt yuv420p " +
                  "-movflags +frag_keyframe+empty_moov+default_base_moof -frag_duration 1000000 -y $outputPath"

        _state.value = State.RECORDING
        session = FFmpegKit.executeAsync(cmd) { s ->
            Log.d(TAG, "ffmpeg session ended rc=${s.returnCode?.value}")
        }

        val frameIntervalMs = 1000L / f
        writeJob = scope.launch {
            // Öffnen blockiert, bis FFmpeg die Leseseite geöffnet hat.
            val out = try { FileOutputStream(fifoFile) } catch (e: Exception) {
                Log.e(TAG, "open fifo for write failed", e); _state.value = State.IDLE; return@launch
            }
            try {
                while (isActive && (_state.value == State.RECORDING || _state.value == State.PAUSED)) {
                    if (_state.value == State.PAUSED) {
                        // Pause: keine Frames schreiben, Encoder wartet auf der FIFO.
                        delay(frameIntervalMs)
                        continue
                    }
                    val bmp = frameFlow.value
                    if (bmp != null && !bmp.isRecycled) {
                        if (burnIn && osdSettings != null) {
                            // M3: OSD in eine MUTABLE Kopie brennen — das Live-Frame (wird
                            // gleichzeitig angezeigt) darf nie verändert werden.
                            val copy = try { bmp.copy(Bitmap.Config.ARGB_8888, true) } catch (_: Exception) { null }
                            if (copy != null) {
                                OsdRenderer.renderBitmap(
                                    copy, osdSettings, osdLine1Provider(), osdLine2Provider(),
                                    findingFlash = findingProvider(), typeface = typeface
                                )
                                copy.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                copy.recycle()
                            } else {
                                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        } else {
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        out.flush()
                    }
                    delay(frameIntervalMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "frame write loop ended", e)
            } finally {
                try { out.flush(); out.close() } catch (_: Exception) {}
            }
        }
        return true
    }

    /** Stoppt die Aufnahme (auch aus der Pause). FFmpeg bekommt EOF und finalisiert (schnell). */
    fun stop(onDone: (String?) -> Unit) {
        if (_state.value != State.RECORDING && _state.value != State.PAUSED) { onDone(null); return }
        _state.value = State.FINISHING
        scope.launch {
            writeJob?.join()              // schließt die FIFO (EOF für FFmpeg)
            val s = session
            // Kurz auf FFmpeg-Finalisierung warten.
            var waited = 0
            while (s != null && s.state?.toString() == "RUNNING" && waited < 8000) {
                delay(100); waited += 100
            }
            cleanup()
            val ok = (s?.returnCode?.value ?: 0) == 0
            _state.value = State.IDLE
            onDone(if (ok) /* path */ s?.command?.substringAfterLast(" ") else null)
        }
    }

    /** Abbruch ohne Finalisierung (View verlassen). */
    fun cancel() {
        if (_state.value == State.IDLE) return
        _state.value = State.IDLE
        writeJob?.cancel()
        try { FFmpegKit.cancel() } catch (_: Exception) {}
        cleanup()
    }

    private fun cleanup() {
        try { fifo?.delete() } catch (_: Exception) {}
        fifo = null
        session = null
    }

    companion object { private const val TAG = "LocalBitmapRecorder" }
}

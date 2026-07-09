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
class LocalBitmapRecorder(
    private val context: Context,
    remuxDelegate: RemuxDelegate? = null
) : Recorder {

    // Injizierbarer Remux (Default: echter remuxToFaststart). Beim gewollten Stopp wird die
    // absturzsicher fragmentierte Aufnahme einmal verlustfrei in eine normale MP4 mit korrektem
    // moov umgebaut — behebt „nur Endzeit" (#5b) und den Abbruch nach Pause (#9a).
    private val remux: RemuxDelegate = remuxDelegate ?: { s, d -> remuxToFaststart(s, d) }

    private val _state = MutableStateFlow(RecordingState.IDLE)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeJob: Job? = null
    private var session: FFmpegSession? = null
    private var fifo: File? = null
    // Live wird absturzsicher in fragFile (fragmentiertes MP4) geschrieben; beim Stopp
    // nach finalFile (dem vom Aufrufer gewünschten Zielpfad) remuxt.
    private var fragFile: File? = null
    private var finalFile: File? = null
    // Die Meter-Spur gehört ausschließlich der writeJob-Coroutine (einziger Producer): sie wird
    // dort erzeugt, pro Frame beschrieben und im finally geschlossen. Kein geteiltes Feld → keine
    // Cross-Thread-Race auf den Writer.

    /** Aufnahme läuft (auch wenn gerade pausiert) — Datei ist offen. */
    override val isRecording: Boolean
        get() = _state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED

    override val isPaused: Boolean get() = _state.value == RecordingState.PAUSED

    /**
     * Pause (CEO-Beschluss 2026-06-07, wie Original-App): Die Frame-Zufuhr an FFmpeg
     * stoppt, die FIFO und die MP4 bleiben offen. Die Pausenzeit fehlt im Video —
     * beim Fortsetzen läuft DIESELBE Datei nahtlos weiter (eine durchgehende MP4).
     */
    override fun pause() {
        // Kein MeterTrackWriter-Hook nötig: die Frame-Schleife schreibt in PAUSED keinen
        // Frame → onFrame() läuft nicht → der Frame-Index (= Zeitbasis) steht von selbst still.
        if (_state.value == RecordingState.RECORDING) _state.value = RecordingState.PAUSED
    }

    override fun resume() {
        if (_state.value == RecordingState.PAUSED) _state.value = RecordingState.RECORDING
    }

    /**
     * @param sdResolution true → Ausgabe auf 720x576 skalieren (M1, SD); false → native Auflösung.
     * @param osdSettings  != null und enableOsdBurnIn → OSD wird pro Frame eingebrannt (M3).
     *                     Die Zeilen werden über die Provider live abgefragt (Meter/Datum/Flash).
     */
    override fun start(
        outputPath: String,
        frameFlow: StateFlow<Bitmap?>,
        fps: Int,
        sdResolution: Boolean,
        osdSettings: OsdSettings?,
        typeface: Typeface?,
        osdLine1Provider: () -> String,
        osdLine2Provider: () -> String,
        findingProvider: () -> String?,
        meterProvider: (() -> Float)?
    ): Boolean {
        if (_state.value != RecordingState.IDLE) return false
        if (frameFlow.value == null) return false
        val f = fps.coerceIn(5, 30)
        val burnIn = osdSettings != null && osdSettings.enableOsdBurnIn

        // Live absturzsicher in eine Temp-Frag-Datei schreiben; beim Stopp nach outputPath remuxen.
        val finalF = File(outputPath)
        val fragF = File(outputPath + FRAG_SUFFIX)
        cleanupOrphanFrags(finalF.parentFile)   // Reste eines früheren Absturzes/Cancels entfernen
        finalFile = finalF
        fragFile = fragF

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
                  "-movflags +frag_keyframe+empty_moov+default_base_moof -frag_duration 1000000 -y ${fragF.absolutePath}"

        _state.value = RecordingState.RECORDING
        session = FFmpegKit.executeAsync(cmd) { s ->
            Log.d(TAG, "ffmpeg session ended rc=${s.returnCode?.value}")
        }
        // Meter-Spur mit derselben fps (f), die ffmpeg für die PTS nutzt — die Samples
        // werden in der Frame-Schleife pro tatsächlich geschriebenem Frame gestempelt.
        val provider = meterProvider
        val meterWriter = provider?.let { MeterTrackWriter(File(outputPath)).apply { start(f) } }

        val frameIntervalMs = 1000L / f
        writeJob = scope.launch {
            // Öffnen blockiert, bis FFmpeg die Leseseite geöffnet hat.
            val out = try { FileOutputStream(fifoFile) } catch (e: Exception) {
                Log.e(TAG, "open fifo for write failed", e)
                // FFmpeg wartet sonst ewig auf die Schreibseite der FIFO — Session gezielt
                // abbrechen und FIFO-Datei aufräumen, nicht nur den State zurücksetzen.
                session?.let { s -> try { FFmpegKit.cancel(s.sessionId) } catch (_: Exception) {} }
                meterWriter?.stop()   // gerade geöffnete Sidecar wieder schließen (kein Leak)
                cleanup()
                _state.value = RecordingState.IDLE
                return@launch
            }
            // Frame-Index = ffmpeg-PTS-Basis: NUR hochzählen, wenn wirklich ein JPEG in die
            // FIFO geht (nicht in Pause, nicht bei fehlendem Bitmap). Muss 1:1 zu den Frames
            // passen, die der Encoder sieht — sonst driftet die Meter-Spur. Erster Frame = 0.
            var frameIndex = 0
            try {
                while (isActive && (_state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED)) {
                    if (_state.value == RecordingState.PAUSED) {
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
                        // Nach erfolgreichem FIFO-Write: dieses Frame existiert im Encoder.
                        if (meterWriter != null && provider != null) meterWriter.onFrame(frameIndex, provider())
                        frameIndex++
                    }
                    delay(frameIntervalMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "frame write loop ended", e)
            } finally {
                try { out.flush(); out.close() } catch (_: Exception) {}
                // Sidecar nach dem LETZTEN Frame flushen+schließen. Einziger Producer → keine
                // Race; garantiert das Wegschreiben der letzten gepufferten Samples, auch wenn
                // die Schleife per Exception/Cancel endet.
                meterWriter?.stop()
            }
        }
        return true
    }

    /** Stoppt die Aufnahme (auch aus der Pause). FFmpeg bekommt EOF und finalisiert (schnell). */
    override fun stop(onDone: (String?) -> Unit) {
        if (_state.value != RecordingState.RECORDING && _state.value != RecordingState.PAUSED) { onDone(null); return }
        _state.value = RecordingState.FINISHING
        scope.launch {
            writeJob?.join()              // Frame-Schleife endet → finally schließt FIFO + Meter-Spur
            val s = session
            // Kurz auf die FFmpeg-Finalisierung der Frag-Datei warten.
            var waited = 0
            while (s != null && s.state?.toString() == "RUNNING" && waited < 8000) {
                delay(100); waited += 100
            }
            val fragF = fragFile
            val finalF = finalFile
            cleanup()                     // FIFO weg + Feld-Referenzen lösen (Dateien bleiben)
            // Gewollter Stopp: fragmentierte Aufnahme einmal verlustfrei nach finalF remuxen
            // (korrekter moov + Gesamtdauer → laufender Timer, seekbar, kein Abbruch nach Pause).
            // Bei Remux-Fehler bleibt die Frag-Datei als finalF erhalten (Aufnahme nie verlieren).
            val result = if (fragF != null && finalF != null)
                finalizeFragRecording(fragF, finalF, remux) else null
            _state.value = RecordingState.IDLE
            onDone(result)
        }
    }

    /** Abbruch ohne Finalisierung (View verlassen). */
    override fun cancel() {
        if (_state.value == RecordingState.IDLE) return
        _state.value = RecordingState.IDLE
        writeJob?.cancel()   // Frame-Schleife bricht ab → finally schließt die Meter-Spur
        // Gezielt NUR die eigene Session — FFmpegKit.cancel() ohne Id würde auch fremde
        // Sessions (z. B. einen laufenden Export-Encode) mitten im File abbrechen.
        session?.let { s -> try { FFmpegKit.cancel(s.sessionId) } catch (_: Exception) {} }
        cleanup()
    }

    private fun cleanup() {
        try { fifo?.delete() } catch (_: Exception) {}
        fifo = null
        session = null
        // Nur die Feld-Referenzen lösen — die Frag-Datei bleibt bei cancel()/Crash auf Platte
        // (Absturzsicherheit); verwaiste Frags räumt der nächste start() via cleanupOrphanFrags auf.
        fragFile = null
        finalFile = null
    }

    companion object { private const val TAG = "LocalBitmapRecorder" }
}

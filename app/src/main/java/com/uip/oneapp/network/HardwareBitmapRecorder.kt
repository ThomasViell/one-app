package com.uip.oneapp.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.export.OsdSettings
import com.uip.oneapp.network.video.H264Encoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * **HW-Encoder-Aufnahme (Welle 5, ADR 0002).** Ersetzt den JPEG/FIFO/libx264-Weg durch den
 * bewiesenen Plattform-`MediaCodec` ([H264Encoder]). Pro Bild: **immer** mutable Kopie des
 * Bus-Bitmaps (nie das geteilte Bild direkt kodieren) → optional SD-Skalierung → optional OSD
 * (derselbe [OsdRenderer] wie Foto/Schaden) → HW-H.264 mit **pausenbereinigter, streng steigender
 * VFR-PTS**. Die AUs laufen sofort ins absturzsichere [H264StreamJournal]; beim Stopp muxt
 * [RecorderJournalMuxer] verlustfrei nach MP4 (Kill → Recovery beim nächsten Start).
 *
 * Ein-Encoder-Ausschluss über [CameraEncoderArbiter]: `OneVideoServer` gibt seinen HW-Codec frei,
 * solange hier aufgenommen wird (die RK3588 hat nur einen AVC-Encoder).
 */
class HardwareBitmapRecorder(
    private val context: Context,
    private val arbiter: CameraEncoderArbiter = CameraEncoderArbiter(),
    // Injizierbar für Tests; Produktion = echter MediaMuxer-Weg.
    private val muxDelegate: (journal: File, out: File) -> Boolean = RecorderJournalMuxer::muxJournalToMp4,
) : Recorder {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    override val isRecording: Boolean
        get() = _state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED
    override val isPaused: Boolean get() = _state.value == RecordingState.PAUSED

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var encodeThread: Thread? = null
    @Volatile private var encoder: H264Encoder? = null
    @Volatile private var journalWriter: H264JournalWriter? = null
    @Volatile private var meterWriter: MeterTrackWriterV3? = null
    @Volatile private var journalFile: File? = null
    @Volatile private var finalFile: File? = null
    @Volatile private var meterSidecar: File? = null
    // Welle 5 (Impl-Review): ein IO-Fehler beim Journal-Schreiben darf nicht still bleiben —
    // sonst würde eine unvollständige Aufnahme als Erfolg gemeldet.
    @Volatile private var journalWriteFailed = false

    // VFR-Uhr (nur vom Encode-Thread beschrieben; von stop() erst nach join gelesen).
    private var recStartNs = 0L
    private var startNsSet = false
    @Volatile private var pausedAccumNs = 0L
    @Volatile private var pauseBeganNs = 0L
    private var lastPtsUs = -1L

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
        meterProvider: (() -> Float)?,
    ): Boolean {
        if (_state.value != RecordingState.IDLE) return false
        if (frameFlow.value == null) return false

        // Einzigen HW-Encoder anfordern (OneVideoServer gibt seinen Codec frei). Erst danach configure().
        if (!arbiter.acquireForRecording()) {
            Log.e(TAG, "HW-Encoder nicht frei (RTSP gab nicht rechtzeitig frei) — Aufnahme abgebrochen")
            arbiter.release()
            return false
        }

        val finalF = File(outputPath)
        val jF = File(outputPath + JOURNAL_SUFFIX)
        try { if (jF.exists()) jF.delete() } catch (_: Exception) {}   // eigenes altes Journal wegräumen
        finalFile = finalF
        journalFile = jF

        val burnIn = osdSettings != null && osdSettings.enableOsdBurnIn
        val jw = H264JournalWriter(jF)
        val mw = meterProvider?.let { MeterTrackWriterV3(finalF).apply { start() } }
        journalWriter = jw
        meterWriter = mw
        meterSidecar = if (mw != null) File(outputPath + METER_SIDECAR_SUFFIX) else null

        // Uhr + Fehler-Flag zurücksetzen.
        recStartNs = 0L; startNsSet = false; pausedAccumNs = 0L; pauseBeganNs = 0L; lastPtsUs = -1L
        journalWriteFailed = false

        // Encoder: eigene Instanz, periodische GOP (seekbar), Journal als Senke.
        lateinit var enc: H264Encoder
        enc = H264Encoder(
            frameRate = fps.coerceIn(5, 30),
            bitRate = if (sdResolution) 2_500_000 else 6_000_000,
            forcePeriodicGop = true,
            onAccessUnit = { annexB, ptsUs, keyframe ->
                if (!jw.writeRecord(annexB, ptsUs, keyframe)) {
                    if (!journalWriteFailed) Log.e(TAG, "Journal-Schreibfehler — Aufnahme wird unvollständig")
                    journalWriteFailed = true
                }
            },
            // Kopf VOR dem ersten AU (Codec-Config kommt zuerst) → Records sind immer recoverbar.
            onConfig = { sps, pps -> jw.writeHeader(enc.encodedWidth, enc.encodedHeight, sps, pps) },
        )
        encoder = enc
        enc.start()

        _state.value = RecordingState.RECORDING
        encodeThread = Thread({
            encodeLoop(
                frameFlow, enc, mw, sdResolution, burnIn, osdSettings, typeface,
                osdLine1Provider, osdLine2Provider, findingProvider, meterProvider
            )
        }, "hw-recorder").apply { isDaemon = true; start() }
        return true
    }

    private fun encodeLoop(
        frameFlow: StateFlow<Bitmap?>,
        enc: H264Encoder,
        meterWriter: MeterTrackWriterV3?,
        sdResolution: Boolean,
        burnIn: Boolean,
        osdSettings: OsdSettings?,
        typeface: Typeface?,
        osdLine1Provider: () -> String,
        osdLine2Provider: () -> String,
        findingProvider: () -> String?,
        meterProvider: (() -> Float)?,
    ) {
        var last: Bitmap? = null
        // Stufen-Messung AUFTRAG 2 (nur geloggt bei `setprop log.tag.DqFpsStats DEBUG`):
        // wo die Zeit im Aufnahmepfad pro Frame hingeht — Vorbereitung (Kopie+OSD) vs. Encode.
        var statWindowStartMs = 0L
        var statFrames = 0
        var statPrepareNs = 0L
        var statEncodeNs = 0L
        try {
            while (_state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED) {
                if (_state.value == RecordingState.PAUSED) {
                    Thread.sleep(10); continue
                }
                val bm = frameFlow.value
                if (bm != null && bm !== last && !bm.isRecycled) {
                    last = bm
                    // Meter-Wert und OSD-Zeile 2 einmal pro Frame lesen, BEVOR der Encoder läuft —
                    // so sind eingebrannte Zahl und Sidecar-Sample garantiert aus demselben Zeitpunkt.
                    val frameMeter = meterProvider?.invoke()
                    val capturedLine2 = osdLine2Provider()
                    val t0 = System.nanoTime()
                    val frame = try {
                        prepareFrame(bm, sdResolution, burnIn, osdSettings, typeface,
                            osdLine1Provider, { capturedLine2 }, findingProvider)
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame-Vorbereitung fehlgeschlagen: ${e.message}"); null
                    }
                    val t1 = System.nanoTime()
                    if (frame != null) {
                        val ptsUs = nextPtsUs()
                        val queued = try {
                            enc.encode(frame, ptsUs)
                        } catch (e: Exception) {
                            Log.w(TAG, "encode-Fehler (übersprungen): ${e.message}"); false
                        }
                        // Meter-Sample NUR für tatsächlich kodierte Frames (1:1 zum Video).
                        if (queued && frameMeter != null) meterWriter?.onSample(ptsUs, frameMeter)
                        // KEIN recycle: frame IST der wiederverwendete scratch-Bitmap (s. prepareFrame).
                        statPrepareNs += t1 - t0
                        statEncodeNs += System.nanoTime() - t1
                        statFrames++
                    }
                    if (Log.isLoggable(FPS_STATS_TAG, Log.DEBUG)) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (statWindowStartMs == 0L) statWindowStartMs = now
                        val elapsed = now - statWindowStartMs
                        if (elapsed >= 5_000 && statFrames > 0) {
                            Log.d(FPS_STATS_TAG, "REC fps=%.1f | vorbereitung=%.1f ms/Frame encode=%.1f ms/Frame (Fenster %d ms)"
                                .format(statFrames * 1000.0 / elapsed, statPrepareNs / 1e6 / statFrames,
                                    statEncodeNs / 1e6 / statFrames, elapsed))
                            statWindowStartMs = now
                            statFrames = 0; statPrepareNs = 0; statEncodeNs = 0
                        }
                    }
                } else {
                    Thread.sleep(2)
                }
            }
        } catch (_: InterruptedException) {
            // stop()/cancel() — normaler Abbruch.
        } catch (e: Exception) {
            Log.e(TAG, "Encode-Schleife beendet", e)
        }
    }

    // Wiederverwendeter Arbeits-Bitmap des Encode-Threads (Anlauf 2, RESULT Abschnitt 10):
    // ersetzt die frühere bm.copy(ARGB_8888)-Allokation (3,7 MB) pro Frame. Nur der
    // Encode-Thread fasst ihn an; MediaCodec kopiert die Pixel synchron in fillImage,
    // danach darf er sofort wieder überschrieben werden. NIEMALS recyclen, solange die
    // Aufnahme läuft.
    private var scratch: Bitmap? = null

    /**
     * Zeichnet das geteilte Bus-Bitmap in den eigenen, wiederverwendeten Arbeits-Bitmap
     * (nie das geteilte Bild direkt kodieren — der konflatierende Producer recycelt es).
     * SD → auf 720×576 skalieren; danach optional OSD. RGB_565 statt ARGB_8888: halbe
     * Kopierlast, und die native I420-Konvertierung unterstützt beide Formate.
     */
    private fun prepareFrame(
        bm: Bitmap,
        sdResolution: Boolean,
        burnIn: Boolean,
        osdSettings: OsdSettings?,
        typeface: Typeface?,
        osdLine1Provider: () -> String,
        osdLine2Provider: () -> String,
        findingProvider: () -> String?,
    ): Bitmap {
        val w = if (sdResolution) SD_WIDTH else bm.width
        val h = if (sdResolution) SD_HEIGHT else bm.height
        var base = scratch
        if (base == null || base.width != w || base.height != h || base.isRecycled) {
            base = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            scratch = base
        }
        Canvas(base).drawBitmap(bm, Rect(0, 0, bm.width, bm.height), Rect(0, 0, w, h), null)
        if (burnIn && osdSettings != null) {
            OsdRenderer.renderBitmap(
                base, osdSettings, osdLine1Provider(), osdLine2Provider(),
                findingFlash = findingProvider(), typeface = typeface
            )
        }
        return base
    }

    /** Pausenbereinigte, streng steigende Medienzeit; erster Frame ≈ 0. Nur vom Encode-Thread. */
    private fun nextPtsUs(): Long {
        if (!startNsSet) { recStartNs = System.nanoTime(); startNsSet = true }
        var us = (System.nanoTime() - recStartNs - pausedAccumNs) / 1000L
        if (us <= lastPtsUs) us = lastPtsUs + 1
        lastPtsUs = us
        return us
    }

    override fun pause() {
        if (_state.value == RecordingState.RECORDING) {
            pauseBeganNs = System.nanoTime()
            _state.value = RecordingState.PAUSED
        }
    }

    override fun resume() {
        if (_state.value == RecordingState.PAUSED) {
            pausedAccumNs += System.nanoTime() - pauseBeganNs
            _state.value = RecordingState.RECORDING
        }
    }

    override fun stop(onDone: (String?) -> Unit) {
        if (_state.value != RecordingState.RECORDING && _state.value != RecordingState.PAUSED) {
            onDone(null); return
        }
        _state.value = RecordingState.FINISHING   // Encode-Schleife läuft aus
        scope.launch {
            val t = encodeThread; encodeThread = null
            try { t?.join(3000) } catch (_: Exception) {}
            val enc = encoder
            try { enc?.drainFinal() } catch (e: Exception) { Log.w(TAG, "drainFinal: ${e.message}") }
            try { enc?.stop() } catch (_: Exception) {}
            encoder = null
            journalWriter?.close(); journalWriter = null
            meterWriter?.stop(); meterWriter = null
            arbiter.release()   // OneVideoServer bekommt den HW-Encoder zurück

            scratch = null   // Arbeits-Bitmap freigeben (Encode-Thread ist gejoint)

            val jf = journalFile; val ff = finalFile
            val result = if (jf != null && ff != null && jf.exists() && jf.length() > 0L) {
                val ok = try { muxDelegate(jf, ff) } catch (e: Exception) { Log.w(TAG, "mux: ${e.message}"); false }
                if (ok) { try { jf.delete() } catch (_: Exception) {}; ff.absolutePath } else null
            } else null

            if (journalWriteFailed) {
                // Nicht still: der gültige Präfix wurde gemuxt, aber die Aufnahme ist unvollständig.
                Log.e(TAG, "Aufnahme mit Journal-Schreibfehler beendet — nur der gültige Präfix ist im Video")
            }
            if (result == null) {
                // Nichts Spielbares entstanden → verwaiste Sidecar/Journal aufräumen (kein Leak).
                try { meterSidecar?.delete() } catch (_: Exception) {}
                Log.w(TAG, "Stopp ohne spielbares Video (kein Frame/Mux fehlgeschlagen)")
            }
            journalFile = null; finalFile = null; meterSidecar = null
            _state.value = RecordingState.IDLE
            onDone(result)
        }
    }

    /**
     * Abbruch = Aufnahme verwerfen (View verlassen). Journal + Sidecar werden GELÖSCHT — anders als
     * ein Prozess-Kill (der das Journal liegen lässt → Recovery beim nächsten Start). So bleibt die
     * Semantik des alten Recorders: bewusstes Verlassen verwirft, Absturz bewahrt.
     */
    override fun cancel() {
        if (_state.value == RecordingState.IDLE) return
        _state.value = RecordingState.IDLE
        val t = encodeThread; encodeThread = null
        t?.interrupt()
        scope.launch {
            try { t?.join(1000) } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}   // kein drainFinal — verworfen
            encoder = null
            journalWriter?.close(); journalWriter = null
            meterWriter?.stop(); meterWriter = null
            arbiter.release()
            try { journalFile?.delete() } catch (_: Exception) {}
            try { meterSidecar?.delete() } catch (_: Exception) {}
            journalFile = null; finalFile = null; meterSidecar = null
        }
    }

    companion object {
        private const val TAG = "HardwareBitmapRecorder"
        private const val SD_WIDTH = 720
        private const val SD_HEIGHT = 576

        /** Gleicher Schalter wie in Camera2FrameSource: `setprop log.tag.DqFpsStats DEBUG`
         * loggt alle 5 s die Aufnahme-Bildrate und die Stufenzeiten der Encode-Schleife. */
        private const val FPS_STATS_TAG = "DqFpsStats"
    }
}

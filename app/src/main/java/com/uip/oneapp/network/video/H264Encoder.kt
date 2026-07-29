package com.uip.oneapp.network.video

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * H.264-Encoder über die Plattform-[MediaCodec] (auf der ONE = Rockchip-HW-Encoder
 * `c2.rk.avc.encoder`). Eingabe = RGB-[Bitmap]s aus dem V4L2-Fan-out
 * ([com.uip.oneapp.network.internal.CameraFrameBus]); Ausgabe = Annex-B-Access-Units +
 * SPS/PPS, die an [RtspVideoServer] gereicht werden.
 *
 * Produktiv übernommen aus dem bewiesenen Spike (`spike/video-rtsp`, `SpikeH264Encoder`);
 * Encoder-Logik unverändert (gemessen: stabil 1280×720, ~27 fps, ffprobe-sauberes yuv420p).
 *
 * Wichtig: der MS2109 zwingt VIDIOC_S_FMT auf eine ihm bekannte Auflösung (z. B.
 * 960×540 → 800×600). Der Encoder konfiguriert sich daher **lazy aus der echten Größe**
 * des ersten Bitmaps statt aus einem angenommenen Wert.
 *
 * Farbkonvertierung RGB→YUV420 über [Image]-Planes (getInputImage), damit row-/pixelStride
 * des Geräts (planar I420 vs. semi-planar NV12) korrekt bedient wird — erfahrungsgemäß der
 * heikelste Punkt. TODO(perf): Zero-Copy-Pfad (V4L2-MJPEG → HW-Decoder → Surface →
 * HW-Encoder) ersetzt BitmapFactory + manuelles YUV → volle 30 fps, weniger Latenz/CPU.
 */
class H264Encoder(
    private val frameRate: Int = 30,
    private val bitRate: Int = 4_000_000,
    /**
     * GOP-Länge in Sekunden. Seit M1 (IDR-on-PLAY via [requestKeyframe]) hängt die Join-Latenz
     * NICHT mehr an der GOP — der Client bekommt seinen IDR on demand. Die GOP darf daher lang
     * sein, und das ist messbar besser: Kurze GOPs (0,5 s) erzeugten alle 15 Frames einen
     * ~72-KB-IDR-Burst zwischen ~12-KB-P-Frames → ±100 ms Anlieferungs-Jitter am Player, der
     * dadurch nicht näher als ~150 ms an die Live-Kante konnte ohne leerzulaufen (Telemetrie
     * 2026-07-03, PERF-Doku). 2 s glättet die Anlieferung (4× seltener) und hebt nebenbei die
     * P-Frame-Qualität bei gleicher CBR-Rate.
     */
    private val iFrameIntervalSec: Float = 2.0f,
    /**
     * Welle 5: true erzwingt periodische IDR-Frames (klassische GOP) und schaltet den
     * Rolling-Intra-Refresh (M9) aus — nötig für **Aufnahmen** (seekbare Datei, mehrere Keyframes;
     * der Journal-Mux springt zum ersten Keyframe). RTSP lässt es auf false (Auto-Erkennung).
     */
    private val forcePeriodicGop: Boolean = false,
    private val onAccessUnit: (annexB: ByteArray, ptsUs: Long, keyframe: Boolean) -> Unit,
    private val onConfig: (sps: ByteArray, pps: ByteArray) -> Unit,
) {
    companion object {
        private const val TAG = "H264Encoder"
        /** Encode-Latenz nur alle N Frames loggen (~2 s bei 30 fps) — kein Logcat-Spam. */
        private const val LATENCY_LOG_EVERY = 60L

        /** Gleicher Schalter wie Camera2FrameSource/HardwareBitmapRecorder:
         * `setprop log.tag.DqFpsStats DEBUG` → Stufenzeiten innerhalb von [encode]. */
        private const val STAGE_STATS_TAG = "DqFpsStats"

        /**
         * M3a (PERF-Doku 2026-07-03): native RGB→I420-Konvertierung aus libv4l2bridge
         * (AndroidBitmap_lockPixels + C-Schleife, ~3–6 ms statt ~25–40 ms getPixels+Kotlin).
         * try/catch: in JVM-Unit-Tests (Robolectric) gibt es die .so nicht → Kotlin-Fallback.
         */
        private val nativeLibLoaded: Boolean = try {
            System.loadLibrary("v4l2bridge")
            true
        } catch (e: Throwable) {
            false
        }
    }

    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    @Volatile private var running = false
    private var width = 0
    private var height = 0
    private var pixels = IntArray(0)
    private var startNs = 0L
    // Welle 5: startNs wird LAZY beim ersten Frame gesetzt (nicht in start()), damit die interne
    // Uhr (RTSP-Weg) beim ersten Bild bei ~0 beginnt. Der Recorder-Weg nutzt die Überladung
    // encode(bm, ptsUs) mit eigener, pausenbereinigter Uhr und setzt startNs NICHT.
    @Volatile private var startNsSet = false
    private var configReported = false
    // Welle 5: PTS des zuletzt eingespeisten Frames — für den EOS-Marker in drainFinal().
    @Volatile private var lastInputPtsUs = 0L

    @Volatile var encodedFrames = 0L; private set
    @Volatile var actualSize = "?"; private set
    /** Letztes per Log gemeldetes Encode-Latenz-Sample (ms) — für externe Diagnose abgreifbar. */
    @Volatile var lastEncodeLatencyMs = 0.0; private set
    private var lastLatencyLogFrame = 0L

    /** Tatsächlich konfigurierte (auf gerade Maße gerundete) Breite/Höhe — Welle 5 für den Journal-Kopf. */
    val encodedWidth: Int get() = width
    val encodedHeight: Int get() = height

    fun start() {
        running = true
    }

    /**
     * Fordert vom laufenden Codec einen sofortigen Sync-Frame (IDR) an — M1 (PERF-Doku
     * 2026-07-03): beim RTSP-`PLAY` gerufen, damit ein frisch verbundener Client nicht bis zu
     * einer GOP-Länge (Ø ½ GOP) auf den nächsten regulären IDR warten muss. Dieser Wartezeit-
     * Versatz bliebe sonst dauerhaft in der Glass-to-Glass-Latenz stehen (RTSP hat keinen
     * Live-Catch-up). Best-effort: vor der Lazy-Konfiguration (kein Codec) ein No-op — der
     * allererste Frame einer Encoder-Instanz ist ohnehin ein IDR.
     */
    fun requestKeyframe() {
        val c = codec ?: return
        try {
            c.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
            Log.i(TAG, "Sync-Frame angefordert (Client-Join)")
        } catch (e: Exception) {
            Log.w(TAG, "requestKeyframe: ${e.message}")
        }
    }

    /**
     * Kodiert ein Bitmap mit der **internen** Uhr (RTSP-Weg; PTS = nanoTime seit dem ersten Frame).
     * true = eingespeist, false = verworfen. Konfiguriert beim 1. Frame.
     */
    fun encode(bm: Bitmap): Boolean {
        if (!startNsSet) { startNs = System.nanoTime(); startNsSet = true }
        return encode(bm, (System.nanoTime() - startNs) / 1000L)
    }

    /**
     * Welle 5: Kodiert ein Bitmap mit **extern vorgegebener** PTS (Aufnahme-Weg — pausenbereinigte,
     * streng steigende Medienzeit vom Recorder). Berührt die interne Uhr NICHT, damit der RTSP-Weg
     * über [encode] bit-identisch bleibt. true = eingespeist, false = verworfen.
     */
    fun encode(bm: Bitmap, ptsUs: Long): Boolean {
        if (!running) return false
        var c = codec
        if (c == null) {
            // auf gerade Maße runden (H.264/YUV420 verlangt gerade Breite/Höhe)
            c = configure(bm.width / 2 * 2, bm.height / 2 * 2) ?: return false
        }
        // Defensive: nach der Lazy-Konfiguration muss das Bitmap groß genug sein.
        if (bm.width < width || bm.height < height) return false

        val stats = Log.isLoggable(STAGE_STATS_TAG, Log.DEBUG)
        val t0 = if (stats) System.nanoTime() else 0L
        val index = try {
            c.dequeueInputBuffer(8_000)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "dequeueInputBuffer: ${e.message}")
            return false
        }
        if (index < 0) {
            drainOutput(c)
            return false
        }
        val t1 = if (stats) System.nanoTime() else 0L
        val cap = c.getInputBuffer(index)?.capacity() ?: (width * height * 3 / 2)
        val image = c.getInputImage(index)
        if (image == null) {
            c.queueInputBuffer(index, 0, 0, 0, 0)
            return false
        }
        val t2 = if (stats) System.nanoTime() else 0L
        fillImage(bm, image)
        val t3 = if (stats) System.nanoTime() else 0L
        lastInputPtsUs = ptsUs
        c.queueInputBuffer(index, 0, cap, ptsUs, 0)
        drainOutput(c)
        if (stats) {
            stageDequeueNs += t1 - t0; stageGetImageNs += t2 - t1
            stageFillNs += t3 - t2; stageQueueDrainNs += System.nanoTime() - t3
            stageFrames++
            if (stageFrames >= 60) {
                Log.d(STAGE_STATS_TAG, "ENC-Stufen: dequeue=%.1f getImage=%.1f fill=%.1f queue+drain=%.1f ms/Frame (n=%d)"
                    .format(stageDequeueNs / 1e6 / stageFrames, stageGetImageNs / 1e6 / stageFrames,
                        stageFillNs / 1e6 / stageFrames, stageQueueDrainNs / 1e6 / stageFrames, stageFrames))
                stageFrames = 0; stageDequeueNs = 0; stageGetImageNs = 0; stageFillNs = 0; stageQueueDrainNs = 0
            }
        }
        return true
    }

    // Stufen-Messzähler (AUFTRAG 2, nur aktiv bei `setprop log.tag.DqFpsStats DEBUG`).
    private var stageFrames = 0
    private var stageDequeueNs = 0L
    private var stageGetImageNs = 0L
    private var stageFillNs = 0L
    private var stageQueueDrainNs = 0L

    private fun configure(w: Int, h: Int): MediaCodec? {
        if (w <= 0 || h <= 0) return null
        return try {
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            // M9 (PERF-Doku 2026-07-03): Rolling-Intra statt periodischer IDR-Bursts. Der
            // ~72-KB-IDR zwischen ~12-KB-P-Frames war die Hauptquelle des Anlieferungs-Jitters
            // am Player (dessen Puffer-Boden = stehende Latenz). Mit Intra-Refresh sind alle
            // Frames ähnlich groß; reguläre IDRs entfallen (GOP -1 = nur der erste Frame),
            // Client-Joins bekommen ihren IDR weiterhin on demand über [requestKeyframe] (M1).
            // Nur aktiv, wenn der Codec das Feature meldet — sonst Fallback auf die lange GOP.
            val intraRefresh = !forcePeriodicGop && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && try {
                c.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_IntraRefresh)
            } catch (e: Exception) {
                false
            }
            val format = buildAvcFormat(
                width = w,
                height = h,
                frameRate = frameRate,
                bitRate = bitRate,
                iFrameIntervalSec = if (intraRefresh) -1f else iFrameIntervalSec,
                enableLowLatencyKeys = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                // Refresh-Welle über 1 s (= frameRate Frames): verteilt die Intra-Kosten
                // gleichmäßig; kürzer = größere Frames, länger = trägere Bild-Erholung.
                intraRefreshPeriodFrames = if (intraRefresh) frameRate else null,
            )
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            width = w; height = h
            pixels = IntArray(w * h)
            actualSize = "${w}x$h"
            codec = c
            Log.i(
                TAG,
                "Encoder konfiguriert ${w}x$h @$frameRate ${bitRate / 1000}kbps " +
                    (if (intraRefresh) "IntraRefresh=${frameRate}f (GOP aus)" else "GOP=${iFrameIntervalSec}s") +
                    " codec=${c.name}"
            )
            c
        } catch (e: Exception) {
            Log.e(TAG, "configure ${w}x$h fehlgeschlagen: ${e.message}", e)
            null
        }
    }

    private fun drainOutput(c: MediaCodec) {
        while (true) {
            val outIndex = try {
                c.dequeueOutputBuffer(bufferInfo, 0)
            } catch (e: IllegalStateException) {
                return
            }
            if (!processOutIndex(c, outIndex)) return   // outIndex < 0 → nichts mehr da
        }
    }

    /**
     * Verarbeitet EINEN Output-Buffer-Index. Gibt `false` zurück, wenn nichts (mehr) anlag
     * (`outIndex < 0`) — Signal für die Drain-Schleife aufzuhören. Format-Wechsel und Daten → `true`.
     */
    private fun processOutIndex(c: MediaCodec, outIndex: Int): Boolean {
        when {
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val fmt = c.outputFormat
                val csd0 = fmt.getByteBuffer("csd-0")
                val csd1 = fmt.getByteBuffer("csd-1")
                if (csd0 != null && csd1 != null && !configReported) {
                    val sps = ByteArray(csd0.remaining()).also { csd0.get(it) }
                    val pps = ByteArray(csd1.remaining()).also { csd1.get(it) }
                    reportConfig(sps + pps)
                }
            }
            outIndex < 0 -> return false
            else -> {
                val buf = c.getOutputBuffer(outIndex)
                if (buf != null && bufferInfo.size > 0) {
                    buf.position(bufferInfo.offset)
                    buf.limit(bufferInfo.offset + bufferInfo.size)
                    val data = ByteArray(bufferInfo.size)
                    buf.get(data)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        reportConfig(data)
                    } else {
                        val key = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        encodedFrames++
                        sampleEncodeLatency(bufferInfo.presentationTimeUs, data.size, key)
                        onAccessUnit(data, bufferInfo.presentationTimeUs, key)
                    }
                }
                c.releaseOutputBuffer(outIndex, false)
            }
        }
        return true
    }

    /**
     * Welle 5 (ADR 0002 B4): Beim Stopp einen `END_OF_STREAM`-Marker einreihen und ALLE noch
     * gepufferten Access-Units drainen — sonst gingen die letzten Bilder (und damit Videodauer)
     * verloren. Nur für den Aufnahme-Weg sinnvoll; der RTSP-Weg ruft es nie (endloser Stream).
     */
    fun drainFinal() {
        val c = codec ?: return
        try {
            val inIndex = c.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                c.queueInputBuffer(inIndex, 0, 0, lastInputPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            val deadline = System.nanoTime() + 3_000_000_000L  // hartes Zeitlimit, nie hängenbleiben
            while (System.nanoTime() < deadline) {
                val outIndex = try {
                    c.dequeueOutputBuffer(bufferInfo, 50_000)
                } catch (e: IllegalStateException) {
                    break
                }
                processOutIndex(c, outIndex)
                if (outIndex >= 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "drainFinal: ${e.message}")
        }
    }

    /**
     * Hebel 5 (Latenz-Lokalisierung): misst die reine **Encoder-Latenz** (Input→Output) und loggt
     * sie periodisch. [bufferInfo.presentationTimeUs] = `(enqueueNanos − startNs)/1000`; damit ist
     * `now − (startNs + ptsUs·1000)` exakt die Zeit vom Einspeisen des Frames bis zur fertigen
     * Access-Unit — der in-Prozess messbare Anteil der Glass-to-Glass-Latenz. Der Rest (Netzwerk-
     * Send-Queue, Decode/Render im Player) wird on-device glass-to-glass gemessen.
     */
    private fun sampleEncodeLatency(ptsUs: Long, auBytes: Int, keyframe: Boolean) {
        // Nur für den internen-Uhr-Weg (RTSP) sinnvoll; im Aufnahme-Weg (externe PTS) ist startNs
        // ungesetzt → die Latenz-Rechnung wäre Unsinn.
        if (!startNsSet) return
        if (encodedFrames - lastLatencyLogFrame < LATENCY_LOG_EVERY) return
        lastLatencyLogFrame = encodedFrames
        val latencyMs = (System.nanoTime() - (startNs + ptsUs * 1000L)) / 1_000_000.0
        lastEncodeLatencyMs = latencyMs
        Log.d(
            TAG,
            "Latenz-Sample: encode=%.1fms AU=%dB %s frames=%d".format(
                latencyMs, auBytes, if (keyframe) "IDR" else "P", encodedFrames
            )
        )
    }

    private fun reportConfig(annexB: ByteArray) {
        if (configReported) return
        val sp = findSpsPps(annexB) ?: return
        configReported = true
        Log.i(TAG, "SPS=${sp.first.size}B PPS=${sp.second.size}B")
        onConfig(sp.first, sp.second)
    }

    // Einmalig auf den Kotlin-Pfad zurückfallen, wenn die native Konvertierung ablehnt
    // (unerwartetes Bitmap-Format / kein Direct-Buffer).
    private var nativeYuv = nativeLibLoaded

    /** Bitmap → YUV420 in die MediaCodec-Input-Planes; nativ (M3a) mit Kotlin-Fallback. */
    private fun fillImage(bm: Bitmap, image: Image) {
        val w = width
        val h = height
        val planes = image.planes
        if (nativeYuv &&
            planes[0].buffer.isDirect && planes[1].buffer.isDirect && planes[2].buffer.isDirect
        ) {
            val ok = try {
                nativeConvertToI420(
                    bm,
                    planes[0].buffer, planes[0].rowStride, planes[0].pixelStride,
                    planes[1].buffer, planes[1].rowStride, planes[1].pixelStride,
                    planes[2].buffer, planes[2].rowStride, planes[2].pixelStride,
                    w, h
                )
            } catch (e: Throwable) {
                false
            }
            if (ok) return
            nativeYuv = false
            Log.w(TAG, "nativeConvertToI420 nicht nutzbar -> Kotlin-Fallback")
        }
        bm.getPixels(pixels, 0, w, 0, 0, w, h)
        val yBuf = planes[0].buffer; val yRs = planes[0].rowStride; val yPs = planes[0].pixelStride
        val uBuf = planes[1].buffer; val uRs = planes[1].rowStride; val uPs = planes[1].pixelStride
        val vBuf = planes[2].buffer; val vRs = planes[2].rowStride; val vPs = planes[2].pixelStride
        var idx = 0
        for (y in 0 until h) {
            val yLine = y * yRs
            val even = (y and 1) == 0
            val cLine = y shr 1
            val uLine = cLine * uRs
            val vLine = cLine * vRs
            for (x in 0 until w) {
                val argb = pixels[idx++]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val yy = (((66 * r + 129 * g + 25 * b) + 128) shr 8) + 16
                yBuf.put(yLine + x * yPs, clamp(yy))
                if (even && (x and 1) == 0) {
                    val cx = x shr 1
                    val u = (((-38 * r - 74 * g + 112 * b) + 128) shr 8) + 128
                    val v = (((112 * r - 94 * g - 18 * b) + 128) shr 8) + 128
                    uBuf.put(uLine + cx * uPs, clamp(u))
                    vBuf.put(vLine + cx * vPs, clamp(v))
                }
            }
        }
    }

    private fun clamp(v: Int): Byte = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()

    // M3a: implementiert in app/src/main/cpp/v4l2bridge.c (BT.601 studio swing, 565+8888).
    private external fun nativeConvertToI420(
        bm: Bitmap,
        yBuf: java.nio.ByteBuffer, yRs: Int, yPs: Int,
        uBuf: java.nio.ByteBuffer, uRs: Int, uPs: Int,
        vBuf: java.nio.ByteBuffer, vRs: Int, vPs: Int,
        width: Int, height: Int,
    ): Boolean

    fun stop() {
        running = false
        val c = codec
        codec = null
        try { c?.stop() } catch (_: Exception) {}
        try { c?.release() } catch (_: Exception) {}
    }
}

/**
 * Baut das Low-Latency-[MediaFormat] für den H.264-Encoder. Als reine Funktion ausgelagert, damit
 * GOP-Länge und die Low-Latency-Schlüssel ohne MediaCodec/Gerät verifizierbar sind (siehe
 * `H264EncoderFormatTest`).
 *
 * @param iFrameIntervalSec GOP-Länge in Sekunden; via `setFloat` gesetzt, weil
 *   [MediaFormat.KEY_I_FRAME_INTERVAL] erst ab API 25 Sub-Sekunden-Werte (float) akzeptiert.
 *   Negativ = nach dem ersten Frame keine periodischen Keyframes mehr (M9-Modus).
 * @param enableLowLatencyKeys setzt [MediaFormat.KEY_LATENCY]=1 und [MediaFormat.KEY_PRIORITY]=0
 *   (realtime) — diese Schlüssel existieren erst ab API 30 (R); auf älteren Geräten weglassen.
 * @param intraRefreshPeriodFrames M9: Rolling-Intra-Welle über N Frames
 *   ([MediaFormat.KEY_INTRA_REFRESH_PERIOD]) statt periodischer IDR-Bursts — macht alle Frames
 *   ähnlich groß (Anlieferungs-Jitter runter). Null = kein Intra-Refresh (klassische GOP).
 */
internal fun buildAvcFormat(
    width: Int,
    height: Int,
    frameRate: Int,
    bitRate: Int,
    iFrameIntervalSec: Float,
    enableLowLatencyKeys: Boolean,
    intraRefreshPeriodFrames: Int? = null,
): MediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
    setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    )
    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
    // Sub-Sekunden-GOP braucht setFloat (KEY_I_FRAME_INTERVAL ist seit API 25 float-fähig);
    // setInteger würde 0.5 auf 0 (alle Frames IDR) abschneiden.
    setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameIntervalSec)
    // Keine B-Frames: erzwingt reine I/P-Reihenfolge ⇒ kein Decoder-Reorder-Delay (eine
    // Frame-Dauer Latenz) und keine encoderseitige Lookahead-Pufferung. KEY_MAX_B_FRAMES ist ein
    // compile-time-inlinter String ("max-bframes"), daher auch unter minSdk 26 unbedenklich.
    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
    setInteger(
        MediaFormat.KEY_BITRATE_MODE,
        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    )
    if (enableLowLatencyKeys) {
        setInteger(MediaFormat.KEY_LATENCY, 1)
        setInteger(MediaFormat.KEY_PRIORITY, 0) // realtime
    }
    if (intraRefreshPeriodFrames != null) {
        setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, intraRefreshPeriodFrames)
    }
}

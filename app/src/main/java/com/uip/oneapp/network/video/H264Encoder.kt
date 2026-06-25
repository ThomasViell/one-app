package com.uip.oneapp.network.video

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
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
     * GOP-Länge in Sekunden. Kürzer ⇒ häufigere IDR ⇒ kürzere „1-GOP"-Wartezeit, bis Player/Decoder
     * (re)joinen können — direkter Hebel auf die stationäre Live-Latenz. Default 0.5 s (statt der
     * 1 s aus dem Spike) als Latenz-/Qualitäts-Kompromiss; 0.25 s ist denkbar, falls der Rockchip-HW-
     * Encoder die Sub-Sekunden-GOP ehrt (TODO(device): on-device verifizieren, ob 0.5/0.25 honoriert
     * oder auf 0/1 gerundet wird). Bei spürbarem Qualitätsverlust [bitRate] leicht anheben.
     */
    private val iFrameIntervalSec: Float = 0.5f,
    private val onAccessUnit: (annexB: ByteArray, ptsUs: Long, keyframe: Boolean) -> Unit,
    private val onConfig: (sps: ByteArray, pps: ByteArray) -> Unit,
) {
    companion object { private const val TAG = "H264Encoder" }

    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    @Volatile private var running = false
    private var width = 0
    private var height = 0
    private var pixels = IntArray(0)
    private var startNs = 0L
    private var configReported = false

    @Volatile var encodedFrames = 0L; private set
    @Volatile var actualSize = "?"; private set

    fun start() {
        running = true
        startNs = System.nanoTime()
    }

    /** Kodiert ein Bitmap. true = eingespeist, false = verworfen. Konfiguriert beim 1. Frame. */
    fun encode(bm: Bitmap): Boolean {
        if (!running) return false
        var c = codec
        if (c == null) {
            // auf gerade Maße runden (H.264/YUV420 verlangt gerade Breite/Höhe)
            c = configure(bm.width / 2 * 2, bm.height / 2 * 2) ?: return false
        }
        // Defensive: nach der Lazy-Konfiguration muss das Bitmap groß genug sein.
        if (bm.width < width || bm.height < height) return false

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
        val cap = c.getInputBuffer(index)?.capacity() ?: (width * height * 3 / 2)
        val image = c.getInputImage(index)
        if (image == null) {
            c.queueInputBuffer(index, 0, 0, 0, 0)
            return false
        }
        fillImage(bm, image)
        val ptsUs = (System.nanoTime() - startNs) / 1000L
        c.queueInputBuffer(index, 0, cap, ptsUs, 0)
        drainOutput(c)
        return true
    }

    private fun configure(w: Int, h: Int): MediaCodec? {
        if (w <= 0 || h <= 0) return null
        return try {
            val format = buildAvcFormat(
                width = w,
                height = h,
                frameRate = frameRate,
                bitRate = bitRate,
                iFrameIntervalSec = iFrameIntervalSec,
                enableLowLatencyKeys = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            )
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            width = w; height = h
            pixels = IntArray(w * h)
            actualSize = "${w}x$h"
            codec = c
            Log.i(TAG, "Encoder konfiguriert ${w}x$h @$frameRate ${bitRate / 1000}kbps GOP=${iFrameIntervalSec}s codec=${c.name}")
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
                outIndex < 0 -> return
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
                            onAccessUnit(data, bufferInfo.presentationTimeUs, key)
                        }
                    }
                    c.releaseOutputBuffer(outIndex, false)
                }
            }
        }
    }

    private fun reportConfig(annexB: ByteArray) {
        if (configReported) return
        val sp = findSpsPps(annexB) ?: return
        configReported = true
        Log.i(TAG, "SPS=${sp.first.size}B PPS=${sp.second.size}B")
        onConfig(sp.first, sp.second)
    }

    /** ARGB-Ints (Bitmap.getPixels) → YUV420, BT.601 (studio swing). */
    private fun fillImage(bm: Bitmap, image: Image) {
        val w = width
        val h = height
        bm.getPixels(pixels, 0, w, 0, 0, w, h)
        val planes = image.planes
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
 * @param enableLowLatencyKeys setzt [MediaFormat.KEY_LATENCY]=1 und [MediaFormat.KEY_PRIORITY]=0
 *   (realtime) — diese Schlüssel existieren erst ab API 30 (R); auf älteren Geräten weglassen.
 */
internal fun buildAvcFormat(
    width: Int,
    height: Int,
    frameRate: Int,
    bitRate: Int,
    iFrameIntervalSec: Float,
    enableLowLatencyKeys: Boolean,
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
}

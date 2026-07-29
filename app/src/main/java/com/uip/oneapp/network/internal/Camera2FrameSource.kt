package com.uip.oneapp.network.internal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.core.content.ContextCompat
import com.uip.oneapp.bootstrap.CameraServiceSelfStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * **Standard-Camera2-Frame-Quelle** für die USB-Kamera der ONE — ersetzt den direkten
 * V4L2-Zugriff ([V4L2Camera]) ab dem Camera2-Umbau 2026-07-29
 * (`UMBAU_CAMERA2_PROMPT.md`, AP-1; Vorlauf: `RESULT_KAMERA_CAMERA2_2026-07-29.md`).
 *
 * Öffnet die Kamera mit `LENS_FACING_EXTERNAL` über die reguläre `android.hardware.camera2`-
 * API (`vendor.camera-provider-2-4-ext`) — **kein** `/dev/video0`-`open()`, **kein** `chmod`,
 * **keine** V4L2-ioctls. Voraussetzung: der Provider-Dienst läuft; das stellt
 * [CameraServiceSelfStarter] bei jedem [start] sicher (AP-2-Krücke, siehe dort für den
 * ungeklärten Boot-Stopp).
 *
 * Frame-Format: bevorzugt `ImageFormat.JPEG` in exakt 1280×720 (der MJPEG-native MS2109-Chip
 * liefert das i. d. R. ohne HAL-seitigen Re-Encode) und dekodiert wie zuvor [V4L2Camera] über
 * `BitmapFactory.decodeByteArray` — **identischer Decode-Pfad**, damit der Rest der App
 * (`LocalBitmapVideoPlayer`, OSD-Einbrennung, Aufnahme, PDF) unverändert bleibt. Liefert die
 * Kamera nur `YUV_420_888`, wird pro Frame über NV21 + `YuvImage.compressToJpeg` in denselben
 * Decode-Pfad überführt (zusätzlicher CPU-Aufwand, aber gleicher Bitmap-Ausgang).
 *
 * Implementiert exakt denselben [FrameSource]-Vertrag wie [V4L2Camera] — [CameraFrameBus] und
 * alle Konsumenten (lokale Anzeige, [com.uip.oneapp.network.video.OneVideoServer]) sehen keinen
 * Unterschied.
 */
class Camera2FrameSource(
    private val context: Context,
    private val width: Int = 1280,
    private val height: Int = 720,
) : FrameSource {

    companion object {
        private const val TAG = "Camera2FrameSource"

        /** Identisch zu [V4L2Camera]s Latenz-Mess-OSD (M7) — gleicher Schalter, gleiches Format,
         * damit bestehende Mess-Workflows (`adb shell setprop log.tag.DqLatencyOsd DEBUG`)
         * unverändert weiterfunktionieren, egal welche Quelle aktiv ist. */
        private const val LATENCY_OSD_TAG = "DqLatencyOsd"

        /** `setprop log.tag.DqFpsStats DEBUG`: loggt alle 5 s Ankunftsrate (Kamera→App) und
         * Verarbeitungszeiten pro Stufe getrennt — Messgrundlage für RESULT Abschnitt 10. */
        private const val FPS_STATS_TAG = "DqFpsStats"

        /** `setprop log.tag.DqFpsProbe DEBUG`: Roh-Ankunftsrate messen — Frames werden nur
         * acquired+closed, KEINE Verarbeitung, kein Publish. Beantwortet isoliert die Frage
         * „wie viel liefert die Kamera überhaupt", bevor unsere Pipeline etwas anfasst. */
        private const val FPS_PROBE_TAG = "DqFpsProbe"

        /** `setprop log.tag.DqLegacyYuv DEBUG`: erzwingt den alten YUV→NV21→JPEG→Bitmap-Pfad
         * (Vergleichsmessung alt/neu im selben Build). Ohne den Schalter läuft die native
         * Direkt-Konvertierung [nativeYuvToBitmap]. */
        private const val LEGACY_YUV_TAG = "DqLegacyYuv"

        /** Abschnitt 7 RESULT_CAMERA2_UMBAU: `init.svc.…=running` kippt sofort beim
         * Prozessstart der HAL, die Registrierung von `/dev/video0` bei `cameraserver`
         * braucht danach noch ~100 ms. Deshalb aufs Erscheinen der Kamera warten statt
         * nach dem ersten Fehlversuch endgültig aufzugeben. */
        private const val CAMERA_APPEAR_TIMEOUT_MS = 5_000L
        private const val CAMERA_APPEAR_POLL_MS = 250L

        private val nativeLibLoaded: Boolean = try {
            System.loadLibrary("v4l2bridge")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "v4l2bridge nicht ladbar — Rückfall auf Legacy-YUV-Pfad: ${e.message}")
            false
        }

        /** Konvertiert einen YUV_420_888-Frame (beliebige row-/pixelStrides, deckt NV12 wie
         * I420 ab) nativ direkt in ein RGB_565-Bitmap — ersetzt die frühere Dreifach-Stufe
         * NV21-Bytekopie → JPEG-Encode → JPEG-Decode (gemessen ~74 ms/Frame ≙ 13,5 fps). */
        @JvmStatic
        private external fun nativeYuvToBitmap(
            yBuf: java.nio.ByteBuffer, yRowStride: Int, yPixStride: Int,
            uBuf: java.nio.ByteBuffer, uRowStride: Int, uPixStride: Int,
            vBuf: java.nio.ByteBuffer, vRowStride: Int, vPixStride: Int,
            width: Int, height: Int, bitmap: Bitmap,
        ): Boolean
    }

    private val _state = MutableStateFlow(V4L2State())
    override val state: StateFlow<V4L2State> = _state.asStateFlow()

    private val _frame = MutableStateFlow<Bitmap?>(null)
    override val frame: StateFlow<Bitmap?> = _frame.asStateFlow()

    private var scope: CoroutineScope? = null
    private var openJob: Job? = null
    // Letzter (gecancelter) Open-Job: ein neuer start() direkt nach stop() wartet dessen Ende
    // ab, analog zum V4L2Camera-Muster (verhindert überlappende openCamera()-Versuche).
    private var lastJob: Job? = null

    private var cameraThread: HandlerThread? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    @Volatile private var frameCount = 0L
    @Volatile private var useJpeg = true

    // Messfenster AUFTRAG 2 (nur aktiv/geloggt wenn DqFpsStats DEBUG): Ankunft und
    // Verarbeitung GETRENNT, damit „Kamera liefert zu wenig" von „wir verlieren es
    // unterwegs" unterscheidbar ist.
    private var statWindowStartMs = 0L
    private var statArrived = 0
    private var statProcessed = 0
    private var statConvNs = 0L
    private var statDecodeNs = 0L

    private val osdStroke = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; textSize = 48f
        color = android.graphics.Color.BLACK; isAntiAlias = true
    }
    private val osdFill = Paint(osdStroke).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }

    @Synchronized
    override fun start() {
        if (openJob != null) return
        val prev = lastJob
        val coScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = coScope
        openJob = coScope.launch {
            prev?.join()
            openCameraBlocking()
        }
    }

    private fun openCameraBlocking() {
        // AP-2-Krücke: Dienst bei jedem Kamera-Start selbst sicherstellen (siehe
        // CameraServiceSelfStarter-Kommentar für den ungeklärten Boot-Stopp).
        val ensureResult = CameraServiceSelfStarter.ensureRunning()
        if (ensureResult is CameraServiceSelfStarter.Result.Failed) {
            Log.e(TAG, "Kameradienst nicht verfügbar: ${ensureResult.reason}")
            _state.update { it.copy(open = false, lastError = "Kameradienst nicht verfügbar: ${ensureResult.reason}") }
            return
        }
        if (!CameraServiceSelfStarter.ensureCameraPermission(context)) {
            _state.update { it.copy(open = false, lastError = "Kamera-Berechtigung nicht erteilt") }
            return
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (manager == null) {
            _state.update { it.copy(lastError = "CameraManager nicht verfügbar") }
            return
        }
        // Wettlauf ctl.start ↔ HAL-Registrierung (RESULT Abschnitt 7): nach dem Dienststart
        // erscheint die Kamera erst ~100 ms später in der cameraIdList. Bis zum Timeout
        // pollen statt nach dem ersten Fehlversuch endgültig aufzugeben.
        var cameraId: String? = null
        val appearDeadline = SystemClock.uptimeMillis() + CAMERA_APPEAR_TIMEOUT_MS
        var appearAttempts = 0
        while (true) {
            cameraId = findExternalCameraId(manager)
            if (cameraId != null) break
            appearAttempts++
            if (SystemClock.uptimeMillis() >= appearDeadline) break
            try {
                Thread.sleep(CAMERA_APPEAR_POLL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
        if (cameraId == null) {
            Log.e(TAG, "Externe Kamera nach ${CAMERA_APPEAR_TIMEOUT_MS}ms/" +
                "$appearAttempts Versuchen nicht erschienen (Dienst-Ergebnis: $ensureResult)")
            _state.update {
                it.copy(lastError = "Keine externe Kamera gefunden (auch nach " +
                    "${CAMERA_APPEAR_TIMEOUT_MS / 1000}s Wartezeit nicht erschienen)")
            }
            return
        }
        if (appearAttempts > 0) {
            Log.i(TAG, "Externe Kamera nach $appearAttempts Wartezyklen erschienen (Wettlauf-Retry griff)")
        }
        val chars = try {
            manager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            _state.update { it.copy(lastError = "getCameraCharacteristics fehlgeschlagen: ${e.message}") }
            return
        }
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val (format, size) = pickFormatAndSize(map)
        if (format == null || size == null) {
            _state.update { it.copy(lastError = "Keine passende Stream-Konfiguration (${width}x${height})") }
            return
        }
        useJpeg = format == ImageFormat.JPEG

        val thread = HandlerThread("Camera2FrameSource").apply { start() }
        cameraThread = thread
        val handler = Handler(thread.looper)

        val reader = ImageReader.newInstance(size.width, size.height, format, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ onImageAvailable(it) }, handler)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.update { it.copy(lastError = "Kamera-Berechtigung nicht erteilt") }
            return
        }
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    _state.update { it.copy(open = true, lastError = null) }
                    createSession(device, reader, handler)
                }

                override fun onDisconnected(device: CameraDevice) {
                    Log.w(TAG, "Kamera getrennt (onDisconnected)")
                    device.close()
                    cameraDevice = null
                    _state.update { it.copy(open = false, streaming = false) }
                }

                override fun onError(device: CameraDevice, error: Int) {
                    Log.e(TAG, "CameraDevice-Fehler code=$error")
                    device.close()
                    cameraDevice = null
                    _state.update {
                        it.copy(open = false, streaming = false, lastError = "CameraDevice-Fehler code=$error")
                    }
                }
            }, handler)
        } catch (e: SecurityException) {
            _state.update { it.copy(lastError = "openCamera: fehlende Berechtigung (${e.message})") }
        } catch (e: Exception) {
            _state.update { it.copy(lastError = "openCamera fehlgeschlagen: ${e.message}") }
        }
    }

    private fun createSession(device: CameraDevice, reader: ImageReader, handler: Handler) {
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
            }
            device.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                            _state.update { it.copy(streaming = true) }
                        } catch (e: Exception) {
                            _state.update { it.copy(lastError = "setRepeatingRequest fehlgeschlagen: ${e.message}") }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _state.update { it.copy(lastError = "CaptureSession-Konfiguration fehlgeschlagen") }
                    }
                },
                handler
            )
        } catch (e: Exception) {
            _state.update { it.copy(lastError = "createCaptureSession fehlgeschlagen: ${e.message}") }
        }
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return
        statArrived++

        // Sonde: nur Ankunftsrate messen, Verarbeitung komplett überspringen (AUFTRAG 2).
        if (Log.isLoggable(FPS_PROBE_TAG, Log.DEBUG)) {
            image.close()
            maybeLogStats(probeMode = true)
            return
        }

        try {
            val bm = decodeFrame(image)
            if (bm != null) {
                statProcessed++
                if (Log.isLoggable(LATENCY_OSD_TAG, Log.DEBUG) && bm.isMutable) {
                    val text = "L %05d".format(SystemClock.uptimeMillis() % 100_000)
                    Canvas(bm).apply {
                        drawText(text, 24f, 64f, osdStroke)
                        drawText(text, 24f, 64f, osdFill)
                    }
                }
                _frame.value = bm
                frameCount++
                if (frameCount % 30L == 0L) {
                    _state.update { it.copy(frameCount = frameCount) }
                }
            }
        } finally {
            image.close()
        }
        maybeLogStats(probeMode = false)
    }

    /** Frame → Bitmap. JPEG-HAL-Frames wie bisher über BitmapFactory; YUV-Frames nativ
     * direkt ins Bitmap ([nativeYuvToBitmap]) — der frühere JPEG-Umweg (NV21-Bytekopie +
     * compressToJpeg + decodeByteArray) bleibt nur als Rückfall/Vergleichspfad
     * (`DqLegacyYuv`-Schalter) erhalten. */
    private fun decodeFrame(image: Image): Bitmap? {
        val useLegacyYuv = !nativeLibLoaded || Log.isLoggable(LEGACY_YUV_TAG, Log.DEBUG)
        if (!useJpeg && !useLegacyYuv) {
            val t0 = System.nanoTime()
            val bm = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
            val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
            val ok = try {
                nativeYuvToBitmap(
                    yP.buffer, yP.rowStride, yP.pixelStride,
                    uP.buffer, uP.rowStride, uP.pixelStride,
                    vP.buffer, vP.rowStride, vP.pixelStride,
                    image.width, image.height, bm,
                )
            } catch (e: Throwable) {
                Log.w(TAG, "nativeYuvToBitmap failed: ${e.message}")
                false
            }
            statConvNs += System.nanoTime() - t0
            return if (ok) bm else null
        }

        val t0 = System.nanoTime()
        val bytes = if (useJpeg) jpegBytes(image) else yuvToJpegBytes(image)
        val t1 = System.nanoTime()
        statConvNs += t1 - t0
        val opts = BitmapFactory.Options().apply {
            inMutable = Log.isLoggable(LATENCY_OSD_TAG, Log.DEBUG)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bm = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Throwable) {
            Log.w(TAG, "decodeByteArray failed: ${e.message}")
            null
        }
        statDecodeNs += System.nanoTime() - t1
        return bm
    }

    /** 5-Sekunden-Fenster-Zusammenfassung, nur bei `setprop log.tag.DqFpsStats DEBUG`. */
    private fun maybeLogStats(probeMode: Boolean) {
        if (!Log.isLoggable(FPS_STATS_TAG, Log.DEBUG)) return
        val now = SystemClock.uptimeMillis()
        if (statWindowStartMs == 0L) {
            statWindowStartMs = now
            return
        }
        val elapsedMs = now - statWindowStartMs
        if (elapsedMs < 5_000) return
        val arrivalFps = statArrived * 1000.0 / elapsedMs
        if (probeMode) {
            Log.d(FPS_STATS_TAG, "PROBE ankunft=%.1f fps (%d Frames/%d ms) — reine Lieferrate der Kamera, keine Verarbeitung"
                .format(arrivalFps, statArrived, elapsedMs))
        } else {
            val processedFps = statProcessed * 1000.0 / elapsedMs
            val avgConvMs = if (statProcessed > 0) statConvNs / 1e6 / statProcessed else 0.0
            val avgDecodeMs = if (statProcessed > 0) statDecodeNs / 1e6 / statProcessed else 0.0
            Log.d(FPS_STATS_TAG, "ankunft=%.1f fps verarbeitet=%.1f fps | konvertierung=%.1f ms/Frame decode=%.1f ms/Frame (Fenster %d ms)"
                .format(arrivalFps, processedFps, avgConvMs, avgDecodeMs, elapsedMs))
        }
        statWindowStartMs = now
        statArrived = 0; statProcessed = 0; statConvNs = 0; statDecodeNs = 0
    }

    private fun jpegBytes(image: Image): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    /** Fallback, falls die Kamera kein direktes JPEG liefert: YUV_420_888 -> NV21 -> JPEG,
     * damit derselbe BitmapFactory-Decode-Pfad wie bei [jpegBytes] genutzt werden kann. */
    private fun yuvToJpegBytes(image: Image): ByteArray {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        return out.toByteArray()
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val nv21 = ByteArray(image.width * image.height * 3 / 2)

        // Y-Ebene: rowStride kann größer als width sein — zeilenweise mit pixelStride kopieren.
        var pos = 0
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until image.height) {
            for (col in 0 until image.width) {
                nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride)
            }
        }
        // NV21 = VU interleaved. Chroma-Ebenen können eigenen row-/pixelStride haben
        // (semi-planar NV12/NV21-artige Sensoren haben pixelStride=2, planare I420 pixelStride=1).
        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                nv21[pos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                nv21[pos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
            }
        }
        return nv21
    }

    /**
     * Sucht die externe Kamera (`LENS_FACING_EXTERNAL`, API 28+). Die ONE läuft auf
     * RK3588/Android-Versionen deutlich über API 28; die Versionsprüfung ist reine
     * Absicherung gegen einen (auf dieser Hardware nicht erwarteten) älteren Build.
     */
    private fun findExternalCameraId(manager: CameraManager): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e(TAG, "LENS_FACING_EXTERNAL erfordert API 28+, Gerät hat ${Build.VERSION.SDK_INT}")
            return null
        }
        return try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraMetadata.LENS_FACING_EXTERNAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "cameraIdList/getCameraCharacteristics fehlgeschlagen: ${e.message}")
            null
        }
    }

    /**
     * Bevorzugt YUV_420_888 exakt in Zielgröße; Fallback JPEG; sonst die jeweils
     * nächstgelegene verfügbare Größe.
     *
     * Die ursprüngliche JPEG-Präferenz („MJPEG-nativ, kein Re-Encode") ist auf der
     * ONE-Hardware widerlegt: der BLOB/JPEG-Pfad der externen Kamera-HAL scheitert dort
     * bei praktisch jedem Frame („Convert V4L2 frame to YU12 failed", 99,7 % gemessen)
     * und liefert dauerhaft Schwarzbild — RESULT_CAMERA2_UMBAU_2026-07-29.md Abschnitt 8.
     * Die HAL ist Gerätesoftware des Herstellers und wird nicht von uns repariert
     * (CEO-Entscheid 29.07.); YUV + native Direkt-Konvertierung ist der tragfähige Pfad.
     */
    private fun pickFormatAndSize(map: StreamConfigurationMap?): Pair<Int?, Size?> {
        map ?: return null to null
        val target = Size(width, height)
        map.getOutputSizes(ImageFormat.YUV_420_888)?.let { sizes ->
            if (sizes.contains(target)) return ImageFormat.YUV_420_888 to target
        }
        map.getOutputSizes(ImageFormat.JPEG)?.let { sizes ->
            if (sizes.contains(target)) return ImageFormat.JPEG to target
        }
        map.getOutputSizes(ImageFormat.YUV_420_888)?.minByOrNull {
            Math.abs(it.width - width) + Math.abs(it.height - height)
        }?.let { return ImageFormat.YUV_420_888 to it }
        map.getOutputSizes(ImageFormat.JPEG)?.minByOrNull {
            Math.abs(it.width - width) + Math.abs(it.height - height)
        }?.let { return ImageFormat.JPEG to it }
        return null to null
    }

    @Synchronized
    override fun stop() {
        openJob?.cancel()
        lastJob = openJob
        openJob = null
        scope?.cancel()
        scope = null
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null
        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        cameraThread?.quitSafely()
        cameraThread = null
        _state.update { it.copy(open = false, streaming = false) }
    }
}

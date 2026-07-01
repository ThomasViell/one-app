package com.uip.oneapp.network.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Direkter V4L2-Capture für die ONE-Schiebekamera.
 *
 * 1:1 portiert aus dem Smoke-Test (one-smoketest). Umgeht den Android-UsbManager-
 * Permission-Pfad — öffnet /dev/video0 direkt als File. Voraussetzung:
 * Tablet rooted, /dev/video0 mit chmod 666 für die App lesbar/schreibbar (Phase P7).
 *
 * Native-Bridge: libv4l2bridge.so aus app/src/main/cpp/v4l2bridge.c.
 * Frame-Format: MJPEG (MS2109-Capture-Chip nativ unterstützt). Decode zu Bitmap
 * via BitmapFactory.decodeByteArray.
 */
class V4L2Camera(
    private val devicePath: String = "/dev/video0",
    private val width: Int = 1280,
    private val height: Int = 720
) : FrameSource {
    companion object {
        private const val TAG = "V4L2Camera"
        init { System.loadLibrary("v4l2bridge") }
    }

    private val _state = MutableStateFlow(V4L2State())
    override val state: StateFlow<V4L2State> = _state.asStateFlow()

    private val _frame = MutableStateFlow<Bitmap?>(null)
    override val frame: StateFlow<Bitmap?> = _frame.asStateFlow()

    private var nativePtr: Long = 0
    private var scope: CoroutineScope? = null
    private var captureJob: Job? = null
    // Letzter (gecancelter) Capture-Job: ein neuer start() direkt nach stop() muss dessen Ende
    // abwarten, bevor er /dev/video0 neu öffnet — V4L2 ist exklusiv; sonst schlägt nativeOpen
    // fehl, solange der alte Job zwischen Loop-Ende und nativeClose steht.
    private var lastJob: Job? = null

    @Synchronized
    override fun start() {
        if (captureJob != null) return
        val prev = lastJob
        val coScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = coScope
        captureJob = coScope.launch {
            prev?.join()
            val ptr = nativeOpen(devicePath)
            if (ptr == 0L) {
                _state.update { it.copy(open = false, lastError = "open($devicePath) failed — chmod 666 fehlt?") }
                return@launch
            }
            nativePtr = ptr
            _state.update { it.copy(open = true, lastError = null) }

            try {
                if (!nativeSetupMjpeg(ptr, width, height)) {
                    _state.update { it.copy(lastError = "MJPEG-Setup ${width}x${height} fehlgeschlagen") }
                    return@launch
                }
                _state.update { it.copy(streaming = true) }

                val opts = BitmapFactory.Options().apply {
                    inMutable = false
                    inPreferredConfig = Bitmap.Config.RGB_565
                }

                var frameCount = 0L
                while (isActive) {
                    val jpeg = nativeDequeueFrame(ptr) ?: continue
                    val bm = try {
                        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
                    } catch (e: Throwable) {
                        Log.w(TAG, "decodeByteArray failed: ${e.message}")
                        null
                    }
                    if (bm != null) {
                        _frame.value = bm
                        frameCount++
                        if (frameCount % 30L == 0L) {
                            _state.update { it.copy(frameCount = frameCount) }
                        }
                    }
                }
            } finally {
                // Auf ALLEN Ausgängen (Loop-Ende, Setup-Fehler, Cancellation) schließen.
                nativeClose(ptr)
                nativePtr = 0
                _state.update { it.copy(open = false, streaming = false) }
            }
        }
    }

    @Synchronized
    override fun stop() {
        captureJob?.cancel()
        lastJob = captureJob
        captureJob = null
        scope?.cancel()
        scope = null
    }

    // ── JNI ───────────────────────────────────────────────────────
    private external fun nativeOpen(path: String): Long
    private external fun nativeSetupMjpeg(ptr: Long, width: Int, height: Int): Boolean
    private external fun nativeDequeueFrame(ptr: Long): ByteArray?
    private external fun nativeClose(ptr: Long)
}

data class V4L2State(
    val open: Boolean = false,
    val streaming: Boolean = false,
    val frameCount: Long = 0,
    val lastError: String? = null
)

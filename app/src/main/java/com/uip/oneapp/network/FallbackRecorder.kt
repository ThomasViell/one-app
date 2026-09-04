package com.uip.oneapp.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.Log
import com.uip.oneapp.export.OsdSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immer HW-Encoder ([HardwareBitmapRecorder]); scheitert dessen start(), unsichtbarer
 * automatischer Rückfall auf [LocalBitmapRecorder]. Für die UI EIN Recorder (delegiert an den
 * aktiven), mit gespiegeltem [state]-Flow.
 */
class FallbackRecorder(
    private val context: Context,
    arbiter: CameraEncoderArbiter,
) : Recorder {
    private val primary: Recorder = HardwareBitmapRecorder(context, arbiter)
    private var active: Recorder = primary
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(RecordingState.IDLE)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()
    private var mirrorJob: Job? = null

    private fun mirror(r: Recorder) {
        mirrorJob?.cancel()
        mirrorJob = scope.launch { r.state.collect { _state.value = it } }
    }
    init { mirror(primary) }

    override val isRecording: Boolean get() = active.isRecording
    override val isPaused: Boolean get() = active.isPaused

    override fun start(
        outputPath: String, frameFlow: StateFlow<Bitmap?>, fps: Int, sdResolution: Boolean,
        osdSettings: OsdSettings?, typeface: Typeface?, osdLine1Provider: () -> String,
        osdLine2Provider: () -> String, findingProvider: () -> String?, meterProvider: (() -> Float)?,
    ): Boolean {
        if (primary.start(outputPath, frameFlow, fps, sdResolution, osdSettings, typeface,
                osdLine1Provider, osdLine2Provider, findingProvider, meterProvider)) {
            active = primary; mirror(primary); return true
        }
        Log.w(TAG, "HW-Encoder-Start fehlgeschlagen — unsichtbarer Rückfall auf LocalBitmapRecorder")
        val fb = LocalBitmapRecorder(context)
        active = fb; mirror(fb)
        return fb.start(outputPath, frameFlow, fps, sdResolution, osdSettings, typeface,
            osdLine1Provider, osdLine2Provider, findingProvider, meterProvider)
    }

    override fun stop(onDone: (String?) -> Unit) = active.stop(onDone)
    override fun pause() = active.pause()
    override fun resume() = active.resume()
    override fun cancel() = active.cancel()

    companion object { private const val TAG = "FallbackRecorder" }
}

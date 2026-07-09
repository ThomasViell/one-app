package com.uip.oneapp.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Schreibt parallel zur Aufnahme eine Meter-Sidecar-Datei (<video>.meter.jsonl).
 *
 * Zeitbasis: Wall-Clock-Elapsed minus Pausen — entspricht der Medienzeit des Encoders,
 * weil LocalBitmapRecorder während Pausen keine Frames schreibt und FfmpegRtspRecorder
 * keine Pause-Funktion hat. Nach Remux (-c copy) bleiben die Timestamps unverändert.
 *
 * Sampling: ~5 Hz (200 ms-Intervall). Während Pause werden keine Samples geschrieben.
 */
class MeterTrackWriter(outputFile: File) {

    private val sidecarFile = File(outputFile.absolutePath + METER_SIDECAR_SUFFIX)
    private var startMs: Long = 0L
    @Volatile private var pausedAt: Long? = null
    @Volatile private var totalPausedMs: Long = 0L
    private var job: Job? = null

    private val mediaTimeMs: Long get() {
        val now = System.currentTimeMillis()
        val ongoingPauseMs = pausedAt?.let { now - it } ?: 0L
        return now - startMs - totalPausedMs - ongoingPauseMs
    }

    fun start(scope: CoroutineScope, meterProvider: () -> Float) {
        startMs = System.currentTimeMillis()
        sidecarFile.delete()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (pausedAt == null) {
                    val t = mediaTimeMs
                    val m = meterProvider()
                    try { sidecarFile.appendText("{\"t\":$t,\"m\":$m}\n") } catch (_: Exception) {}
                }
                delay(200)
            }
        }
    }

    fun pause() {
        if (pausedAt == null) pausedAt = System.currentTimeMillis()
    }

    fun resume() {
        val p = pausedAt ?: return
        totalPausedMs += System.currentTimeMillis() - p
        pausedAt = null
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

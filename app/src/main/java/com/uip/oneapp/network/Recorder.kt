package com.uip.oneapp.network

import android.graphics.Bitmap
import android.graphics.Typeface
import com.uip.oneapp.export.OsdSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Gemeinsamer Aufnahme-Zustand (Welle 5). Ersetzt die frühere `LocalBitmapRecorder.State`, damit
 * beide Recorder (HW + Rückfallebene) uniform von der UI abgefragt werden.
 */
enum class RecordingState { IDLE, RECORDING, PAUSED, FINISHING }

/**
 * Gemeinsame Aufnahme-Schnittstelle für [LocalBitmapRecorder] (Rückfallebene, JPEG/FIFO/libx264)
 * und [HardwareBitmapRecorder] (HW-Encoder, Welle 5). Die UI (`InspectionScreen`) hält genau eine
 * Instanz, die der [RecorderFactory] anhand [FeatureFlags.useHardwareRecorder] liefert, und ruft
 * ausschließlich diese Schnittstelle — kein Call-Site verzweigt mehr auf den konkreten Typ.
 */
interface Recorder {

    val state: StateFlow<RecordingState>

    /** Aufnahme läuft (auch pausiert) — Datei ist offen. */
    val isRecording: Boolean

    val isPaused: Boolean

    /**
     * Startet die Aufnahme. Signatur identisch für beide Recorder (OSD-Parameter default-leer für
     * den „ohne Einblendung"-Weg). true = gestartet.
     *
     * @param outputPath      Zielpfad der fertigen MP4.
     * @param frameFlow       Live-Frames (RGBA) aus dem V4L2-Fan-out.
     * @param fps             Ziel-Bildrate (Default [RecorderConfig.TARGET_FPS]).
     * @param sdResolution    true → auf 720×576 skalieren (SD), sonst native Auflösung.
     * @param osdSettings     != null und enableOsdBurnIn → OSD pro Bild eingebrannt.
     */
    fun start(
        outputPath: String,
        frameFlow: StateFlow<Bitmap?>,
        fps: Int = RecorderConfig.TARGET_FPS,
        sdResolution: Boolean = false,
        osdSettings: OsdSettings? = null,
        typeface: Typeface? = null,
        osdLine1Provider: () -> String = { "" },
        osdLine2Provider: () -> String = { "" },
        findingProvider: () -> String? = { null },
        meterProvider: (() -> Float)? = null,
    ): Boolean

    /** Stoppt die Aufnahme (auch aus der Pause) und finalisiert die Datei. */
    fun stop(onDone: (String?) -> Unit)

    /** Pause: Frame-Zufuhr aus; Datei/Journal bleiben offen, die Pausenzeit fehlt im Video. */
    fun pause()

    /** Fortsetzen aus der Pause. */
    fun resume()

    /** Abbruch ohne Finalisierung (View verlassen). */
    fun cancel()
}

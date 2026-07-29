package com.uip.oneapp.network.internal

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * Frame-liefernde Quelle hinter [CameraFrameBus] — real [Camera2FrameSource]
 * (`android.hardware.camera2`, `LENS_FACING_EXTERNAL`); Tests injizieren ein Fake.
 */
interface FrameSource {
    /** Neuestes dekodiertes Kamera-Bild (konflatierend; `null` bis zum ersten Frame). */
    val frame: StateFlow<Bitmap?>

    /** Capture-Zustand (offen / streaming / Frame-Zähler / letzter Fehler). */
    val state: StateFlow<V4L2State>

    /** Öffnet das Gerät und startet den Capture-Loop. Idempotent. */
    fun start()

    /** Stoppt den Capture-Loop und gibt das Gerät frei. */
    fun stop()
}

/** Capture-Zustand einer [FrameSource]. Name historisch (aus dem entfernten V4L2-Direktpfad,
 * AP-5) — der Vertrag gilt unverändert für [Camera2FrameSource]. */
data class V4L2State(
    val open: Boolean = false,
    val streaming: Boolean = false,
    val frameCount: Long = 0,
    val lastError: String? = null
)

/**
 * **Kamera-Frame-Fan-out (Dual-Modus, Welle 3d-Video).** Additive Schicht über genau EINER
 * [FrameSource] (real: [Camera2FrameSource]). Das Capture-Gerät hat nur EINEN Besitzer —
 * dieser Bus ist dieser Besitzer und reicht denselben konflatierenden [frames]-`StateFlow`
 * an **mehrere Konsumenten** weiter:
 *   1. die lokale Direkt-Modus-Anzeige (`OneInternalHardwareService` → `VideoSource.LocalBitmap`),
 *   2. den RTSP/H.264-Encoder (Welle 3c, [com.uip.oneapp.network.video.OneVideoServer]).
 *
 * **Warum nur ein dünner Wrapper?** Der Wert liegt nicht in einer Transformation, sondern in
 * der *Seam*: alle Konsumenten beziehen Frames über genau diese eine, per DI geteilte Instanz,
 * statt eine zweite Quelle zu konstruieren (= zweites Öffnen des exklusiven Kamera-Geräts →
 * Fehlschlag). [frames] ist **dieselbe StateFlow-Instanz** wie die der Quelle (keine Kopie,
 * keine Frame-Drops) → der lokale Anzeigepfad bleibt bit-identisch.
 *
 * **Lebenszyklus (bewusst):** Start/Stop bleibt vollständig beim lokalen Anzeige-Besitzer
 * ([OneInternalHardwareService.startPolling]/`stopPolling`, getrieben vom Inspektions-Screen).
 * Der RTSP-Encoder ist **reiner Konsument** und startet/stoppt das Gerät NICHT — er bekommt nur
 * dann Frames, wenn die lokale Anzeige aktiv ist. Echte Arbitrierung („Tablet hält die Kamera
 * an, auch wenn der ONE-Screen aus ist") ist Welle 3d-vollständig — TODO(W3d).
 */
class CameraFrameBus(
    private val source: FrameSource
) {
    private val lock = Any()
    private var running = false

    /** Geteilter Frame-Strom für ALLE Konsumenten — exakt die StateFlow der Quelle. */
    val frames: StateFlow<Bitmap?> get() = source.frame

    /** Capture-Zustand der einzigen Quelle. */
    val state: StateFlow<V4L2State> get() = source.state

    /** true zwischen [start] und [stop] — von Konsumenten abfragbar (z. B. Status-HUD). */
    val isRunning: Boolean get() = synchronized(lock) { running }

    /**
     * Öffnet das Gerät über die Quelle. Idempotent: ein zweiter [start] ohne zwischenzeitlichen
     * [stop] ist ein No-op (die Quelle selbst ist ebenfalls idempotent) — verhindert
     * doppeltes `open(/dev/video0)`.
     */
    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        source.start()
    }

    /** Stoppt die Quelle und gibt das Gerät frei. Nach [stop] ohne vorherigen [start] No-op. */
    fun stop() {
        synchronized(lock) {
            if (!running) return
            running = false
        }
        source.stop()
    }
}

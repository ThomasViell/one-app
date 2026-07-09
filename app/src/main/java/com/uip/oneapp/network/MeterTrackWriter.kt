package com.uip.oneapp.network

import java.io.BufferedWriter
import java.io.File
import java.util.Locale

/**
 * Schreibt parallel zur Aufnahme eine Meter-Sidecar-Datei (<video>.meter.jsonl).
 *
 * Zeitbasis: **Frame-Index**, nicht Wall-Clock (Welle 4b). ffmpeg (image2pipe,
 * `-framerate fps`) vergibt die Präsentationszeit strikt nach Frame-Index
 * (Medienzeit = frameIndex / fps). Die Schreibschleife des Recorders liefert
 * langsamer als 1/fps, eine Uhr würde also dem Video davonlaufen. Wir stempeln
 * daher jedes Sample mit dem Index des Frames, der wirklich in die FIFO geht.
 *
 * Pausen erledigen sich von selbst: während einer Pause schreibt der Recorder
 * keinen Frame → [onFrame] wird nicht aufgerufen → der Index steht still.
 *
 * Kopfzeile: `{"v":2,"fps":<tatsächliche fps>}`, danach `{"f":index,"m":meter}`.
 * Sampling: jeder `max(1, fps/5)`-te Frame (Frame 0 immer) → ~5–6 Hz je nach fps
 * (Ganzzahl-Division: fps=12→jeder 2.→6 Hz, fps=15→jeder 3.→5 Hz). Dichter ist unkritisch;
 * lookupMeter interpoliert ohnehin über den Frame-Index, nicht über die Sample-Anzahl.
 *
 * Nach Remux (Welle 2, `-c copy`) bleiben die Timestamps unverändert → gültig.
 */
class MeterTrackWriter(outputFile: File) {

    private val sidecarFile = File(outputFile.absolutePath + METER_SIDECAR_SUFFIX)
    // @Volatile: stop() ist öffentliche API und könnte aus einem anderen Thread als onFrame()
    // aufgerufen werden — Sichtbarkeit des null-Setzens garantieren (Muster wie FfmpegRtspRecorder).
    @Volatile private var writer: BufferedWriter? = null
    private var sampleEvery = 1

    /** Öffnet die Sidecar und schreibt die Kopfzeile mit der TATSÄCHLICH genutzten fps. */
    fun start(fps: Int) {
        if (fps <= 0) return
        try {
            sidecarFile.delete()
            val w = sidecarFile.bufferedWriter()
            w.write("{\"v\":$METER_SIDECAR_VERSION,\"fps\":$fps}\n")
            sampleEvery = (fps / 5).coerceAtLeast(1)
            writer = w
        } catch (_: Exception) {
            writer = null
        }
    }

    /**
     * Vom Recorder für JEDEN in die FIFO geschriebenen Frame aufzurufen (0-basiert).
     * Dezimiert intern (~5–6 Hz). [frameIndex] MUSS exakt dem ffmpeg-Frame-Index
     * entsprechen (nur echte, nicht-pausierte Frames zählen).
     */
    fun onFrame(frameIndex: Int, meter: Float) {
        val w = writer ?: return
        if (frameIndex % sampleEvery != 0) return
        try {
            // Locale.US erzwingt '.' als Dezimaltrenner — sonst zerbricht deutsches
            // Locale ("0,31") das JSON und der Reader verwirft die Zeile.
            w.write("{\"f\":$frameIndex,\"m\":${String.format(Locale.US, "%.2f", meter)}}\n")
        } catch (_: Exception) {
        }
    }

    /** Flusht und schließt die Sidecar. Idempotent. */
    fun stop() {
        val w = writer ?: return
        writer = null
        try { w.flush(); w.close() } catch (_: Exception) {}
    }
}

package com.uip.oneapp.network

import java.io.BufferedWriter
import java.io.File
import java.util.Locale

/**
 * Schreibt parallel zur HW-Encoder-Aufnahme eine Meter-Sidecar **v3** (`<video>.meter.jsonl`).
 *
 * Zeitbasis: **echte Medienzeit** (`tUs`) == exakt die `presentationTimeUs`, die auch dem Encoder
 * für dieses Bild gefüttert wird (monotone Uhr minus Pausenzeit). Damit liegt die Meter-Spur auf
 * derselben Achse wie der Container → Nachschlagen über `exoPlayer.currentPosition` trifft ohne
 * Umrechnung.
 *
 * Kopfzeile `{"v":3}`, danach `{"tUs":<pts>,"m":<meter>}`. [onSample] MUSS nur für tatsächlich
 * kodierte Bilder aufgerufen werden (1:1 zum Encoder), sonst hätte die Spur Samples für verworfene
 * Frames. Pausen erledigen sich von selbst (in Pause kein Frame → kein [onSample]).
 */
class MeterTrackWriterV3(outputFile: File) {

    private val sidecarFile = File(outputFile.absolutePath + METER_SIDECAR_SUFFIX)
    // @Volatile: stop() ist öffentliche API und könnte aus einem anderen Thread als onSample()
    // aufgerufen werden — Sichtbarkeit des null-Setzens garantieren.
    @Volatile private var writer: BufferedWriter? = null
    // Welle 5a (Befund 1): Medienzeit des letzten flush(). Ein Absturz (force-stop) verwirft den
    // BufferedWriter-Puffer — ohne regelmäßiges flush() fehlten der geretteten Aufnahme ~13 s
    // Stationen. Wir zwingen die Spur mind. jede Sekunde Medienzeit auf die Platte.
    private var lastFlushTUs = 0L

    /** Öffnet die Sidecar und schreibt die v3-Kopfzeile. */
    fun start() {
        try {
            sidecarFile.delete()
            val w = sidecarFile.bufferedWriter()
            w.write("{\"v\":$METER_SIDECAR_VERSION_V3}\n")
            lastFlushTUs = 0L
            writer = w
        } catch (_: Exception) {
            writer = null
        }
    }

    /**
     * Für JEDES tatsächlich kodierte Bild aufzurufen. [tUs] == die dem Encoder gefütterte,
     * pausenbereinigte Input-PTS in Mikrosekunden (streng steigend).
     */
    fun onSample(tUs: Long, meter: Float) {
        val w = writer ?: return
        try {
            // Locale.US erzwingt '.' als Dezimaltrenner — deutsches Locale ("0,31") zerbräche das JSON.
            w.write("{\"tUs\":$tUs,\"m\":${String.format(Locale.US, "%.2f", meter)}}\n")
            // Absturz-Budget: höchstens ~1 s Spur im Puffer. Bei einer Pause steht tUs still →
            // kein unnötiges flush; nach Resume läuft tUs (und damit der Takt) weiter.
            if (tUs - lastFlushTUs >= FLUSH_INTERVAL_US) {
                w.flush()
                lastFlushTUs = tUs
            }
        } catch (_: Exception) {
        }
    }

    /** Flusht und schließt die Sidecar. Idempotent. */
    fun stop() {
        val w = writer ?: return
        writer = null
        try { w.flush(); w.close() } catch (_: Exception) {}
    }

    companion object {
        /** Max. Medienzeit zwischen zwei flush() → höchstens so viel Spur kostet ein Absturz. */
        private const val FLUSH_INTERVAL_US = 1_000_000L
    }
}

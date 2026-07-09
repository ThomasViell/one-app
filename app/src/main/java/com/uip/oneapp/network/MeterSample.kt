package com.uip.oneapp.network

/** Datei-Suffix der Meter-Sidecar-Datei neben jeder Aufnahme. */
const val METER_SIDECAR_SUFFIX = ".meter.jsonl"

/** Aktuelle Sidecar-Version. v1 (Wall-Clock) wird bewusst NIE gelesen (Welle 4b). */
const val METER_SIDECAR_VERSION = 2

/**
 * Ein Sample der Meter-Spur: Frame-Index (0-basiert, wie ffmpeg image2pipe die PTS
 * vergibt: Medienzeit = frameIndex / fps) + Meterwert an diesem Frame.
 *
 * Bewusst KEINE Wall-Clock: die Schreibschleife liefert langsamer als 1/fps
 * (Bitmap-Copy, OSD-Burn-in, JPEG, FIFO), also driftet jede Uhr-Zeitbasis dem
 * Video linear davon. Der Frame-Index ist dagegen exakt an die Encoder-Medienzeit
 * gekoppelt — unabhängig von Systemlast und Aufnahmelänge.
 */
data class MeterSample(val frameIndex: Int, val meter: Float)

/**
 * Eine geladene Meter-Spur: die tatsächlich verwendete Bildrate + die Samples.
 * [MeterTrack.EMPTY] bedeutet „keine gültige Spur" (fehlt, Altversion v1, kaputt)
 * → Wiedergabe fällt auf das leere Pflichtfeld (Stufe 1) zurück, NIE auf 0.00.
 */
data class MeterTrack(val fps: Int, val samples: List<MeterSample>) {
    companion object {
        val EMPTY = MeterTrack(0, emptyList())
    }

    /**
     * Erwartete Videodauer aus der Spur: letzterFrameIndex / fps.
     * Für die Invariante „Videodauer ≈ letzterFrameIndex / fps" (Selbstschutz).
     * 0 wenn keine Samples/ungültige fps.
     */
    fun expectedDurationMs(): Long =
        if (fps > 0 && samples.isNotEmpty())
            Math.round(samples.last().frameIndex * 1000.0 / fps)
        else 0L
}

/**
 * Rechnet eine Player-Position (ms Medienzeit) in den Frame-Index um.
 * `frameIndex = round(positionMs * fps / 1000)`. Bei fps <= 0 → 0.
 */
fun frameIndexForPosition(positionMs: Long, fps: Int): Long =
    if (fps <= 0) 0L else Math.round(positionMs * fps / 1000.0)

/**
 * Schlägt den Meterwert zur gegebenen Player-Position nach.
 *
 * - Leere/ungültige Spur → null (Fallback auf leeres Pflichtfeld, Stufe 1).
 * - Position vor dem ersten Sample → erster Meterwert (kein Rückextrapolieren).
 * - Position nach dem letzten Sample → letzter Meterwert.
 * - Sonst: lineare Interpolation zwischen den zwei umgebenden Samples nach Frame-Index.
 *
 * Reine Funktion — die Uhr taucht nirgends auf; nur Frame-Index und fps.
 */
fun lookupMeter(track: MeterTrack, positionMs: Long): Float? {
    if (track.fps <= 0 || track.samples.isEmpty()) return null
    val samples = track.samples
    val target = frameIndexForPosition(positionMs, track.fps)
    if (target <= samples.first().frameIndex) return samples.first().meter
    if (target >= samples.last().frameIndex) return samples.last().meter
    val afterIdx = samples.indexOfFirst { it.frameIndex >= target }
    val s0 = samples[afterIdx - 1]
    val s1 = samples[afterIdx]
    if (s1.frameIndex == s0.frameIndex) return s0.meter
    val t = (target - s0.frameIndex).toFloat() / (s1.frameIndex - s0.frameIndex).toFloat()
    return s0.meter + t * (s1.meter - s0.meter)
}

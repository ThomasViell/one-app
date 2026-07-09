package com.uip.oneapp.network

/**
 * Meter-Spur **v3** (Welle 5) — Zeitachse statt Frame-Index.
 *
 * Der HW-Encoder-Aufnahmeweg (`HardwareBitmapRecorder`) stempelt jedes Bild mit einer echten,
 * pausenbereinigten `presentationTimeUs` (monotone Uhr minus Pausenzeit). Die Meter-Spur wird auf
 * **dieselbe Achse** gestempelt: Kopf `{"v":3}`, Samples `{"tUs":<pts>,"m":<meter>}`. Nachschlagen
 * bei Wiedergabe über `exoPlayer.currentPosition` (ms → µs), lineare Interpolation.
 *
 * Bewusst getrennt vom v2-Stack ([MeterSample]/[MeterTrack]/[lookupMeter], Frame-Index): der
 * v2-Writer bleibt für den erhaltenen `LocalBitmapRecorder` (Rückfallebene), v3 ist der einzige
 * in Produktion gelesene Weg. v1/v2-Sidecars ergeben beim v3-Reader [MeterTrackV3.EMPTY] →
 * Stufe-1-Fallback (leeres Pflichtfeld, nie stilles 0.00).
 */

/** Aktuelle, in Produktion gelesene Sidecar-Version (Zeitachse). */
const val METER_SIDECAR_VERSION_V3 = 3

/** Ein Sample der v3-Spur: Medienzeit in Mikrosekunden (== gefütterte Encoder-PTS) + Meterwert. */
data class MeterSampleV3(val tUs: Long, val meter: Float)

/**
 * Eine geladene v3-Meter-Spur. [MeterTrackV3.EMPTY] = „keine gültige v3-Spur" (fehlt, Altversion
 * v1/v2, kaputt) → Wiedergabe fällt auf das leere Pflichtfeld (Stufe 1) zurück, NIE auf 0.00.
 */
data class MeterTrackV3(val samples: List<MeterSampleV3>) {
    companion object {
        val EMPTY = MeterTrackV3(emptyList())
    }

    /** Erwartete Videodauer aus der Spur: letzte PTS. 0 wenn leer. */
    fun expectedDurationMs(): Long =
        if (samples.isNotEmpty()) samples.last().tUs / 1000L else 0L
}

/**
 * Schlägt den Meterwert zur gegebenen Player-Position (ms Medienzeit) nach.
 *
 * - Leere Spur → null (Fallback auf leeres Pflichtfeld, Stufe 1).
 * - Position vor dem ersten Sample → erster Meterwert (kein Rückextrapolieren).
 * - Position nach dem letzten Sample → letzter Meterwert.
 * - Sonst: lineare Interpolation zwischen den zwei umgebenden Samples nach Zeit (tUs).
 *
 * Reine Funktion — nur Zeit, kein Frame-Index. Negative/UNSET-Positionen klemmen auf das erste Sample.
 */
fun lookupMeterV3(track: MeterTrackV3, positionMs: Long): Float? {
    val samples = track.samples
    if (samples.isEmpty()) return null
    val targetUs = positionMs * 1000L
    if (targetUs <= samples.first().tUs) return samples.first().meter
    if (targetUs >= samples.last().tUs) return samples.last().meter
    val afterIdx = samples.indexOfFirst { it.tUs >= targetUs }
    val s0 = samples[afterIdx - 1]
    val s1 = samples[afterIdx]
    if (s1.tUs == s0.tUs) return s0.meter
    val t = (targetUs - s0.tUs).toFloat() / (s1.tUs - s0.tUs).toFloat()
    return s0.meter + t * (s1.meter - s0.meter)
}

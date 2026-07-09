package com.uip.oneapp.network

/** Datei-Suffix der Meter-Sidecar-Datei neben jeder Aufnahme. */
const val METER_SIDECAR_SUFFIX = ".meter.jsonl"

/** Ein Messwert-Sample der Meter-Spur: Medienzeit in ms + Meterwert. */
data class MeterSample(val positionMs: Long, val meter: Float)

/**
 * Schlägt den Meterwert zur gegebenen Player-Position nach.
 *
 * - Leere Spur (Altaufnahmen ohne Sidecar) → null (Fallback auf leeres Pflichtfeld, Stufe 1).
 * - Position vor dem ersten Sample → erster Meterwert (kein Rückextrapolieren).
 * - Position nach dem letzten Sample → letzter Meterwert.
 * - Sonst: lineare Interpolation zwischen den zwei umgebenden Samples.
 *
 * Die Funktion ist stateless und rein testbar.
 */
fun lookupMeter(samples: List<MeterSample>, positionMs: Long): Float? {
    if (samples.isEmpty()) return null
    if (positionMs <= samples.first().positionMs) return samples.first().meter
    if (positionMs >= samples.last().positionMs) return samples.last().meter
    val afterIdx = samples.indexOfFirst { it.positionMs >= positionMs }
    val s0 = samples[afterIdx - 1]
    val s1 = samples[afterIdx]
    val t = (positionMs - s0.positionMs).toFloat() / (s1.positionMs - s0.positionMs).toFloat()
    return s0.meter + t * (s1.meter - s0.meter)
}

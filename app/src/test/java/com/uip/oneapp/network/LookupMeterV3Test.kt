package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Welle 5 — Zeitachsen-Meter-Spur (v3).
 *
 * Deckt ab: lookupMeterV3 (leer/clamp/exakt/Interpolation/Single/negativ), das v3-Reader-Parsing
 * (Kopf fehlt, v=1, v=2 → EMPTY; nur v=3 gültig) und die expectedDuration-Invariante. Spiegelt die
 * Garantien aus Welle 4b (LookupMeterTest), aber auf der echten Zeitachse statt Frame-Index.
 */
class LookupMeterV3Test {

    // --- lookupMeterV3: Grundfälle -------------------------------------------------------

    @Test
    fun empty_track_returns_null() {
        assertNull(lookupMeterV3(MeterTrackV3.EMPTY, 5000))
    }

    @Test
    fun single_sample_clamps_everywhere() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 5.0f)))
        assertEquals(5.0f, lookupMeterV3(t, 0))
        assertEquals(5.0f, lookupMeterV3(t, 9999))
    }

    @Test
    fun position_before_first_returns_first() {
        val t = MeterTrackV3(listOf(MeterSampleV3(1_000_000, 2.0f), MeterSampleV3(2_000_000, 4.0f)))
        // 0 ms = 0 µs < 1_000_000 µs → erster Wert.
        assertEquals(2.0f, lookupMeterV3(t, 0))
    }

    @Test
    fun position_after_last_returns_last() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 2.0f), MeterSampleV3(1_000_000, 4.0f)))
        assertEquals(4.0f, lookupMeterV3(t, 1000))   // exakt am letzten
        assertEquals(4.0f, lookupMeterV3(t, 99999))  // danach geklemmt
    }

    @Test
    fun exact_sample_hit() {
        val t = MeterTrackV3(
            listOf(MeterSampleV3(0, 1.0f), MeterSampleV3(1_000_000, 5.0f), MeterSampleV3(2_000_000, 9.0f))
        )
        // 1000 ms = 1_000_000 µs → exakt 5.0.
        assertEquals(5.0f, lookupMeterV3(t, 1000)!!, 0.001f)
    }

    @Test
    fun midpoint_interpolates() {
        // Samples bei 0 (m=0) und 2_000_000 µs (m=30). Position 1000 ms = 1_000_000 µs → Mitte → 15.
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 0.0f), MeterSampleV3(2_000_000, 30.0f)))
        assertEquals(15.0f, lookupMeterV3(t, 1000)!!, 0.001f)
    }

    @Test
    fun uneven_spacing_interpolates_by_time() {
        // Ungleiche Abstände (VFR): 0→3.0, 500ms→3.5, 2000ms→5.0. Bei 1250 ms zwischen 500 und 2000:
        // t = (1_250_000 - 500_000)/(2_000_000 - 500_000) = 0.5 → 3.5 + 0.5*(5.0-3.5) = 4.25.
        val t = MeterTrackV3(
            listOf(MeterSampleV3(0, 3.0f), MeterSampleV3(500_000, 3.5f), MeterSampleV3(2_000_000, 5.0f))
        )
        assertEquals(4.25f, lookupMeterV3(t, 1250)!!, 0.001f)
    }

    @Test
    fun negative_position_clamps_to_first() {
        // ExoPlayer.currentPosition kann im Fehlerzustand negativ/UNSET sein — nie crashen.
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 3.3f), MeterSampleV3(1_000_000, 5.0f)))
        assertEquals(3.3f, lookupMeterV3(t, -5000))
    }

    // --- expectedDuration ----------------------------------------------------------------

    @Test
    fun expectedDuration_is_last_tUs_in_ms() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 0f), MeterSampleV3(10_000_000, 5f)))
        assertEquals(10_000L, t.expectedDurationMs())
    }

    @Test
    fun expectedDuration_empty_is_zero() {
        assertEquals(0L, MeterTrackV3.EMPTY.expectedDurationMs())
    }

    // --- Reader: Version + Robustheit ----------------------------------------------------

    @Test
    fun reader_parses_valid_v3() {
        val lines = sequenceOf(
            "{\"v\":3}",
            "{\"tUs\":0,\"m\":0.00}",
            "{\"tUs\":1000000,\"m\":0.31}"
        )
        val t = MeterTrackReaderV3.parseLines(lines)
        assertEquals(2, t.samples.size)
        assertEquals(0L, t.samples[0].tUs)
        assertEquals(0.31f, t.samples[1].meter, 0.001f)
    }

    @Test
    fun reader_rejects_v2_frameindex() {
        // v2 (Frame-Index) NIE lesen — falsche Zeitbasis, falscher Wert schlimmer als keiner.
        val lines = sequenceOf("{\"v\":2,\"fps\":15}", "{\"f\":0,\"m\":0.0}", "{\"f\":15,\"m\":0.3}")
        assertEquals(MeterTrackV3.EMPTY, MeterTrackReaderV3.parseLines(lines))
    }

    @Test
    fun reader_rejects_v1_wallclock() {
        val lines = sequenceOf("{\"t\":0,\"m\":0.0}", "{\"t\":200,\"m\":0.3}")
        assertEquals(MeterTrackV3.EMPTY, MeterTrackReaderV3.parseLines(lines))
    }

    @Test
    fun reader_rejects_missing_header() {
        assertEquals(MeterTrackV3.EMPTY, MeterTrackReaderV3.parseLines(emptySequence()))
    }

    @Test
    fun reader_rejects_header_only_no_samples() {
        assertEquals(MeterTrackV3.EMPTY, MeterTrackReaderV3.parseLines(sequenceOf("{\"v\":3}")))
    }

    @Test
    fun reader_skips_broken_lines_and_sorts() {
        val lines = sequenceOf(
            "{\"v\":3}",
            "{\"tUs\":600000,\"m\":0.6}",
            "GARBAGE",
            "{\"tUs\":0,\"m\":0.0}",
            "{\"tUs\":300000,\"m\":0.3}"
        )
        val t = MeterTrackReaderV3.parseLines(lines)
        assertEquals(listOf(0L, 300000L, 600000L), t.samples.map { it.tUs })
    }
}

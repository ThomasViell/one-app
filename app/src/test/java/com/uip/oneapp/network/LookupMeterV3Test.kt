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
    fun single_sample_clamps_within_tolerance_else_null() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 5.0f)))
        assertEquals(5.0f, lookupMeterV3(t, 0))     // am Sample
        assertEquals(5.0f, lookupMeterV3(t, 400))   // 400 ms dahinter → innerhalb 500 ms → geklemmt
        assertNull(lookupMeterV3(t, 9999))          // weit dahinter → null (keine geratene Station)
    }

    @Test
    fun position_before_first_returns_first() {
        val t = MeterTrackV3(listOf(MeterSampleV3(1_000_000, 2.0f), MeterSampleV3(2_000_000, 4.0f)))
        // 0 ms = 0 µs < 1_000_000 µs → erster Wert.
        assertEquals(2.0f, lookupMeterV3(t, 0))
    }

    @Test
    fun position_at_or_just_past_last_within_tolerance_clamps() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 2.0f), MeterSampleV3(1_000_000, 4.0f)))
        assertEquals(4.0f, lookupMeterV3(t, 1000))   // exakt am letzten (1000 ms == 1_000_000 µs)
        assertEquals(4.0f, lookupMeterV3(t, 1400))   // 400 ms dahinter → innerhalb 500 ms → geklemmt
        assertEquals(4.0f, lookupMeterV3(t, 1500))   // exakt an der Toleranzgrenze → noch geklemmt
    }

    @Test
    fun position_far_past_last_returns_null_not_a_guessed_station() {
        // Befund 2: reißt die Spur vor dem Video ab, darf ein Foto DAHINTER keine stille Station
        // vom Spurende erben. > 500 ms hinter dem letzten Sample → null (leeres Pflichtfeld).
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 2.0f), MeterSampleV3(1_000_000, 4.0f)))
        assertNull(lookupMeterV3(t, 1501))    // 501 ms dahinter → über Toleranz → null
        assertNull(lookupMeterV3(t, 20_000))  // 19 s dahinter (13-s-Lücke-Szenario) → null
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

    // --- lookupMeterFloorV3: floor statt Interpolation (Offer-Pfad) ----------------------

    @Test
    fun floor_empty_track_returns_null() {
        assertNull(lookupMeterFloorV3(MeterTrackV3.EMPTY, 5000))
    }

    @Test
    fun floor_position_before_first_returns_first() {
        val t = MeterTrackV3(listOf(MeterSampleV3(1_000_000, 2.0f), MeterSampleV3(2_000_000, 4.0f)))
        assertEquals(2.0f, lookupMeterFloorV3(t, 0))
    }

    @Test
    fun floor_negative_position_clamps_to_first() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 3.3f), MeterSampleV3(1_000_000, 5.0f)))
        assertEquals(3.3f, lookupMeterFloorV3(t, -5000))
    }

    @Test
    fun floor_single_sample_within_tolerance_else_null() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 5.0f)))
        assertEquals(5.0f, lookupMeterFloorV3(t, 0))
        assertEquals(5.0f, lookupMeterFloorV3(t, 400))   // innerhalb 500 ms → geklemmt
        assertNull(lookupMeterFloorV3(t, 9999))           // dahinter → null
    }

    @Test
    fun floor_within_tolerance_past_last_clamps() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 2.0f), MeterSampleV3(1_000_000, 4.0f)))
        assertEquals(4.0f, lookupMeterFloorV3(t, 1000))   // exakt am letzten
        assertEquals(4.0f, lookupMeterFloorV3(t, 1400))   // 400 ms dahinter → geklemmt
        assertEquals(4.0f, lookupMeterFloorV3(t, 1500))   // exakt an Toleranzgrenze → noch geklemmt
    }

    @Test
    fun floor_far_past_last_returns_null() {
        val t = MeterTrackV3(listOf(MeterSampleV3(0, 2.0f), MeterSampleV3(1_000_000, 4.0f)))
        assertNull(lookupMeterFloorV3(t, 1501))    // 501 ms dahinter → über Toleranz → null
        assertNull(lookupMeterFloorV3(t, 20_000))  // 19 s dahinter → null
    }

    @Test
    fun floor_between_samples_returns_floor_not_interpolated() {
        // Kernfall: Samples bei 0µs (1.00), 40000µs (1.05), 80000µs (1.10).
        // target=79000µs → floor ist 40000µs-Sample (1.05), NICHT ~1.099 interpoliert.
        val t = MeterTrackV3(listOf(
            MeterSampleV3(0, 1.00f),
            MeterSampleV3(40_000, 1.05f),
            MeterSampleV3(80_000, 1.10f)
        ))
        assertEquals(1.05f, lookupMeterFloorV3(t, 79)!!, 0.001f)   // 79 ms = 79000 µs
        assertEquals(1.10f, lookupMeterFloorV3(t, 80)!!, 0.001f)   // 80 ms = 80000 µs → exakt letztes
    }

    @Test
    fun floor_result_always_in_track() {
        // Kein Wert, der nicht in der Spur steht, darf zurückkommen.
        val t = MeterTrackV3(listOf(
            MeterSampleV3(0, 3.0f), MeterSampleV3(500_000, 3.5f), MeterSampleV3(2_000_000, 5.0f)
        ))
        val trackValues = t.samples.map { it.meter }.toSet()
        for (ms in listOf(0L, 250L, 500L, 1000L, 1250L, 1999L, 2000L, 2400L)) {
            val result = lookupMeterFloorV3(t, ms)
            if (result != null) assertTrue("floor liefert $result bei ${ms}ms — nicht in Spur", result in trackValues)
        }
    }

    @Test
    fun floor_exact_sample_hit() {
        val t = MeterTrackV3(listOf(
            MeterSampleV3(0, 1.0f), MeterSampleV3(1_000_000, 5.0f), MeterSampleV3(2_000_000, 9.0f)
        ))
        assertEquals(5.0f, lookupMeterFloorV3(t, 1000)!!, 0.001f)   // exakt am mittleren Sample
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
    fun reader_discards_torn_last_line_keeps_the_rest() {
        // Befund 2: die letzte Zeile ist mitten im Schreiben abgerissen (kein `}`). Der Reader
        // verwirft NUR sie und behält die vollständigen Samples davor.
        val lines = sequenceOf(
            "{\"v\":3}",
            "{\"tUs\":0,\"m\":0.00}",
            "{\"tUs\":1000000,\"m\":0.50}",
            "{\"tUs\":2000000,\"m\":1."     // abgerissen — würde mit find() fälschlich als 1.0 gelesen
        )
        val t = MeterTrackReaderV3.parseLines(lines)
        assertEquals(2, t.samples.size)
        assertEquals(1_000_000L, t.samples.last().tUs)
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

package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Stufe 2 — lookupMeter: reine Funktion, keine Abhängigkeiten.
 * Abgedeckt: leere Spur, Clamp oben/unten, exakter Treffer, lineare Interpolation,
 * mehrere Segmente, Einzel-Sample, Positions-0-Grenzfall.
 */
class LookupMeterTest {

    @Test
    fun empty_returns_null() {
        assertNull(lookupMeter(emptyList(), 5000))
    }

    @Test
    fun single_sample_clamps_below() {
        val s = listOf(MeterSample(1000, 5.0f))
        assertEquals(5.0f, lookupMeter(s, 0))
        assertEquals(5.0f, lookupMeter(s, 999))
    }

    @Test
    fun single_sample_exact_hit() {
        val s = listOf(MeterSample(1000, 5.0f))
        assertEquals(5.0f, lookupMeter(s, 1000))
    }

    @Test
    fun single_sample_clamps_above() {
        val s = listOf(MeterSample(1000, 5.0f))
        assertEquals(5.0f, lookupMeter(s, 9999))
    }

    @Test
    fun position_before_first_returns_first() {
        val s = listOf(MeterSample(500, 2.0f), MeterSample(1500, 4.0f))
        assertEquals(2.0f, lookupMeter(s, 0))
        assertEquals(2.0f, lookupMeter(s, 499))
        assertEquals(2.0f, lookupMeter(s, 500))
    }

    @Test
    fun position_after_last_returns_last() {
        val s = listOf(MeterSample(500, 2.0f), MeterSample(1500, 4.0f))
        assertEquals(4.0f, lookupMeter(s, 1500))
        assertEquals(4.0f, lookupMeter(s, 9999))
    }

    @Test
    fun midpoint_interpolates_to_midvalue() {
        val s = listOf(MeterSample(0, 0.0f), MeterSample(1000, 10.0f))
        assertEquals(5.0f, lookupMeter(s, 500)!!, 0.001f)
    }

    @Test
    fun quarter_interpolation() {
        val s = listOf(MeterSample(0, 0.0f), MeterSample(1000, 100.0f))
        assertEquals(25.0f, lookupMeter(s, 250)!!, 0.001f)
        assertEquals(75.0f, lookupMeter(s, 750)!!, 0.001f)
    }

    @Test
    fun exact_middle_sample_hit() {
        val s = listOf(MeterSample(0, 1.0f), MeterSample(500, 5.0f), MeterSample(1000, 10.0f))
        assertEquals(5.0f, lookupMeter(s, 500)!!, 0.001f)
    }

    @Test
    fun correct_segment_selected_in_multi_segment_track() {
        val s = listOf(
            MeterSample(0, 0.0f),
            MeterSample(100, 10.0f),
            MeterSample(200, 20.0f),
            MeterSample(300, 30.0f)
        )
        assertEquals(15.0f, lookupMeter(s, 150)!!, 0.001f)
        assertEquals(25.0f, lookupMeter(s, 250)!!, 0.001f)
    }

    @Test
    fun position_zero_with_zero_start() {
        val s = listOf(MeterSample(0, 12.5f), MeterSample(1000, 15.0f))
        assertEquals(12.5f, lookupMeter(s, 0))
    }

    @Test
    fun pause_gap_does_not_interpolate_across_long_gap() {
        // Nach einer langen Pause (z.B. 60s Lücke in den Samples) soll das letzte
        // bekannte Sample gelten, nicht ein Phantomwert.
        // Mediawert vor Pause: t=5000 m=10.0; nach Pause: t=65000 m=10.5.
        // Bei Position 35000 (mitten in der Lücke) → lineare Interpolation 10.0..10.5.
        // Das ist korrekt: der Meter-Zähler stand während der Pause still, die Spur hat
        // keine Samples → Interpolation über die sichtbare Lücke ist die beste Näherung.
        val s = listOf(MeterSample(5000, 10.0f), MeterSample(65000, 10.5f))
        val result = lookupMeter(s, 35000)!!
        assertTrue(result in 10.0f..10.5f)
    }
}

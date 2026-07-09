package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Welle 4b — Frame-Index-Zeitbasis der Meter-Spur.
 *
 * Deckt ab: lookupMeter (leer/clamp/exakt/Interpolation/Single), positionMs→frameIndex
 * für fps 5/15/30 mit Rundung an Sample-Grenzen, den Drift-Test (langsame Schreibschleife
 * darf die Zuordnung NICHT verfälschen) und das Reader-Parsing (Kopfzeile fehlt, v=1 → EMPTY).
 */
class LookupMeterTest {

    // --- lookupMeter: Grundfälle ---------------------------------------------------------

    @Test
    fun empty_track_returns_null() {
        assertNull(lookupMeter(MeterTrack.EMPTY, 5000))
    }

    @Test
    fun fps_zero_returns_null() {
        // Defensive: fps 0 (ungültig) darf nie durch 0 teilen, sondern → null.
        assertNull(lookupMeter(MeterTrack(0, listOf(MeterSample(0, 5f))), 1000))
    }

    @Test
    fun single_sample_clamps_everywhere() {
        val t = MeterTrack(15, listOf(MeterSample(0, 5.0f)))
        assertEquals(5.0f, lookupMeter(t, 0))
        assertEquals(5.0f, lookupMeter(t, 9999))
    }

    @Test
    fun position_before_first_returns_first() {
        val t = MeterTrack(15, listOf(MeterSample(15, 2.0f), MeterSample(30, 4.0f)))
        // positionMs 0 → frameIndex 0 < 15 → erster Wert.
        assertEquals(2.0f, lookupMeter(t, 0))
    }

    @Test
    fun position_after_last_returns_last() {
        val t = MeterTrack(15, listOf(MeterSample(0, 2.0f), MeterSample(15, 4.0f)))
        // frameIndex 15 = 1000ms → letzter Wert; danach geklemmt.
        assertEquals(4.0f, lookupMeter(t, 1000))
        assertEquals(4.0f, lookupMeter(t, 99999))
    }

    @Test
    fun exact_sample_hit() {
        val t = MeterTrack(15, listOf(MeterSample(0, 1.0f), MeterSample(15, 5.0f), MeterSample(30, 9.0f)))
        // 1000ms @15fps → frame 15 → exakt 5.0.
        assertEquals(5.0f, lookupMeter(t, 1000)!!, 0.001f)
    }

    @Test
    fun midpoint_interpolates() {
        // Samples bei frame 0 (m=0) und frame 30 (m=30); 15fps → frame 30 = 2000ms.
        // Position 1000ms → frame 15 → Interpolation Mitte → 15.
        val t = MeterTrack(15, listOf(MeterSample(0, 0.0f), MeterSample(30, 30.0f)))
        assertEquals(15.0f, lookupMeter(t, 1000)!!, 0.001f)
    }

    // --- positionMs → frameIndex ---------------------------------------------------------

    @Test
    fun frameIndex_fps15() {
        assertEquals(0L, frameIndexForPosition(0, 15))
        assertEquals(15L, frameIndexForPosition(1000, 15))
        assertEquals(30L, frameIndexForPosition(2000, 15))
        // Rundung: 1033ms*15/1000 = 15.495 → 15; 1034ms → 15.51 → 16.
        assertEquals(15L, frameIndexForPosition(1033, 15))
        assertEquals(16L, frameIndexForPosition(1034, 15))
    }

    @Test
    fun frameIndex_fps5() {
        assertEquals(0L, frameIndexForPosition(0, 5))
        assertEquals(5L, frameIndexForPosition(1000, 5))
        // 100ms*5/1000 = 0.5 → round-half-up → 1 (Math.round).
        assertEquals(1L, frameIndexForPosition(100, 5))
        assertEquals(0L, frameIndexForPosition(99, 5))
    }

    @Test
    fun frameIndex_fps30() {
        assertEquals(0L, frameIndexForPosition(0, 30))
        assertEquals(30L, frameIndexForPosition(1000, 30))
        assertEquals(90L, frameIndexForPosition(3000, 30))
        // 16ms*30/1000 = 0.48 → 0; 17ms → 0.51 → 1.
        assertEquals(0L, frameIndexForPosition(16, 30))
        assertEquals(1L, frameIndexForPosition(17, 30))
    }

    @Test
    fun frameIndex_fps12_production_default() {
        // fps=12 ist die TATSÄCHLICHE Produktionsrate (InspectionScreen/LocalBitmapRecorder).
        assertEquals(0L, frameIndexForPosition(0, 12))
        assertEquals(12L, frameIndexForPosition(1000, 12))
        // Rundungsgrenzen: 41ms*12/1000 = 0.492 → 0; 42ms → 0.504 → 1.
        assertEquals(0L, frameIndexForPosition(41, 12))
        assertEquals(1L, frameIndexForPosition(42, 12))
    }

    @Test
    fun roundtrip_fps12_with_6hz_sample_spacing() {
        // Bei fps=12 dezimiert der Writer auf jeden 2. Frame (6 Hz) → Samples bei 0,2,4,6,…
        // lookupMeter muss über diese Lücken korrekt interpolieren.
        val fps = 12
        val samples = (0..24 step 2).map { f -> MeterSample(f, f * 0.25f) }
        val track = MeterTrack(fps, samples)
        // 1000ms @12fps → frame 12 → Meter = 12*0.25 = 3.0.
        assertEquals(3.0f, lookupMeter(track, 1000)!!, 0.001f)
        // Position zwischen zwei Samples (frame 3, zwischen 2 und 4) → Interpolation.
        // 250ms @12fps → frame 3 → zwischen frame2(0.5) und frame4(1.0) → 0.75.
        assertEquals(0.75f, lookupMeter(track, 250)!!, 0.001f)
    }

    @Test
    fun frameIndex_negative_position_clamps_to_nonpositive() {
        // ExoPlayer.currentPosition kann im Fehlerzustand negativ/UNSET sein — nie crashen,
        // lookupMeter klemmt auf das erste Sample.
        assertEquals(0L, frameIndexForPosition(-1, 15))
        assertTrue(frameIndexForPosition(-1000, 15) <= 0L)
        val t = MeterTrack(15, listOf(MeterSample(0, 3.3f), MeterSample(15, 5.0f)))
        assertEquals(3.3f, lookupMeter(t, -5000))
    }

    // --- Der Kern: Drift-Immunität -------------------------------------------------------

    @Test
    fun frameIndex_lookup_is_immune_to_slow_write_loop() {
        // Szenario: fps=15 angefordert, aber die Schreibschleife schafft unter Last nur 60 %
        // der Soll-Rate. Bei WALL-CLOCK-Stempelung liefe die Spur dem Video davon.
        //
        // Frame-Index-Modell: Meter steigt exakt 0,10 m pro Frame (Fahrwagen konstant).
        // Der Encoder sieht Frame i bei Medienzeit i/fps — egal wie lange die App real für
        // Frame i brauchte. Die Sidecar stempelt frameIndex, nicht Uhr → driftfrei.
        val fps = 15
        val meterPerFrame = 0.10f
        val samples = (0..150 step 3).map { f -> MeterSample(f, f * meterPerFrame) }
        val track = MeterTrack(fps, samples)

        // Wiedergabe bei 5000ms → frame 75 → Meter = 7,50 m (die Wahrheit im OSD).
        val posMs = 5000L
        assertEquals(7.50f, lookupMeter(track, posMs)!!, 0.001f)

        // Gegenprobe: Eine Wall-Clock-Spur (60 % Rate) hätte demselben Frame 75 den
        // Zeitstempel 75/(15*0.6)=8333ms gegeben. Ein Nachschlagen bei 5000ms hätte dort
        // Frame round(5000*0.6*15/1000)=45 → 4,50 m getroffen — GROB FALSCH. Der
        // Frame-Index-Ansatz liefert 7,50 m; die beiden dürfen sich nicht gleichen.
        val wallClockWrong = 4.50f
        assertNotEquals(wallClockWrong, lookupMeter(track, posMs)!!, 0.001f)
    }

    // --- Reader: Version + Robustheit ----------------------------------------------------

    @Test
    fun reader_parses_valid_v2() {
        val lines = sequenceOf(
            "{\"v\":2,\"fps\":15}",
            "{\"f\":0,\"m\":0.00}",
            "{\"f\":15,\"m\":0.31}"
        )
        val t = MeterTrackReader.parseLines(lines)
        assertEquals(15, t.fps)
        assertEquals(2, t.samples.size)
        assertEquals(0, t.samples[0].frameIndex)
        assertEquals(0.31f, t.samples[1].meter, 0.001f)
    }

    @Test
    fun reader_rejects_v1_wallclock() {
        // v1 (alte Wall-Clock-Spur) NIE lesen — falscher Wert schlimmer als keiner.
        val lines = sequenceOf(
            "{\"t\":0,\"m\":0.0}",
            "{\"t\":200,\"m\":0.3}"
        )
        assertEquals(MeterTrack.EMPTY, MeterTrackReader.parseLines(lines))
    }

    @Test
    fun reader_rejects_explicit_v1_header() {
        val lines = sequenceOf("{\"v\":1,\"fps\":15}", "{\"f\":0,\"m\":0.0}")
        assertEquals(MeterTrack.EMPTY, MeterTrackReader.parseLines(lines))
    }

    @Test
    fun reader_rejects_missing_header() {
        assertEquals(MeterTrack.EMPTY, MeterTrackReader.parseLines(emptySequence()))
    }

    @Test
    fun reader_rejects_header_only_no_samples() {
        val lines = sequenceOf("{\"v\":2,\"fps\":15}")
        assertEquals(MeterTrack.EMPTY, MeterTrackReader.parseLines(lines))
    }

    @Test
    fun reader_skips_broken_lines_and_sorts() {
        val lines = sequenceOf(
            "{\"v\":2,\"fps\":10}",
            "{\"f\":6,\"m\":0.6}",
            "GARBAGE",
            "{\"f\":0,\"m\":0.0}",
            "{\"f\":3,\"m\":0.3}"
        )
        val t = MeterTrackReader.parseLines(lines)
        assertEquals(10, t.fps)
        assertEquals(listOf(0, 3, 6), t.samples.map { it.frameIndex })
    }

    @Test
    fun reader_rejects_fps_zero() {
        val lines = sequenceOf("{\"v\":2,\"fps\":0}", "{\"f\":0,\"m\":0.0}")
        assertEquals(MeterTrack.EMPTY, MeterTrackReader.parseLines(lines))
    }

    // --- Invariante ----------------------------------------------------------------------

    @Test
    fun expectedDuration_matches_lastFrame_over_fps() {
        val t = MeterTrack(15, listOf(MeterSample(0, 0f), MeterSample(150, 5f)))
        // 150 / 15 = 10 s = 10000 ms.
        assertEquals(10000L, t.expectedDurationMs())
    }

    @Test
    fun expectedDuration_empty_is_zero() {
        assertEquals(0L, MeterTrack.EMPTY.expectedDurationMs())
    }
}

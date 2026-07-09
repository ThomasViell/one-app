package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Welle 5 — Monotonie-Garantie für MediaMuxer (ADR 0002 B3). MediaMuxer verlangt streng steigende
 * PTS; µs-Rundung + Scheduler-Jitter können gleiche/fallende PTS erzeugen → hier abgefangen.
 */
class RecorderJournalMuxerTest {

    @Test
    fun first_sample_uses_candidate_clamped_nonnegative() {
        assertEquals(0L, RecorderJournalMuxer.monotonicPtsUs(0L, null))
        assertEquals(1234L, RecorderJournalMuxer.monotonicPtsUs(1234L, null))
        assertEquals(0L, RecorderJournalMuxer.monotonicPtsUs(-5L, null))
    }

    @Test
    fun strictly_increasing_passes_through() {
        assertEquals(40_000L, RecorderJournalMuxer.monotonicPtsUs(40_000L, 0L))
        assertEquals(80_000L, RecorderJournalMuxer.monotonicPtsUs(80_000L, 40_000L))
    }

    @Test
    fun equal_pts_is_bumped_by_one() {
        assertEquals(40_001L, RecorderJournalMuxer.monotonicPtsUs(40_000L, 40_000L))
    }

    @Test
    fun decreasing_pts_is_bumped_to_last_plus_one() {
        assertEquals(40_001L, RecorderJournalMuxer.monotonicPtsUs(39_000L, 40_000L))
    }
}

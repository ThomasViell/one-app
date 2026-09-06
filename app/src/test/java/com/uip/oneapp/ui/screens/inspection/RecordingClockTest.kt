package com.uip.oneapp.ui.screens.inspection

import com.uip.oneapp.network.RecordingState
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingClockTest {

    @Test
    fun `formatElapsed formatiert die fuenf Werte aus dem Auftrag`() {
        assertEquals("00:00", formatElapsed(0L))
        assertEquals("00:59", formatElapsed(59_000L))
        assertEquals("59:59", formatElapsed(3_599_000L))
        assertEquals("1:00:00", formatElapsed(3_600_000L))
        assertEquals("1:01:01", formatElapsed(3_661_000L))
    }

    @Test
    fun `Uhr zaehlt nur waehrend RECORDING, Pause haelt an, weiter zaehlen addiert`() {
        var t = 0L
        val clock = RecordingClock(now = { t })

        clock.onState(RecordingState.RECORDING)
        t += 10_000
        clock.onState(RecordingState.PAUSED)
        assertEquals(10_000L, clock.elapsedMs())

        t += 20_000 // waehrend PAUSED vergangene Zeit zaehlt nicht
        assertEquals(10_000L, clock.elapsedMs())

        clock.onState(RecordingState.RECORDING)
        t += 5_000
        assertEquals(15_000L, clock.elapsedMs())
    }

    @Test
    fun `IDLE setzt die Uhr auf 0 zurueck`() {
        var t = 0L
        val clock = RecordingClock(now = { t })
        clock.onState(RecordingState.RECORDING)
        t += 7_000
        clock.onState(RecordingState.IDLE)
        assertEquals(0L, clock.elapsedMs())
    }

    @Test
    fun `FINISHING friert den erreichten Wert ein`() {
        var t = 0L
        val clock = RecordingClock(now = { t })
        clock.onState(RecordingState.RECORDING)
        t += 12_000
        clock.onState(RecordingState.FINISHING)
        t += 30_000
        assertEquals(12_000L, clock.elapsedMs())
    }
}

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

    // Nachbesserung Runde 2, N-1/N-2: Verdrahtungstest. Prueft nicht die Klasse (die ist
    // richtig), sondern die Abbildung von RecordingState/isRecording auf die Uhr, so wie
    // InspectionScreen sie ueber `recordingStateFor` + `LaunchedEffect(actualRecState) {
    // recordingClock.onState(actualRecState) }` vornimmt (onState wird jetzt IMMER gerufen,
    // auch mit IDLE — vorher nur bei sichtbarer Zeile). War vor der Behebung ROT (Nachbau der
    // alten `recIndicator?.let{...}`-Verdrahtung, die IDLE nie durchliess); Rohausgaben in
    // belege/r2_n1n2_test_rot.txt (rot) und belege/r2_n1n2_test_gruen.txt (gruen).
    private fun feedCurrentWiring(clock: RecordingClock, localState: RecordingState, isRecording: Boolean) {
        clock.onState(recordingStateFor(localState, isRecording))
    }

    @Test
    fun `Lokaler Pfad -- zweite Aufnahme zeigt eigene Laufzeit, nicht die addierte`() {
        var t = 0L
        val clock = RecordingClock(now = { t })

        // Erste Aufnahme: 3 s, dann Stop (localRecorder faellt auf IDLE zurueck, isRecording=false).
        feedCurrentWiring(clock, RecordingState.RECORDING, isRecording = true)
        t += 3_000
        feedCurrentWiring(clock, RecordingState.IDLE, isRecording = false)

        // Zweite Aufnahme startet bei t=10s (Bediener braucht etwas Zeit zwischen den Haltungen).
        t += 7_000
        feedCurrentWiring(clock, RecordingState.RECORDING, isRecording = true)
        t += 3_000

        assertEquals("zweite Aufnahme muss nach 3s wieder 00:03 zeigen, nicht 00:33 (addiert)",
            3_000L, clock.elapsedMs())
    }

    @Test
    fun `RTSP-Pfad -- Uebergang true zu false ohne FINISHING setzt trotzdem zurueck`() {
        var t = 0L
        val clock = RecordingClock(now = { t })

        // RTSP-Pfad kennt kein FINISHING/PAUSED, nur isRecording an/aus; localState bleibt IDLE.
        feedCurrentWiring(clock, RecordingState.IDLE, isRecording = true)
        t += 3_000
        feedCurrentWiring(clock, RecordingState.IDLE, isRecording = false)

        t += 7_000
        feedCurrentWiring(clock, RecordingState.IDLE, isRecording = true)
        t += 3_000

        assertEquals("RTSP-Pfad: zweite Aufnahme muss nach 3s wieder 00:03 zeigen, nicht 02:33",
            3_000L, clock.elapsedMs())
    }
}

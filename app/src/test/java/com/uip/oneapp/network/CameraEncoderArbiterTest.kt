package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Welle 5 — Ein-Encoder-Ausschluss: Handshake zwischen Recorder und OneVideoServer.
 * Deckt ab: kein RTSP präsent → sofort frei; RTSP präsent + freigegeben → frei; RTSP präsent, gibt
 * spät frei → wartet dann frei; RTSP präsent, gibt nie frei → Timeout=false; release() senkt Flag.
 */
class CameraEncoderArbiterTest {

    @Test
    fun no_rtsp_present_acquires_immediately() {
        val a = CameraEncoderArbiter()
        var slept = 0L
        val ok = a.acquireForRecording(2000, 20, { 0L }, { slept += it })
        assertTrue(ok)
        assertEquals("darf nicht warten", 0L, slept)
        assertTrue(a.isRecordingActive)
    }

    @Test
    fun rtsp_present_and_already_released_acquires_immediately() {
        val a = CameraEncoderArbiter()
        a.setRtspPresent(true)
        a.setRtspEncoderReleased(true)
        var slept = 0L
        val ok = a.acquireForRecording(2000, 20, { 0L }, { slept += it })
        assertTrue(ok)
        assertEquals(0L, slept)
    }

    @Test
    fun rtsp_present_releases_after_a_few_polls() {
        val a = CameraEncoderArbiter()
        a.setRtspPresent(true)
        a.setRtspEncoderReleased(false)  // hält den Codec zunächst
        var now = 0L
        var polls = 0
        val ok = a.acquireForRecording(
            timeoutMs = 2000,
            pollMs = 20,
            nowMs = { now },
            sleep = {
                now += it
                polls++
                if (polls == 3) a.setRtspEncoderReleased(true)  // gibt nach 3 Polls frei
            },
        )
        assertTrue(ok)
        assertEquals(3, polls)
    }

    @Test
    fun rtsp_present_never_releases_times_out_false() {
        val a = CameraEncoderArbiter()
        a.setRtspPresent(true)
        a.setRtspEncoderReleased(false)
        var now = 0L
        val ok = a.acquireForRecording(100, 20, { now }, { now += it })
        assertFalse(ok)
        assertTrue("Recording-Flag bleibt gesetzt (Recorder entscheidet über Abbruch)", a.isRecordingActive)
    }

    @Test
    fun release_clears_recording_flag() {
        val a = CameraEncoderArbiter()
        a.acquireForRecording(2000, 20, { 0L }, {})
        assertTrue(a.isRecordingActive)
        a.release()
        assertFalse(a.isRecordingActive)
    }

    @Test
    fun deregister_marks_released() {
        val a = CameraEncoderArbiter()
        a.setRtspPresent(true)
        a.setRtspEncoderReleased(false)
        a.setRtspPresent(false)  // OneVideoServer stoppt → gilt als freigegeben
        val ok = a.acquireForRecording(2000, 20, { 0L }, {})
        assertTrue(ok)
    }
}

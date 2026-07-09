package com.uip.oneapp.network

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Welle 5 — Zustands-Guards des HW-Recorders (ohne echten MediaCodec; nur die Übergänge).
 * Der eigentliche Encode-/Mux-/Kill-Pfad ist nur am Gerät verifizierbar (Geräte-Abnahme).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class HardwareBitmapRecorderStateTest {

    private fun recorder() =
        HardwareBitmapRecorder(ApplicationProvider.getApplicationContext(), CameraEncoderArbiter())

    @Test
    fun `pause is a no-op when idle`() {
        val r = recorder()
        r.pause()
        assertEquals(RecordingState.IDLE, r.state.value)
        assertFalse(r.isRecording)
        assertFalse(r.isPaused)
    }

    @Test
    fun `resume is a no-op when idle`() {
        val r = recorder()
        r.resume()
        assertEquals(RecordingState.IDLE, r.state.value)
        assertFalse(r.isPaused)
    }

    @Test
    fun `stop from idle reports null without state change`() {
        val r = recorder()
        var result: String? = "sentinel"
        r.stop { result = it }
        assertEquals(null, result)
        assertEquals(RecordingState.IDLE, r.state.value)
    }

    @Test
    fun `start returns false when no frame available`() {
        val r = recorder()
        // frameFlow.value == null → Abbruch vor Encoder/Arbiter (kein MediaCodec nötig).
        val started = r.start("/tmp/does_not_matter.mp4", MutableStateFlow(null))
        assertFalse(started)
        assertEquals(RecordingState.IDLE, r.state.value)
    }
}

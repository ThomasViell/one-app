package com.uip.oneapp.network

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pause/Fortsetzen (CEO-Beschluss 2026-06-07): Zustands-Guards der Aufnahme.
 * Der eigentliche Pause-Effekt (Frames werden nicht geschrieben, eine durchgehende
 * MP4) ist nur am Gerät verifizierbar — hier sind die Übergänge abgesichert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class LocalBitmapRecorderStateTest {

    private fun recorder() = LocalBitmapRecorder(ApplicationProvider.getApplicationContext())

    @Test
    fun `pause is a no-op when idle`() {
        val r = recorder()
        r.pause()
        assertEquals(LocalBitmapRecorder.State.IDLE, r.state.value)
        assertFalse(r.isRecording)
        assertFalse(r.isPaused)
    }

    @Test
    fun `resume is a no-op when idle`() {
        val r = recorder()
        r.resume()
        assertEquals(LocalBitmapRecorder.State.IDLE, r.state.value)
        assertFalse(r.isPaused)
    }

    @Test
    fun `stop from idle reports null without state change`() {
        val r = recorder()
        var result: String? = "sentinel"
        r.stop { result = it }
        assertEquals(null, result)
        assertEquals(LocalBitmapRecorder.State.IDLE, r.state.value)
    }
}

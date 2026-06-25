package com.uip.oneapp.network.internal

import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sichert den V4L2-Frame-Fan-out ([CameraFrameBus], Dual-Modus W3d-Video) ab — **ohne native
 * V4L2-Kamera** (Fake-[FrameSource], kein `System.loadLibrary`):
 *  - der Bus reicht **denselben** Frame-/State-Flow weiter (keine Kopie/Transformation → der
 *    lokale Anzeigepfad bleibt bit-identisch),
 *  - eine Quelle bedient **mehrere** parallele Konsumenten (Fan-out),
 *  - Lebenszyklus delegiert genau einmal an die Quelle und ist idempotent (verhindert
 *    doppeltes `open(/dev/video0)`).
 *
 * Robolectric nur, um echte [Bitmap]s erzeugen zu können; die Bus-Logik selbst ist reines Kotlin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = android.app.Application::class)
class CameraFrameBusTest {

    /** Aufzeichnende Fake-Quelle — zählt start/stop, erlaubt Frame-/State-Emission. */
    private class FakeFrameSource : FrameSource {
        private val _frame = MutableStateFlow<Bitmap?>(null)
        override val frame: StateFlow<Bitmap?> = _frame
        private val _state = MutableStateFlow(V4L2State())
        override val state: StateFlow<V4L2State> = _state

        var startCount = 0; private set
        var stopCount = 0; private set

        override fun start() {
            startCount++
            _state.value = _state.value.copy(open = true, streaming = true)
        }

        override fun stop() {
            stopCount++
            _state.value = _state.value.copy(open = false, streaming = false)
        }

        fun emit(bm: Bitmap?) { _frame.value = bm }
        fun emitState(s: V4L2State) { _state.value = s }
    }

    private fun bus(fake: FakeFrameSource = FakeFrameSource()) = CameraFrameBus(fake) to fake

    // ── Frame-Identität: lokaler Pfad unverändert ──────────────────────────────

    @Test
    fun `frames and state are the very same flow as the source`() {
        val (b, fake) = bus()
        // Identität (nicht nur Gleichheit): jeder Konsument liest exakt die Quell-StateFlow,
        // es gibt keine zwischengeschaltete Kopie, die Frames droppen oder umformen könnte.
        assertSame(fake.frame, b.frames)
        assertSame(fake.state, b.state)
    }

    @Test
    fun `frames reflects the latest value pushed by the source`() {
        val (b, fake) = bus()
        assertEquals(null, b.frames.value)
        val bm = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565)
        fake.emit(bm)
        assertSame(bm, b.frames.value)
    }

    // ── Fan-out: eine Quelle, mehrere Konsumenten ──────────────────────────────

    @Test
    fun `one source serves multiple concurrent consumers`() = runTest {
        val (b, fake) = bus()
        val seenA = mutableListOf<Bitmap?>()
        val seenB = mutableListOf<Bitmap?>()
        val jobA = launch(UnconfinedTestDispatcher(testScheduler)) { b.frames.collect { seenA += it } }
        val jobB = launch(UnconfinedTestDispatcher(testScheduler)) { b.frames.collect { seenB += it } }

        val f1 = Bitmap.createBitmap(4, 4, Bitmap.Config.RGB_565)
        val f2 = Bitmap.createBitmap(4, 4, Bitmap.Config.RGB_565)
        fake.emit(f1); runCurrent()
        fake.emit(f2); runCurrent()

        // Beide Konsumenten sehen denselben aktuellen Frame aus der EINEN Quelle.
        assertSame(f2, seenA.last())
        assertSame(f2, seenB.last())
        jobA.cancel(); jobB.cancel()
    }

    @Test
    fun `state changes propagate through the bus`() {
        val (b, fake) = bus()
        assertFalse(b.state.value.open)
        fake.emitState(V4L2State(open = true, streaming = true, frameCount = 30))
        assertTrue(b.state.value.open)
        assertEquals(30L, b.state.value.frameCount)
    }

    // ── Lebenszyklus: ein Owner, ein Geräte-Open ───────────────────────────────

    @Test
    fun `start delegates to source exactly once and is idempotent`() {
        val (b, fake) = bus()
        assertFalse(b.isRunning)
        b.start()
        b.start() // zweiter Start ohne Stop = No-op (kein zweites open(/dev/video0))
        assertEquals(1, fake.startCount)
        assertTrue(b.isRunning)
    }

    @Test
    fun `stop is a no-op when never started`() {
        val (b, fake) = bus()
        b.stop()
        assertEquals(0, fake.stopCount)
        assertFalse(b.isRunning)
    }

    @Test
    fun `stop delegates once and a second stop is a no-op`() {
        val (b, fake) = bus()
        b.start()
        b.stop()
        b.stop()
        assertEquals(1, fake.stopCount)
        assertFalse(b.isRunning)
    }

    @Test
    fun `bus can be restarted after stop`() {
        val (b, fake) = bus()
        b.start(); b.stop(); b.start()
        assertEquals(2, fake.startCount)
        assertEquals(1, fake.stopCount)
        assertTrue(b.isRunning)
    }
}

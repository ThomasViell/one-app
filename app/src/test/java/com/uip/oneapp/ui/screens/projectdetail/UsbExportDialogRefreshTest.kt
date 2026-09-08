package com.uip.oneapp.ui.screens.projectdetail

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.export.UsbExportService
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * N-2 (Runde 2): Der remember-Schluessel der Export-Dateiliste muss neue
 * damages/notes einschliessen. Vorher hing er nur an project.id — kamen bei
 * offenem Dialog Zeilen dazu (die Flows starten mit emptyList und liefern
 * die Datenbankzeilen kurz nach dem Oeffnen), zeigte und exportierte der
 * Dialog veraltete Namen: jede Datei fiel in den Waisen-Zweig und verlor
 * Position und Schadensart.
 *
 * Der Test treibt die Komposition headless (Recomposer ohne View, kein
 * UI-Knoten, NoopApplier) und zaehlt, wie oft der Sammeleffekt laeuft:
 * Aendern sich damages oder notes bei gleicher Projekt-Id, muss die Liste
 * neu gebaut werden. Robolectric deshalb, weil compose-runtime beim Dispose
 * android.os.Trace anfasst — auf reinem JVM eine Stub-Falle.
 *
 * Maschinerie — alles gemessen (Runde 2):
 * - Der Frame-Loop verlangt eine MonotonicFrameClock im Kontext der Coroutine,
 *   die runRecomposeAndApplyChanges aufruft (Recomposer.kt:1017 wirft sonst
 *   "A MonotonicFrameClock is not available in this CoroutineContext"). Auf dem
 *   JVM gibt es keinen Choreographer, also haelt eine ImmediateFrameClock den
 *   Platz: sie liefert jeden Frame sofort.
 * - Ein Schreibzugriff auf einen Snapshot-State von aussen weckt den Recomposer
 *   NICHT von selbst (gemessen: State blieb Idle); erst
 *   Snapshot.sendApplyNotifications() feuert den Apply-Observer
 *   (State = PendingWork) und der Frame recomponiert.
 * - Der Runner laeuft in einem eigenen Scope auf Dispatchers.Default: als Kind
 *   der runBlocking-Coroutine kam der Test nie zu Ende (gemessen — die
 *   BlockingCoroutine wartet auf ihre Kinder, und der gekuendigte Runner
 *   meldete sich nie fertig).
 * - Statt withTimeout begrenzte Pump-Schleifen (Thread.sleep(10), hoechstens
 *   500 Runden): withTimeout feuerte unter Robolectric nicht (gemessen).
 * - Vor dem Schreibzugriff pumpt der Test, bis der Runner seinen Start-Frame
 *   gefahren hat (Frame-Zaehler >= 1 und State == Idle): erst dann ist sein
 *   Apply-Observer registriert (Reihenfolge im Recomposer-Quelltext:
 *   registerRunnerJob, dann registerApplyObserver, dann Start-Frame).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UsbExportDialogRefreshTest {

    /** Liefert jeden Frame sofort; zaehlt Frames fuer die Bereitschafts-Pumpe. */
    private class ImmediateFrameClock : MonotonicFrameClock {
        val frames = AtomicInteger()
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frames.incrementAndGet()
            return onFrame(System.nanoTime())
        }
    }

    /** Applier ohne UI — es wird nur ein gemerkter Listenwert komponiert. */
    private class NoopApplier : AbstractApplier<Any>(Any()) {
        override fun insertTopDown(index: Int, instance: Any) {}
        override fun insertBottomUp(index: Int, instance: Any) {}
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun onClear() {}
    }

    private class Fixture {
        val project = ProjectEntity(id = 42, projectNumber = "REF0904-H1")
        val damages = mutableStateOf<List<DamageEntity>>(emptyList())
        val notes = mutableStateOf<List<NoteEntity>>(emptyList())
        val collectCalls = AtomicInteger()
        val collect: () -> List<UsbExportService.ExportFile> = {
            collectCalls.incrementAndGet()
            emptyList()
        }
    }

    /**
     * Baut die headless Komposition auf, pumpt sie bis zur Bereitschaft des
     * Runners und uebergibt die Fiktion an den Block. Raeumt danach ab.
     */
    private fun withComposition(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        val clock = ImmediateFrameClock()
        val runnerError = AtomicReference<Throwable?>(null)
        val handler = CoroutineExceptionHandler { _, e -> runnerError.set(e) }
        val runnerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoopApplier(), recomposer)
        // LAZY gestartet und erst NACH setContent angeworfen: die Erstkomposition
        // laeuft synchron im setContent-Fast-Path (gemessen), und der Runner sieht
        // beim Start garantiert die registrierte Komposition — sein Start-Frame
        // (invalidateAll beim Start) laeuft damit immer und der Frame-Zaehler
        // wird zum verlaesslichen Bereitschafts-Signal.
        val runnerJob = runnerScope.launch(start = CoroutineStart.LAZY, context = clock + handler) {
            recomposer.runRecomposeAndApplyChanges()
        }
        try {
            composition.setContent {
                rememberExportFiles(
                    fixture.project, fixture.damages.value, fixture.notes.value, fixture.collect
                )
            }
            runnerJob.start()
            // Bereitschaft: Runner hat seinen Start-Frame gefahren (und damit
            // seinen Apply-Observer registriert) und meldet Idle.
            var spins = 0
            while (!(clock.frames.get() >= 1 &&
                    recomposer.currentState.value == Recomposer.State.Idle) && spins < 500) {
                Thread.sleep(10)
                spins++
            }
            check(clock.frames.get() >= 1 && recomposer.currentState.value == Recomposer.State.Idle) {
                "Runner wurde nicht bereit (frames=${clock.frames.get()}, " +
                    "state=${recomposer.currentState.value}, spins=$spins)"
            }
            block(fixture)
            runnerError.get()?.let { throw AssertionError("Runner starb: $it") }
        } finally {
            composition.dispose()
            runnerScope.cancel()
        }
    }

    /** Pumpt, bis [ready] gilt, hoechstens 500 x 10 ms — der Aufrufer assertet. */
    private fun pump(ready: () -> Boolean) {
        var spins = 0
        while (!ready() && spins < 500) {
            Thread.sleep(10)
            spins++
        }
    }

    @Test
    fun neueDamagesBeiGleichemProjekt_bauenDateilisteNeu() = withComposition { fixture ->
        assertEquals("Erstkomposition muss einmal sammeln", 1, fixture.collectCalls.get())

        // Schreibzugriff von aussen: Write + sendApplyNotifications (der Write
        // allein weckt den Recomposer nicht — gemessen, State blieb Idle).
        fixture.damages.value = listOf(
            DamageEntity(projectId = 42, position = 4.1f, damageType = "Riss")
        )
        Snapshot.sendApplyNotifications()

        pump({ fixture.collectCalls.get() >= 2 })
        assertEquals(
            "Neue Schadenszeile bei gleicher Projekt-Id muss die Dateiliste neu bauen " +
                "(ein remember-Schluessel ohne damages laesst die neue Eingabe aus)",
            2,
            fixture.collectCalls.get()
        )
    }

    @Test
    fun neueNotizBeiGleichemProjekt_bautDateilisteNeu() = withComposition { fixture ->
        assertEquals("Erstkomposition muss einmal sammeln", 1, fixture.collectCalls.get())

        fixture.notes.value = listOf(
            NoteEntity(projectId = 42, position = 1.5f)
        )
        Snapshot.sendApplyNotifications()

        pump({ fixture.collectCalls.get() >= 2 })
        assertEquals(
            "Neue Notizzeile bei gleicher Projekt-Id muss die Dateiliste neu bauen " +
                "(ein remember-Schluessel ohne notes laesst die neue Eingabe aus)",
            2,
            fixture.collectCalls.get()
        )
    }
}

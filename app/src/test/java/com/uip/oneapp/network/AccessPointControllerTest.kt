package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die (Android-freie) Zustandslogik des [AccessPointController] ab (Welle 3a): Gate,
 * Idempotenz und die Übergänge Idle → Starting → Active/Failed/Idle. Der echte privilegierte
 * SoftAP-Aufruf liegt hinter [HotspotStarter] und wird hier durch einen Fake ersetzt;
 * der Plattform-Round-Trip ist Geräte-Test.
 */
class AccessPointControllerTest {

    /** Fake-Starter: hält die Callbacks fest, damit der Test die Übergänge deterministisch treibt. */
    private class FakeStarter : HotspotStarter {
        var onActive: ((String, String) -> Unit)? = null
        var onFailed: ((String) -> Unit)? = null
        var onStopped: (() -> Unit)? = null
        var startCount = 0
        var stopCount = 0

        override fun start(
            onActive: (String, String) -> Unit,
            onFailed: (String) -> Unit,
            onStopped: () -> Unit,
        ): HotspotSession {
            startCount++
            this.onActive = onActive
            this.onFailed = onFailed
            this.onStopped = onStopped
            return HotspotSession { stopCount++ }
        }
    }

    private fun controller(mode: HardwareMode, starter: FakeStarter = FakeStarter()) =
        AccessPointController(mode, starter) to starter

    @Test
    fun blocksStartOutsideDirectMode() {
        val (controller, starter) = controller(HardwareMode.WIFI)
        controller.start()
        assertEquals(ApState.Blocked(AccessPointSpec.GateResult.NOT_DIRECT_MODE), controller.state.value)
        assertEquals(0, starter.startCount) // Plattform gar nicht erst angefasst.
    }

    @Test
    fun directStartGoesToStartingThenActiveWithGeneratedCredentials() {
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()
        assertTrue(controller.state.value is ApState.Starting)

        starter.onActive!!.invoke("DrainQ-ONE-abc", "secretpass")
        assertEquals(ApState.Active("DrainQ-ONE-abc", "secretpass"), controller.state.value)
        assertEquals(1, starter.startCount)
    }

    @Test
    fun startIsIdempotentWhileStartingOrActive() {
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()           // Starting
        controller.start()           // no-op
        starter.onActive!!.invoke("S", "p") // Active
        controller.start()           // no-op
        assertEquals(1, starter.startCount)
    }

    @Test
    fun failedStartSurfacesReason() {
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()
        starter.onFailed!!.invoke("ERROR_NO_CHANNEL")
        assertEquals(ApState.Failed("ERROR_NO_CHANNEL"), controller.state.value)
    }

    @Test
    fun stopReleasesReservationAndReturnsToIdle() {
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()
        starter.onActive!!.invoke("S", "p")
        controller.stop()
        assertEquals(ApState.Idle, controller.state.value)
        assertEquals(1, starter.stopCount)
    }

    @Test
    fun platformInitiatedStopReturnsToIdle() {
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()
        starter.onActive!!.invoke("S", "p")
        starter.onStopped!!.invoke() // z. B. WLAN am Gerät ausgeschaltet
        assertEquals(ApState.Idle, controller.state.value)
    }

    @Test
    fun stopDuringStartingReleasesSessionAndIgnoresLateCallbacks() {
        // Race: Toggle AUS während „Starting" → stop() bricht ab; ein danach eintreffender
        // (überholter) onActive/onStopped der alten Sitzung darf den Zustand NICHT mehr ändern.
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()                 // Starting, session #1
        controller.stop()                  // → Idle, session.stop() aufgerufen, Generation gebumpt
        assertEquals(ApState.Idle, controller.state.value)
        assertEquals(1, starter.stopCount)

        // Späte Plattform-Callbacks der abgebrochenen Sitzung:
        starter.onActive!!.invoke("DrainQ-ONE-x", "pw") // darf NICHT auf Active springen
        assertEquals(ApState.Idle, controller.state.value)
        starter.onStopped!!.invoke()
        assertEquals(ApState.Idle, controller.state.value)
    }

    @Test
    fun canRestartAfterFailure() {
        val (controller, starter) = controller(HardwareMode.DIRECT)
        controller.start()
        starter.onFailed!!.invoke("ERROR_GENERIC")
        controller.start() // Failed ist kein Block → neuer Versuch
        assertTrue(controller.state.value is ApState.Starting)
        assertEquals(2, starter.startCount)
    }
}

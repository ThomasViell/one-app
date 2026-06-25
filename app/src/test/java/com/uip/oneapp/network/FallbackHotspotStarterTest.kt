package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die (Android-freie) Rückfall-Logik des [FallbackHotspotStarter] ab (Dual-Modus, W3a):
 * der privilegierte SoftAP wird bevorzugt; nur bei [REASON_PRIVILEGE] fällt der Starter still auf
 * den öffentlichen LocalOnlyHotspot zurück. Die echten Plattform-Starter sind durch steuerbare
 * Fakes ersetzt (Stil wie [AccessPointControllerTest.FakeStarter]).
 */
class FallbackHotspotStarterTest {

    /**
     * Fake-Starter: hält die Callbacks fest und kann synchron-aus-`start()` ODER asynchron
     * (per [emitFailedNow] etc. nach dem Aufruf) melden — beides braucht der Test, weil der
     * echte [AndroidSoftApStarter] den Privileg-Fehler SYNCHRON aus `start()` meldet.
     */
    private class FakeStarter(
        /** Wenn gesetzt, meldet [start] diesen Grund SOFORT (synchron) über onFailed. */
        private val failSyncWith: String? = null,
    ) : HotspotStarter {
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
            failSyncWith?.let { onFailed(it) }
            return HotspotSession { stopCount++ }
        }
    }

    private class Recorder {
        var active: Pair<String, String>? = null
        var failed: String? = null
        var stopped = false
    }

    private fun start(starter: FallbackHotspotStarter, rec: Recorder = Recorder()) =
        starter.start(
            onActive = { s, p -> rec.active = s to p },
            onFailed = { rec.failed = it },
            onStopped = { rec.stopped = true },
        ) to rec

    @Test
    fun privilegedPathSucceeds_noFallback() {
        val primary = FakeStarter()
        val fallback = FakeStarter()
        val rec = Recorder()
        start(FallbackHotspotStarter(primary, fallback), rec)

        primary.onActive!!.invoke("DrainQ-ONE-abc", "secretpass")

        assertEquals("DrainQ-ONE-abc" to "secretpass", rec.active)
        assertEquals(1, primary.startCount)
        assertEquals(0, fallback.startCount) // Rückfall gar nicht erst angefasst.
        assertNull(rec.failed)
    }

    @Test
    fun privilegeError_fallsBackToLohs_andSwallowsPrivilegeFailure() {
        // SoftAP meldet REASON_PRIVILEGE synchron aus start() (echtes Verhalten des @SystemApi-
        // Reflection-Pfads) → Rückfall, LOHS liefert die Plattform-Zugangsdaten.
        val primary = FakeStarter(failSyncWith = REASON_PRIVILEGE)
        val fallback = FakeStarter()
        val rec = Recorder()
        start(FallbackHotspotStarter(primary, fallback), rec)

        assertEquals(1, fallback.startCount) // synchroner Rückfall
        assertNull(rec.failed)               // Privileg-Fehler NICHT durchgereicht

        fallback.onActive!!.invoke("AndroidShared_1234", "lohspass")
        assertEquals("AndroidShared_1234" to "lohspass", rec.active)
    }

    @Test
    fun asyncPrivilegeError_fallsBack() {
        // Defensive: selbst wenn der Privileg-Fehler erst NACH start() einträfe, greift der Rückfall.
        val primary = FakeStarter()
        val fallback = FakeStarter()
        val rec = Recorder()
        start(FallbackHotspotStarter(primary, fallback), rec)

        primary.onFailed!!.invoke(REASON_PRIVILEGE)
        assertEquals(1, fallback.startCount)
        assertNull(rec.failed)
    }

    @Test
    fun nonPrivilegeError_isSurfaced_noFallback() {
        val primary = FakeStarter(failSyncWith = "ap-failed")
        val fallback = FakeStarter()
        val rec = Recorder()
        start(FallbackHotspotStarter(primary, fallback), rec)

        assertEquals("ap-failed", rec.failed) // echter Fehler → durchgereicht
        assertEquals(0, fallback.startCount)  // kein Rückfall bei Nicht-Privileg-Fehler
    }

    @Test
    fun fallbackFailure_isSurfaced() {
        // Privileg fehlt → Rückfall, aber auch LOHS scheitert (z. B. Standort fehlt) → der
        // LOHS-Fehler ist der echte, dem Operator zu zeigende Grund.
        val primary = FakeStarter(failSyncWith = REASON_PRIVILEGE)
        val fallback = FakeStarter(failSyncWith = "SecurityException")
        val rec = Recorder()
        start(FallbackHotspotStarter(primary, fallback), rec)

        assertEquals(1, fallback.startCount)
        assertEquals("SecurityException", rec.failed)
    }

    @Test
    fun stop_onPrivilegedSession_stopsPrimary() {
        val primary = FakeStarter()
        val fallback = FakeStarter()
        val (session, _) = start(FallbackHotspotStarter(primary, fallback))

        session.stop()
        assertEquals(1, primary.stopCount)
        assertEquals(0, fallback.stopCount)
    }

    @Test
    fun stop_afterFallback_stopsFallbackSession() {
        val primary = FakeStarter(failSyncWith = REASON_PRIVILEGE)
        val fallback = FakeStarter()
        val (session, _) = start(FallbackHotspotStarter(primary, fallback))

        session.stop()
        assertEquals(1, fallback.stopCount) // die aktive (Rückfall-)Session wird gestoppt
    }

    @Test
    fun stopDuringStarting_thenAsyncPrivilegeError_doesNotLeakFallbackHotspot() {
        // Race: Toggle AUS, WÄHREND der Privileg-Fehler noch unterwegs ist. Der danach
        // angestoßene Rückfall darf keinen Hotspot „hängen" lassen — die soeben übernommene
        // Rückfall-Session wird sofort wieder gestoppt.
        val primary = FakeStarter()
        val fallback = FakeStarter()
        val (session, _) = start(FallbackHotspotStarter(primary, fallback))

        session.stop()                                  // stop kommt zuerst
        primary.onFailed!!.invoke(REASON_PRIVILEGE)     // verspäteter Privileg-Fehler → Rückfall
        // Der Rückfall wird zwar gestartet, aber sofort wieder gestoppt (stopped-Flag).
        assertTrue(fallback.startCount <= 1)
        assertEquals(fallback.startCount, fallback.stopCount) // gestartet ⇒ auch gestoppt
    }
}

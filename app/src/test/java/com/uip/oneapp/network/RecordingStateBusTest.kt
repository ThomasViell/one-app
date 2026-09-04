package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Kette kiosk-pflicht, Runde 5 (P-1) — prozessweiter Aufnahmezustand für die
 * Ausstiegssperre. Deckt ab: Default AUS (kein frisch geöffneter Dialog ist gesperrt);
 * setzen/lesen; Rückfall auf AUS nach dem Stopp (Ausstieg muss danach wieder gehen).
 */
class RecordingStateBusTest {

    @Test
    fun default_is_inactive() {
        assertFalse(RecordingStateBus().active.value)
    }

    @Test
    fun setActive_true_then_false_roundtrip() {
        val bus = RecordingStateBus()
        bus.setActive(true)
        assertTrue(bus.active.value)
        bus.setActive(false)
        assertFalse("Ausstieg muss nach dem Stopp wieder frei sein", bus.active.value)
    }

    @Test
    fun distinct_instances_do_not_share_state() {
        // Schutz gegen versehentlichen Umbau auf globale/companion-State: die Sperre hängt
        // an DER Koin-Single-Instanz — zwei Instanzen dürfen sich nicht sehen.
        val a = RecordingStateBus()
        val b = RecordingStateBus()
        a.setActive(true)
        assertFalse(b.active.value)
    }
}

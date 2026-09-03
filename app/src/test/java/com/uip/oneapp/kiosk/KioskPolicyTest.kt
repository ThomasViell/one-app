package com.uip.oneapp.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert die (Android-freie) Kiosk-Politik ab (Kette kiosk-pflicht, Runde 2, 03.09.2026,
 * NACHBESSERUNG N-2): Der Kiosk hängt an der **Geräteidentität** (ONE-Board-Marker), nicht
 * mehr am Laufzeit-Transport (`HardwareMode`/`one_transport`/lesbares ttyS5). Fällt die
 * serielle Schnittstelle aus oder steht die per adb setzbare Voreinstellung
 * `one_transport=remote`, läuft eine ONE beim Kunden **trotzdem im Kiosk** (Befund B3:
 * vorher „wegmessbar"). Die Tablet-Ausnahme (E2, CEO-Annahme R1) bleibt strukturell
 * erhalten: Ein Tablet trägt den ONE-Board-Marker nie, bekommt also auch im WiFi-Modus
 * keinen Kiosk. LockTask (die harte Sperre) weiterhin nur als Geräteeigentümer (E4/R2).
 */
class KioskPolicyTest {

    @Test
    fun oneGeraetMitEigentuemer_immersivUndLockTask() {
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = true, lockTask = true),
            KioskPolicy.plan(isOneDevice = true, deviceOwner = true)
        )
    }

    @Test
    fun oneGeraetOhneEigentuemer_immersivOhneLockTask() {
        // Ohne Owner kein startLockTask() — der System-Anpinn-Dialog ist am 03.09. als
        // Vollbild-Falle gemessen worden (belege/ma2_anpinn_schleife.txt).
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = true, lockTask = false),
            KioskPolicy.plan(isOneDevice = true, deviceOwner = false)
        )
    }

    @Test
    fun tabletMitEigentuemer_nichts() {
        // Tablets bleiben unveraendert (E2/R1) — auch wenn dort aus Versehen ein
        // Owner-Status bestuende.
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = false, lockTask = false),
            KioskPolicy.plan(isOneDevice = false, deviceOwner = true)
        )
    }

    @Test
    fun tabletOhneEigentuemer_nichts() {
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = false, lockTask = false),
            KioskPolicy.plan(isOneDevice = false, deviceOwner = false)
        )
    }
}

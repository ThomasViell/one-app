package com.uip.oneapp.kiosk

import com.uip.oneapp.network.HardwareMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert die (Android-freie) Kiosk-Politik ab (Kette kiosk-pflicht, 03.09.2026, Plan E2/E4,
 * CEO-Entscheid R1/R2): Kiosk-Pflicht gilt nur im DIRECT-Modus; LockTask (die harte Sperre)
 * nur als Geräteeigentümer — ohne Owner käme der System-Anpinn-Dialog, der am 03.09. als
 * Vollbild-Falle gemessen wurde (`belege/ma2_anpinn_schleife.txt`).
 */
class KioskPolicyTest {

    @Test
    fun directMitEigentuemer_immersivUndLockTask() {
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = true, lockTask = true),
            KioskPolicy.plan(HardwareMode.DIRECT, deviceOwner = true)
        )
    }

    @Test
    fun directOhneEigentuemer_immersivOhneLockTask() {
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = true, lockTask = false),
            KioskPolicy.plan(HardwareMode.DIRECT, deviceOwner = false)
        )
    }

    @Test
    fun wifiMitEigentuemer_nichts() {
        // Tablets im WiFi-Modus bleiben unveraendert (R1) — auch wenn dort aus Versehen
        // ein Owner-Status bestuende.
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = false, lockTask = false),
            KioskPolicy.plan(HardwareMode.WIFI, deviceOwner = true)
        )
    }

    @Test
    fun wifiOhneEigentuemer_nichts() {
        assertEquals(
            KioskPolicy.LockdownPlan(immersive = false, lockTask = false),
            KioskPolicy.plan(HardwareMode.WIFI, deviceOwner = false)
        )
    }
}

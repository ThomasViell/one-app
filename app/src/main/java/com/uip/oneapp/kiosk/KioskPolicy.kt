package com.uip.oneapp.kiosk

import com.uip.oneapp.network.HardwareMode

/**
 * Kiosk-Politik (Kette kiosk-pflicht, 03.09.2026, Plan E2/E4; CEO-Entscheid R1/R2).
 *
 * Kiosk ist seit dieser Kette **Pflicht und Konstante** im DIRECT-Modus (ONE-Hardware):
 * Vollbild + Balken-Behandlung + HOME-Rolle sind immer aktiv. Die harte Sperre (LockTask)
 * laeuft nur als Geraeteeigentuemer — ohne Owner zeigt `startLockTask()` den
 * System-Anpinn-Dialog, der am 03.09. als Vollbild-Falle gemessen wurde (blockiert jede
 * Bedienung, kommt nach jeder Fokus-Rueckkehr wieder; `belege/ma2_anpinn_schleife.txt`
 * der Kette). Im WIFI-/Tablet-Modus ist weiterhin alles aus.
 */
object KioskPolicy {

    /**
     * @property immersive Vollbild (System-Bars aus, Legacy-Flags 5894, Waechter, Dialog-Immersive)
     * @property lockTask  harte Sperre via startLockTask() — nur als Geraeteeigentuemer
     */
    data class LockdownPlan(val immersive: Boolean, val lockTask: Boolean)

    fun plan(mode: HardwareMode, deviceOwner: Boolean): LockdownPlan = when (mode) {
        HardwareMode.DIRECT -> LockdownPlan(immersive = true, lockTask = deviceOwner)
        HardwareMode.WIFI -> LockdownPlan(immersive = false, lockTask = false)
    }
}

package com.uip.oneapp.kiosk

/**
 * Kiosk-Politik (Kette kiosk-pflicht, 03.09.2026, Plan E2/E4; CEO-Entscheid R1/R2;
 * Runde 2: NACHBESSERUNG N-2).
 *
 * Kiosk ist auf der ONE-Hardware **Pflicht und Konstante**: Vollbild + Balken-Behandlung +
 * HOME-Rolle sind immer aktiv. Die Entscheidung haengt seit Runde 2 an der
 * **Geraeteidentitaet** (`isOneDevice` = ONE-Board-Marker rk3588_s/rk30sdk, siehe
 * [com.uip.oneapp.network.HardwareModeDetector.isOneBoardModel]) — NICHT mehr am
 * Laufzeit-Transport (`HardwareMode`). Hintergrund (Befund B3 der Pruefer): Der Transport
 * wird WIFI, sobald `/dev/ttyS5` unlesbar ist oder die per adb setzbare Voreinstellung
 * `one_transport=remote` steht — damit lief eine ONE beim Kunden ganz ohne Kiosk; der
 * Kiosk war nicht abschaltbar, aber **wegmessbar**. Ein Board-Ausfall ist dagegen kein
 * realistischer Pfad: Der Marker kommt aus den Build-Konstanten des Geraete-Images.
 *
 * Die Tablet-Ausnahme (E2, CEO-Annahme R1) bleibt strukturell erhalten: Ein Tablet traegt
 * den ONE-Board-Marker nie und bekommt daher keinen Kiosk — auch nicht, wenn es per
 * Dev-Voreinstellung `one_transport=internal` auf den Direkt-Transport gezwungen wird.
 *
 * Die harte Sperre (LockTask) laeuft nur als Geraeteeigentuemer — ohne Owner zeigt
 * `startLockTask()` den System-Anpinn-Dialog, der am 03.09. als Vollbild-Falle gemessen
 * wurde (blockiert jede Bedienung, kommt nach jeder Fokus-Rueckkehr wieder;
 * `belege/ma2_anpinn_schleife.txt` der Kette).
 */
object KioskPolicy {

    /**
     * @property immersive Vollbild (System-Bars aus, Legacy-Flags 5894, Waechter, Dialog-Immersive)
     * @property lockTask  harte Sperre via startLockTask() — nur als Geraeteeigentuemer
     */
    data class LockdownPlan(val immersive: Boolean, val lockTask: Boolean)

    fun plan(isOneDevice: Boolean, deviceOwner: Boolean): LockdownPlan =
        if (isOneDevice) {
            LockdownPlan(immersive = true, lockTask = deviceOwner)
        } else {
            LockdownPlan(immersive = false, lockTask = false)
        }
}

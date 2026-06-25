package com.uip.oneapp.bootstrap

// HINWEIS: Diese Datei trägt die **reine, Android-freie** Entscheidungs-Logik des
// Standort-Auto-Grants (Dual-Modus, Welle 3a). Sie ist voll unit-getestet
// (vgl. [DeviceOwnerLocationProvisionerTest]). Der Android-/DevicePolicyManager-behaftete
// Teil liegt ausschließlich in `AndroidDevicePolicyGateway.kt` — der einzigen Schicht mit
// Geräte-Bezug.

/**
 * Schmaler, Android-freier Seam über die wenigen DevicePolicyManager-Operationen, die der
 * Standort-Auto-Grant braucht. Im Test wird ein Fake injiziert (vgl. `HotspotStarter`).
 * Android-Impl: [AndroidDevicePolicyGateway].
 */
interface DevicePolicyGateway {
    /** Ist DIESE App der Geräteeigentümer (Device-Owner)? Nur dann darf sie sich selbst Policy setzen. */
    fun isDeviceOwner(): Boolean

    /**
     * Gewährt der App still `ACCESS_FINE_LOCATION` (DevicePolicyManager.setPermissionGrantState
     * GRANTED). Liefert `true`, wenn die Plattform den Grant annahm.
     */
    fun grantFineLocation(): Boolean

    /**
     * Aktiviert die Standortdienste (DevicePolicyManager.setLocationEnabled(true)). Liefert `true`,
     * wenn der Aufruf durchlief (erst ab Android 11 verfügbar — darunter `false`).
     */
    fun enableLocation(): Boolean
}

/**
 * **Standort-Auto-Grant für die Kiosk-ONE** (Dual-Modus, Welle 3a) — reine Ablauf-Logik.
 *
 * Hintergrund: Der LOHS-Rückfall des Tablet-Hotspots ([com.uip.oneapp.network.AndroidLohsStarter])
 * verlangt `ACCESS_FINE_LOCATION` + aktivierte Standortdienste; auf der Kiosk-ONE lässt sich die
 * Laufzeit-Berechtigung aber **nicht** per UI erteilen (kein bedienbarer Berechtigungsdialog) und
 * kein adb. Lösung: ist die App **Geräteeigentümer**, gewährt sie sich die Berechtigung selbst und
 * schaltet die Standortdienste ein. Das löst zugleich den GPS-Backlog (der Standort-Knopf
 * funktioniert auf der Kiosk-ONE).
 *
 * **Strikt nur als Device-Owner:** ohne Owner-Status passiert **nichts** — normales
 * Android-Verhalten (der Nutzer erteilt Standort wie gewohnt per Dialog). Das hält den Eingriff
 * auf das provisionierte Werks-/Kiosk-Gerät beschränkt.
 */
object DeviceOwnerLocationProvisioner {

    /** Ergebnis des Auto-Grant-Versuchs — für Logging/Test, nicht handlungsleitend. */
    data class Result(
        val deviceOwner: Boolean,
        val locationGranted: Boolean,
        val locationEnabled: Boolean,
    ) {
        companion object {
            /** Kein Device-Owner → kein Eingriff. */
            val NOT_OWNER = Result(deviceOwner = false, locationGranted = false, locationEnabled = false)
        }
    }

    /**
     * Führt den Auto-Grant aus, **sofern** [gateway] meldet, dass die App Device-Owner ist.
     * Idempotent (mehrfacher Aufruf je App-Start ist harmlos — `setPermissionGrantState`/
     * `setLocationEnabled` sind selbst idempotent).
     */
    fun provision(gateway: DevicePolicyGateway): Result {
        if (!gateway.isDeviceOwner()) return Result.NOT_OWNER
        val granted = gateway.grantFineLocation()
        val enabled = gateway.enableLocation()
        return Result(deviceOwner = true, locationGranted = granted, locationEnabled = enabled)
    }
}

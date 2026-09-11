package com.uip.oneapp.system

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Ergebnis eines Sprungs in eine Systemeinstellung (Welle geraetezeit Z-2).
 *
 * Der Kiosk (LockTask) blockiert `com.android.settings`; die App erfuhr davon bisher
 * nichts (stille Rueckkehr ohne Ausnahme). Der Launcher schliesst diese Fehlerklasse:
 * Vorabpruefung statt Blindstart, jede Ausnahme als Ergebnis statt als Absturz.
 */
sealed class SystemSettingsLaunchResult {
    data object Launched : SystemSettingsLaunchResult()
    data object BlockedByKiosk : SystemSettingsLaunchResult()
    data object MissingActivity : SystemSettingsLaunchResult()
    data object Denied : SystemSettingsLaunchResult()
}

/** Schmaler Port ueber die Android-Dienste — im JVM-Test per Fake stellbar (Regel 20). */
interface SystemSettingsLaunchPort {
    fun lockTaskModeState(): Int
    fun isLockTaskPermitted(pkg: String): Boolean
    fun resolvedPackage(intent: Intent): String?
    fun start(intent: Intent)
}

/**
 * Kiosk-bewusster Sprung in eine Systemeinstellung.
 *
 * Nachtrag 2 (11.09.2026, kein Geraet): Welcher der drei Mess-Ausgaenge (Plan 2.2) am
 * Geraet greift, ist am Schreibtisch nicht entscheidbar — die Bauform deckt deshalb alle
 * drei ab: stille Rueckkehr (Vorabpruefung), `SecurityException`/sonstige Ausnahme
 * (`Denied`), fehlende Aktivitaet (`MissingActivity`). Zusaetzlich bleibt der bisherige
 * `ActivityNotFoundException`-Fall abgedeckt.
 */
class SystemSettingsLauncher(private val port: SystemSettingsLaunchPort) {

    fun launch(intent: Intent): SystemSettingsLaunchResult {
        val target = port.resolvedPackage(intent)
            ?: return SystemSettingsLaunchResult.MissingActivity

        // Vorabpruefung (stille Rueckkehr): Ist LockTask aktiv und das Ziel nicht auf der
        // Erlaubnisliste, wird gar nicht erst gestartet — kein E/ActivityTaskManager-Eintrag,
        // kein Fokuswechsel, und die App haengt nicht am catch, das ohnehin nie greift.
        if (port.lockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE &&
            !port.isLockTaskPermitted(target)
        ) {
            Log.i(TAG, "Blockiert (Kiosk/LockTask), kein Start: target=$target")
            return SystemSettingsLaunchResult.BlockedByKiosk
        }

        return try {
            port.start(intent)
            SystemSettingsLaunchResult.Launched
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Zielaktivitaet fehlt: target=$target", e)
            SystemSettingsLaunchResult.MissingActivity
        } catch (e: SecurityException) {
            Log.w(TAG, "Start verweigert: target=$target", e)
            SystemSettingsLaunchResult.Denied
        } catch (e: Exception) {
            // Mess-Ausgang (c): jede nicht vorhergesagte Ausnahme wird als Denied gemeldet,
            // kein stiller Pfad (Nachtrag 2 Punkt 1 sinngemaess).
            Log.w(TAG, "Start fehlgeschlagen: target=$target", e)
            SystemSettingsLaunchResult.Denied
        }
    }

    companion object {
        private const val TAG = "SystemSettingsLauncher"
    }
}

/** Android-Implementierung des Ports (ActivityManager, DevicePolicyManager, PackageManager). */
class AndroidSystemSettingsLaunchPort(private val context: Context) : SystemSettingsLaunchPort {

    private val appContext = context.applicationContext

    override fun lockTaskModeState(): Int =
        appContext.getSystemService(ActivityManager::class.java)?.lockTaskModeState
            ?: ActivityManager.LOCK_TASK_MODE_NONE

    override fun isLockTaskPermitted(pkg: String): Boolean =
        appContext.getSystemService(DevicePolicyManager::class.java)?.isLockTaskPermitted(pkg)
            ?: true

    override fun resolvedPackage(intent: Intent): String? =
        intent.resolveActivity(appContext.packageManager)?.packageName

    override fun start(intent: Intent) {
        appContext.startActivity(intent)
    }
}

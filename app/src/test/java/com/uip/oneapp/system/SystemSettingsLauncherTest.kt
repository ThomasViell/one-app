package com.uip.oneapp.system

import android.app.ActivityManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Welle geraetezeit Z-2 / Nachtrag 2 (11.09.2026): Rot-Beweis gegen die Attrappe.
 *
 * Ohne Geraet ist nicht messbar, ob die LockTask-Blockade als Ausnahme, als stille
 * Rueckkehr oder gar nicht bei der App ankommt (Plan 2.2, Ausgaenge a/b/c). Die Bauform
 * deckt deshalb alle drei Wege ab und wird hier gegen einen Fake-Port belegt:
 *
 * - Ausnahme        → `start` wirft → `Denied`/`MissingActivity`
 * - stille Rueckkehr → Vorabpruefung (LockTask aktiv + Ziel nicht erlaubt) verhindert,
 *                      dass ueberhaupt gestartet wird → `BlockedByKiosk`
 * - Vorabpruefung   → `isLockTaskPermitted` schaltet den Start frei
 *
 * Robolectric stellt nur den echten `Intent` (Stub-Konstruktor im JVM-Test); der Port
 * bleibt reines Kotlin.
 */
// Plain Application statt OneApp, damit Koin waehrend des Robolectric-Setups nicht startet
// (dasselbe Muster wie UpdateServiceTest).
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SystemSettingsLauncherTest {

    private class FakePort(
        var lockTaskModeState: Int = ActivityManager.LOCK_TASK_MODE_NONE,
        var permittedPackages: Map<String, Boolean> = emptyMap(),
        var resolvedPackage: String? = "com.android.settings",
        var startBehavior: () -> Unit = {},
    ) : SystemSettingsLaunchPort {
        var startCalls = 0
            private set
        override fun lockTaskModeState(): Int = lockTaskModeState
        override fun isLockTaskPermitted(pkg: String): Boolean = permittedPackages[pkg] ?: false
        override fun resolvedPackage(intent: Intent): String? = resolvedPackage
        override fun start(intent: Intent) {
            startCalls++
            startBehavior()
        }
    }

    private fun dateSettingsIntent(): Intent = Intent(Settings.ACTION_DATE_SETTINGS)

    @Test
    fun kioskBlockedTargetNotPermitted_returnsBlockedByKiosk_andNeverStarts() {
        // Der gemessene Logbeleg (E/ActivityTaskManager: Lock Task Mode violation) kehrt bei
        // der App als stille Rueckkehr zurueck — deshalb die Vorabpruefung: der Start wird
        // gar nicht erst abgesetzt, kein E/ActivityTaskManager-Eintrag, kein Fokuswechsel.
        val port = FakePort(
            lockTaskModeState = ActivityManager.LOCK_TASK_MODE_LOCKED,
            permittedPackages = mapOf("com.android.settings" to false),
        )
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.BlockedByKiosk, result)
        assertEquals(0, port.startCalls)
    }

    @Test
    fun kioskBlockedButTargetPermitted_launches() {
        val port = FakePort(
            lockTaskModeState = ActivityManager.LOCK_TASK_MODE_LOCKED,
            permittedPackages = mapOf("com.android.settings" to true),
        )
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.Launched, result)
        assertEquals(1, port.startCalls)
    }

    @Test
    fun noLockTask_precheckSkipped_launches() {
        val port = FakePort(lockTaskModeState = ActivityManager.LOCK_TASK_MODE_NONE)
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.Launched, result)
        assertEquals(1, port.startCalls)
    }

    @Test
    fun targetNotResolvable_returnsMissingActivity_withoutStarting() {
        val port = FakePort(resolvedPackage = null)
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.MissingActivity, result)
        assertEquals(0, port.startCalls)
    }

    @Test
    fun startThrowsActivityNotFound_returnsMissingActivity() {
        val port = FakePort(startBehavior = { throw ActivityNotFoundException("weg") })
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.MissingActivity, result)
    }

    @Test
    fun startThrowsSecurityException_returnsDenied() {
        val port = FakePort(startBehavior = { throw SecurityException("verboten") })
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.Denied, result)
    }

    @Test
    fun startThrowsOtherException_returnsDenied() {
        // Mess-Ausgang (c) des Plans: eine nicht vorhergesagte Ausnahme — sie darf nicht
        // durchschlagen, sondern wird als Denied gemeldet (kein stiller Pfad, Nachtrag 2).
        val port = FakePort(startBehavior = { throw IllegalStateException("kaputt") })
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.Denied, result)
    }

    @Test
    fun silentReturnCase_isCoveredByPrecheck() {
        // Nachtrag 2: die drei Moeglichkeiten Ausnahme / stille Rueckkehr / Vorabpruefung.
        // Der Fake stellt die Blockade nach (LOCKED + nichts erlaubt): haette die App
        // blind gestartet, waere der Start ohne Ausnahme und ohne Wirkung zurueckgekehrt.
        val port = FakePort(
            lockTaskModeState = ActivityManager.LOCK_TASK_MODE_LOCKED,
            permittedPackages = emptyMap(),
        )
        val result = SystemSettingsLauncher(port).launch(dateSettingsIntent())
        assertEquals(SystemSettingsLaunchResult.BlockedByKiosk, result)
        assertEquals(0, port.startCalls)
    }
}

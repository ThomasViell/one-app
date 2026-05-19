package com.uip.oneapp.bootstrap

import android.util.Log
import java.io.File

/**
 * Beim App-Start: chmod 666 auf /dev/ttyS5 und /dev/video0 — damit DrainQ.ONE als
 * normale User-App auf die Hardware-Schnittstellen der ONE-Hardware zugreifen kann.
 *
 * Pilot-Variante 7.3 (siehe docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md, Abschnitt 7):
 * App nutzt einen `su`-Befehl, der auf gerooteten Tablets vorinstalliert ist. Auf der
 * BWELL/Bominwell ONE-Hardware ist das gegeben (verifiziert via `adb shell whoami → root`).
 *
 * Vor produktivem Roll-out wird das durch Variante 7.1 ersetzt: App wird von Bominwell
 * mit dem Plattform-Cert signiert und ins `/system/priv-app/`-Verzeichnis installiert,
 * dann braucht sie keine Tricks mehr.
 *
 * Verhalten:
 *   - Wenn die Device-Files nicht existieren (z. B. TWO-Modus oder Nicht-ONE-Tablet):
 *     keine Aktion, leise zurück
 *   - Wenn die Files bereits weltzugänglich sind (R+W): keine Aktion
 *   - Sonst: `su -c "chmod 666 /dev/ttyS5 /dev/video0"` absetzen, Ergebnis loggen
 *
 * Bezug: docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md, Phase P7.
 */
object DeviceFilePermissionBootstrap {
    private const val TAG = "DeviceFilePermission"

    private val PATHS = listOf("/dev/ttyS5", "/dev/video0")

    fun grantIfNeeded() {
        // Nur ausführen, wenn die ONE-Hardware-Files überhaupt vorhanden sind.
        // (Auf Samsung-Tablets im TWO-Modus existieren sie nicht — nichts zu tun.)
        val existingPaths = PATHS.filter { File(it).exists() }
        if (existingPaths.isEmpty()) {
            Log.i(TAG, "ONE-Devices not present — skipping chmod (likely TWO-mode or non-ONE-tablet)")
            return
        }

        // Bereits R+W zugänglich? Dann fertig — vermeidet unnötigen su-Aufruf.
        val allAccessible = existingPaths.all { File(it).let { f -> f.canRead() && f.canWrite() } }
        if (allAccessible) {
            Log.i(TAG, "Device files already RW-accessible — no chmod needed")
            return
        }

        // su-Befehl absetzen
        val cmd = "chmod 666 ${existingPaths.joinToString(" ")}"
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exit = proc.waitFor()
            val stderr = proc.errorStream.bufferedReader().readText()
            val stdout = proc.inputStream.bufferedReader().readText()
            if (exit == 0) {
                Log.i(TAG, "chmod via su OK: $cmd")
            } else {
                Log.w(TAG, "chmod via su failed (exit=$exit, stderr=$stderr, stdout=$stdout)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "su not available or chmod failed: ${e.message}", e)
        }
    }
}

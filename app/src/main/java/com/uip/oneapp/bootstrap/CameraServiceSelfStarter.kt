package com.uip.oneapp.bootstrap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Camera2-Umbau 2026-07-29 (CEO-Entscheid, siehe `UMBAU_CAMERA2_PROMPT.md` AP-2) —
 * BEWUSSTE KRÜCKE, KEIN URSACHENFIX.
 *
 * Die ONE stellt ihre USB-Kamera reguär über Standard-Camera2 bereit
 * (`LENS_FACING_EXTERNAL`), sobald der Dienst `vendor.camera-provider-2-4-ext` läuft.
 * Ein bislang nicht identifizierter Mechanismus stoppt diesen Dienst ~1s nach
 * Boot-Completed — die DrainQ-App selbst wurde dabei als Verursacher **positiv
 * widerlegt** (Prozess war beim Stopp-Zeitpunkt nicht im Prozessbaum), der
 * tatsächliche Auslöser konnte trotz gezielter Log-Suche (`dmesg`, `logcat -b all`)
 * NICHT ermittelt werden. Siehe `RESULT_KAMERA_CAMERA2_2026-07-29.md`, Frage 1.
 *
 * Diese Klasse umgeht das Problem, indem sie den Dienst bei jedem Kamera-Start selbst
 * sicherstellt (prüfen → bei Bedarf starten → auf `running` warten), statt die Ursache
 * zu beheben. Ausdrücklich so dokumentiert, damit das nicht als gelöste Ursache
 * missverstanden wird.
 *
 * Läuft komplett über `su` (Gerät ist geroutet, gleiches Muster wie
 * [DeviceFilePermissionBootstrap]) — keine Plattform-Signatur nötig.
 */
object CameraServiceSelfStarter {
    private const val TAG = "CameraServiceSelfStart"
    private const val SERVICE = "vendor.camera-provider-2-4-ext"
    private const val START_TIMEOUT_MS = 2_500L
    private const val POLL_INTERVAL_MS = 100L
    private const val SU_TIMEOUT_S = 3L

    sealed class Result {
        data object AlreadyRunning : Result()
        data object StartedNow : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * Prüft/startet [SERVICE]. Blockierend (su-Aufrufe + Poll-Warteschleife bis max.
     * [START_TIMEOUT_MS]) — vom Aufrufer bewusst auf einem Hintergrund-Dispatcher zu rufen,
     * NIEMALS auf dem Main-Thread.
     */
    fun ensureRunning(): Result {
        if (isRunning()) {
            Log.i(TAG, "$SERVICE bereits running")
            return Result.AlreadyRunning
        }
        Log.w(TAG, "$SERVICE nicht running — starte via su (Krücke; Verursacher des Boot-Stopps ungeklärt, siehe RESULT_KAMERA_CAMERA2_2026-07-29.md)")
        if (!runSu("start $SERVICE")) {
            Log.e(TAG, "AUDIT camera_self_start_failed reason=su_exec_failed service=$SERVICE")
            return Result.Failed("su-Start-Befehl fehlgeschlagen (kein Root oder su nicht erreichbar)")
        }
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isRunning()) {
                Log.i(TAG, "$SERVICE erfolgreich gestartet")
                return Result.StartedNow
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        Log.e(TAG, "AUDIT camera_self_start_failed reason=timeout service=$SERVICE timeoutMs=$START_TIMEOUT_MS")
        return Result.Failed("Dienst startete nicht innerhalb von ${START_TIMEOUT_MS}ms")
    }

    /**
     * Stellt sicher, dass die App die Laufzeit-Berechtigung CAMERA hat — ohne die ist
     * `CameraManager.openCamera()` unabhängig vom Dienst-Zustand ein SecurityException.
     * Auf dem gerooteten Kiosk-Gerät per `pm grant` via su erzwungen (kein bedienbarer
     * Berechtigungsdialog auf der Kiosk-ONE). Nicht Teil des ursprünglichen AP-2-Auftrags-
     * texts, aber notwendige Voraussetzung dafür, dass der Camera2-Pfad überhaupt Bilder
     * liefert — deshalb hier mit erledigt.
     */
    fun ensureCameraPermission(context: Context): Boolean {
        if (hasCameraPermission(context)) return true
        Log.w(TAG, "CAMERA-Berechtigung fehlt — erzwinge via su pm grant")
        runSu("pm grant ${context.packageName} ${Manifest.permission.CAMERA}")
        val granted = hasCameraPermission(context)
        if (!granted) {
            Log.e(TAG, "AUDIT camera_self_start_failed reason=permission_denied pkg=${context.packageName}")
        }
        return granted
    }

    private fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isRunning(): Boolean =
        runSuCapture("getprop init.svc.$SERVICE").trim() == "running"

    private fun runSu(cmd: String): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val finished = proc.waitFor(SU_TIMEOUT_S, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                Log.w(TAG, "su timed out: $cmd")
                return false
            }
            val exit = proc.exitValue()
            if (exit != 0) {
                val stderr = proc.errorStream.bufferedReader().readText()
                Log.w(TAG, "su exit=$exit cmd=$cmd stderr=$stderr")
            }
            exit == 0
        } catch (e: Exception) {
            Log.e(TAG, "su exec failed ($cmd): ${e.message}", e)
            false
        }
    }

    private fun runSuCapture(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val finished = proc.waitFor(SU_TIMEOUT_S, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return ""
            }
            proc.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "su capture failed ($cmd): ${e.message}", e)
            ""
        }
    }
}

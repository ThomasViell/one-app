package com.uip.oneapp.bootstrap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Camera2-Umbau 2026-07-29 (CEO-Entscheid, siehe `UMBAU_CAMERA2_PROMPT.md` AP-2) —
 * BEWUSSTES SICHERHEITSNETZ FÜR GERÄTE MIT ABWEICHENDER FIRMWARE, KEIN URSACHENFIX.
 *
 * Die ONE stellt ihre USB-Kamera regulär über Standard-Camera2 bereit
 * (`LENS_FACING_EXTERNAL`), sobald der Dienst `vendor.camera-provider-2-4-ext` läuft.
 * Auf zwei Testgeräten wurde dieser Dienst am 29.07.2026 ~1s nach Boot-Completed
 * gestoppt, ohne dass die DrainQ-App selbst der Verursacher war (Prozess war beim
 * Stopp-Zeitpunkt nicht im Prozessbaum) — siehe `RESULT_KAMERA_CAMERA2_2026-07-29.md`,
 * Frage 1. Als Verdächtige stand die werkseitig vorinstallierte `com.bominwell.minipush`
 * im Raum.
 *
 * **Messung vom 30.07.2026** (siehe `RESULT_WERKSEINRICHTUNG_2026-07-30.md`, Abschnitt
 * „Camera-Provider-Messung"): auf zwei Geräten (`e92df62d2dbd2143`, `80cfaba8f63b8362`),
 * jeweils direkt nach dem Neustart und OHNE den Inspektionsbildschirm zu öffnen, blieb der
 * Dienst nach Entfernung von `com.bominwell.minipush` durchgehend `running` — kein einziges
 * `REMOVE`-Ereignis, im Unterschied zum Befund vom 29.07. mit noch vorhandener `minipush`
 * (`ADD` um 13:40:36, `REMOVE` um 13:40:49). **Das ist ein starkes Indiz, dass `minipush` der
 * gesuchte Mechanismus war — kein letztgültiger Beweis**, da kein A/B-Test auf demselben
 * Gerät stattfand und die Ursache im Detail (warum `minipush` das täte) ungeklärt bleibt.
 *
 * Diese Klasse bleibt deshalb als bewusstes Sicherheitsnetz bestehen: für Geräte mit
 * abweichender Firmware (andere Werks-Vorinstallation, anderer Auslöser als `minipush`,
 * zukünftige Firmware-Stände), auf denen der Dienst trotz entfernter `minipush` doch nicht
 * durchläuft. Sie stellt den Dienst bei jedem Kamera-Start selbst sicher (prüfen → bei
 * Bedarf starten → auf `running` warten), statt sich auf eine nicht abschließend bewiesene
 * Ursache zu verlassen. **Die Klasse wird nicht ausgebaut** (CEO-Entscheid 30.07.2026) —
 * kein erneuter Messlauf, keine Erweiterung über dieses Sicherheitsnetz hinaus.
 *
 * Nachbesserung 2026-07-29 (RESULT_CAMERA2_UMBAU_2026-07-29.md Abschnitt 2): der
 * ursprüngliche Weg über `su` war eine Sackgasse — `/system/xbin/su` ist auf
 * `233b4bd2865177ed` nur für `root`/Gruppe `shell` ausführbar, der App-Prozess gehört zu
 * keinem von beiden, auch nicht als `sharedUserId="android.uid.system"` (ADR-0005).
 * DrainQ.ONE ist plattformsigniert und läuft als uid=system — dafür reicht die versteckte
 * API `android.os.SystemProperties` per Reflection, kein `su` mehr nötig:
 *   - Lesen (`SystemProperties.get`) ist für jeden Prozess ohne Sonderrechte erlaubt.
 *   - Starten über `ctl.start` verlangt einen SELinux-Kontext, der diese Property setzen
 *     darf — bei uid=system + Plattformsignatur ist das der Fall (siehe Belege im
 *     genannten Bericht), ansonsten schlägt `setProp` fehl und wird als solches gemeldet,
 *     nicht stillschweigend ignoriert.
 */
object CameraServiceSelfStarter {
    private const val TAG = "CameraServiceSelfStart"
    private const val SERVICE = "vendor.camera-provider-2-4-ext"
    private const val START_TIMEOUT_MS = 2_500L
    private const val POLL_INTERVAL_MS = 100L

    sealed class Result {
        data object AlreadyRunning : Result()
        data object StartedNow : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * Prüft/startet [SERVICE]. Blockierend (Poll-Warteschleife bis max.
     * [START_TIMEOUT_MS]) — vom Aufrufer bewusst auf einem Hintergrund-Dispatcher zu rufen,
     * NIEMALS auf dem Main-Thread.
     */
    fun ensureRunning(): Result {
        if (isRunning()) {
            Log.i(TAG, "$SERVICE bereits running")
            return Result.AlreadyRunning
        }
        Log.w(TAG, "$SERVICE nicht running — starte via SystemProperties ctl.start (Krücke; Verursacher des Boot-Stopps ungeklärt, siehe RESULT_KAMERA_CAMERA2_2026-07-29.md)")
        if (!startService()) {
            Log.e(TAG, "AUDIT camera_self_start_failed reason=ctl_start_failed service=$SERVICE")
            return Result.Failed("ctl.start fehlgeschlagen (SystemProperties nicht erreichbar oder verweigert)")
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
     * Meldet nur noch den Ist-Zustand der CAMERA-Berechtigung. Der frühere `su pm grant`-
     * Zwangsweg ist mit dem su-Ausbau entfallen; auf `233b4bd2865177ed` ist die
     * Berechtigung als uid=system ohnehin `SYSTEM_FIXED|GRANTED_BY_DEFAULT` (per
     * `dumpsys package` bestätigt), ein Erzwingen ist dort also gar nicht nötig. Auf einem
     * Gerät ohne diesen Default-Grant und ohne bedienbaren Berechtigungsdialog (Kiosk)
     * bliebe eine fehlende Berechtigung ein sichtbarer, aber ungelöster Fall — bewusst
     * kein stiller Workaround dafür.
     */
    fun ensureCameraPermission(context: Context): Boolean {
        val granted = hasCameraPermission(context)
        if (!granted) {
            Log.e(TAG, "AUDIT camera_permission_missing pkg=${context.packageName}")
        }
        return granted
    }

    private fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isRunning(): Boolean = getSystemProperty("init.svc.$SERVICE") == "running"

    private fun startService(): Boolean = setSystemProperty("ctl.start", SERVICE)

    private fun getSystemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        method.invoke(null, key) as? String
    } catch (e: Exception) {
        Log.e(TAG, "SystemProperties.get($key) fehlgeschlagen: ${e.message}", e)
        null
    }

    private fun setSystemProperty(key: String, value: String): Boolean = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("set", String::class.java, String::class.java)
        method.invoke(null, key, value)
        true
    } catch (e: Exception) {
        Log.e(TAG, "SystemProperties.set($key, $value) fehlgeschlagen: ${e.message}", e)
        false
    }
}

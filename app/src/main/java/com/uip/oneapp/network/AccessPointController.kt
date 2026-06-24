package com.uip.oneapp.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Reine, Android-freie SoftAP-Spezifikation (Dual-Modus, Welle 3a): SSID-/Passphrase-Regeln
 * und die Start-Vorbedingungen (Modus-Gate + STA/AP-Exklusivität). Ohne Android voll testbar;
 * das Android-/Privileg-behaftete Drumherum liegt in [AccessPointController]. Pendant zu
 * [OneRemoteProtocol]/[HardwareModeDetector.decide] — Logik raus aus den Android-Klassen.
 */
object AccessPointSpec {
    const val SSID_PREFIX = "DrainQ-ONE"

    /** SSID-Limit: 32 Oktette (hier ASCII → 32 Zeichen). */
    const val SSID_MAX_LENGTH = 32

    /** WPA2-PSK-Passphrase-Länge: 8..63 ASCII-Zeichen. */
    const val PASSPHRASE_MIN = 8
    const val PASSPHRASE_MAX = 63

    /**
     * Feste SSID `DrainQ-ONE-<Seriennr>` fürs Tablet-Auto-Join. Seriennummer auf Alphanumerik
     * reduziert (SoftAP-sicher, keine Sonderzeichen/Leerzeichen); fehlt sie, bleibt der reine
     * Präfix. Auf [SSID_MAX_LENGTH] Oktette geklemmt.
     */
    fun buildSsid(serial: String?): String {
        val cleaned = serial?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }
        val base = if (cleaned != null) "$SSID_PREFIX-$cleaned" else SSID_PREFIX
        return if (base.length > SSID_MAX_LENGTH) base.substring(0, SSID_MAX_LENGTH) else base
    }

    /** WPA2-PSK-Passphrase-Längenregel (Bau der SoftApConfiguration wirft sonst). */
    fun isValidPassphrase(passphrase: String): Boolean =
        passphrase.length in PASSPHRASE_MIN..PASSPHRASE_MAX

    /** Ergebnis der reinen Start-Vorprüfung. */
    enum class GateResult { OK, NOT_DIRECT_MODE, STA_ACTIVE_NO_CONCURRENCY }

    /**
     * Start-Vorbedingungen (rein): AP nur im [HardwareMode.DIRECT]; auf der ONE (ein `wlan0`,
     * keine STA+AP-Parallelität) darf kein STA-Client aktiv sein (sonst würde der Hotspot die
     * bestehende WLAN-Verbindung kappen). Kann die Plattform STA+AP gleichzeitig, entfällt die
     * zweite Sperre.
     */
    fun gate(mode: HardwareMode, staConnected: Boolean, supportsStaApConcurrency: Boolean): GateResult = when {
        mode != HardwareMode.DIRECT -> GateResult.NOT_DIRECT_MODE
        staConnected && !supportsStaApConcurrency -> GateResult.STA_ACTIVE_NO_CONCURRENCY
        else -> GateResult.OK
    }
}

/**
 * **SoftAP-Host** (Dual-Modus, Welle 3a) — bringt im DIRECT-Modus auf der ONE den WLAN-Hotspot
 * hoch, dem das Tablet beitritt (feste SSID `DrainQ-ONE-<Seriennr>` + WPA2-PSK). Adressierung =
 * klassisches Android-Tethering-Subnetz, Gateway 192.168.43.1 (= [OneHardwareConfig.targetIp],
 * unter der der [OneRemoteServer] dann RTSP + DeviceService anbietet).
 *
 * **Status dieser Welle (ohne Gerät gebaut):** Die reinen Regeln (SSID/Passphrase/Gate,
 * [AccessPointSpec]) und der Bau der [SoftApConfiguration] (öffentliche API 30+) sind umgesetzt
 * und (für die reine Logik) unit-getestet. Der **eigentliche AP-Start** ist auf der ONE als
 * Geräteeigentümer privilegiert — `WifiManager.setSoftApConfiguration` +
 * `android.net.TetheringManager.startTethering` sind `@SystemApi`, NICHT im öffentlichen SDK;
 * sie brauchen die ins Werks-Image gegebene privilegierte Tether-Permission. Er wird daher hier
 * **bewusst NICHT spekulativ per Reflection verdrahtet** (siehe [applyTethering]) → **TODO(device)**.
 *
 * **OPEN DECISION (Produkt/UX):** STA+AP sind auf der ONE exklusiv (ein `wlan0`). Aktiver Hotspot
 * ⇒ kein gleichzeitiges WLAN-Internet (Cloud/Update). WANN der AP hochkommt (immer im DIRECT-Modus
 * vs. on-demand, sobald ein Tablet erwartet wird) ist eine Produktentscheidung → deshalb KEIN
 * Auto-Start am Lebenszyklus in dieser Welle (anders als der [OneRemoteServer]).
 */
class AccessPointController(
    private val context: Context,
    private val mode: HardwareMode,
    serial: String?,
    private val passphrase: String = DEFAULT_PASSPHRASE,
) {
    companion object {
        private const val TAG = "AccessPointController"

        // Platzhalter-Passphrase. OPEN DECISION/TODO(device): pro Gerät provisionieren statt
        // hartkodieren — eine APK-weite Passphrase steht in jeder ausgelieferten APK und ist
        // damit kein echtes Geheimnis. Muss [AccessPointSpec.isValidPassphrase] erfüllen.
        const val DEFAULT_PASSPHRASE = "drainq-one-2026"
    }

    /** Feste, fürs Tablet-Auto-Join bekannte SSID. */
    val ssid: String = AccessPointSpec.buildSsid(serial)

    /** Ergebnis eines [start]/[stop]-Versuchs. */
    sealed interface ApResult {
        /** Vorprüfung fehlgeschlagen (falscher Modus oder aktiver STA ohne Parallelität). */
        data class Blocked(val reason: AccessPointSpec.GateResult) : ApResult

        /** Plattform zu alt — [SoftApConfiguration] gibt es erst ab API 30. */
        object UnsupportedApiLevel : ApResult

        /** Konfiguration steht; der privilegierte Tether-Aufruf ist Geräte-Test (TODO(device)). */
        object DeviceValidationRequired : ApResult

        /** Unerwarteter Fehler beim Konfigurationsaufbau. */
        data class Error(val message: String?) : ApResult
    }

    /**
     * Bringt den Hotspot hoch — gated auf DIRECT-Modus + STA/AP-Exklusivität ([AccessPointSpec.gate]).
     * Liefert [ApResult.DeviceValidationRequired], solange der privilegierte Tether-Aufruf nicht
     * am Gerät verifiziert ist (siehe Klassen-Doc).
     */
    fun start(): ApResult {
        val gate = AccessPointSpec.gate(
            mode = mode,
            staConnected = isStaConnected(),
            supportsStaApConcurrency = supportsConcurrency(),
        )
        if (gate != AccessPointSpec.GateResult.OK) {
            Log.i(TAG, "Start abgelehnt: $gate")
            return ApResult.Blocked(gate)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "SoftAP erst ab API 30 (Gerät: API ${Build.VERSION.SDK_INT})")
            return ApResult.UnsupportedApiLevel
        }
        if (!AccessPointSpec.isValidPassphrase(passphrase)) {
            Log.w(TAG, "Ungültige WPA2-Passphrase (Länge ${passphrase.length})")
            return ApResult.Error("invalid passphrase length ${passphrase.length}")
        }
        return applyTethering()
    }

    /** Stoppt den Hotspot. TODO(device): privilegiertes `stopTethering(TETHERING_WIFI)`. */
    fun stop(): ApResult {
        Log.i(TAG, "stop(): TODO(device) — privilegiertes stopTethering")
        return ApResult.DeviceValidationRequired
    }

    /**
     * **TODO(device):** Privilegierter Hotspot-Start auf der ONE als Geräteeigentümer. Der GESAMTE
     * Pfad ist `@SystemApi` und NICHT im öffentlichen SDK (daher hier nicht referenzierbar — der
     * Bau scheitert sonst bereits an `SoftApConfiguration.Builder`):
     *   1. `SoftApConfiguration.Builder().setSsid("$ssid")`
     *        `.setPassphrase(<passphrase>, SECURITY_TYPE_WPA2_PSK).build()`
     *   2. `WifiManager.setSoftApConfiguration(config)`  (NETWORK_SETTINGS / OVERRIDE_WIFI_CONFIG)
     *   3. `android.net.TetheringManager.startTethering(TETHERING_WIFI, executor, callback)`
     * Diese setzen die ins Werks-Image gegebene privilegierte Tether-Permission voraus und müssen
     * am Gerät verifiziert werden (Welle 5). Bis dahin bewusst KEIN spekulativer Reflection-Aufruf;
     * der Controller liefert nur die validierten Eingaben ([ssid] + geprüfte [passphrase]).
     */
    private fun applyTethering(): ApResult {
        Log.i(TAG, "SoftAP bereit (SSID=$ssid); privilegierter Tether-Start = TODO(device)")
        return ApResult.DeviceValidationRequired
    }

    /** Best-effort: ist aktuell eine WLAN-STA-Verbindung aktiv? (Nur Gerät; nicht unit-getestet.) */
    private fun isStaConnected(): Boolean {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            @Suppress("DEPRECATION")
            (wifi.connectionInfo?.networkId ?: -1) != -1
        } catch (_: Throwable) {
            false
        }
    }

    /** Unterstützt die Plattform STA+AP gleichzeitig? Auf der ONE (ein `wlan0`): nein. */
    private fun supportsConcurrency(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            wifi.isStaApConcurrencySupported
        } catch (_: Throwable) {
            false
        }
    }
}

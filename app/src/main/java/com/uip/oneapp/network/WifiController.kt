package com.uip.oneapp.network

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Ein gefundenes WLAN-Netz für die Liste. [level] = 0..4 Balken. */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val rssi: Int,
    val secured: Boolean,
)

/**
 * Welcher Verbindungs-Pfad ist auf diesem Gerät möglich?
 *  - [PRIVILEGED]: App ist Device-Owner oder System-App → direktes [WifiManager]-Connect.
 *  - [SUGGESTION]: Stock-Gerät → [WifiNetworkSuggestion] (Android 10+) + WLAN-Einstellungen.
 */
enum class WifiPath { PRIVILEGED, SUGGESTION }

/**
 * Kapselt WLAN-Scan und -Verbindung. Zwei Pfade je nach Geräte-Rolle (siehe [WifiPath]).
 *
 * WICHTIG: WLAN-Passwörter werden NICHT persistiert und NICHT geloggt. Sie werden nur
 * unmittelbar an die Plattform-API (WifiConfiguration bzw. WifiNetworkSuggestion) übergeben.
 */
class WifiController(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    /** Erkennt zur Laufzeit, ob der privilegierte Pfad verfügbar ist. */
    fun detectPath(): WifiPath {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isDeviceOwner = try {
            dpm?.isDeviceOwnerApp(context.packageName) == true
        } catch (_: Exception) { false }
        val isSystemApp = (context.applicationInfo.flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        return if (isDeviceOwner || isSystemApp) WifiPath.PRIVILEGED else WifiPath.SUGGESTION
    }

    /**
     * Startet einen Scan und liefert die gefundenen Netze (nach Signal sortiert, SSID-eindeutig).
     * Nutzt einen einmaligen BroadcastReceiver; fällt nach Timeout auf den Cache zurück.
     * Setzt voraus, dass die Standort-/NEARBY_WIFI-Berechtigung erteilt ist.
     */
    @SuppressLint("MissingPermission")
    suspend fun scan(): List<WifiNetwork> {
        val fresh = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<List<ScanResult>> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(safeScanResults())
                    }
                }
                context.registerReceiver(
                    receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )
                cont.invokeOnCancellation {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                }
                @Suppress("DEPRECATION")
                val started = try { wifiManager.startScan() } catch (_: Exception) { false }
                // Bei gedrosseltem/abgelehntem startScan sofort den Cache verwenden.
                if (!started && cont.isActive) {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    cont.resume(safeScanResults())
                }
            }
        } ?: safeScanResults()

        return fresh
            .filter { (it.SSID ?: "").isNotBlank() }
            .map { it.toWifiNetwork() }
            .groupBy { it.ssid }
            .map { (_, group) -> group.maxByOrNull { it.rssi }!! }
            .sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission")
    private fun safeScanResults(): List<ScanResult> =
        try { wifiManager.scanResults ?: emptyList() } catch (_: Exception) { emptyList() }

    @Suppress("DEPRECATION")
    private fun ScanResult.toWifiNetwork(): WifiNetwork {
        val secured = capabilities.contains("WEP") || capabilities.contains("WPA") ||
            capabilities.contains("PSK") || capabilities.contains("EAP") ||
            capabilities.contains("SAE")
        val bars = WifiManager.calculateSignalLevel(level, 5).coerceIn(0, 4)
        return WifiNetwork(
            ssid = (SSID ?: "").removeSurrounding("\""),
            bssid = BSSID ?: "",
            level = bars,
            rssi = level,
            secured = secured,
        )
    }

    /**
     * Privilegierter Pfad: Netz direkt anlegen und verbinden (nur als Device-Owner/System wirksam).
     * Gibt true zurück, wenn das Verbinden angestoßen werden konnte.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun connectPrivileged(ssid: String, password: String, secured: Boolean): Boolean {
        return try {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                if (secured) {
                    preSharedKey = "\"$password\""
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }
            val netId = wifiManager.addNetwork(config)
            if (netId == -1) return false
            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()
            enabled
        } catch (e: Exception) {
            Log.w(TAG, "connectPrivileged fehlgeschlagen: ${e.message}")
            false
        }
    }

    /**
     * Fallback-Pfad: System-Vorschlag (Android 10+). Das System verbindet automatisch,
     * sobald das Netz verfügbar ist und der Nutzer den Vorschlag (einmalig) bestätigt.
     * Gibt true zurück, wenn der Vorschlag akzeptiert wurde.
     */
    fun suggest(ssid: String, password: String, secured: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
            if (secured && password.isNotEmpty()) builder.setWpa2Passphrase(password)
            val suggestion = builder.build()
            // Vorherige Vorschläge entfernen, damit ein neuer Versuch nicht an DUPLICATE scheitert.
            wifiManager.removeNetworkSuggestions(listOf(suggestion))
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "suggest fehlgeschlagen: ${e.message}")
            false
        }
    }

    companion object { private const val TAG = "WifiController" }
}

package com.uip.oneapp.network

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
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
 *  - [PRIVILEGED]: App ist Device-Owner oder System-App → direktes, geräteweites,
 *    persistentes [WifiManager]-Connect (addNetwork/enableNetwork).
 *  - [REQUEST]: Stock-/Test-Gerät → app-gebundene Verbindung über
 *    [WifiNetworkSpecifier] + [ConnectivityManager.requestNetwork] (System-Dialog).
 *    Hintergrund: Auf Android 10+ ist addNetwork()/enableNetwork() für normale Apps
 *    ein No-op — daher hier zwingend der Specifier-Pfad.
 */
enum class WifiPath { PRIVILEGED, REQUEST }

/**
 * Kapselt WLAN-Scan und -Verbindung. Zwei Pfade je nach Geräte-Rolle (siehe [WifiPath]).
 *
 * WICHTIG: WLAN-Passwörter werden NICHT persistiert und NICHT geloggt. Sie werden nur
 * unmittelbar an die Plattform-API (WifiConfiguration bzw. WifiNetworkSpecifier) übergeben.
 */
class WifiController(private val context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Aktive Specifier-Anfrage (REQUEST-Pfad), damit wir sie wieder freigeben können. */
    private var activeRequestCallback: ConnectivityManager.NetworkCallback? = null

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    fun isDeviceOwner(): Boolean {
        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return try { dpm?.isDeviceOwnerApp(appContext.packageName) == true } catch (_: Exception) { false }
    }

    private fun isSystemApp(): Boolean =
        (appContext.applicationInfo.flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    /** Erkennt zur Laufzeit, ob der privilegierte Pfad verfügbar ist (+ Diagnose-Log). */
    fun detectPath(): WifiPath {
        val deviceOwner = isDeviceOwner()
        val systemApp = isSystemApp()
        val path = if (deviceOwner || systemApp) WifiPath.PRIVILEGED else WifiPath.REQUEST
        Log.i(TAG, "detectPath: deviceOwner=$deviceOwner systemApp=$systemApp -> $path")
        return path
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
                        try { appContext.unregisterReceiver(this) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(safeScanResults())
                    }
                }
                appContext.registerReceiver(
                    receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )
                cont.invokeOnCancellation {
                    try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
                }
                @Suppress("DEPRECATION")
                val started = try { wifiManager.startScan() } catch (_: Exception) { false }
                // Bei gedrosseltem/abgelehntem startScan sofort den Cache verwenden.
                if (!started && cont.isActive) {
                    try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
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
     * Privilegierter Pfad: Netz direkt anlegen und verbinden (nur als Device-Owner/System wirksam,
     * dann geräteweit + persistent). Gibt true zurück, wenn das Verbinden angestoßen werden konnte.
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
            Log.i(TAG, "connectPrivileged: addNetwork -> netId=$netId (ssid=$ssid)")
            if (netId == -1) return false
            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()
            Log.i(TAG, "connectPrivileged: enableNetwork=$enabled")
            enabled
        } catch (e: Exception) {
            Log.w(TAG, "connectPrivileged fehlgeschlagen: ${e.message}")
            false
        }
    }

    /**
     * Fallback-Pfad (Android 10+, normale App): app-gebundene Verbindung über
     * [WifiNetworkSpecifier]. Das System zeigt einen Bestätigungsdialog. Bei Erfolg wird
     * der Prozess auf das Netz gebunden, damit App-/Update-Traffic darüber läuft.
     *
     * Die Callbacks melden den Verlauf an die ViewModel-Schicht; nichts wird persistiert.
     */
    fun connectViaRequest(
        ssid: String,
        password: String,
        secured: Boolean,
        onAvailable: () -> Unit,
        onUnavailable: () -> Unit,
        onLost: () -> Unit,
    ) {
        cancelRequest()
        try {
            val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
            if (secured && password.isNotEmpty()) specBuilder.setWpa2Passphrase(password)
            val specifier = specBuilder.build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "connectViaRequest: onAvailable (ssid=$ssid) -> bindProcessToNetwork")
                    try { connectivityManager.bindProcessToNetwork(network) } catch (_: Exception) {}
                    onAvailable()
                }
                override fun onUnavailable() {
                    Log.w(TAG, "connectViaRequest: onUnavailable (ssid=$ssid)")
                    onUnavailable()
                }
                override fun onLost(network: Network) {
                    Log.w(TAG, "connectViaRequest: onLost (ssid=$ssid)")
                    try { connectivityManager.bindProcessToNetwork(null) } catch (_: Exception) {}
                    onLost()
                }
            }
            activeRequestCallback = callback
            // 30 s Timeout: ohne Nutzerbestätigung/Erreichbarkeit kommt onUnavailable.
            connectivityManager.requestNetwork(request, callback, 30_000)
            Log.i(TAG, "connectViaRequest: requestNetwork gestellt (ssid=$ssid)")
        } catch (e: Exception) {
            Log.w(TAG, "connectViaRequest fehlgeschlagen: ${e.message}")
            onUnavailable()
        }
    }

    /** Aktive Specifier-Anfrage freigeben und Prozess-Bindung lösen. */
    fun cancelRequest() {
        activeRequestCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        activeRequestCallback = null
        try { connectivityManager.bindProcessToNetwork(null) } catch (_: Exception) {}
    }

    companion object { private const val TAG = "WifiController" }
}

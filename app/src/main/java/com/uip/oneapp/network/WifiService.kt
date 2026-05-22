package com.uip.oneapp.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Network
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WLAN-Service fuer das ONE-Tablet.
 *
 * Funktionen:
 *  - Scan der sichtbaren Netze (Liste mit SSID + Signal-Staerke + Security)
 *  - Verbindung mit Passwort (legacy API mit WifiConfiguration auf userdebug,
 *    Fallback auf WifiNetworkSpecifier ab Android 10)
 *  - Aktuelle Verbindung beobachten (SSID, RSSI, Status)
 *
 * Ohne Plattform-Cert sind manche WLAN-Operationen ab Android 10 restriktiv —
 * Scan und SSID-Anzeige funktionieren nur mit ACCESS_FINE_LOCATION-Permission.
 */
class WifiService(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(WifiState())
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                publishScanResults()
            }
        }
    }

    init {
        try {
            context.applicationContext.registerReceiver(
                scanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
        } catch (e: Exception) {
            Log.w(TAG, "registerReceiver failed", e)
        }
        publishCurrentConnection()
    }

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    @Suppress("DEPRECATION")
    fun setWifiEnabled(enabled: Boolean): Boolean {
        return try {
            wifiManager.setWifiEnabled(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "setWifiEnabled failed", e)
            false
        }
    }

    /** Triggert WLAN-Scan und veroeffentlicht das Ergebnis ueber state. */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!wifiManager.isWifiEnabled) {
            setWifiEnabled(true)
        }
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
            publishScanResults()
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishScanResults() {
        try {
            val results = wifiManager.scanResults
                .filter { it.SSID.isNotBlank() }
                .sortedByDescending { it.level }
                .distinctBy { it.SSID }
                .map { it.toEntry() }
            _state.value = _state.value.copy(scanResults = results)
        } catch (e: SecurityException) {
            Log.w(TAG, "scanResults SecurityException — ACCESS_FINE_LOCATION fehlt?", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishCurrentConnection() {
        val info = try { wifiManager.connectionInfo } catch (_: Exception) { null }
        val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        _state.value = _state.value.copy(
            currentSsid = ssid,
            currentRssi = info?.rssi ?: 0,
            connected = ssid != null && info?.networkId != -1
        )
    }

    /**
     * Verbindet mit einem Netzwerk. Auf Android 9 und aelter (userdebug) nutzen wir
     * die legacy-API; auf Android 10+ wuerde man WifiNetworkSpecifier brauchen, was
     * aber Benutzer-Bestaetigung erfordert. Da userdebug + adb root, koennen wir
     * versuchen die legacy-API zu nutzen — sie ist noch eingeschaltet auf Geraeten
     * mit ro.debuggable=1.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun connect(ssid: String, password: String?): Boolean {
        try {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                if (password.isNullOrEmpty()) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else {
                    preSharedKey = "\"$password\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
            }
            val netId = wifiManager.addNetwork(config)
            if (netId == -1) {
                Log.w(TAG, "addNetwork returned -1 for $ssid")
                return false
            }
            wifiManager.disconnect()
            wifiManager.enableNetwork(netId, true)
            val ok = wifiManager.reconnect()
            Log.d(TAG, "connect($ssid) → netId=$netId reconnect=$ok")
            publishCurrentConnection()
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "connect($ssid) failed", e)
            return false
        }
    }

    fun refreshConnection() = publishCurrentConnection()

    private fun ScanResult.toEntry(): WifiScanEntry {
        val cap = capabilities ?: ""
        val secured = cap.contains("WPA") || cap.contains("WEP") || cap.contains("EAP")
        return WifiScanEntry(
            ssid = SSID ?: "",
            bssid = BSSID ?: "",
            level = level,
            secured = secured,
            capabilities = cap
        )
    }

    companion object { private const val TAG = "WifiService" }
}

data class WifiScanEntry(
    val ssid: String,
    val bssid: String,
    val level: Int,           // dBm, e.g. -50 (strong) … -90 (weak)
    val secured: Boolean,
    val capabilities: String
) {
    /** 0..4 — fuer Anzeige von Signalbalken. */
    fun signalBars(): Int = WifiManager.calculateSignalLevel(level, 5).coerceIn(0, 4)
}

data class WifiState(
    val scanResults: List<WifiScanEntry> = emptyList(),
    val currentSsid: String? = null,
    val currentRssi: Int = 0,
    val connected: Boolean = false
)

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
 * WICHTIG: WLAN-Passwörter werden NICHT geloggt und hier NICHT persistiert. Sie werden
 * unmittelbar an die Plattform-API (WifiConfiguration bzw. WifiNetworkSpecifier) übergeben.
 * Einzige Ausnahme (Auto-Reconnect W1): Credentials gekoppelter `DrainQ-ONE-*`-Hotspots
 * speichert das NetworkViewModel verschlüsselt im [KnownOneStore] (Keystore, at rest).
 */
class WifiController(private val context: Context) : AutoConnectWifi {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Aktive Specifier-Anfrage (REQUEST-Pfad), damit wir sie wieder freigeben können. */
    private var activeRequestCallback: ConnectivityManager.NetworkCallback? = null

    /** Watcher auf das per [bindToCurrentWifi] gebundene System-WLAN (Trigger C). */
    private var watchedNetworkCallback: ConnectivityManager.NetworkCallback? = null

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
    override suspend fun scan(): List<WifiNetwork> {
        val fresh = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<List<ScanResult>> { cont ->
                // Atomarer Resume-Guard: Receiver (Main-Thread, feuert bei JEDEM System-Scan)
                // und der !started-Fallback (Aufrufer-Thread) können sonst beide das nicht-
                // atomare isActive-Check-then-resume passieren → IllegalStateException.
                val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        try { appContext.unregisterReceiver(this) } catch (_: Exception) {}
                        if (resumed.compareAndSet(false, true)) cont.resume(safeScanResults())
                    }
                }
                appContext.registerReceiver(
                    receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )
                cont.invokeOnCancellation {
                    resumed.set(true)
                    try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
                }
                @Suppress("DEPRECATION")
                val started = try { wifiManager.startScan() } catch (_: Exception) { false }
                // Bei gedrosseltem/abgelehntem startScan sofort den Cache verwenden.
                if (!started && resumed.compareAndSet(false, true)) {
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
    override fun connectViaRequest(
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

    /**
     * SSID des aktuell verbundenen WLANs (ohne Anführungszeichen), `null` wenn nicht
     * verbunden oder die Plattform sie verweigert (`<unknown ssid>` ohne Standort-/
     * NEARBY-Berechtigung). Für per Specifier/Suggestion selbst verbundene Netze liefert
     * Android die SSID auch ohne Standortberechtigung — genau der Auto-Reconnect-Fall.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun currentWifiSsid(): String? = try {
        val raw = wifiManager.connectionInfo?.ssid ?: ""
        val ssid = raw.removeSurrounding("\"")
        if (ssid.isEmpty() || ssid.equals("<unknown ssid>", ignoreCase = true)) null else ssid
    } catch (_: Exception) {
        null
    }

    /**
     * Bindet den Prozess an das aktive WLAN-Netz (Auto-Reconnect Trigger C: System ist
     * bereits im ONE-Hotspot, z. B. via WifiNetworkSuggestion). Nötig, weil ein Netz ohne
     * Internet sonst nicht als App-Default-Route dient. [onLost] wird über einen
     * NetworkCallback-Watcher genau einmal gemeldet, wenn dieses Netz wegfällt — der
     * System-Join-Pfad hat sonst keinen Abriss-Kanal. True bei Erfolg.
     */
    @Suppress("DEPRECATION")
    override fun bindToCurrentWifi(onLost: () -> Unit): Boolean = try {
        // allNetworks (deprecated) statt activeNetwork: ein internetloses WLAN ist oft
        // NICHT das Default-Netz — hier zählt allein der WIFI-Transport.
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifiNetwork != null) {
            connectivityManager.bindProcessToNetwork(wifiNetwork)
            watchNetwork(wifiNetwork, onLost)
            Log.i(TAG, "bindToCurrentWifi: Prozess an aktives WLAN gebunden")
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.w(TAG, "bindToCurrentWifi fehlgeschlagen: ${e.message}")
        false
    }

    /** Watcher auf das gebundene System-WLAN (Trigger C) — genau ein onLost, dann Selbst-Cleanup. */
    private fun watchNetwork(network: Network, onLost: () -> Unit) {
        unwatchNetwork()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(lost: Network) {
                if (lost != network) return
                Log.w(TAG, "watchNetwork: gebundenes WLAN verloren")
                try { connectivityManager.bindProcessToNetwork(null) } catch (_: Exception) {}
                try { connectivityManager.unregisterNetworkCallback(this) } catch (_: Exception) {}
                if (watchedNetworkCallback === this) watchedNetworkCallback = null
                onLost()
            }
        }
        watchedNetworkCallback = callback
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                callback,
            )
        } catch (e: Exception) {
            watchedNetworkCallback = null
            Log.w(TAG, "watchNetwork fehlgeschlagen: ${e.message}")
        }
    }

    private fun unwatchNetwork() {
        watchedNetworkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        watchedNetworkCallback = null
    }

    /** Aktive Specifier-Anfrage freigeben und Prozess-Bindung lösen (inkl. Trigger-C-Watcher). */
    fun cancelRequest() {
        activeRequestCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        activeRequestCallback = null
        // Auch den System-WLAN-Watcher lösen: eine neue Verbindung ersetzt die alte Bindung —
        // sein spätes onLost gälte sonst einem längst irrelevanten Netz (Leak + Fehlmeldung).
        unwatchNetwork()
        try { connectivityManager.bindProcessToNetwork(null) } catch (_: Exception) {}
    }

    // ===== Auto-Reconnect W2: WifiNetworkSuggestion (Null-Tap-Pfad, rein additiv) =====
    //
    // Zusätzlich zur Specifier-Wiederverbindung (W1) wird die bekannte ONE als Suggestion
    // hinterlegt: dann joint ANDROID SELBST das Netz, sobald es in Reichweite ist — der
    // AutoConnector erkennt das (Trigger C) und bindet nur noch den Prozess: 0 Taps.
    // Erstnutzung zeigt einmalig eine System-Notification (Zustimmung des Nutzers).
    // Bekanntes Risiko am Gerät: internetlose Suggestions kann Android/Samsung abwerten —
    // deshalb bleibt der W1-Specifier-Pfad vollständiger Fallback.

    /**
     * Hinterlegt die ONE als [WifiNetworkSuggestion] (API 29+; darunter No-op → W1-Pfad).
     * Ein Duplikat gilt als Erfolg. Die Passphrase geht nur an die Plattform-API.
     */
    fun addSuggestion(ssid: String, passphrase: String, secured: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val status = wifiManager.addNetworkSuggestions(listOf(buildSuggestion(ssid, passphrase, secured)))
            val ok = status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
            Log.i(TAG, "addSuggestion(ssid=$ssid): status=$status")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "addSuggestion fehlgeschlagen: ${e.message}")
            false
        }
    }

    /**
     * Entfernt die Suggestion der ONE rückstandsfrei ("Vergessen"). Muss mit denselben
     * Credentials gebaut werden wie beim Hinzufügen — die Plattform matcht per equals.
     */
    fun removeSuggestion(ssid: String, passphrase: String, secured: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val status = wifiManager.removeNetworkSuggestions(listOf(buildSuggestion(ssid, passphrase, secured)))
            Log.i(TAG, "removeSuggestion(ssid=$ssid): status=$status")
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "removeSuggestion fehlgeschlagen: ${e.message}")
            false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun buildSuggestion(ssid: String, passphrase: String, secured: Boolean): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (secured && passphrase.isNotEmpty()) builder.setWpa2Passphrase(passphrase)
        return builder.build()
    }

    companion object { private const val TAG = "WifiController" }
}

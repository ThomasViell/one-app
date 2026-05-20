package com.uip.oneapp.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hotspot-Service fuer das ONE-Tablet.
 *
 * Nutzt verdeckte WifiManager-APIs via Reflection, weil das offizielle
 * TetheringManager API TETHER_PRIVILEGED-Permission braucht (nur via
 * Plattform-Cert verfuegbar).
 *
 * Auf userdebug-Builds (wie unserem ONE-Tablet, adb root verfuegbar) gehen die
 * Reflection-Aufrufe meist durch. Falls nicht, faellt der Service auf den
 * LocalOnlyHotspot zurueck (Android-Standard, in-App, kein Internet-Sharing).
 *
 * Default-Config:
 *  SSID     = "DrainQ-ONE-<deviceid4>"
 *  Passwort = "drainq2026"        (kann via setApConfig geaendert werden)
 *  Security = WPA2-PSK
 */
class HotspotService(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _state = MutableStateFlow(HotspotState())
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    private var localOnlyReservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun isHotspotActive(): Boolean {
        // Reflection: WifiManager.isWifiApEnabled
        return try {
            val method = wifiManager.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wifiManager) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isWifiApEnabled via reflection failed: ${e.message}")
            false
        }
    }

    /** Aktiviert Hotspot mit Standard-Config oder uebergebener Config. */
    @SuppressLint("MissingPermission")
    fun startHotspot(ssid: String = DEFAULT_SSID, password: String = DEFAULT_PASS): Boolean {
        // Versuche zuerst Reflection auf legacy API (vollwertiger Hotspot mit fester SSID)
        if (startViaReflection(ssid, password)) {
            _state.value = HotspotState(active = true, ssid = ssid, password = password, mode = HotspotMode.LegacyAp)
            return true
        }

        // Fallback: LocalOnly Hotspot (in-App only, kein Internet)
        return startLocalOnly()
    }

    @SuppressLint("MissingPermission")
    fun stopHotspot(): Boolean {
        localOnlyReservation?.let {
            try { it.close() } catch (_: Exception) {}
            localOnlyReservation = null
        }
        // Reflection: WifiManager.setWifiApEnabled(null, false)
        val ok = try {
            val setApEnabled = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                Class.forName("android.net.wifi.WifiConfiguration"),
                java.lang.Boolean.TYPE
            )
            setApEnabled.invoke(wifiManager, null, false) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "stopHotspot via reflection failed: ${e.message}")
            false
        }
        _state.value = HotspotState(active = false)
        return ok
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun startViaReflection(ssid: String, password: String): Boolean {
        return try {
            // WifiConfiguration aufbauen
            val wcClass = Class.forName("android.net.wifi.WifiConfiguration")
            val config = wcClass.getDeclaredConstructor().newInstance()
            wcClass.getDeclaredField("SSID").set(config, ssid)
            wcClass.getDeclaredField("preSharedKey").set(config, password)
            // allowedKeyManagement.set(WPA_PSK = 4)
            val akmField = wcClass.getDeclaredField("allowedKeyManagement")
            val akm = akmField.get(config) as java.util.BitSet
            akm.set(4)  // WPA_PSK
            akmField.set(config, akm)

            // setWifiApEnabled(config, true)
            val setApEnabled = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                wcClass,
                java.lang.Boolean.TYPE
            )
            // Hinweis: WLAN muss vor AP-Start aus sein
            if (wifiManager.isWifiEnabled) {
                wifiManager.setWifiEnabled(false)
                Thread.sleep(400)
            }
            val ok = setApEnabled.invoke(wifiManager, config, true) as? Boolean ?: false
            Log.d(TAG, "startViaReflection: setWifiApEnabled=$ok ssid='$ssid'")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Reflection-Hotspot fehlgeschlagen: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocalOnly(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        localOnlyReservation = reservation
                        val cfg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            reservation.softApConfiguration
                        } else null
                        @Suppress("DEPRECATION")
                        val legacy = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            reservation.wifiConfiguration
                        } else null
                        val ssid = cfg?.ssid ?: legacy?.SSID ?: "DrainQ-ONE"
                        val pwd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            cfg?.passphrase ?: ""
                        } else legacy?.preSharedKey ?: ""
                        _state.value = HotspotState(
                            active = true, ssid = ssid, password = pwd, mode = HotspotMode.LocalOnly
                        )
                        Log.d(TAG, "LocalOnly hotspot started: $ssid")
                    }
                    override fun onStopped() {
                        _state.value = HotspotState(active = false)
                        localOnlyReservation = null
                    }
                    override fun onFailed(reason: Int) {
                        Log.w(TAG, "LocalOnly hotspot failed: reason=$reason")
                        _state.value = HotspotState(active = false, lastError = "Failed: $reason")
                    }
                },
                null
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "startLocalOnlyHotspot failed", e)
            _state.value = HotspotState(active = false, lastError = e.message)
            false
        }
    }

    companion object {
        private const val TAG = "HotspotService"
        const val DEFAULT_SSID = "DrainQ-ONE"
        const val DEFAULT_PASS = "drainq2026"
    }
}

enum class HotspotMode { LegacyAp, LocalOnly }

data class HotspotState(
    val active: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val mode: HotspotMode = HotspotMode.LegacyAp,
    val lastError: String? = null
)

package com.uip.oneapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Art der aktiven Verbindung (für die Online-Status-Anzeige im Netzwerk-Screen). */
enum class ConnectionType { NONE, WIFI, ETHERNET, USB_TETHER, BLUETOOTH, CELLULAR, VPN, OTHER }

data class OnlineStatus(
    val online: Boolean = false,
    val type: ConnectionType = ConnectionType.NONE,
)

/**
 * Beobachtet live die Standard-Netzwerkverbindung via [ConnectivityManager.NetworkCallback]
 * und stellt einen [OnlineStatus]-Flow bereit (verbunden ja/nein + Verbindungstyp).
 *
 * USB-/Bluetooth-Tethering: Auf der Tablet-Seite erscheint USB-Tethering meist als
 * ETHERNET-Transport mit einem Interface-Namen wie `usb0`/`rndis0`; Bluetooth-PAN als
 * BLUETOOTH-Transport bzw. `bt-pan`. Wir werten daher zusätzlich die [LinkProperties]
 * (Interface-Name) aus, um die Tethering-Typen sauber zu unterscheiden.
 */
class ConnectivityMonitor(context: Context) {

    // try/catch: BridgeContext (Paparazzi) throws AssertionError for unsupported services;
    // null cm causes recompute() to emit offline status immediately — correct for JVM tests.
    private val cm: ConnectivityManager? = try {
        context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (_: Throwable) { null }

    private val _status = MutableStateFlow(OnlineStatus())
    val status: StateFlow<OnlineStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = recompute()
        override fun onLost(network: Network) = recompute()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = recompute()
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = recompute()
    }

    init {
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) { /* z. B. fehlende ACCESS_NETWORK_STATE — Status bleibt offline */ }
        recompute()
    }

    /** Erneut auswerten (z. B. beim Öffnen des Screens). */
    fun refresh() = recompute()

    private fun recompute() {
        val network = cm?.activeNetwork
        val caps = network?.let { cm?.getNetworkCapabilities(it) }
        if (network == null || caps == null) {
            _status.value = OnlineStatus(online = false, type = ConnectionType.NONE)
            return
        }
        val iface = (cm?.getLinkProperties(network)?.interfaceName ?: "").lowercase()
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectionType.BLUETOOTH
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                if (iface.startsWith("usb") || iface.startsWith("rndis")) ConnectionType.USB_TETHER
                else ConnectionType.ETHERNET
            iface.startsWith("usb") || iface.startsWith("rndis") -> ConnectionType.USB_TETHER
            iface.startsWith("bt") -> ConnectionType.BLUETOOTH
            else -> ConnectionType.OTHER
        }
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        _status.value = OnlineStatus(online = online, type = type)
    }
}

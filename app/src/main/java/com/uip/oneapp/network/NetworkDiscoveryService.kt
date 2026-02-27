package com.uip.oneapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class WifiInfo(
    val isConnected: Boolean = false,
    val ssid: String = "",
    val localIp: String = "",
    val gateway: String = ""
)

data class DiscoveredHost(
    val ip: String,
    val hostname: String = "",
    val openPorts: List<Int> = emptyList(),
    val isRtspCandidate: Boolean = false
)

class NetworkDiscoveryService(private val context: Context) {

    private val _wifiInfo = MutableStateFlow(WifiInfo())
    val wifiInfo: StateFlow<WifiInfo> = _wifiInfo.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val rtspPorts = listOf(554, 8554, 8080, 8081, 80)

    fun refreshWifiInfo() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isWifi) {
            @Suppress("DEPRECATION")
            val wifiInfoSys = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            val ssid = wifiInfoSys.ssid?.removeSurrounding("\"") ?: "Unknown"
            val ipInt = wifiInfoSys.ipAddress
            val localIp = intToIp(ipInt)

            val dhcpInfo = wifiManager.dhcpInfo
            val gateway = intToIp(dhcpInfo.gateway)

            _wifiInfo.value = WifiInfo(
                isConnected = true,
                ssid = ssid,
                localIp = localIp,
                gateway = gateway
            )
        } else {
            _wifiInfo.value = WifiInfo(isConnected = false)
        }
    }

    suspend fun scanNetwork(): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        _scanProgress.value = 0f
        _discoveredHosts.value = emptyList()

        val currentWifi = _wifiInfo.value
        if (!currentWifi.isConnected || currentWifi.localIp.isEmpty()) {
            _isScanning.value = false
            return@withContext emptyList()
        }

        val subnet = currentWifi.localIp.substringBeforeLast(".")
        val hosts = mutableListOf<DiscoveredHost>()
        val totalHosts = 254

        coroutineScope {
            // Scan in batches of 32 to avoid overwhelming the network
            val batchSize = 32
            for (batchStart in 1..totalHosts step batchSize) {
                val batchEnd = minOf(batchStart + batchSize - 1, totalHosts)
                val deferreds = (batchStart..batchEnd).map { i ->
                    async {
                        val ip = "$subnet.$i"
                        val reachable = try {
                            InetAddress.getByName(ip).isReachable(300)
                        } catch (_: Exception) {
                            false
                        }

                        if (reachable) {
                            val openPorts = checkRtspPorts(ip)
                            val hostname = try {
                                InetAddress.getByName(ip).hostName
                            } catch (_: Exception) {
                                ""
                            }
                            DiscoveredHost(
                                ip = ip,
                                hostname = if (hostname != ip) hostname else "",
                                openPorts = openPorts,
                                isRtspCandidate = openPorts.isNotEmpty()
                            )
                        } else null
                    }
                }
                val results = deferreds.awaitAll().filterNotNull()
                hosts.addAll(results)
                _discoveredHosts.value = hosts.toList()
                _scanProgress.value = batchEnd.toFloat() / totalHosts
            }
        }

        _isScanning.value = false
        _scanProgress.value = 1f
        hosts.toList()
    }

    private fun checkRtspPorts(ip: String): List<Int> {
        return rtspPorts.filter { port ->
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 200)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}

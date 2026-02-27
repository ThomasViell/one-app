package com.uip.oneapp.ui.screens.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uip.oneapp.network.DiscoveredHost
import com.uip.oneapp.network.OneHardwareState
import com.uip.oneapp.network.RtspTestResult
import com.uip.oneapp.ui.components.VlcVideoPlayer
import com.uip.oneapp.ui.components.VideoPlayerPlaceholder
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Connected
import com.uip.oneapp.ui.theme.Connecting
import com.uip.oneapp.ui.theme.DarkSurfaceVariant
import com.uip.oneapp.ui.theme.Disconnected
import com.uip.oneapp.ui.theme.MeterBlue
import com.uip.oneapp.ui.theme.StatusGreen
import com.uip.oneapp.ui.theme.StatusRed
import com.uip.oneapp.ui.theme.StatusYellow
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Left column: Controls
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .padding(end = 8.dp)
        ) {
            // WiFi Status
            item {
                WifiStatusCard(state)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Network Scan
            item {
                NetworkScanCard(
                    state = state,
                    onScan = { viewModel.startNetworkScan() },
                    onTestRtsp = { viewModel.testRtspOnHost(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // RTSP Test Results
            if (state.rtspTestResults.isNotEmpty()) {
                item {
                    RtspResultsCard(
                        results = state.rtspTestResults,
                        onConnect = { viewModel.connectToStream(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Manual URL
            item {
                ManualUrlCard(
                    url = state.manualRtspUrl,
                    onUrlChange = { viewModel.updateManualUrl(it) },
                    onTest = { viewModel.testManualUrl() },
                    onConnect = { viewModel.connectToStream(state.manualRtspUrl) },
                    isTesting = state.isTestingRtsp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Hardware Status
            item {
                HardwareStatusCard(
                    hardwareState = state.hardwareState,
                    isProbing = state.isProbing,
                    onProbe = { viewModel.probeHardware() },
                    onCycleLight = { viewModel.cycleLightPower() },
                    onCycleFrequency = { viewModel.cycleFrequency() },
                    onResetMeterAbsolute = { viewModel.resetMeterAbsolute() },
                    onResetMeterRelative = { viewModel.resetMeterRelative() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Log
            item {
                LogCard(messages = state.logMessages)
            }
        }

        // Right column: Video Preview
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
        ) {
            // Stream Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = S("stream_preview"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (state.streamStatus) {
                                StreamStatus.IDLE -> S("status_not_connected")
                                StreamStatus.CONNECTING -> S("status_connecting")
                                StreamStatus.CONNECTED -> S("status_connected")
                                StreamStatus.ERROR -> S("status_error")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state.streamStatus) {
                                StreamStatus.IDLE -> Color.Gray
                                StreamStatus.CONNECTING -> Connecting
                                StreamStatus.CONNECTED -> Connected
                                StreamStatus.ERROR -> Disconnected
                            }
                        )
                    }
                    if (state.activeRtspUrl.isNotEmpty()) {
                        IconButton(onClick = { viewModel.disconnectStream() }) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = S("stop"),
                                tint = StatusRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Video Player
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.activeRtspUrl.isNotEmpty()) {
                    VlcVideoPlayer(
                        rtspUrl = state.activeRtspUrl,
                        modifier = Modifier.fillMaxSize(),
                        onConnected = { viewModel.onStreamConnected() },
                        onError = { viewModel.onStreamError(it) }
                    )
                } else {
                    VideoPlayerPlaceholder(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun WifiStatusCard(state: ConnectionUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.wifiInfo.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (state.wifiInfo.isConnected) Connected else Disconnected,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = S("wifi_status"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.wifiInfo.isConnected) {
                InfoRow(S("ssid"), state.wifiInfo.ssid)
                InfoRow(S("ip_address"), state.wifiInfo.localIp)
                InfoRow(S("gateway"), state.wifiInfo.gateway)

                val isOneNetwork = state.wifiInfo.ssid.contains("ONE", ignoreCase = true)
                if (isOneNetwork) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = S("one_network_detected"),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGreen
                        )
                    }
                }
            } else {
                Text(
                    text = S("wifi_not_connected"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Disconnected
                )
                Text(
                    text = S("wifi_connect_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun NetworkScanCard(
    state: ConnectionUiState,
    onScan: () -> Unit,
    onTestRtsp: (DiscoveredHost) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("network_scan"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = onScan,
                    enabled = !state.isScanning
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (state.isScanning) S("scanning") else S("scan"))
                }
            }

            if (state.isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = state.scanProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(state.scanProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            if (state.discoveredHosts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = DarkSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                state.discoveredHosts.forEach { host ->
                    HostItem(
                        host = host,
                        isTestingThis = state.isTestingRtsp && state.testingIp == host.ip,
                        onTestRtsp = { onTestRtsp(host) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else if (!state.isScanning && state.wifiInfo.isConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = S("no_scan_yet"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun HostItem(
    host: DiscoveredHost,
    isTestingThis: Boolean,
    onTestRtsp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = host.ip,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (host.hostname.isNotEmpty()) {
                Text(
                    text = host.hostname,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            if (host.openPorts.isNotEmpty()) {
                Text(
                    text = "Ports: ${host.openPorts.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (host.isRtspCandidate) StatusGreen else Color.Gray
                )
            }
        }
        FilledTonalButton(
            onClick = onTestRtsp,
            enabled = !isTestingThis
        ) {
            if (isTestingThis) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(S("rtsp"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RtspResultsCard(
    results: List<RtspTestResult>,
    onConnect: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = S("rtsp_results"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            val reachable = results.filter { it.reachable }
            val unreachable = results.filter { !it.reachable }

            if (reachable.isNotEmpty()) {
                Text(
                    text = "${S("reachable")} (${reachable.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                reachable.forEach { result ->
                    RtspResultItem(result = result, onConnect = { onConnect(result.url) })
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            if (unreachable.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${S("not_reachable")} (${unreachable.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Show only first few unreachable
                unreachable.take(3).forEach { result ->
                    Text(
                        text = result.url,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
                if (unreachable.size > 3) {
                    Text(
                        text = S("and_more").replace("{count}", "${unreachable.size - 3}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
private fun RtspResultItem(
    result: RtspTestResult,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.url,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Response: ${result.responseCode}",
                style = MaterialTheme.typography.labelSmall,
                color = if (result.responseCode.startsWith("2")) StatusGreen else Connecting
            )
        }
        Button(
            onClick = onConnect,
            modifier = Modifier.padding(start = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(S("stream"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ManualUrlCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
    isTesting: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = S("manual_rtsp_url"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("rtsp://192.168.1.100:554/stream") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = url.isNotBlank() && !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S("test"))
                }
                Button(
                    onClick = onConnect,
                    enabled = url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S("start_stream"))
                }
            }
        }
    }
}

@Composable
private fun HardwareStatusCard(
    hardwareState: OneHardwareState,
    isProbing: Boolean,
    onProbe: () -> Unit,
    onCycleLight: () -> Unit,
    onCycleFrequency: () -> Unit,
    onResetMeterAbsolute: () -> Unit,
    onResetMeterRelative: () -> Unit
) {
    val cable = hardwareState.cableController
    val crawler = hardwareState.crawlerController
    val conn = hardwareState.connectionStatus
    val hasData = conn.cableControllerReachable || conn.crawlerControllerReachable

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S("hardware_status"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = onProbe,
                    enabled = !isProbing
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isProbing) S("searching") else S("search"))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!conn.probeCompleted && !isProbing) {
                Text(
                    text = S("hardware_not_searched"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else if (!hasData && conn.probeCompleted) {
                Text(
                    text = S("no_hardware_reachable"),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed
                )
            } else {
                // Light - controlled via command sending, status tracked locally
                val lightText = when {
                    !crawler.lightAvailable -> S("status_not_connected")
                    crawler.lightOn == true -> S("light_on") + (crawler.frontLightPower?.let { " ($it%)" } ?: "")
                    crawler.lightOn == false -> S("light_off")
                    else -> S("light_off")
                }
                val lightColor = when {
                    !crawler.lightAvailable -> Color.Gray
                    crawler.lightOn == true -> StatusGreen
                    else -> StatusRed
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = lightColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S("light"), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = lightText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (conn.tcpConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onCycleLight,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (crawler.lightOn == true) S("light_level") else S("light_on"),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sonde/Laser
                val laserText = when {
                    crawler.laserOn == null -> S("no_data")
                    crawler.laserOn -> S("light_on") + (crawler.sondeFrequency?.let { " ($it)" } ?: "")
                    else -> S("light_off")
                }
                val laserColor = when (crawler.laserOn) {
                    true -> StatusGreen
                    false -> StatusRed
                    null -> Color.Gray
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        tint = laserColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S("sonde"), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = laserText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (conn.tcpConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onCycleFrequency,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (crawler.laserOn == true) S("frequency") else S("light_on"),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Meter - Absolute (distance)
                val absoluteText = cable.meterReading?.let {
                    String.format("%.2f m", it)
                } ?: S("no_data")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Straighten,
                        contentDescription = null,
                        tint = if (cable.meterReading != null) MeterBlue else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S("meter_absolute"), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = absoluteText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (conn.tcpConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onResetMeterAbsolute,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("0", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Meter - Relative (currentDistance)
                val relativeText = cable.currentDistance?.let {
                    String.format("%.2f m", it)
                } ?: S("no_data")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Straighten,
                        contentDescription = null,
                        tint = if (cable.currentDistance != null) MeterBlue else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S("meter_distance"), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = relativeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (conn.tcpConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onResetMeterRelative,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("0", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Battery (if available)
                cable.batteryLevel?.let { battery ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BatteryStd,
                            contentDescription = null,
                            tint = when {
                                battery > 50 -> StatusGreen
                                battery > 20 -> StatusYellow
                                else -> StatusRed
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("battery"), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "$battery%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Connection info
            if (conn.probeCompleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = DarkSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                if (conn.cableControllerReachable) {
                    Text(
                        text = "${S("cable_label")} ${conn.cableControllerIp}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = StatusGreen
                    )
                }
                if (conn.crawlerControllerReachable) {
                    Text(
                        text = "${S("crawler_label")} ${conn.crawlerControllerIp}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = StatusGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun LogCard(messages: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = S("log"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (messages.isEmpty()) {
                Text(
                    text = S("no_activity_yet"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    messages.take(15).forEach { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

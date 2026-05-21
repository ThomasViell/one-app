package com.uip.oneapp.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.uip.oneapp.network.HotspotService
import com.uip.oneapp.network.HotspotMode
import com.uip.oneapp.network.WifiScanEntry
import com.uip.oneapp.network.WifiService
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import org.koin.compose.koinInject

/**
 * Zwei Settings-Sektionen fuer das ONE-Tablet:
 *  - WLAN-Verwaltung (Scan + Verbindung)
 *  - Hotspot (vollwertiger AP fuer Video-Streaming zu Drittgeraeten)
 *
 * Beide sind als eigenstaendige Composables fuer SettingsScreen.kt gedacht.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSettingsSection(
    wifiService: WifiService = koinInject()
) {
    val state by wifiService.state.collectAsState()
    var wifiEnabled by remember { mutableStateOf(wifiService.isWifiEnabled()) }
    var selected by remember { mutableStateOf<WifiScanEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(wifiEnabled) {
        wifiService.refreshConnection()
        if (wifiEnabled) wifiService.startScan()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.PanelEdgePadding)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.connected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.IconSizeXLarge)
                )
                Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(S("wifi_title"), style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize)
                    Text(
                        when {
                            !wifiEnabled -> LocalizationManager.t("state_disabled")
                            state.connected -> LocalizationManager.t("wifi_connected_status", state.currentSsid ?: "?", "${state.currentRssi}")
                            else -> LocalizationManager.t("state_enabled")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = wifiEnabled,
                    onCheckedChange = {
                        wifiEnabled = it
                        wifiService.setWifiEnabled(it)
                    }
                )
            }

            AnimatedVisibility(visible = wifiEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    Button(
                        onClick = { wifiService.startScan() },
                        modifier = Modifier.fillMaxWidth().height(Dimensions.TouchMedium)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSizeMedium))
                        Spacer(modifier = Modifier.width(Dimensions.ButtonIconSpacing))
                        Text(S("wifi_scan_btn"), fontSize = Dimensions.ButtonLabelFontSize,
                            fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    if (state.scanResults.isEmpty()) {
                        Text(
                            S("wifi_no_networks"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Limit auf 8 Netze damit Settings nicht unendlich wird
                        state.scanResults.take(12).forEach { net ->
                            WifiNetworkRow(
                                net = net,
                                isCurrent = net.ssid == state.currentSsid,
                                onClick = {
                                    if (net.ssid == state.currentSsid) return@WifiNetworkRow
                                    selected = net
                                    passwordInput = ""
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Passwort-Dialog
    val target = selected
    if (target != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
            title = { Text(target.ssid) },
            text = {
                Column {
                    if (target.secured) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text(S("wifi_password")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showPassword) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    } else {
                        Text(S("wifi_open_network"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    wifiService.connect(target.ssid, if (target.secured) passwordInput else null)
                    selected = null
                }) { Text(S("connect")) }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text(S("cancel")) }
            }
        )
    }
}

@Composable
private fun WifiNetworkRow(net: WifiScanEntry, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Dimensions.MediumSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (net.signalBars()) {
                0 -> Icons.Default.SignalWifi0Bar
                1 -> Icons.Default.NetworkWifi1Bar
                2 -> Icons.Default.NetworkWifi2Bar
                3 -> Icons.Default.NetworkWifi3Bar
                else -> Icons.Default.Wifi
            },
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.IconSizeMedium)
        )
        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                net.ssid,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${if (net.secured) LocalizationManager.t("wifi_secured") else LocalizationManager.t("wifi_open_state")}  ${net.level} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (net.secured) {
            Icon(Icons.Default.Lock, contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSizeSmall))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotSettingsSection(
    hotspotService: HotspotService = koinInject()
) {
    val state by hotspotService.state.collectAsState()
    var active by remember { mutableStateOf(hotspotService.isHotspotActive()) }
    var ssid by remember { mutableStateOf(HotspotService.DEFAULT_SSID) }
    var password by remember { mutableStateOf(HotspotService.DEFAULT_PASS) }
    var showPwd by remember { mutableStateOf(false) }

    LaunchedEffect(state.active) { active = state.active }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.PanelEdgePadding)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.IconSizeXLarge)
                )
                Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(S("hotspot_title"), style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize)
                    val hotspotLastError = state.lastError
                    Text(
                        when {
                            state.active && state.mode == HotspotMode.LegacyAp ->
                                LocalizationManager.t("hotspot_active_status", state.ssid)
                            state.active && state.mode == HotspotMode.LocalOnly ->
                                LocalizationManager.t("hotspot_active_local", state.ssid)
                            hotspotLastError != null -> LocalizationManager.t("hotspot_error", hotspotLastError)
                            else -> LocalizationManager.t("state_disabled")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = {
                        active = it
                        if (it) hotspotService.startHotspot(ssid, password)
                        else hotspotService.stopHotspot()
                    }
                )
            }

            AnimatedVisibility(visible = !active) {
                Column {
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text(S("wifi_network_name_label")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(S("wifi_password_min8_label")) },
                        singleLine = true,
                        visualTransformation = if (showPwd) VisualTransformation.None
                                                else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(
                                    if (showPwd) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(visible = state.active) {
                Column {
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    HotspotInfoRow("SSID", state.ssid)
                    HotspotInfoRow(S("wifi_password"), state.password)
                    HotspotInfoRow(
                        S("hotspot_mode"),
                        if (state.mode == HotspotMode.LegacyAp) S("hotspot_mode_legacy")
                        else S("hotspot_mode_local_only")
                    )
                }
            }
        }
    }
}

@Composable
private fun HotspotInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

package com.uip.oneapp.ui.screens.network

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.uip.oneapp.network.AutoConnectPhase
import com.uip.oneapp.network.ConnectionType
import com.uip.oneapp.network.WifiNetwork
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqButtonStyle
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.components.KeyboardHideButton
import com.uip.oneapp.ui.components.appHintLocales
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun NetworkScreen(
    navController: NavController,
    viewModel: NetworkViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val c = DrainQTheme.colors

    var pendingNetwork by remember { mutableStateOf<WifiNetwork?>(null) }

    // Laufzeit-Berechtigungen für den WLAN-Scan (Standort bzw. NEARBY_WIFI ab Android 13).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.scan()
    }
    fun requestScan() {
        val needed = wifiScanPermissions()
        val allGranted = needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.scan() else permissionLauncher.launch(needed)
    }

    // Tablet-Kopplung (Welle 3a): QR-Code des ONE-Hotspots scannen → beitreten. Die ZXing-
    // CaptureActivity holt die Kamera-Berechtigung selbst ein; das Ergebnis (oder null bei
    // Abbruch) geht an joinFromQr.
    val qrScanPrompt = S("connect_one_prompt")
    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        viewModel.joinFromQr(result.contents)
    }
    fun scanOneQr() {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(qrScanPrompt)
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
        )
    }

    Scaffold(
        containerColor = c.bgWindow,
        topBar = {
            NetworkTopBar(
                title = S("network_title"),
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.refreshStatus() },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space16),
        ) {
            // === Online-Status ===
            DqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("access_point", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Text(
                        S("network_status_title"),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    val online = state.online.online
                    DqStatusChip(
                        text = if (online) S(connectionTypeKey(state.online.type)) else S("network_offline"),
                        color = if (online) c.success else c.error,
                    )
                }
            }

            // === Mit ONE verbinden (QR-Kopplung) — nur Tablet/WiFi (Welle 3a) ===
            // Die ONE spannt im Feld einen eigenen Hotspot auf (Pairing-Screen) und zeigt einen
            // WIFI-QR; hier scannt das Tablet ihn und tritt bei. Danach greifen Discovery (:8555)
            // + Video/Telemetrie automatisch.
            if (state.isTablet) {
                DqCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DqIcon("access_point", tint = c.amber)
                        Spacer(Modifier.width(Dimensions.Space12))
                        Text(
                            S("connect_one_title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Dimensions.Space8))
                    Text(
                        S("connect_one_subtitle"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                    // Join-Feedback (Phase/Fehler) — auf dem Tablet (REQUEST-Pfad) ist dies die
                    // einzige Stelle, die den Verbindungsstatus zeigt.
                    ConnectStatusLine(
                        phase = state.connectPhase,
                        ssid = state.connectSsid,
                        failReasonKey = state.failReasonKey,
                    )
                    Spacer(Modifier.height(Dimensions.Space12))
                    DqButton(
                        text = S("connect_one_scan"),
                        onClick = { scanOneQr() },
                        style = DqButtonStyle.Primary,
                        iconKey = "camera",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // === Bekannte ONE (Auto-Reconnect W1) — einmal gekoppelt, nie wieder QR ===
                // Einträge aus dem KnownOneStore; "Verbinden" nutzt die gespeicherten
                // Credentials, "Vergessen" löscht Kopplung + Suggestion rückstandsfrei.
                if (state.knownOnes.isNotEmpty()) {
                    DqCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DqIcon("wifi", tint = c.amber)
                            Spacer(Modifier.width(Dimensions.Space12))
                            Text(
                                S("known_one_title"),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Banner: Verbindung verloren + der eine Auto-Retry ist verbraucht.
                        if (state.autoState.phase == AutoConnectPhase.LOST) {
                            Spacer(Modifier.height(Dimensions.Space8))
                            Text(
                                S("known_one_lost"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.error,
                            )
                            Spacer(Modifier.height(Dimensions.Space12))
                            DqButton(
                                text = S("known_one_retry"),
                                onClick = { viewModel.retryAutoConnect() },
                                style = DqButtonStyle.Primary,
                                iconKey = "refresh",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        state.knownOnes.forEach { ssid ->
                            Spacer(Modifier.height(Dimensions.Space12))
                            KnownOneRow(
                                ssid = ssid,
                                status = state.knownOneStatus(ssid),
                                connecting = state.connectPhase == ConnectPhase.CONNECTING,
                                onConnect = { viewModel.connectKnown(ssid) },
                                onForget = { viewModel.forgetKnown(ssid) },
                            )
                        }
                    }
                }
            }

            // === WLAN ===
            // Adaptiv nach Geräte-Rolle:
            //  - Device-Owner (Kiosk/LockTask, Android-WLAN-Settings evtl. gesperrt):
            //    In-App-Picker (Scan/Liste/Passwort → WifiManager direkt).
            //  - Nicht-Owner: Der In-App-Scan wäre nur der eingeschränkte System-Dialog;
            //    daher ist der Sprung in die Android-WLAN-Einstellungen die primäre Aktion.
            DqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("wifi", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Text(
                        S("wifi"),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.inAppPicker && state.scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.DqIconStd),
                            strokeWidth = 2.dp,
                            color = c.amber,
                        )
                    }
                }

                if (!state.wifiEnabled) {
                    Spacer(Modifier.height(Dimensions.Space8))
                    Text(
                        S("wifi_disabled_hint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.warning,
                    )
                }

                if (state.inAppPicker) {
                    // --- Device-Owner: In-App-Picker ---
                    ConnectStatusLine(
                        phase = state.connectPhase,
                        ssid = state.connectSsid,
                        failReasonKey = state.failReasonKey,
                    )

                    Spacer(Modifier.height(Dimensions.Space12))
                    DqButton(
                        text = S("wifi_scan"),
                        onClick = { requestScan() },
                        style = DqButtonStyle.Secondary,
                        iconKey = "refresh",
                        enabled = !state.scanning,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.networks.isNotEmpty()) {
                        Spacer(Modifier.height(Dimensions.Space12))
                        state.networks.forEach { net ->
                            WifiRow(network = net, onClick = {
                                if (net.secured) pendingNetwork = net
                                else viewModel.connect(net, "")
                            })
                        }
                    } else if (!state.scanning) {
                        Spacer(Modifier.height(Dimensions.Space8))
                        Text(
                            S("wifi_no_networks"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                    }
                } else {
                    // --- Nicht-Owner: Sprung in die System-WLAN-Einstellungen (primär) ---
                    Spacer(Modifier.height(Dimensions.Space8))
                    Text(
                        S("wifi_settings_primary_hint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.height(Dimensions.Space12))
                    DqButton(
                        text = S("wifi_open_settings"),
                        onClick = { openWifiSettings(context) },
                        style = DqButtonStyle.Primary,
                        iconKey = "wifi",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // === USB-/Bluetooth-Tethering ===
            DqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("access_point", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Text(
                        S("tethering_title"),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    DqStatusChip(
                        text = if (state.tetheringActive) S("tethering_active") else S("tethering_inactive"),
                        color = if (state.tetheringActive) c.success else c.textSecondary,
                        showDot = true,
                    )
                }
                Spacer(Modifier.height(Dimensions.Space8))
                Text(
                    S("tethering_hint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(Dimensions.Space12))
                DqButton(
                    text = S("tethering_open_settings"),
                    onClick = { openTetheringSettings(context) },
                    style = DqButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // === DrainQ Cloud-Konto ===
            DqCard(modifier = Modifier.clickable { navController.navigate("cloud_login") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("cloud", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Column(Modifier.weight(1f)) {
                        Text(
                            S("cloud_account"),
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                        )
                        Text(
                            S("cloud_coming_soon"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                    }
                    DqIcon("chevron_right", tint = c.textSecondary)
                }
            }
        }
    }

    // Passwort-Dialog (DqCard) für gesicherte Netze.
    pendingNetwork?.let { net ->
        WifiPasswordDialog(
            network = net,
            connecting = state.connectPhase == ConnectPhase.CONNECTING,
            onConnect = { pw ->
                viewModel.connect(net, pw)
                pendingNetwork = null
            },
            onDismiss = { pendingNetwork = null },
        )
    }
}

@Composable
private fun ConnectStatusLine(phase: ConnectPhase, ssid: String, failReasonKey: String?) {
    if (phase == ConnectPhase.IDLE) return
    val c = DrainQTheme.colors
    val text: String
    val color: androidx.compose.ui.graphics.Color
    when (phase) {
        ConnectPhase.CONNECTING -> { text = S("net_connecting").replace("{ssid}", ssid); color = c.warning }
        ConnectPhase.CONNECTED -> { text = S("net_connected").replace("{ssid}", ssid); color = c.success }
        ConnectPhase.FAILED -> {
            val reason = failReasonKey?.let { S(it) } ?: ""
            text = S("net_failed").replace("{reason}", reason); color = c.error
        }
        ConnectPhase.IDLE -> { text = ""; color = c.textSecondary }
    }
    Spacer(Modifier.height(Dimensions.Space12))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (phase == ConnectPhase.CONNECTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimensions.DqIconInline),
                strokeWidth = 2.dp,
                color = color,
            )
            Spacer(Modifier.width(Dimensions.Space8))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * Eintrag der "Bekannte ONE"-Sektion (Auto-Reconnect W1): Name + Status-Chip +
 * Aktionen "Verbinden"/"Vergessen" (handschuh-freundlich als volle Buttons).
 */
@Composable
private fun KnownOneRow(
    ssid: String,
    status: KnownOneStatus,
    connecting: Boolean,
    onConnect: () -> Unit,
    onForget: () -> Unit,
) {
    val c = DrainQTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DqIcon("access_point", tint = c.textSecondary, size = Dimensions.DqIconInline)
            Spacer(Modifier.width(Dimensions.Space12))
            Text(
                ssid,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            when (status) {
                KnownOneStatus.CONNECTED ->
                    DqStatusChip(text = S("known_one_status_connected"), color = c.success)
                KnownOneStatus.IN_RANGE ->
                    DqStatusChip(text = S("known_one_status_in_range"), color = c.amber)
                KnownOneStatus.NOT_FOUND ->
                    DqStatusChip(text = S("known_one_status_not_found"), color = c.textSecondary)
                KnownOneStatus.UNKNOWN -> Unit // noch kein Scan-Wissen → kein Chip
            }
        }
        Spacer(Modifier.height(Dimensions.Space8))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.Space12)) {
            if (status != KnownOneStatus.CONNECTED) {
                DqButton(
                    text = S("connect"),
                    onClick = onConnect,
                    style = DqButtonStyle.Secondary,
                    enabled = !connecting,
                    modifier = Modifier.weight(1f),
                )
            }
            DqButton(
                text = S("known_one_forget"),
                onClick = onForget,
                style = DqButtonStyle.Ghost,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WifiRow(network: WifiNetwork, onClick: () -> Unit) {
    val c = DrainQTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.SettingRowHeight)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DqIcon("wifi", tint = c.textSecondary, size = Dimensions.DqIconInline)
        Spacer(Modifier.width(Dimensions.Space12))
        Text(
            network.ssid,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (network.secured) {
            DqIcon("lock", tint = c.textSecondary, size = Dimensions.DqIconInline)
            Spacer(Modifier.width(Dimensions.Space8))
        }
        Text(
            "${network.level}/4",
            style = MaterialTheme.typography.labelLarge,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun WifiPasswordDialog(
    network: WifiNetwork,
    connecting: Boolean,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = DrainQTheme.colors
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        HideSystemBarsInDialog()
        DqCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                S("wifi_connect_to").replace("{ssid}", network.ssid),
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(Dimensions.Space16))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(S("wifi_password")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = appHintLocales()),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { visible = !visible }) {
                            DqIcon(if (visible) "close" else "search", tint = c.textSecondary)
                        }
                        KeyboardHideButton()
                    }
                },
            )
            Spacer(Modifier.height(Dimensions.Space16))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Space12),
            ) {
                DqButton(
                    text = S("cancel"),
                    onClick = onDismiss,
                    style = DqButtonStyle.Ghost,
                    modifier = Modifier.weight(1f),
                )
                DqButton(
                    text = S("connect"),
                    onClick = { onConnect(password) },
                    enabled = !connecting,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Dq-Header mit Zurück-Button (links) + Aktualisieren (rechts), nur Tokens. */
@Composable
private fun NetworkTopBar(title: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    val c = DrainQTheme.colors
    Surface(color = c.bgPanel, modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.HeaderHeight)
                    .padding(horizontal = Dimensions.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    DqIcon("back", size = Dimensions.DqIconToolbar, tint = c.textPrimary)
                }
                Spacer(Modifier.width(Dimensions.Space8))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    DqIcon("refresh", size = Dimensions.DqIconToolbar, tint = c.amber)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.borderSubtle)
                    .align(Alignment.BottomStart)
            )
        }
    }
}

private fun connectionTypeKey(type: ConnectionType): String = when (type) {
    ConnectionType.WIFI -> "conn_wifi"
    ConnectionType.ETHERNET -> "conn_ethernet"
    ConnectionType.USB_TETHER -> "conn_usb_tether"
    ConnectionType.BLUETOOTH -> "conn_bluetooth"
    ConnectionType.CELLULAR -> "conn_cellular"
    ConnectionType.VPN -> "conn_vpn"
    ConnectionType.OTHER -> "conn_other"
    ConnectionType.NONE -> "network_offline"
}

private fun wifiScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)   // neverForLocation — kein Standort ab Android 13
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun openWifiSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun openTetheringSettings(context: Context) {
    val intents = listOf(
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent("android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) { /* nächsten Fallback versuchen */ }
    }
}

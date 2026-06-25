package com.uip.oneapp.ui.screens.pairing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.uip.oneapp.network.ApState
import com.uip.oneapp.network.WifiQr
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqButtonStyle
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqQrCode
import com.uip.oneapp.ui.components.DqSettingRow
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.components.DqToggle
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.DrainQTheme
import org.koin.androidx.compose.koinViewModel

/**
 * **Pairing-Screen** (Dual-Modus, Welle 3a) — nur DIRECT-Modus (App auf der ONE). Schaltet den
 * Tablet-Hotspot on-demand an/aus und zeigt die von der Plattform generierten Zugangsdaten als
 * WIFI-QR (+ Klartext-Fallback). Das Tablet scannt den QR im Netzwerk-Screen ("Mit ONE verbinden").
 */
@Composable
fun PairingScreen(
    navController: NavController,
    viewModel: PairingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val c = DrainQTheme.colors

    // LocalOnlyHotspot setzt die Standortberechtigung (+ aktivierte Standortdienste) voraus.
    // Bei Verweigerung sonst stille Rückkehr auf „aus" ohne jeden Hinweis → expliziter Hinweis.
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    fun requestStart() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val running = state is ApState.Active || state is ApState.Starting

    Scaffold(
        containerColor = c.bgWindow,
        topBar = {
            Surface(color = c.bgPanel, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.HeaderHeight)
                        .padding(horizontal = Dimensions.Space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        DqIcon("back", size = Dimensions.DqIconToolbar, tint = c.textPrimary)
                    }
                    Spacer(Modifier.width(Dimensions.Space8))
                    Text(
                        text = S("pairing_title"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary,
                    )
                }
            }
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
            // === Hotspot-Schalter ===
            DqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("access_point", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Text(
                        S("pairing_title"),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    DqStatusChip(
                        text = if (running) S("tethering_active") else S("tethering_inactive"),
                        color = if (state is ApState.Active) c.success
                        else if (state is ApState.Starting) c.warning
                        else c.textSecondary,
                    )
                }
                Spacer(Modifier.height(Dimensions.Space8))
                DqSettingRow(
                    title = S("pairing_toggle"),
                    iconKey = "wifi",
                    subtitle = S("pairing_intro"),
                    trailing = {
                        DqToggle(
                            checked = running,
                            onCheckedChange = { on -> if (on) requestStart() else viewModel.stop() },
                        )
                    },
                )
            }

            // === Standortberechtigung verweigert → Hinweis + Sprung in die App-Einstellungen ===
            if (permissionDenied && state is ApState.Idle) {
                DqCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(c.warning, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(Dimensions.Space12))
                        Text(
                            S("pairing_permission_needed"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.warning,
                        )
                    }
                    Spacer(Modifier.height(Dimensions.Space12))
                    DqButton(
                        text = S("pairing_open_app_settings"),
                        onClick = { openAppSettings() },
                        style = DqButtonStyle.Secondary,
                        iconKey = "settings",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // === Zustandsabhängiger Inhalt ===
            when (val s = state) {
                is ApState.Starting -> DqCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.DqIconInline),
                            strokeWidth = 2.dp,
                            color = c.amber,
                        )
                        Spacer(Modifier.width(Dimensions.Space12))
                        Text(S("pairing_starting"), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is ApState.Active -> DqCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            S("pairing_scan_prompt"),
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Dimensions.Space16))
                        DqQrCode(content = WifiQr.encode(ssid = s.ssid, passphrase = s.passphrase))
                        Spacer(Modifier.height(Dimensions.Space16))
                        CredentialRow(label = S("pairing_network_name"), value = s.ssid)
                        Spacer(Modifier.height(Dimensions.Space8))
                        CredentialRow(label = S("pairing_password"), value = s.passphrase)
                    }
                }

                is ApState.Blocked -> InfoCard(text = S("pairing_blocked_mode"), color = c.warning)

                is ApState.Failed -> InfoCard(
                    text = S("pairing_failed").replace("{reason}", s.reason),
                    color = c.error,
                )

                is ApState.Idle -> InfoCard(text = S("pairing_off_hint"), color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun CredentialRow(label: String, value: String) {
    val c = DrainQTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
    }
}

@Composable
private fun InfoCard(text: String, color: androidx.compose.ui.graphics.Color) {
    DqCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(Dimensions.Space12))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

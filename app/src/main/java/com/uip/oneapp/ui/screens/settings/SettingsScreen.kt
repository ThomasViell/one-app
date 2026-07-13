package com.uip.oneapp.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.R
import com.uip.oneapp.export.ReportLogo
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqDropdownRow
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqSettingRow
import com.uip.oneapp.ui.components.DqThemeToggle
import com.uip.oneapp.ui.components.DqToggle
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.components.KeyboardHideButton
import com.uip.oneapp.ui.components.appHintLocales
import com.uip.oneapp.ui.components.rememberKeyboardHider
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

/** Dünne 1-dp-Trennlinie in SA-Border-Farbe (für Zeilen innerhalb einer DqCard). */
@Composable
private fun DqRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimensions.Space8)
            .height(1.dp)
            .background(DrainQTheme.colors.borderSubtle)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentLang by LocalizationManager.currentLanguage.collectAsState()
    val c = DrainQTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = S("settings_saved")
    val hideKeyboard = rememberKeyboardHider()

    Scaffold(
        containerColor = c.bgWindow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DqHeader(
                title = S("settings_title"),
                actions = {
                    KeyboardHideButton()
                    IconButton(
                        onClick = {
                            viewModel.saveAll()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = savedMessage,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    ) {
                        DqIcon("save", size = Dimensions.DqIconToolbar, tint = c.amber)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space16),
        ) {

            // === Anzeige & Bedienung (Mockup 04: Kiosk · Sprache · Erscheinungsbild) ===
            DqCard {
                Text(
                    text = S("display_and_operation"),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(Dimensions.Space12))

                // Kiosk-Modus / Geräteeigentümer (LockTask) — nur Direkt-auf-ONE: sperrt die
                // ONE-Feldeinheit. Im Tablet-Modus sinnlos (das Tablet ist kein Feldgerät) → aus.
                if (state.hardwareMode == HardwareMode.DIRECT) {
                    DqSettingRow(
                        title = S("kiosk_mode"),
                        iconKey = "fullscreen",
                        subtitle = S("kiosk_mode_desc"),
                        trailing = { DqToggle(checked = state.kioskMode, onCheckedChange = { viewModel.updateKioskMode(it) }) },
                    )
                    DqRowDivider()
                }

                DqSettingRow(
                    title = S("settings_autohide_title"),
                    iconKey = "expand_less",
                    subtitle = S("settings_autohide_desc"),
                    trailing = { DqToggle(checked = state.controlsAutoHide, onCheckedChange = { viewModel.updateControlsAutoHide(it) }) },
                )
                DqRowDivider()

                val selectedLang = LocalizationManager.availableLanguages.find { it.code == currentLang }
                DqDropdownRow(
                    label = S("language"),
                    iconKey = "language",
                    selectedText = "${selectedLang?.flag ?: ""} ${selectedLang?.name ?: currentLang}",
                    options = LocalizationManager.availableLanguages.map { it.code to "${it.flag}  ${it.name}" },
                    onSelect = { code -> LocalizationManager.setLanguage(context, code) },
                )
                DqRowDivider()

                DqSettingRow(
                    title = S("appearance"),
                    iconKey = "sun",
                    trailing = { DqThemeToggle() },
                )

                // Bildschirmhelligkeit (CEO-Beschluss 2026-06-07, wie Original-App):
                // Toggle = manuell/automatisch; Slider nur im manuellen Modus. Nur Direkt-auf-ONE
                // — steuert das ONE-Display; im Tablet-Modus regelt das Tablet-OS die Helligkeit
                // selbst (Welle 4).
                if (state.hardwareMode == HardwareMode.DIRECT) {
                    DqRowDivider()
                    val brightnessManual = state.screenBrightness >= 0
                    DqSettingRow(
                        title = S("brightness_title"),
                        iconKey = "sun",
                        subtitle = if (brightnessManual) "${state.screenBrightness}%" else S("brightness_auto"),
                        trailing = {
                            DqToggle(
                                checked = brightnessManual,
                                onCheckedChange = { manual ->
                                    viewModel.updateScreenBrightness(if (manual) 80 else -1)
                                },
                            )
                        },
                    )
                    if (brightnessManual) {
                        Slider(
                            value = state.screenBrightness.toFloat(),
                            onValueChange = { viewModel.updateScreenBrightness(it.toInt()) },
                            valueRange = 5f..100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimensions.Space12),
                        )
                    }
                }
            }

            // === Datum & Uhrzeit (Louis #7) ===
            // Springt in die Android-System-Einstellung. Die Geräte-Uhr der ONE fällt offline
            // gern auf 2021 zurück; ist sie falsch, bekommen neue Projekte ein falsches Datum
            // (ProjectFormViewModel belegt mit LocalDate.now() vor). Die App setzt die Systemuhr
            // NICHT selbst (privilegiert) — nur der Sprung in die OS-Einstellung.
            DqCard(modifier = Modifier.clickable {
                try {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.w("SettingsScreen", "ACTION_DATE_SETTINGS nicht verfügbar", e)
                }
            }) {
                DqSettingRow(
                    title = S("settings_datetime_title"),
                    iconKey = "clock",
                    subtitle = S("settings_datetime_desc"),
                    trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                )
            }

            // === Offline-Karten ===
            DqCard(modifier = Modifier.clickable { navController.navigate("offline_maps") }) {
                DqSettingRow(
                    title = S("offline_maps_title"),
                    iconKey = "map",
                    subtitle = S("offline_maps_subtitle"),
                    trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                )
            }

            // === Netzwerk & Verbindung ===
            DqCard(modifier = Modifier.clickable { navController.navigate("network") }) {
                DqSettingRow(
                    title = S("network_title"),
                    iconKey = "wifi",
                    subtitle = S("network_subtitle"),
                    trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                )
            }

            // === Tablet-Hotspot (Pairing) — nur Direkt-auf-ONE (Welle 3a) ===
            // Die ONE spannt on-demand ihren eigenen WLAN-Hotspot auf, dem ein Tablet ohne
            // Büro-WLAN per QR-Kopplung beitritt. Im Tablet-/WiFi-Modus sinnlos (das Tablet ist
            // der Client, nicht der AP) → ausgeblendet.
            if (state.hardwareMode == HardwareMode.DIRECT) {
                DqCard(modifier = Modifier.clickable { navController.navigate("pairing") }) {
                    DqSettingRow(
                        title = S("pairing_title"),
                        iconKey = "access_point",
                        subtitle = S("pairing_settings_subtitle"),
                        trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                    )
                }
            }

            // === DrainQ Cloud-Konto ===
            DqCard(modifier = Modifier.clickable { navController.navigate("cloud_login") }) {
                DqSettingRow(
                    title = S("cloud_account"),
                    iconKey = "cloud",
                    subtitle = S("cloud_coming_soon"),
                    trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                )
            }

            // === Berichte (M11: Übersicht aller erzeugten PDFs) ===
            DqCard(modifier = Modifier.clickable { navController.navigate("reports") }) {
                DqSettingRow(
                    title = S("reports_title"),
                    iconKey = "save",
                    trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                )
            }

            // === ONE-Verbindung / Diagnose (ConnectionScreen: RTSP-Eingabe + Hardware-Status +
            // Log-Panel) — nur Tablet/WiFi: dort verbindet sich die App per RTSP/:12345 mit der
            // ONE. Im Direkt-Modus (App läuft auf der ONE) gibt es keine Netzverbindung zu
            // konfigurieren → Screen inkl. Log-Panel ausgeblendet (Welle 4, Backlog #1).
            if (state.hardwareMode == HardwareMode.WIFI) {
                DqCard(modifier = Modifier.clickable { navController.navigate("connection") }) {
                    DqSettingRow(
                        title = S("nav_connection"),
                        iconKey = "wifi",
                        trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                    )
                }

                // Auto-Reconnect W1: Toggle "Automatisch mit bekannter ONE verbinden"
                // (Default AN, app_settings) — nur Tablet/WiFi, im Direkt-Modus sinnlos.
                DqCard {
                    DqSettingRow(
                        title = S("auto_connect_title"),
                        iconKey = "refresh",
                        subtitle = S("auto_connect_desc"),
                        trailing = {
                            DqToggle(
                                checked = state.autoConnectOne,
                                onCheckedChange = { viewModel.updateAutoConnectOne(it) },
                            )
                        },
                    )
                }
            }

            // === Firmendaten ===
            DqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("company", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Text(S("company_data"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                }
                Spacer(Modifier.height(Dimensions.Space16))

                OutlinedTextField(
                    value = state.companyName,
                    onValueChange = { viewModel.updateCompanyName(it) },
                    label = { Text(S("field_company_name")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = appHintLocales()),
                    keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                )
                Spacer(Modifier.height(Dimensions.Space8))
                OutlinedTextField(
                    value = state.companyAddress,
                    onValueChange = { viewModel.updateCompanyAddress(it) },
                    label = { Text(S("field_address")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = appHintLocales()),
                    keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                )
                Spacer(Modifier.height(Dimensions.Space16))

                Text(S("company_logo"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(Dimensions.Space8))

                // Kiosk-sicherer In-App-Picker statt System-Dateipicker: Der System-Picker ist
                // eine fremde Vollbild-App — im Kiosk gibt es dort keinen Weg zurück (Befund
                // 2026-06-07). Quellen: USB-Stick, Download, DCIM, Pictures.
                var showLogoPicker by remember { mutableStateOf(false) }
                if (showLogoPicker) {
                    com.uip.oneapp.ui.components.ImagePickerDialog(
                        title = S("select_logo"),
                        onPick = { file ->
                            viewModel.setCompanyLogoFromFile(file)
                            showLogoPicker = false
                        },
                        onDismiss = { showLogoPicker = false }
                    )
                }

                when {
                    // Eigenes Logo gewählt (Pfad gesetzt + Datei existiert)
                    state.companyLogoPath.isNotEmpty() &&
                        state.companyLogoPath != ReportLogo.PREF_NONE &&
                        File(state.companyLogoPath).exists() -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            AsyncImage(
                                model = File(state.companyLogoPath),
                                contentDescription = S("company_logo"),
                                modifier = Modifier
                                    .height(Dimensions.CompanyLogoHeight)
                                    .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                    .border(1.dp, c.borderSubtle, RoundedCornerShape(Dimensions.OverlayCornerRadius)),
                                contentScale = ContentScale.Fit
                            )
                            IconButton(onClick = { viewModel.removeCompanyLogo() }, modifier = Modifier.align(Alignment.TopEnd)) {
                                DqIcon("delete", tint = c.error)
                            }
                        }
                        Spacer(Modifier.height(Dimensions.Space8))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8)) {
                            OutlinedButton(onClick = { showLogoPicker = true }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                                DqIcon("edit", size = Dimensions.DqIconInline)
                                Spacer(Modifier.width(Dimensions.Space8))
                                Text(S("change_logo"))
                            }
                            OutlinedButton(onClick = { viewModel.useNoLogo() }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                                Text(S("logo_none"))
                            }
                        }
                    }
                    // Bewusst kein Logo im Bericht
                    state.companyLogoPath == ReportLogo.PREF_NONE -> {
                        Text(S("logo_none_hint"), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.height(Dimensions.Space8))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8)) {
                            OutlinedButton(onClick = { viewModel.removeCompanyLogo() }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                                Text(S("logo_use_default"))
                            }
                            OutlinedButton(onClick = { showLogoPicker = true }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                                DqIcon("photo", size = Dimensions.DqIconInline)
                                Spacer(Modifier.width(Dimensions.Space8))
                                Text(S("select_logo"))
                            }
                        }
                    }
                    // Standard: mitgeliefertes NSP3CT-Logo (Default, CEO 2026-06-07)
                    else -> {
                        val pkg = LocalContext.current.packageName
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            AsyncImage(
                                model = "android.resource://$pkg/${R.raw.logo_nsp3ct_report}",
                                contentDescription = S("company_logo"),
                                modifier = Modifier
                                    .height(Dimensions.CompanyLogoHeight)
                                    .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                    .border(1.dp, c.borderSubtle, RoundedCornerShape(Dimensions.OverlayCornerRadius)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(Dimensions.Space8))
                        Text(S("logo_default_label"), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.height(Dimensions.Space8))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8)) {
                            OutlinedButton(onClick = { showLogoPicker = true }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                                DqIcon("photo", size = Dimensions.DqIconInline)
                                Spacer(Modifier.width(Dimensions.Space8))
                                Text(S("select_logo"))
                            }
                            OutlinedButton(onClick = { viewModel.useNoLogo() }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                                Text(S("logo_none"))
                            }
                        }
                    }
                }
            }

            // === Wetter-Presets (einklappbar) ===
            val weatherPresets by viewModel.weatherPresets.collectAsState()
            var weatherExpanded by remember { mutableStateOf(false) }
            var weatherEditingIndex by remember { mutableStateOf(-1) }
            var weatherEditText by remember { mutableStateOf("") }
            var newWeatherText by remember { mutableStateOf("") }
            DqCard {
                CollapsibleHeader(
                    iconKey = "weather",
                    title = S("weather_presets"),
                    expanded = weatherExpanded,
                    onToggle = { weatherExpanded = !weatherExpanded },
                )
                AnimatedVisibility(visible = weatherExpanded) {
                    PresetEditor(
                        presets = weatherPresets,
                        editingIndex = weatherEditingIndex,
                        editText = weatherEditText,
                        newText = newWeatherText,
                        onEditTextChange = { weatherEditText = it },
                        onNewTextChange = { newWeatherText = it },
                        onStartEdit = { i, v -> weatherEditingIndex = i; weatherEditText = v },
                        onCommitEdit = { viewModel.updateWeatherPreset(weatherEditingIndex, weatherEditText); weatherEditingIndex = -1 },
                        onCancelEdit = { weatherEditingIndex = -1 },
                        onRemove = { viewModel.removeWeatherPreset(it) },
                        onAdd = { if (newWeatherText.isNotBlank()) { viewModel.addWeatherPreset(newWeatherText); newWeatherText = "" } },
                        onReset = { viewModel.resetWeatherPresets() },
                    )
                }
            }

            // === Schadens-Presets (einklappbar) ===
            val damagePresets by viewModel.damagePresets.collectAsState()
            var damageExpanded by remember { mutableStateOf(false) }
            var damageEditingIndex by remember { mutableStateOf(-1) }
            var damageEditText by remember { mutableStateOf("") }
            var newDamageText by remember { mutableStateOf("") }
            DqCard {
                CollapsibleHeader(
                    iconKey = "alert",
                    title = S("damage_presets"),
                    expanded = damageExpanded,
                    onToggle = { damageExpanded = !damageExpanded },
                )
                AnimatedVisibility(visible = damageExpanded) {
                    PresetEditor(
                        presets = damagePresets,
                        editingIndex = damageEditingIndex,
                        editText = damageEditText,
                        newText = newDamageText,
                        onEditTextChange = { damageEditText = it },
                        onNewTextChange = { newDamageText = it },
                        onStartEdit = { i, v -> damageEditingIndex = i; damageEditText = v },
                        onCommitEdit = { viewModel.updateDamagePreset(damageEditingIndex, damageEditText); damageEditingIndex = -1 },
                        onCancelEdit = { damageEditingIndex = -1 },
                        onRemove = { viewModel.removeDamagePreset(it) },
                        onAdd = { if (newDamageText.isNotBlank()) { viewModel.addDamagePreset(newDamageText); newDamageText = "" } },
                        onReset = { viewModel.resetDamagePresets() },
                    )
                }
            }

            // === OSD-Einbrennung (einklappbar) ===
            var osdExpanded by remember { mutableStateOf(false) }
            DqCard {
                CollapsibleHeader(
                    iconKey = "osd",
                    title = S("osd_settings"),
                    subtitle = if (state.osdEnabled) S("osd_enable_burnin") else null,
                    expanded = osdExpanded,
                    onToggle = { osdExpanded = !osdExpanded },
                )
                AnimatedVisibility(visible = osdExpanded) {
                    Column {
                        Spacer(Modifier.height(Dimensions.Space8))
                        DqSettingRow(
                            title = S("osd_enable_burnin"),
                            subtitle = S("osd_enable_burnin_desc"),
                            trailing = { DqToggle(checked = state.osdEnabled, onCheckedChange = { viewModel.updateOsdEnabled(it) }) },
                        )

                        if (state.osdEnabled) {
                            DqRowDivider()
                            DqSettingRow(title = S("osd_show_meter"), trailing = { DqToggle(checked = state.osdShowMeter, onCheckedChange = { viewModel.updateOsdShowMeter(it) }) })
                            DqSettingRow(title = S("osd_show_date"), trailing = { DqToggle(checked = state.osdShowDate, onCheckedChange = { viewModel.updateOsdShowDate(it) }) })
                            DqRowDivider()
                            OsdDropdowns(state = state, viewModel = viewModel)
                        }
                    }
                }
            }

            // === Software-Update ===
            UpdateSection()

            // === App-Info ===
            DqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DqIcon("info", tint = c.amber)
                    Spacer(Modifier.width(Dimensions.Space12))
                    Text(S("app_info"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                }
                Spacer(Modifier.height(Dimensions.Space8))
                Text(S("app_full_name"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Text("${S("app_version")} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Text(S("app_copyright"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(
    iconKey: String,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
) {
    val c = DrainQTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.SettingRowHeight)
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DqIcon(iconKey, tint = c.amber)
        Spacer(Modifier.width(Dimensions.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.amber)
            }
        }
        DqIcon(if (expanded) "expand_less" else "chevron_down", tint = c.textSecondary)
    }
}

@Composable
private fun PresetEditor(
    presets: List<String>,
    editingIndex: Int,
    editText: String,
    newText: String,
    onEditTextChange: (String) -> Unit,
    onNewTextChange: (String) -> Unit,
    onStartEdit: (Int, String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
) {
    val c = DrainQTheme.colors
    Column {
        Spacer(Modifier.height(Dimensions.Space8))
        presets.forEachIndexed { index, preset ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.TouchMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editingIndex == index) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = onEditTextChange,
                        modifier = Modifier.weight(1f).heightIn(min = Dimensions.InputHeight),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = appHintLocales()),
                        keyboardActions = KeyboardActions(onDone = { onCommitEdit() }),
                    )
                    IconButton(onClick = onCommitEdit) { DqIcon("check", tint = c.amber) }
                    IconButton(onClick = onCancelEdit) { DqIcon("close", tint = c.textSecondary) }
                } else {
                    Text(preset, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    IconButton(onClick = { onStartEdit(index, preset) }) { DqIcon("edit", tint = c.textSecondary) }
                    IconButton(onClick = { onRemove(index) }) { DqIcon("delete", tint = c.error) }
                }
            }
        }
        Spacer(Modifier.height(Dimensions.Space8))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newText,
                onValueChange = onNewTextChange,
                label = { Text(S("new_entry")) },
                modifier = Modifier.weight(1f).heightIn(min = Dimensions.InputHeight),
                singleLine = true,
                textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = appHintLocales()),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )
            Spacer(Modifier.width(Dimensions.Space8))
            IconButton(onClick = onAdd) { DqIcon("plus", tint = c.amber) }
        }
        Spacer(Modifier.height(Dimensions.Space8))
        TextButton(onClick = onReset) {
            DqIcon("refresh", size = Dimensions.DqIconInline)
            Spacer(Modifier.width(Dimensions.Space4))
            Text(S("reset_defaults"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OsdDropdowns(state: SettingsUiState, viewModel: SettingsViewModel) {
    var fontSizeExpanded by remember { mutableStateOf(false) }
    var fontColorExpanded by remember { mutableStateOf(false) }
    var bgExpanded by remember { mutableStateOf(false) }
    var flashExpanded by remember { mutableStateOf(false) }

    val fontSizeLabel = when (state.osdFontSize) {
        com.uip.oneapp.export.OsdFontSize.Small  -> S("osd_font_small")
        com.uip.oneapp.export.OsdFontSize.Medium -> S("osd_font_medium")
        com.uip.oneapp.export.OsdFontSize.Large  -> S("osd_font_large")
        com.uip.oneapp.export.OsdFontSize.Maxi   -> S("osd_font_maxi")
    }
    OsdDropdown(
        label = S("osd_font_size"), value = fontSizeLabel,
        expanded = fontSizeExpanded, onExpandedChange = { fontSizeExpanded = it },
    ) {
        com.uip.oneapp.export.OsdFontSize.entries.forEach { fs ->
            val label = when (fs) {
                com.uip.oneapp.export.OsdFontSize.Small  -> S("osd_font_small")
                com.uip.oneapp.export.OsdFontSize.Medium -> S("osd_font_medium")
                com.uip.oneapp.export.OsdFontSize.Large  -> S("osd_font_large")
                com.uip.oneapp.export.OsdFontSize.Maxi   -> S("osd_font_maxi")
            }
            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateOsdFontSize(fs); fontSizeExpanded = false })
        }
    }
    Spacer(Modifier.height(Dimensions.Space8))

    val fontColorLabel = when (state.osdFontColor) {
        com.uip.oneapp.export.OsdColor.Green  -> S("osd_color_green")
        com.uip.oneapp.export.OsdColor.White  -> S("osd_color_white")
        com.uip.oneapp.export.OsdColor.Yellow -> S("osd_color_yellow")
    }
    OsdDropdown(
        label = S("osd_font_color"), value = fontColorLabel,
        expanded = fontColorExpanded, onExpandedChange = { fontColorExpanded = it },
    ) {
        com.uip.oneapp.export.OsdColor.entries.forEach { oc ->
            val label = when (oc) {
                com.uip.oneapp.export.OsdColor.Green  -> S("osd_color_green")
                com.uip.oneapp.export.OsdColor.White  -> S("osd_color_white")
                com.uip.oneapp.export.OsdColor.Yellow -> S("osd_color_yellow")
            }
            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateOsdFontColor(oc); fontColorExpanded = false })
        }
    }
    Spacer(Modifier.height(Dimensions.Space8))

    val bgLabel = when (state.osdBackground) {
        com.uip.oneapp.export.OsdBackground.Transparent     -> S("osd_bg_transparent")
        com.uip.oneapp.export.OsdBackground.SemiTransparent -> S("osd_bg_semi")
        com.uip.oneapp.export.OsdBackground.Solid           -> S("osd_bg_solid")
    }
    OsdDropdown(
        label = S("osd_background"), value = bgLabel,
        expanded = bgExpanded, onExpandedChange = { bgExpanded = it },
    ) {
        com.uip.oneapp.export.OsdBackground.entries.forEach { bg ->
            val label = when (bg) {
                com.uip.oneapp.export.OsdBackground.Transparent     -> S("osd_bg_transparent")
                com.uip.oneapp.export.OsdBackground.SemiTransparent -> S("osd_bg_semi")
                com.uip.oneapp.export.OsdBackground.Solid           -> S("osd_bg_solid")
            }
            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateOsdBackground(bg); bgExpanded = false })
        }
    }
    Spacer(Modifier.height(Dimensions.Space8))

    val flashLabel = when (state.osdFlashPosition) {
        com.uip.oneapp.export.OsdFlashPosition.Center     -> S("osd_flash_center")
        com.uip.oneapp.export.OsdFlashPosition.BelowLine1 -> S("osd_flash_below_line1")
    }
    OsdDropdown(
        label = S("osd_flash_position"), value = flashLabel,
        expanded = flashExpanded, onExpandedChange = { flashExpanded = it },
    ) {
        com.uip.oneapp.export.OsdFlashPosition.entries.forEach { fp ->
            val label = when (fp) {
                com.uip.oneapp.export.OsdFlashPosition.Center     -> S("osd_flash_center")
                com.uip.oneapp.export.OsdFlashPosition.BelowLine1 -> S("osd_flash_below_line1")
            }
            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateOsdFlashPosition(fp); flashExpanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OsdDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight).menuAnchor(),
            singleLine = true,
            textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            HideSystemBarsInDialog()
            items()
        }
    }
}

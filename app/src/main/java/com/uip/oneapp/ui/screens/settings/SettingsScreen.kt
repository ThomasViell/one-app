package com.uip.oneapp.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqDropdownRow
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqSettingRow
import com.uip.oneapp.ui.components.DqThemeToggle
import com.uip.oneapp.ui.components.DqToggle
import com.uip.oneapp.ui.components.KeyboardHideButton
import com.uip.oneapp.ui.components.appHintLocales
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
    var pendingLangCode by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = S("settings_saved")

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

                DqSettingRow(
                    title = S("kiosk_mode"),
                    iconKey = "fullscreen",
                    subtitle = S("kiosk_mode_desc"),
                    trailing = { DqToggle(checked = state.kioskMode, onCheckedChange = { viewModel.updateKioskMode(it) }) },
                )
                DqRowDivider()

                val selectedLang = LocalizationManager.availableLanguages.find { it.code == currentLang }
                DqDropdownRow(
                    label = S("language"),
                    iconKey = "language",
                    selectedText = "${selectedLang?.flag ?: ""} ${selectedLang?.name ?: currentLang}",
                    options = LocalizationManager.availableLanguages.map { it.code to "${it.flag}  ${it.name}" },
                    onSelect = { code -> pendingLangCode = code },
                )
                DqRowDivider()

                DqSettingRow(
                    title = S("appearance"),
                    iconKey = "sun",
                    trailing = { DqThemeToggle() },
                )
            }

            // Restart-Dialog nach Sprachauswahl — Texte in der neu gewählten Sprache
            pendingLangCode?.let { langCode ->
                val restartTitle = LocalizationManager.getString("restart_required", langCode)
                val restartMsg = LocalizationManager.getString("restart_language_message", langCode)
                val restartNow = LocalizationManager.getString("restart_now", langCode)
                val restartLater = LocalizationManager.getString("restart_later", langCode)
                AlertDialog(
                    onDismissRequest = {
                        LocalizationManager.setLanguage(context, langCode)
                        pendingLangCode = null
                    },
                    title = { Text(restartTitle) },
                    text = { Text(restartMsg) },
                    confirmButton = {
                        TextButton(onClick = {
                            LocalizationManager.setLanguage(context, langCode)
                            pendingLangCode = null
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }) { Text(restartNow) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            LocalizationManager.setLanguage(context, langCode)
                            pendingLangCode = null
                        }) { Text(restartLater) }
                    }
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

            // === DrainQ Cloud-Konto ===
            DqCard(modifier = Modifier.clickable { navController.navigate("cloud_login") }) {
                DqSettingRow(
                    title = S("cloud_account"),
                    iconKey = "cloud",
                    subtitle = S("cloud_coming_soon"),
                    trailing = { DqIcon("chevron_right", tint = c.textSecondary) },
                )
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
                    keyboardOptions = KeyboardOptions(hintLocales = appHintLocales()),
                )
                Spacer(Modifier.height(Dimensions.Space8))
                OutlinedTextField(
                    value = state.companyAddress,
                    onValueChange = { viewModel.updateCompanyAddress(it) },
                    label = { Text(S("field_address")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                    keyboardOptions = KeyboardOptions(hintLocales = appHintLocales()),
                )
                Spacer(Modifier.height(Dimensions.Space16))

                Text(S("company_logo"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(Dimensions.Space8))

                val logoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri -> if (uri != null) viewModel.setCompanyLogo(uri) }

                if (state.companyLogoPath.isNotEmpty() && File(state.companyLogoPath).exists()) {
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
                    OutlinedButton(onClick = { logoPickerLauncher.launch("image/*") }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                        DqIcon("edit", size = Dimensions.DqIconInline)
                        Spacer(Modifier.width(Dimensions.Space8))
                        Text(S("change_logo"))
                    }
                } else {
                    OutlinedButton(onClick = { logoPickerLauncher.launch("image/*") }, modifier = Modifier.heightIn(min = Dimensions.TouchMin)) {
                        DqIcon("photo", size = Dimensions.DqIconInline)
                        Spacer(Modifier.width(Dimensions.Space8))
                        Text(S("select_logo"))
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
                            DqSettingRow(title = S("osd_show_inclination"), trailing = { DqToggle(checked = state.osdShowInclination, onCheckedChange = { viewModel.updateOsdShowInclination(it) }) })
                            DqRowDivider()
                            OsdDropdowns(state = state, viewModel = viewModel)
                        }

                        DqRowDivider()
                        DqSettingRow(
                            title = S("hardware_osd_label"),
                            subtitle = S("hardware_osd_desc"),
                            trailing = {
                                DqToggle(
                                    checked = state.useHardwareOsd && !state.osdEnabled,
                                    onCheckedChange = {
                                        if (it && state.osdEnabled) viewModel.updateOsdEnabled(false)
                                        viewModel.updateUseHardwareOsd(it)
                                    },
                                    enabled = !state.osdEnabled,
                                )
                            },
                        )
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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, content = items)
    }
}

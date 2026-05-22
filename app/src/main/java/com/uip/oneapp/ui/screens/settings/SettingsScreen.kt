package com.uip.oneapp.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentLang by LocalizationManager.currentLanguage.collectAsState()
    val availableLanguages by LocalizationManager.availableLanguages.collectAsState()
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var pendingLangCode by remember { mutableStateOf<String?>(null) }
    var downloadingCode by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = S("settings_saved")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(S("settings_title")) },
                actions = {
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
                        Icon(
                            Icons.Default.Save,
                            contentDescription = savedMessage,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimensions.PanelEdgePadding)
    ) {

        // Offline-Maps Einstieg
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("offline_maps") },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.CardMinHeight)
                    .padding(Dimensions.PanelEdgePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        S("offline_maps_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        S("offline_maps_subtitle"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

        // WLAN
        WifiSettingsSection()

        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

        // Hotspot
        HotspotSettingsSection()

        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

        // Language & Translations
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.PanelEdgePadding)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                    Text(
                        text = S("language_translations"),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded,
                    onExpandedChange = { languageDropdownExpanded = it }
                ) {
                    val selected = availableLanguages.find { it.code == currentLang }
                    OutlinedTextField(
                        value = "${selected?.flag ?: ""} ${selected?.name ?: currentLang}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimensions.InputHeight)
                            .menuAnchor(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                    )
                    ExposedDropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        availableLanguages.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text("${lang.flag}  ${lang.name}")
                                },
                                onClick = {
                                    pendingLangCode = lang.code
                                    languageDropdownExpanded = false
                                },
                                trailingIcon = if (lang.code == currentLang) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                    }
                }

                if (availableLanguages.size > 2) {
                    Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    availableLanguages.forEach { lang ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${lang.flag}  ${lang.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            when {
                                downloadingCode == lang.code -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimensions.IconSizeMedium)
                                    )
                                }
                                lang.isBundle -> {
                                    Text(
                                        S("bundle_included"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                lang.isCached -> {
                                    Text(
                                        S("l10n_cached"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { LocalizationManager.deleteLocale(context, lang.code) },
                                        modifier = Modifier.size(Dimensions.CardMinHeight)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = S("delete"),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                else -> {
                                    val sizeKb = if (lang.sizeBytes > 0L) " (${lang.sizeBytes / 1024} kB)" else ""
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                downloadingCode = lang.code
                                                LocalizationManager.downloadLocale(context, lang.code)
                                                downloadingCode = null
                                            }
                                        },
                                        enabled = downloadingCode == null
                                    ) {
                                        Text(
                                            S("download") + sizeKb,
                                            fontSize = Dimensions.ButtonLabelFontSize
                                        )
                                    }
                                }
                            }
                            if (lang.code == currentLang) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(Dimensions.IconSizeMedium)
                                )
                            }
                        }
                    }

                    val isCurrentBundle = currentLang == "de" || currentLang == "en"
                    if (!isCurrentBundle && downloadingCode == null) {
                        Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                        TextButton(
                            onClick = { scope.launch { LocalizationManager.refreshCurrent(context) } },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSizeMedium)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                            Text(S("refresh"), fontSize = Dimensions.ButtonLabelFontSize)
                        }
                    }
                }
            }
        }

        // Restart dialog after language selection — strings shown in the newly selected language
        pendingLangCode?.let { langCode ->
            val restartTitle = LocalizationManager.getString("restart_required", langCode)
            val restartMsg = LocalizationManager.getString("restart_language_message", langCode)
            val restartNow = LocalizationManager.getString("restart_now", langCode)
            val restartLater = LocalizationManager.getString("restart_later", langCode)
            AlertDialog(
                onDismissRequest = {
                    scope.launch {
                        LocalizationManager.setLanguageAndAwait(context, langCode)
                        pendingLangCode = null
                    }
                },
                title = { Text(restartTitle) },
                text = { Text(restartMsg) },
                confirmButton = {
                    TextButton(onClick = {
                        // Persistenz synchron (kleiner DataStore-Write, ~10ms). Dann startActivity + killProcess
                        // direkt im Click-Handler, NICHT in einer Composition-gebundenen Coroutine —
                        // sonst wird der Job gecancelt sobald startActivity die alte Activity tötet
                        // und killProcess kommt nie zur Ausfuehrung.
                        runBlocking {
                            LocalizationManager.setLanguageAndAwait(context, langCode)
                        }
                        pendingLangCode = null
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }) { Text(restartNow) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scope.launch {
                            LocalizationManager.setLanguageAndAwait(context, langCode)
                            pendingLangCode = null
                        }
                    }) { Text(restartLater) }
                }
            )
        }

        // Company Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.PanelEdgePadding)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                    Text(
                        text = S("company_data"),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

                OutlinedTextField(
                    value = state.companyName,
                    onValueChange = { viewModel.updateCompanyName(it) },
                    label = { Text(S("field_company_name")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.InputHeight),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                )

                Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                OutlinedTextField(
                    value = state.companyAddress,
                    onValueChange = { viewModel.updateCompanyAddress(it) },
                    label = { Text(S("field_address")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.InputHeight),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                )

                Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

                // Company Logo
                Text(
                    text = S("company_logo"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                val logoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        viewModel.setCompanyLogo(uri)
                    }
                }

                if (state.companyLogoPath.isNotEmpty() && File(state.companyLogoPath).exists()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        AsyncImage(
                            model = File(state.companyLogoPath),
                            contentDescription = S("company_logo"),
                            modifier = Modifier
                                .height(Dimensions.CompanyLogoHeight)
                                .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                .border(
                                    Dimensions.BorderWidthDefault,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(Dimensions.OverlayCornerRadius)
                                ),
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { viewModel.removeCompanyLogo() },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = S("remove_logo"),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    OutlinedButton(
                        onClick = { logoPickerLauncher.launch("image/*") },
                        modifier = Modifier.height(Dimensions.TouchMedium)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Text(S("change_logo"))
                    }
                } else {
                    OutlinedButton(
                        onClick = { logoPickerLauncher.launch("image/*") },
                        modifier = Modifier.height(Dimensions.TouchMedium)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Text(S("select_logo"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

        // === Weather Presets (collapsible) ===
        val weatherPresets by viewModel.weatherPresets.collectAsState()
        var weatherExpanded by remember { mutableStateOf(false) }
        var weatherEditingIndex by remember { mutableStateOf(-1) }
        var weatherEditText by remember { mutableStateOf("") }
        var newWeatherText by remember { mutableStateOf("") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.CardMinHeight)
                        .clickable { weatherExpanded = !weatherExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                    Text(
                        text = S("weather_presets"),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (weatherExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = weatherExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                        weatherPresets.forEachIndexed { index, preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Dimensions.TouchMedium)
                                    .padding(vertical = Dimensions.SmallItemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (weatherEditingIndex == index) {
                                    OutlinedTextField(
                                        value = weatherEditText,
                                        onValueChange = { weatherEditText = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = Dimensions.InputHeight),
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            viewModel.updateWeatherPreset(index, weatherEditText)
                                            weatherEditingIndex = -1
                                        })
                                    )
                                    IconButton(onClick = {
                                        viewModel.updateWeatherPreset(index, weatherEditText)
                                        weatherEditingIndex = -1
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { weatherEditingIndex = -1 }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                } else {
                                    Text(
                                        text = preset,
                                        modifier = Modifier.weight(1f).padding(start = Dimensions.SmallSpacing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = {
                                        weatherEditingIndex = index
                                        weatherEditText = preset
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = S("edit_preset"), modifier = Modifier.size(Dimensions.IconSizeLarge))
                                    }
                                    IconButton(onClick = { viewModel.removeWeatherPreset(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = S("delete_preset"), modifier = Modifier.size(Dimensions.IconSizeLarge), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (index < weatherPresets.lastIndex) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newWeatherText,
                                onValueChange = { newWeatherText = it },
                                label = { Text(S("new_entry")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = Dimensions.InputHeight),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newWeatherText.isNotBlank()) {
                                        viewModel.addWeatherPreset(newWeatherText)
                                        newWeatherText = ""
                                    }
                                })
                            )
                            Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                            IconButton(onClick = {
                                if (newWeatherText.isNotBlank()) {
                                    viewModel.addWeatherPreset(newWeatherText)
                                    newWeatherText = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = S("add_weather_preset"), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                        TextButton(onClick = { viewModel.resetWeatherPresets() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                            Text(S("reset_defaults"))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

        // === Damage Presets (collapsible) ===
        val damagePresets by viewModel.damagePresets.collectAsState()
        var damageExpanded by remember { mutableStateOf(false) }
        var damageEditingIndex by remember { mutableStateOf(-1) }
        var damageEditText by remember { mutableStateOf("") }
        var newDamageText by remember { mutableStateOf("") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.CardMinHeight)
                        .clickable { damageExpanded = !damageExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                    Text(
                        text = S("damage_presets"),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (damageExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = damageExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                        damagePresets.forEachIndexed { index, preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Dimensions.TouchMedium)
                                    .padding(vertical = Dimensions.SmallItemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (damageEditingIndex == index) {
                                    OutlinedTextField(
                                        value = damageEditText,
                                        onValueChange = { damageEditText = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = Dimensions.InputHeight),
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            viewModel.updateDamagePreset(index, damageEditText)
                                            damageEditingIndex = -1
                                        })
                                    )
                                    IconButton(onClick = {
                                        viewModel.updateDamagePreset(index, damageEditText)
                                        damageEditingIndex = -1
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { damageEditingIndex = -1 }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                } else {
                                    Text(
                                        text = preset,
                                        modifier = Modifier.weight(1f).padding(start = Dimensions.SmallSpacing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = {
                                        damageEditingIndex = index
                                        damageEditText = preset
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = S("edit_preset"), modifier = Modifier.size(Dimensions.IconSizeLarge))
                                    }
                                    IconButton(onClick = { viewModel.removeDamagePreset(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = S("delete_preset"), modifier = Modifier.size(Dimensions.IconSizeLarge), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (index < damagePresets.lastIndex) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newDamageText,
                                onValueChange = { newDamageText = it },
                                label = { Text(S("new_entry")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = Dimensions.InputHeight),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newDamageText.isNotBlank()) {
                                        viewModel.addDamagePreset(newDamageText)
                                        newDamageText = ""
                                    }
                                })
                            )
                            Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                            IconButton(onClick = {
                                if (newDamageText.isNotBlank()) {
                                    viewModel.addDamagePreset(newDamageText)
                                    newDamageText = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = S("add_damage_preset"), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                        TextButton(onClick = { viewModel.resetDamagePresets() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                            Text(S("reset_defaults"))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

        // === OSD Burn-In Settings (collapsible) ===
        var osdExpanded by remember { mutableStateOf(false) }
        var fontSizeDropdownExpanded by remember { mutableStateOf(false) }
        var fontColorDropdownExpanded by remember { mutableStateOf(false) }
        var osdBgDropdownExpanded by remember { mutableStateOf(false) }
        var flashPosDropdownExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.CardMinHeight)
                        .clickable { osdExpanded = !osdExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = S("osd_settings"),
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = Dimensions.SectionTitleFontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (state.osdEnabled) {
                            Text(
                                text = S("osd_enable_burnin"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        if (osdExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = osdExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                        // Enable toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.TouchMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = S("osd_enable_burnin"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = S("osd_enable_burnin_desc"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.osdEnabled,
                                onCheckedChange = { viewModel.updateOsdEnabled(it) }
                            )
                        }

                        if (state.osdEnabled) {
                            Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                            // Content toggles
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Dimensions.TouchMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S("osd_show_meter"),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = state.osdShowMeter,
                                    onCheckedChange = { viewModel.updateOsdShowMeter(it) }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Dimensions.TouchMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S("osd_show_date"),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = state.osdShowDate,
                                    onCheckedChange = { viewModel.updateOsdShowDate(it) }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Dimensions.TouchMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S("osd_show_inclination"),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = state.osdShowInclination,
                                    onCheckedChange = { viewModel.updateOsdShowInclination(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                            // Font size dropdown
                            val fontSizeLabel = when (state.osdFontSize) {
                                com.uip.oneapp.export.OsdFontSize.Small  -> S("osd_font_small")
                                com.uip.oneapp.export.OsdFontSize.Medium -> S("osd_font_medium")
                                com.uip.oneapp.export.OsdFontSize.Large  -> S("osd_font_large")
                                com.uip.oneapp.export.OsdFontSize.Maxi   -> S("osd_font_maxi")
                            }
                            ExposedDropdownMenuBox(
                                expanded = fontSizeDropdownExpanded,
                                onExpandedChange = { fontSizeDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = fontSizeLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(S("osd_font_size")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontSizeDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Dimensions.InputHeight)
                                        .menuAnchor(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                                )
                                ExposedDropdownMenu(
                                    expanded = fontSizeDropdownExpanded,
                                    onDismissRequest = { fontSizeDropdownExpanded = false }
                                ) {
                                    com.uip.oneapp.export.OsdFontSize.entries.forEach { fs ->
                                        val label = when (fs) {
                                            com.uip.oneapp.export.OsdFontSize.Small  -> S("osd_font_small")
                                            com.uip.oneapp.export.OsdFontSize.Medium -> S("osd_font_medium")
                                            com.uip.oneapp.export.OsdFontSize.Large  -> S("osd_font_large")
                                            com.uip.oneapp.export.OsdFontSize.Maxi   -> S("osd_font_maxi")
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.updateOsdFontSize(fs)
                                                fontSizeDropdownExpanded = false
                                            },
                                            trailingIcon = if (fs == state.osdFontSize) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                            // Font color dropdown
                            val fontColorLabel = when (state.osdFontColor) {
                                com.uip.oneapp.export.OsdColor.Green  -> S("osd_color_green")
                                com.uip.oneapp.export.OsdColor.White  -> S("osd_color_white")
                                com.uip.oneapp.export.OsdColor.Yellow -> S("osd_color_yellow")
                            }
                            ExposedDropdownMenuBox(
                                expanded = fontColorDropdownExpanded,
                                onExpandedChange = { fontColorDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = fontColorLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(S("osd_font_color")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontColorDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Dimensions.InputHeight)
                                        .menuAnchor(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                                )
                                ExposedDropdownMenu(
                                    expanded = fontColorDropdownExpanded,
                                    onDismissRequest = { fontColorDropdownExpanded = false }
                                ) {
                                    com.uip.oneapp.export.OsdColor.entries.forEach { oc ->
                                        val label = when (oc) {
                                            com.uip.oneapp.export.OsdColor.Green  -> S("osd_color_green")
                                            com.uip.oneapp.export.OsdColor.White  -> S("osd_color_white")
                                            com.uip.oneapp.export.OsdColor.Yellow -> S("osd_color_yellow")
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.updateOsdFontColor(oc)
                                                fontColorDropdownExpanded = false
                                            },
                                            trailingIcon = if (oc == state.osdFontColor) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                            // Background dropdown
                            val bgLabel = when (state.osdBackground) {
                                com.uip.oneapp.export.OsdBackground.Transparent     -> S("osd_bg_transparent")
                                com.uip.oneapp.export.OsdBackground.SemiTransparent -> S("osd_bg_semi")
                                com.uip.oneapp.export.OsdBackground.Solid           -> S("osd_bg_solid")
                            }
                            ExposedDropdownMenuBox(
                                expanded = osdBgDropdownExpanded,
                                onExpandedChange = { osdBgDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = bgLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(S("osd_background")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = osdBgDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Dimensions.InputHeight)
                                        .menuAnchor(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                                )
                                ExposedDropdownMenu(
                                    expanded = osdBgDropdownExpanded,
                                    onDismissRequest = { osdBgDropdownExpanded = false }
                                ) {
                                    com.uip.oneapp.export.OsdBackground.entries.forEach { bg ->
                                        val label = when (bg) {
                                            com.uip.oneapp.export.OsdBackground.Transparent     -> S("osd_bg_transparent")
                                            com.uip.oneapp.export.OsdBackground.SemiTransparent -> S("osd_bg_semi")
                                            com.uip.oneapp.export.OsdBackground.Solid           -> S("osd_bg_solid")
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.updateOsdBackground(bg)
                                                osdBgDropdownExpanded = false
                                            },
                                            trailingIcon = if (bg == state.osdBackground) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                            // Flash position dropdown
                            val flashLabel = when (state.osdFlashPosition) {
                                com.uip.oneapp.export.OsdFlashPosition.Center      -> S("osd_flash_center")
                                com.uip.oneapp.export.OsdFlashPosition.BelowLine1  -> S("osd_flash_below_line1")
                            }
                            ExposedDropdownMenuBox(
                                expanded = flashPosDropdownExpanded,
                                onExpandedChange = { flashPosDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = flashLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(S("osd_flash_position")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = flashPosDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Dimensions.InputHeight)
                                        .menuAnchor(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                                )
                                ExposedDropdownMenu(
                                    expanded = flashPosDropdownExpanded,
                                    onDismissRequest = { flashPosDropdownExpanded = false }
                                ) {
                                    com.uip.oneapp.export.OsdFlashPosition.entries.forEach { fp ->
                                        val label = when (fp) {
                                            com.uip.oneapp.export.OsdFlashPosition.Center     -> S("osd_flash_center")
                                            com.uip.oneapp.export.OsdFlashPosition.BelowLine1 -> S("osd_flash_below_line1")
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.updateOsdFlashPosition(fp)
                                                flashPosDropdownExpanded = false
                                            },
                                            trailingIcon = if (fp == state.osdFlashPosition) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

                        // Hardware OSD (Camera-side OSD)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.TouchMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = S("hardware_osd_label"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = S("hardware_osd_desc"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.useHardwareOsd && !state.osdEnabled,
                                onCheckedChange = {
                                    if (it && state.osdEnabled) {
                                        viewModel.updateOsdEnabled(false)
                                    }
                                    viewModel.updateUseHardwareOsd(it)
                                },
                                enabled = !state.osdEnabled
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

        // Update Section
        UpdateSection()

        Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

        // App Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.PanelEdgePadding)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                    Text(
                        text = S("app_info"),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = Dimensions.SectionTitleFontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                Text(
                    text = S("app_full_name"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = S("app_copyright"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.LargeSpacing))

        // Exit zur Android-Oberflaeche (Service-Mode)
        // Da DrainQ.ONE als HOME-Launcher registriert ist, bringt 'finish()' allein
        // nicht raus — die App wird sofort wieder gestartet. Statt dessen oeffnen
        // wir Android System Settings, von dort kann der User in 'Apps → Default
        // Apps → Launcher' den Standard-Launcher zurueck-setzen.
        var showExitDialog by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showExitDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.TouchLarge),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Default.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSizeMedium)
            )
            Spacer(modifier = Modifier.width(Dimensions.ButtonIconSpacing))
            Text(
                S("service_mode_exit"),
                fontSize = Dimensions.ButtonLabelFontSize
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                icon = {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(S("exit_app_title")) },
                text = {
                    Text(S("exit_app_message"))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        try {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text(S("open_settings_btn")) }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(S("cancel"))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
    }
    } // Scaffold
}

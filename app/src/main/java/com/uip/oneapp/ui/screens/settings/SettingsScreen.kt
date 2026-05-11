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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.network.DeviceType
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
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
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var deviceTypeDropdownExpanded by remember { mutableStateOf(false) }
    var pendingDeviceType by remember { mutableStateOf<DeviceType?>(null) }
    var pendingLangCode by remember { mutableStateOf<String?>(null) }
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
            .padding(16.dp)
    ) {

        // Language Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("language"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded,
                    onExpandedChange = { languageDropdownExpanded = it }
                ) {
                    val selected = LocalizationManager.availableLanguages.find { it.code == currentLang }
                    OutlinedTextField(
                        value = "${selected?.flag ?: ""} ${selected?.name ?: currentLang}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        LocalizationManager.availableLanguages.forEach { lang ->
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Type Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("device_type"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = deviceTypeDropdownExpanded,
                    onExpandedChange = { deviceTypeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.deviceType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceTypeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = deviceTypeDropdownExpanded,
                        onDismissRequest = { deviceTypeDropdownExpanded = false }
                    ) {
                        DeviceType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    deviceTypeDropdownExpanded = false
                                    if (type != state.deviceType) {
                                        pendingDeviceType = type
                                    }
                                },
                                trailingIcon = if (type == state.deviceType) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = S("device_type_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Device type change restart dialog
        pendingDeviceType?.let { newType ->
            AlertDialog(
                onDismissRequest = { pendingDeviceType = null },
                title = { Text(S("restart_required")) },
                text = { Text(S("device_switch_restart")) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateDeviceType(newType)
                        pendingDeviceType = null
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }) { Text(S("restart_now")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.updateDeviceType(newType)
                        pendingDeviceType = null
                    }) { Text(S("restart_later")) }
                }
            )
        }

        // Restart dialog after language selection — strings shown in the newly selected language
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

        Spacer(modifier = Modifier.height(16.dp))

        // DrainQ Connection Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("nsp3ct_connection"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.brokerIp,
                    onValueChange = { viewModel.updateBrokerIp(it) },
                    label = { Text(S("field_broker_ip")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.brokerPort,
                    onValueChange = { viewModel.updateBrokerPort(it) },
                    label = { Text(S("field_broker_port")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.rtspUrl,
                    onValueChange = { viewModel.updateRtspUrl(it) },
                    label = { Text(S("field_rtsp_url")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // TWO-specific camera settings
                if (state.deviceType == DeviceType.TWO) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = S("two_camera_settings"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.twoCameraIp,
                        onValueChange = { viewModel.updateTwoCameraIp(it) },
                        label = { Text(S("camera_ip")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.twoCameraUser,
                        onValueChange = { viewModel.updateTwoCameraUser(it) },
                        label = { Text(S("camera_user")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.twoCameraPassword,
                        onValueChange = { viewModel.updateTwoCameraPassword(it) },
                        label = { Text(S("camera_password")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { /* TODO: Test Connection */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S("test_connection"))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Company Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("company_data"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.companyName,
                    onValueChange = { viewModel.updateCompanyName(it) },
                    label = { Text(S("field_company_name")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.companyAddress,
                    onValueChange = { viewModel.updateCompanyAddress(it) },
                    label = { Text(S("field_address")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Company Logo
                Text(
                    text = S("company_logo"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { logoPickerLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("change_logo"))
                    }
                } else {
                    OutlinedButton(
                        onClick = { logoPickerLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("select_logo"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { weatherExpanded = !weatherExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("weather_presets"),
                        style = MaterialTheme.typography.titleMedium,
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
                        Spacer(modifier = Modifier.height(12.dp))

                        weatherPresets.forEachIndexed { index, preset ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (weatherEditingIndex == index) {
                                    OutlinedTextField(
                                        value = weatherEditText,
                                        onValueChange = { weatherEditText = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
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
                                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = {
                                        weatherEditingIndex = index
                                        weatherEditText = preset
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = S("edit_preset"), modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { viewModel.removeWeatherPreset(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = S("delete_preset"), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (index < weatherPresets.lastIndex) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newWeatherText,
                                onValueChange = { newWeatherText = it },
                                label = { Text(S("new_entry")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newWeatherText.isNotBlank()) {
                                        viewModel.addWeatherPreset(newWeatherText)
                                        newWeatherText = ""
                                    }
                                })
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                if (newWeatherText.isNotBlank()) {
                                    viewModel.addWeatherPreset(newWeatherText)
                                    newWeatherText = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = S("add_weather_preset"), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { viewModel.resetWeatherPresets() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(S("reset_defaults"))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { damageExpanded = !damageExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("damage_presets"),
                        style = MaterialTheme.typography.titleMedium,
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
                        Spacer(modifier = Modifier.height(12.dp))

                        damagePresets.forEachIndexed { index, preset ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (damageEditingIndex == index) {
                                    OutlinedTextField(
                                        value = damageEditText,
                                        onValueChange = { damageEditText = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
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
                                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = {
                                        damageEditingIndex = index
                                        damageEditText = preset
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = S("edit_preset"), modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { viewModel.removeDamagePreset(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = S("delete_preset"), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (index < damagePresets.lastIndex) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newDamageText,
                                onValueChange = { newDamageText = it },
                                label = { Text(S("new_entry")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newDamageText.isNotBlank()) {
                                        viewModel.addDamagePreset(newDamageText)
                                        newDamageText = ""
                                    }
                                })
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                if (newDamageText.isNotBlank()) {
                                    viewModel.addDamagePreset(newDamageText)
                                    newDamageText = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = S("add_weather_preset"), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { viewModel.resetDamagePresets() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(S("reset_defaults"))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { osdExpanded = !osdExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = S("osd_settings"),
                            style = MaterialTheme.typography.titleMedium,
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
                        Spacer(modifier = Modifier.height(12.dp))

                        // Enable toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Content toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.fillMaxWidth(),
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

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(12.dp))

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
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true
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

                            Spacer(modifier = Modifier.height(8.dp))

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
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true
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

                            Spacer(modifier = Modifier.height(8.dp))

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
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true
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

                            Spacer(modifier = Modifier.height(8.dp))

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
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true
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
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ONE Verbindung
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.navigate("connection") },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = S("one_connection"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = S("one_connection_subtitle"),
                        style = MaterialTheme.typography.bodyMedium,
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

        Spacer(modifier = Modifier.height(16.dp))

        // App Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = S("app_info"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
    }
    } // Scaffold
}

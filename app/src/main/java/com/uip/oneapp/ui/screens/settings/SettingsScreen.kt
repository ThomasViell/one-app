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

        // NSP3CT Connection Settings
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

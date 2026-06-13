package com.uip.oneapp.ui.screens.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.components.UpdateDialog
import com.uip.oneapp.ui.components.UpdateProgressDialog
import com.uip.oneapp.ui.components.UpdateProgressStage
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.update.UpdateCheckResult
import com.uip.oneapp.update.UpdateConfig
import com.uip.oneapp.update.UpdateService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

private const val PREFS_LAST_CHECK = "last_update_check"
private const val PREFS_NAME = "update"
private const val EASTER_EGG_TAPS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSection(
    updateService: UpdateService = koinInject(),
    updateConfig: UpdateConfig = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressStage by remember { mutableStateOf(UpdateProgressStage.Downloading) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var tapCount by remember { mutableStateOf(0) }
    var showChannelDropdown by remember { mutableStateOf(false) }
    var channelDropdownExpanded by remember { mutableStateOf(false) }

    val lastCheckMs = remember { readLastCheck(context) }
    var lastCheckDisplay by remember {
        mutableStateOf(
            if (lastCheckMs > 0L) formatTime(lastCheckMs) else ""
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                Text(
                    text = S("update_section_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = Dimensions.SectionTitleFontSize,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

            // Version + channel row — 7-tap easter egg on this row reveals channel selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        tapCount++
                        if (tapCount >= EASTER_EGG_TAPS) {
                            showChannelDropdown = true
                            tapCount = 0
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val channelLabel = when (updateConfig.channel) {
                    "beta" -> S("update_channel_beta")
                    else -> S("update_channel_stable")
                }
                Text(
                    "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                DqStatusChip(
                    text = "${S("update_channel_label")} $channelLabel",
                    color = DrainQTheme.colors.info,
                    showDot = false,
                )
            }

            // Hidden channel selector — only visible after 7-tap easter egg
            AnimatedVisibility(visible = showChannelDropdown) {
                Column {
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    ExposedDropdownMenuBox(
                        expanded = channelDropdownExpanded,
                        onExpandedChange = { channelDropdownExpanded = it }
                    ) {
                        val selectedLabel = when (updateConfig.channel) {
                            "beta" -> S("update_channel_beta")
                            else -> S("update_channel_stable")
                        }
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S("update_channel_label")) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.InputHeight)
                                .menuAnchor(),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                        )
                        ExposedDropdownMenu(
                            expanded = channelDropdownExpanded,
                            onDismissRequest = { channelDropdownExpanded = false }
                        ) {
                            HideSystemBarsInDialog()
                            listOf("stable", "beta").forEach { ch ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (ch == "beta") S("update_channel_beta")
                                            else S("update_channel_stable")
                                        )
                                    },
                                    onClick = {
                                        updateConfig.overrideChannel(ch)
                                        channelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (lastCheckDisplay.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                Text(
                    S("update_last_check").replace("{time}", lastCheckDisplay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (val state = checkState) {
                is CheckState.NoUpdate -> {
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Text(
                        S("update_no_update"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is CheckState.Error -> {
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is CheckState.NotConfigured -> {
                    // M15: 404/NotConfigured ehrlich anzeigen statt grün "App ist aktuell".
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Text(
                        S("update_not_configured"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))

            DqButton(
                text = S("update_check_now"),
                iconKey = "refresh",
                enabled = checkState !is CheckState.Checking,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    checkState = CheckState.Checking
                    scope.launch {
                        val result = try {
                            updateService.checkForUpdate()
                        } catch (e: Exception) {
                            UpdateCheckResult.Error(e.message ?: "")
                        }
                        val now = System.currentTimeMillis()
                        saveLastCheck(context, now)
                        lastCheckDisplay = formatTime(now)

                        checkState = when (result) {
                            is UpdateCheckResult.Available -> {
                                showUpdateDialog = true
                                CheckState.Available(result.release)
                            }
                            is UpdateCheckResult.NoUpdate ->
                                CheckState.NoUpdate
                            is UpdateCheckResult.Error ->
                                CheckState.Error(LocalizationManager.getString("update_error_network"))
                            is UpdateCheckResult.NotConfigured ->
                                CheckState.NotConfigured
                        }
                    }
                },
            )
        }
    }

    // Update available dialog
    if (showUpdateDialog && checkState is CheckState.Available) {
        val release = (checkState as CheckState.Available).release
        UpdateDialog(
            release = release,
            onInstall = {
                showUpdateDialog = false
                showProgressDialog = true
                progressStage = UpdateProgressStage.Downloading
                downloadJob = scope.launch {
                    try {
                        updateService.downloadAndInstall(release)
                        progressStage = UpdateProgressStage.Installing
                        showProgressDialog = false
                        checkState = CheckState.Idle
                    } catch (e: Exception) {
                        showProgressDialog = false
                        checkState = CheckState.Error(
                            LocalizationManager.getString(
                                when (e) {
                                    is SecurityException -> "update_error_hash_mismatch"
                                    else -> "update_error_install_failed"
                                }
                            )
                        )
                    }
                }
            },
            onDismiss = {
                showUpdateDialog = false
                checkState = CheckState.Idle
            }
        )
    }

    // Download/install progress dialog
    if (showProgressDialog) {
        UpdateProgressDialog(
            stage = progressStage,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            onCancel = {
                downloadJob?.cancel()
                downloadJob = null
                showProgressDialog = false
                checkState = CheckState.Idle
            }
        )
    }
}

private sealed class CheckState {
    object Idle : CheckState()
    object Checking : CheckState()
    object NoUpdate : CheckState()
    object NotConfigured : CheckState()
    data class Available(val release: com.uip.oneapp.update.ReleaseInfo) : CheckState()
    data class Error(val message: String) : CheckState()
}

private fun readLastCheck(context: Context): Long =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(PREFS_LAST_CHECK, 0L)

private fun saveLastCheck(context: Context, ms: Long) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putLong(PREFS_LAST_CHECK, ms).apply()
}

private fun formatTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

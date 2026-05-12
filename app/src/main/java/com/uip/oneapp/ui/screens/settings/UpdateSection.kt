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
import androidx.compose.ui.unit.dp
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.ui.components.UpdateDialog
import com.uip.oneapp.ui.components.UpdateProgressDialog
import com.uip.oneapp.ui.components.UpdateProgressStage
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.update.UpdateCheckResult
import com.uip.oneapp.update.UpdateConfig
import com.uip.oneapp.update.UpdateEventType
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
    var bytesDownloaded by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
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

    val events by updateService.getUpdateEvents().collectAsState(initial = emptyList())

    // Track download progress from events
    LaunchedEffect(events) {
        val last = events.lastOrNull() ?: return@LaunchedEffect
        when (last.type) {
            UpdateEventType.DOWNLOAD_PROGRESS -> {
                progressStage = UpdateProgressStage.Downloading
                last.message.split("/").let { parts ->
                    if (parts.size == 2) {
                        bytesDownloaded = parts[0].toLongOrNull() ?: bytesDownloaded
                        totalBytes = parts[1].toLongOrNull() ?: totalBytes
                    }
                }
            }
            UpdateEventType.DOWNLOAD_OK -> progressStage = UpdateProgressStage.Verifying
            UpdateEventType.INSTALL_INITIATED -> progressStage = UpdateProgressStage.Installing
            UpdateEventType.INSTALL_DONE -> {
                showProgressDialog = false
                checkState = CheckState.Idle
            }
            UpdateEventType.DOWNLOAD_FAIL -> {
                showProgressDialog = false
                checkState = CheckState.Error(
                    LocalizationManager.getString("update_error_network")
                )
            }
            else -> {}
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = S("update_section_title"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${S("app_version")} ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val channelLabel = when (updateConfig.channel) {
                        "beta" -> S("update_channel_beta")
                        else -> S("update_channel_stable")
                    }
                    Text(
                        "${S("update_channel_label")}: $channelLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hidden channel selector — only visible after 7-tap easter egg
            AnimatedVisibility(visible = showChannelDropdown) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
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
                                .menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = channelDropdownExpanded,
                            onDismissRequest = { channelDropdownExpanded = false }
                        ) {
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    S("update_last_check").replace("{time}", lastCheckDisplay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (val state = checkState) {
                is CheckState.NoUpdate -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        S("update_no_update"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is CheckState.Error -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
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
                                CheckState.NoUpdate
                        }
                    }
                },
                enabled = checkState !is CheckState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (checkState is CheckState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(S("update_check_now"))
            }
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
                bytesDownloaded = 0L
                totalBytes = release.size
                downloadJob = scope.launch {
                    try {
                        updateService.downloadAndInstall(release)
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
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
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

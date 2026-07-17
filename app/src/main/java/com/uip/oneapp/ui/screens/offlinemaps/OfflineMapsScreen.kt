package com.uip.oneapp.ui.screens.offlinemaps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.WorkInfo
import com.uip.oneapp.maps.OfflineMapCatalog
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.help.HelpButton
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.StatusGreen
import com.uip.oneapp.ui.theme.StatusOrange
import com.uip.oneapp.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    navController: NavController,
    viewModel: OfflineMapsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf<OfflineMapCatalog.Entry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S("offline_maps_title")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S("back"),
                            modifier = Modifier.size(Dimensions.NavRailIconSize))
                    }
                },
                actions = {
                    HelpButton(route = "offline_maps")
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = S("refresh"),
                            modifier = Modifier.size(Dimensions.NavRailIconSize))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openPicker() },
                containerColor = DrainQTheme.colors.amber,
                contentColor = DrainQTheme.colors.onAmber,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(S("offline_maps_add")) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimensions.TouchSpacing, vertical = Dimensions.SectionSpacing)
        ) {
            // Summary
            DqCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Dimensions.SectionSpacing))
                        Text(
                            S("offline_maps_installed_count").replace("{count}", state.installed.size.toString()),
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = Dimensions.SectionTitleFontSize
                        )
                    }
                    Spacer(Modifier.height(Dimensions.SmallSpacing))
                    Text(
                        S("offline_maps_used").replace("{size}", "%.1f".format(state.totalSizeBytes / 1024.0 / 1024.0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Dimensions.MediumSpacing))
                    Text(
                        S("offline_maps_source_note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Dimensions.SectionSpacing))

            if (state.installed.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimensions.XLargeSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSizeXXLarge),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Dimensions.SectionSpacing))
                    Text(
                        S("offline_maps_empty_title"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        S("offline_maps_empty_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimensions.MediumSpacing)) {
                    items(state.installed) { installed ->
                        InstalledMapRow(
                            entry = installed.entry,
                            sizeMB = installed.sizeBytes / 1024.0 / 1024.0,
                            bboxLabel = "%.2f..%.2f °N, %.2f..%.2f °E".format(
                                installed.minLat, installed.maxLat, installed.minLon, installed.maxLon
                            ),
                            onDelete = { confirmDelete = installed.entry }
                        )
                    }
                }
            }
        }
    }

    if (state.pickerOpen) {
        PickerDialog(
            catalog = viewModel.catalog(),
            isInstalled = { viewModel.isInstalled(it) },
            verifyingId = (state.verify as? OfflineMapsViewModel.Verify.Probing)?.entry?.id,
            workInfoFor = { entry ->
                viewModel.workInfo(entry).observeAsState(emptyList()).value
                    .firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            },
            onPick = { viewModel.requestSizeCheck(it) },
            onCancel = { viewModel.cancelDownload(it) },
            onClose = { viewModel.closePicker() }
        )
    }

    // Confirm / failure dialog after the HEAD probe.
    when (val v = state.verify) {
        is OfflineMapsViewModel.Verify.Probing -> Unit  // picker shows spinner on the row
        is OfflineMapsViewModel.Verify.Ready -> {
            val realMB = v.realBytes / 1024.0 / 1024.0
            val catalogMB = v.entry.approxSizeMB.toDouble()
            val drift = realMB - catalogMB
            val driftHint = when {
                kotlin.math.abs(drift) < 5  -> null
                drift > 0                   -> S("offline_maps_size_larger").replace("{mb}", "%.0f".format(drift))
                else                        -> S("offline_maps_size_smaller").replace("{mb}", "%.0f".format(-drift))
            }
            AlertDialog(
                onDismissRequest = { viewModel.dismissVerify() },
                icon = { Icon(Icons.Default.CloudDownload, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary) },
                title = { Text(S("offline_maps_download_confirm_title")) },
                text = {
                    HideSystemBarsInDialog()
                    Column {
                        Text(v.entry.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimensions.MediumSpacing))
                        Text(
                            S("offline_maps_server_size"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${"%.1f".format(realMB)} MB",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (driftHint != null) {
                            Spacer(Modifier.height(Dimensions.SmallSpacing))
                            Text(
                                driftHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (drift > 0) StatusOrange
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(Dimensions.SectionSpacing))
                        Text(
                            S("offline_maps_source_short"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmVerifiedDownload() }) {
                        Text(S("download"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissVerify() }) {
                        Text(S("cancel"))
                    }
                }
            )
        }
        is OfflineMapsViewModel.Verify.Failed -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVerify() },
                icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = StatusRed) },
                title = { Text(S("offline_maps_server_unreachable")) },
                text = {
                    HideSystemBarsInDialog()
                    Column {
                        Text(v.entry.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimensions.SmallSpacing))
                        Text(
                            S("offline_maps_head_failed"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(Dimensions.SmallSpacing))
                        Text(v.message, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Dimensions.SectionSpacing))
                        Text(
                            S("offline_maps_check_connection"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissVerify() }) { Text(S("ok")) }
                }
            )
        }
        null -> Unit
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StatusRed) },
            title = { Text(S("offline_maps_delete_title")) },
            text = { HideSystemBarsInDialog(); Text(S("offline_maps_delete_text").replace("{name}", entry.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry)
                    confirmDelete = null
                }) { Text(S("delete"), color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(S("cancel")) }
            }
        )
    }
}

@Composable
private fun InstalledMapRow(
    entry: OfflineMapCatalog.Entry,
    sizeMB: Double,
    bboxLabel: String,
    onDelete: () -> Unit
) {
    DqCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.CardMinHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Map, contentDescription = null, tint = StatusGreen)
            Spacer(Modifier.width(Dimensions.TouchSpacing))
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(sizeMB)} MB · ${entry.country}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bboxLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = S("delete"), tint = StatusRed)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerDialog(
    catalog: List<OfflineMapCatalog.Entry>,
    isInstalled: (OfflineMapCatalog.Entry) -> Boolean,
    verifyingId: String?,
    workInfoFor: @Composable (OfflineMapCatalog.Entry) -> WorkInfo?,
    onPick: (OfflineMapCatalog.Entry) -> Unit,
    onCancel: (OfflineMapCatalog.Entry) -> Unit,
    onClose: () -> Unit
) {
    val grouped = catalog.groupBy { it.continent to it.country }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(S("offline_maps_pick_region")) },
        text = {
            HideSystemBarsInDialog()
            Column(modifier = Modifier
                .heightIn(min = Dimensions.DialogContentMinHeight, max = Dimensions.DialogContentMaxHeight)
                .verticalScroll(rememberScrollState())) {
                for ((header, entries) in grouped) {
                    Text(
                        "${header.first} — ${header.second}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Dimensions.TouchSpacing, bottom = Dimensions.SmallSpacing)
                    )
                    for (entry in entries) {
                        val info = workInfoFor(entry)
                        val installed = isInstalled(entry)
                        val isProbing = verifyingId == entry.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.CardMinHeight)
                                .clickable(enabled = !installed && info == null && !isProbing) { onPick(entry) }
                                .padding(vertical = Dimensions.MediumSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                                val sub = when {
                                    installed -> S("offline_maps_already_installed")
                                    isProbing -> S("offline_maps_probing")
                                    info != null -> {
                                        val pct = info.progress.getInt(
                                            com.uip.oneapp.maps.OfflineMapDownloadWorker.KEY_PROGRESS, -1
                                        )
                                        if (pct >= 0) S("offline_maps_downloading").replace("{pct}", pct.toString())
                                        else S("offline_maps_queued")
                                    }
                                    else -> "~ ${entry.approxSizeMB} MB"
                                }
                                Text(sub, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                installed ->
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                                isProbing ->
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimensions.IconSizeStandard),
                                        strokeWidth = Dimensions.StrokeWidthMedium
                                    )
                                info != null -> {
                                    val pct = info.progress.getInt(
                                        com.uip.oneapp.maps.OfflineMapDownloadWorker.KEY_PROGRESS, -1
                                    )
                                    if (pct in 0..99) {
                                        CircularProgressIndicator(
                                            progress = { pct / 100f },
                                            modifier = Modifier.size(Dimensions.IconSizeStandard),
                                            strokeWidth = Dimensions.StrokeWidthMedium
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Dimensions.IconSizeStandard),
                                            strokeWidth = Dimensions.StrokeWidthMedium
                                        )
                                    }
                                    Spacer(Modifier.width(Dimensions.SmallSpacing))
                                    IconButton(onClick = { onCancel(entry) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = S("cancel"),
                                            tint = StatusOrange)
                                    }
                                }
                                else ->
                                    Icon(Icons.Default.CloudDownload, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(S("close")) } }
    )
}

package com.uip.oneapp.ui.screens.offlinemaps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.WorkInfo
import com.uip.oneapp.maps.OfflineMapCatalog
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
                title = { Text("Offline-Karten") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openPicker() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Karte hinzufügen") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${state.installed.size} Karte(n) installiert",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Belegt: ${"%.1f".format(state.totalSizeBytes / 1024.0 / 1024.0)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Quelle: download.mapsforge.org (ODbL, frei verwendbar)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.installed.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Noch keine Karten heruntergeladen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tippe auf '+ Karte hinzufügen' um eine Region zu laden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                drift > 0                   -> "⚠ ${"%.0f".format(drift)} MB größer als der Katalog-Hinweis"
                else                        -> "Hinweis: ${"%.0f".format(-drift)} MB kleiner als erwartet"
            }
            AlertDialog(
                onDismissRequest = { viewModel.dismissVerify() },
                icon = { Icon(Icons.Default.CloudDownload, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Download starten?") },
                text = {
                    Column {
                        Text(v.entry.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Aktuelle Dateigröße auf dem Server:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${"%.1f".format(realMB)} MB",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (driftHint != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                driftHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (drift > 0) StatusOrange
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Quelle: download.mapsforge.org",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmVerifiedDownload() }) {
                        Text("Herunterladen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissVerify() }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
        is OfflineMapsViewModel.Verify.Failed -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissVerify() },
                icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = StatusRed) },
                title = { Text("Server nicht erreichbar") },
                text = {
                    Column {
                        Text(v.entry.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Die aktuelle Dateigröße konnte nicht vom Server abgefragt werden:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(v.message, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Prüfe deine Internetverbindung und versuche es erneut.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissVerify() }) { Text("OK") }
                }
            )
        }
        null -> Unit
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StatusRed) },
            title = { Text("Karte löschen?") },
            text = { Text("\"${entry.displayName}\" wird vom Tablet entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry)
                    confirmDelete = null
                }) { Text("Löschen", color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Map, contentDescription = null, tint = StatusGreen)
            Spacer(Modifier.width(10.dp))
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
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = StatusRed)
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
        title = { Text("Region auswählen") },
        text = {
            Column(modifier = Modifier
                .heightIn(min = 200.dp, max = 480.dp)
                .verticalScroll(rememberScrollState())) {
                for ((header, entries) in grouped) {
                    Text(
                        "${header.first} — ${header.second}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    for (entry in entries) {
                        val info = workInfoFor(entry)
                        val installed = isInstalled(entry)
                        val isProbing = verifyingId == entry.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !installed && info == null && !isProbing) { onPick(entry) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                                val sub = when {
                                    installed -> "Bereits installiert"
                                    isProbing -> "Server-Größe wird abgefragt…"
                                    info != null -> {
                                        val pct = info.progress.getInt(
                                            com.uip.oneapp.maps.OfflineMapDownloadWorker.KEY_PROGRESS, -1
                                        )
                                        if (pct >= 0) "Download läuft … $pct %"
                                        else "Download in Warteschlange"
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
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                info != null -> {
                                    val pct = info.progress.getInt(
                                        com.uip.oneapp.maps.OfflineMapDownloadWorker.KEY_PROGRESS, -1
                                    )
                                    if (pct in 0..99) {
                                        CircularProgressIndicator(
                                            progress = pct / 100f,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(onClick = { onCancel(entry) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = "Abbrechen",
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
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } }
    )
}

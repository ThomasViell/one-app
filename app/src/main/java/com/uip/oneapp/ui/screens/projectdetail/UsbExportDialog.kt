package com.uip.oneapp.ui.screens.projectdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.export.UsbExportService
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import kotlinx.coroutines.launch

/**
 * USB-Export-Dialog (CEO-Beschluss 2026-06-07): PC-freier Datenabholweg.
 * Zwei Modi — komplettes Projekt oder Einzeldatei-Auswahl — Ziel ist
 * <Stick>/DrainQ/<Projektnummer>/.
 */
@Composable
fun UsbExportDialog(
    project: ProjectEntity,
    damages: List<DamageEntity>,
    notes: List<NoteEntity>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val service = remember { UsbExportService(context) }
    val scope = rememberCoroutineScope()

    // Zustand bei jedem Öffnen frisch ermitteln (Stick kann gerade gesteckt worden sein).
    var volumes by remember { mutableStateOf(service.findUsbVolumes()) }
    var hasAccess by remember { mutableStateOf(service.hasAllFilesAccess()) }
    val allFiles = remember(project.id) { service.collectProjectFiles(project, damages, notes) }

    var selectedVolumeIdx by remember { mutableStateOf(0) }
    var fullProject by remember { mutableStateOf(true) }
    val selectedPaths = remember { mutableStateListOf<String>().apply { addAll(allFiles.map { it.zipPath }) } }

    var progress by remember { mutableStateOf<Float?>(null) }
    var resultPath by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Texte vorab auflösen — S() ist @Composable und darf nicht in onClick-Lambdas stehen.
    val noFilesMsg = S("usb_no_files")
    val failedMsg = S("usb_export_failed")

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DqIcon("download", tint = DrainQTheme.colors.amber)
                Spacer(Modifier.width(Dimensions.Space12))
                Text(S("usb_export_title"))
            }
        },
        text = {
            HideSystemBarsInDialog()
            Column(Modifier.fillMaxWidth()) {
                when {
                    // 1) Berechtigung fehlt → Sprung in die Android-Einstellungen anbieten.
                    !hasAccess -> {
                        Text(S("usb_access_needed"))
                        Spacer(Modifier.height(Dimensions.Space12))
                        Button(onClick = {
                            runCatching { context.startActivity(service.allFilesAccessIntent()) }
                        }) { Text(S("usb_grant_access")) }
                        Spacer(Modifier.height(Dimensions.Space8))
                        TextButton(onClick = {
                            hasAccess = service.hasAllFilesAccess()
                            volumes = service.findUsbVolumes()
                        }) { Text(S("usb_recheck")) }
                    }

                    // 2) Kein Stick erkannt.
                    volumes.isEmpty() -> {
                        Text(S("usb_no_stick"))
                        Spacer(Modifier.height(Dimensions.Space12))
                        TextButton(onClick = { volumes = service.findUsbVolumes() }) {
                            Text(S("usb_recheck"))
                        }
                    }

                    // 3) Export läuft.
                    progress != null -> {
                        Text(S("usb_copying"))
                        Spacer(Modifier.height(Dimensions.Space12))
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 4) Fertig.
                    resultPath != null -> {
                        Text(S("usb_export_done"))
                        Spacer(Modifier.height(Dimensions.Space8))
                        Text(
                            resultPath ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = DrainQTheme.colors.textSecondary,
                        )
                    }

                    // 5) Auswahl.
                    else -> {
                        errorMsg?.let {
                            Text(it, color = DrainQTheme.colors.error)
                            Spacer(Modifier.height(Dimensions.Space8))
                        }
                        if (volumes.size > 1) {
                            Text(S("usb_target"), style = MaterialTheme.typography.labelLarge)
                            volumes.forEachIndexed { idx, vol ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = idx == selectedVolumeIdx,
                                        onClick = { selectedVolumeIdx = idx },
                                    )
                                    Text(vol.name)
                                }
                            }
                            Spacer(Modifier.height(Dimensions.Space8))
                        }

                        // Modus: komplett vs. Einzelauswahl
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = fullProject, onClick = { fullProject = true })
                            Text(S("usb_mode_full"))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = !fullProject, onClick = { fullProject = false })
                            Text(S("usb_mode_files"))
                        }

                        if (!fullProject) {
                            Spacer(Modifier.height(Dimensions.Space8))
                            if (allFiles.isEmpty()) {
                                Text(S("usb_no_files"), color = DrainQTheme.colors.textSecondary)
                            } else {
                                val grouped = remember(allFiles) { allFiles.groupBy { it.category } }
                                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                                    grouped.forEach { (category, group) ->
                                        item(key = "hdr_$category") {
                                            Text(
                                                category.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = DrainQTheme.colors.amber,
                                                modifier = Modifier.padding(top = Dimensions.Space8),
                                            )
                                        }
                                        items(group, key = { it.zipPath }) { ef ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Checkbox(
                                                    checked = ef.zipPath in selectedPaths,
                                                    onCheckedChange = { checked ->
                                                        if (checked) selectedPaths.add(ef.zipPath)
                                                        else selectedPaths.remove(ef.zipPath)
                                                    },
                                                )
                                                Text(
                                                    ef.zipPath.substringAfterLast('/'),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                resultPath != null -> TextButton(onClick = onDismiss) { Text(S("close")) }
                progress == null && hasAccess && volumes.isNotEmpty() -> {
                    TextButton(
                        onClick = {
                            val vol = volumes.getOrNull(selectedVolumeIdx) ?: return@TextButton
                            val files = if (fullProject) allFiles
                                        else allFiles.filter { it.zipPath in selectedPaths }
                            if (files.isEmpty()) { errorMsg = noFilesMsg; return@TextButton }
                            errorMsg = null
                            progress = 0f
                            scope.launch {
                                try {
                                    val target = service.export(vol, project, files) { p -> progress = p }
                                    progress = null
                                    resultPath = target.absolutePath
                                } catch (e: Exception) {
                                    progress = null
                                    errorMsg = e.message ?: failedMsg
                                }
                            }
                        }
                    ) { Text(S("export_start")) }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (progress == null && resultPath == null) {
                TextButton(onClick = onDismiss) { Text(S("close")) }
            }
        }
    )
}

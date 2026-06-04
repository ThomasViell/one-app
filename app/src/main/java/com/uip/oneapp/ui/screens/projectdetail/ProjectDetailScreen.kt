package com.uip.oneapp.ui.screens.projectdetail

import android.content.Intent
import android.media.MediaPlayer as AndroidMediaPlayer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqButtonStyle
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.screens.inspection.DamageDialog
import com.uip.oneapp.ui.screens.inspection.ImageAnnotationDialog
import com.uip.oneapp.ui.screens.inspection.NoteDialog
import com.uip.oneapp.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ProjectDetailScreen"
private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    navController: NavController,
    projectId: Long,
    viewModel: ProjectDetailViewModel = koinViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val project by viewModel.project.collectAsState()
    val damages by viewModel.damages.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val recordings by viewModel.recordingFiles.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val previewPdfFile by viewModel.previewPdfFile.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }
    var fullscreenDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var playbackVideo by remember { mutableStateOf<File?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var pendingExportType by remember { mutableStateOf(ExportType.PDF) }
    var showDeleteProjectDialog by remember { mutableStateOf(false) }
    val deleteResult by viewModel.deleteResult.collectAsState()

    LaunchedEffect(deleteResult) {
        when (val r = deleteResult) {
            is DeleteResult.Done -> {
                val kb = r.bytesFreed / 1024
                android.widget.Toast.makeText(
                    context,
                    "Projekt gelöscht — ${r.filesRemoved} Dateien, ${kb} KB freigegeben",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.clearDeleteResult()
                navController.popBackStack()
            }
            is DeleteResult.Error -> {
                android.widget.Toast.makeText(
                    context,
                    "Löschen fehlgeschlagen: ${r.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                viewModel.clearDeleteResult()
            }
            null -> Unit
        }
    }

    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var exportOptionsAction by remember { mutableStateOf(ExportType.PDF) }
    var exportIncludePhotos by remember { mutableStateOf(true) }
    var exportIncludeXml by remember { mutableStateOf(true) }
    val hasProjectMap = project?.mapImagePath?.let { File(it).exists() } == true
    var exportIncludeMap by remember(hasProjectMap) { mutableStateOf(hasProjectMap) }

    var editingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var creatingNote by remember { mutableStateOf(false) }
    var deletingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var deletingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var deletingVideo by remember { mutableStateOf<File?>(null) }
    var annotatingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var annotationPhotoPath by remember { mutableStateOf("") }

    val shareReportTitle = S("share_report")

    val saveToLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (pendingExportType == ExportType.PDF) "application/pdf" else "application/zip"
        )
    ) { uri ->
        if (uri != null && pendingExportFile != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    pendingExportFile!!.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save to external failed", e)
            }
        }
        pendingExportFile = null
    }

    LaunchedEffect(exportResult) {
        val result = exportResult ?: return@LaunchedEffect
        if (result.file != null && result.error == null) {
            pendingExportFile = result.file
            pendingExportType = result.type
            showExportDialog = true
            viewModel.clearExportResult()
        } else if (result.error != null) {
            Log.e(TAG, "Export error: ${result.error}")
            viewModel.clearExportResult()
        }
    }

    if (showExportDialog && pendingExportFile != null) {
        val fileName = pendingExportFile!!.name
        val fileSize = formatFileSize(pendingExportFile!!.length())
        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                pendingExportFile = null
            },
            icon = {
                Icon(
                    if (pendingExportType == ExportType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(S("export_complete")) },
            text = {
                Text("$fileName ($fileSize)")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            pendingExportFile!!
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (pendingExportType == ExportType.PDF) "application/pdf" else "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, shareReportTitle))
                    } catch (e: Exception) {
                        Log.e(TAG, "Share failed", e)
                    }
                    pendingExportFile = null
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                    Spacer(modifier = Modifier.width(Dimensions.MediumSpacing))
                    Text(S("export_share"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    saveToLauncher.launch(pendingExportFile!!.name)
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                    Spacer(modifier = Modifier.width(Dimensions.MediumSpacing))
                    Text(S("export_save_to"))
                }
            }
        )
    }

    if (showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExportOptionsDialog = false },
            icon = {
                Icon(
                    if (exportOptionsAction == ExportType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(S("export_options")) },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = exportIncludePhotos,
                            onCheckedChange = { exportIncludePhotos = it }
                        )
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Text(S("export_include_photos"))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.SmallSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = exportIncludeXml,
                            onCheckedChange = { exportIncludeXml = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Column {
                            Text(
                                text = S("export_include_xml"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = S("export_include_xml_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.SmallSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = exportIncludeMap,
                            onCheckedChange = { exportIncludeMap = it },
                            enabled = hasProjectMap,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Text(
                            text = S("include_map"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasProjectMap)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    if (exportOptionsAction == ExportType.PDF) {
                        TextButton(onClick = {
                            showExportOptionsDialog = false
                            viewModel.previewPdf(exportIncludePhotos, exportIncludeMap)
                        }) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSizeMedium)
                            )
                            Spacer(Modifier.width(Dimensions.SmallSpacing))
                            Text(S("pdf_preview"))
                        }
                    }
                    TextButton(onClick = {
                        showExportOptionsDialog = false
                        if (exportOptionsAction == ExportType.PDF) {
                            viewModel.exportPdf(exportIncludePhotos, exportIncludeXml, exportIncludeMap)
                        } else {
                            viewModel.exportZip(exportIncludePhotos, exportIncludeXml, exportIncludeMap)
                        }
                    }) {
                        Text(S("export_start"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportOptionsDialog = false }) {
                    Text(S("close"))
                }
            }
        )
    }

    val photoDamages = damages.filter {
        it.photoPath.isNotEmpty() && File(it.photoPath).exists() && File(it.photoPath).length() > 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            project?.projectNumber ?: S("nav_projects"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (project?.auftraggeber?.isNotEmpty() == true) {
                            Text(
                                project!!.auftraggeber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = S("back"),
                            modifier = Modifier.size(Dimensions.NavRailIconSize))
                    }
                },
                actions = {
                    // Aktionen als Icon-MIT-Label (analog Navi-Rail): DqIcon (28 dp) in
                    // semantischer Token-Farbe + kurzes Label darunter. Touch-Target je
                    // ≥ 56 dp; Aktionen/Logik unverändert.
                    val actionsEnabled = exportProgress == null && project != null
                    HeaderAction(
                        iconKey = "edit",
                        label = S("edit"),
                        tint = DrainQTheme.colors.amber,
                        onClick = { navController.navigate("project_form/$projectId") }
                    )
                    HeaderAction(
                        iconKey = "inspection",
                        label = S("action_video"),
                        tint = DrainQTheme.colors.success,
                        onClick = { navController.navigate("inspection/$projectId") }
                    )
                    HeaderAction(
                        iconKey = "pdf",
                        label = S("action_pdf"),
                        tint = DrainQTheme.colors.amber,
                        enabled = actionsEnabled,
                        onClick = {
                            exportOptionsAction = ExportType.PDF
                            showExportOptionsDialog = true
                        }
                    )
                    HeaderAction(
                        iconKey = "archive",
                        label = S("action_archive"),
                        tint = DrainQTheme.colors.info,
                        enabled = actionsEnabled,
                        onClick = {
                            exportOptionsAction = ExportType.ZIP
                            showExportOptionsDialog = true
                        }
                    )
                    HeaderAction(
                        iconKey = "delete",
                        label = S("delete"),
                        tint = DrainQTheme.colors.error,
                        enabled = actionsEnabled,
                        onClick = { showDeleteProjectDialog = true }
                    )
                }
            )
        },
        bottomBar = {
            // Mockup 03 Detail: prominente Aktionen unten (Galerie-Detail).
            Surface(color = DrainQTheme.colors.bgPanel) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Space16),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Space12),
                ) {
                    DqButton(
                        text = S("continue_inspection"),
                        iconKey = "inspection",
                        style = DqButtonStyle.Secondary,
                        onClick = { navController.navigate("inspection/$projectId") },
                        modifier = Modifier.weight(1f),
                    )
                    DqButton(
                        text = S("pdf_report"),
                        iconKey = "save",
                        style = DqButtonStyle.Primary,
                        enabled = exportProgress == null && project != null,
                        onClick = {
                            exportOptionsAction = ExportType.PDF
                            showExportOptionsDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (exportProgress != null) {
                LinearProgressIndicator(
                    progress = exportProgress!!,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (project != null) {
                val p = project!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.TouchSpacing, vertical = Dimensions.SmallSpacing),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.TouchSpacing, vertical = Dimensions.SectionSpacing),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.PanelEdgePadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val done = p.status.uppercase().let {
                            it.contains("DONE") || it.contains("FERTIG") || it.contains("COMPLET")
                        }
                        DqStatusChip(
                            text = if (done) S("status_done") else S("status_open"),
                            color = if (done) DrainQTheme.colors.success else DrainQTheme.colors.warning,
                        )
                        InfoChip(Icons.Default.CalendarMonth, p.inspektionsdatum)
                        InfoChip(Icons.Default.Person, p.inspektor)
                        if (p.material.isNotEmpty()) InfoChip(Icons.Default.Build, p.material)
                        if (p.durchmesser.isNotEmpty()) InfoChip(Icons.Default.Circle, "DN ${p.durchmesser}")
                        if (p.inspektionslaenge.isNotEmpty()) InfoChip(Icons.Default.Straighten, "${p.inspektionslaenge} m")
                        if (p.standortAdresse.isNotEmpty()) InfoChip(Icons.Default.LocationOn, p.standortAdresse)
                        InfoChip(
                            if (p.videoQuality == "HD") Icons.Default.HighQuality else Icons.Default.SdCard,
                            p.videoQuality
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                TabWithBadge(S("tab_photos"), photoDamages.size, 0, selectedTab) { selectedTab = 0 }
                TabWithBadge(S("tab_damages"), damages.size, 1, selectedTab) { selectedTab = 1 }
                TabWithBadge(S("tab_videos"), recordings.size, 2, selectedTab) { selectedTab = 2 }
                TabWithBadge(S("tab_notes"), notes.size, 3, selectedTab) { selectedTab = 3 }
            }

            when (selectedTab) {
                0 -> PhotosTab(photoDamages) { damage, path ->
                    fullscreenPhoto = path
                    fullscreenDamage = damage
                }
                1 -> DamagesTab(
                    damages = damages,
                    onPhotoClick = { damage, path ->
                        fullscreenPhoto = path
                        fullscreenDamage = damage
                    },
                    onEdit = { editingDamage = it },
                    onDelete = { deletingDamage = it }
                )
                2 -> VideosTab(
                    files = recordings,
                    onVideoClick = { file -> playbackVideo = file },
                    onDelete = { deletingVideo = it }
                )
                3 -> NotesTab(
                    notes = notes,
                    onEdit = { editingNote = it },
                    onDelete = { deletingNote = it },
                    onAdd = { creatingNote = true }
                )
            }
        }
    }

    if (fullscreenPhoto != null) {
        FullscreenImageDialog(
            photoPath = fullscreenPhoto!!,
            onDismiss = {
                fullscreenPhoto = null
                fullscreenDamage = null
            },
            onDoubleTap = if (fullscreenDamage != null) {
                {
                    annotatingDamage = fullscreenDamage
                    annotationPhotoPath = fullscreenPhoto!!
                    fullscreenPhoto = null
                    fullscreenDamage = null
                }
            } else null
        )
    }

    if (playbackVideo != null) {
        VideoPlaybackDialog(
            videoFile = playbackVideo!!,
            onDismiss = { playbackVideo = null },
            projectId = projectId
        )
    }

    if (previewPdfFile != null) {
        PdfPreviewDialog(
            pdfFile = previewPdfFile!!,
            onDismiss = { viewModel.clearPreviewPdf() },
            onExport = {
                val file = previewPdfFile
                viewModel.clearPreviewPdf()
                if (file != null) {
                    pendingExportFile = file
                    pendingExportType = ExportType.PDF
                    showExportDialog = true
                }
            }
        )
    }

    if (editingDamage != null) {
        DamageDialog(
            photoPath = editingDamage!!.photoPath,
            annotatedPhotoPath = editingDamage!!.annotatedPhotoPath,
            currentMeter = editingDamage!!.position,
            projectId = projectId,
            existingDamage = editingDamage,
            onSave = { updated ->
                viewModel.updateDamage(updated)
                editingDamage = null
            },
            onDismiss = { editingDamage = null }
        )
    }

    if (editingNote != null) {
        NoteDialog(
            currentMeter = editingNote!!.position,
            projectId = projectId,
            existingNote = editingNote,
            onSave = { updated ->
                viewModel.updateNote(updated)
                editingNote = null
            },
            onDismiss = { editingNote = null }
        )
    }

    if (creatingNote) {
        NoteDialog(
            currentMeter = 0f,
            projectId = projectId,
            existingNote = null,
            onSave = { newNote ->
                viewModel.addNote(newNote)
                creatingNote = false
            },
            onDismiss = { creatingNote = false }
        )
    }

    if (showDeleteProjectDialog) {
        val pNum = project?.projectNumber.orEmpty().ifEmpty { "(ohne Nummer)" }
        val dmgCount = damages.size
        val noteCount = notes.size
        val recCount = recordings.size
        // Dieser Lösch-Dialog bewusst ~15 % größere Schrift (token-basiert, aus der
        // jeweiligen Typo-Stufe abgeleitet) — kritische, unwiderrufliche Aktion.
        val ds = 1.15f
        val t = MaterialTheme.typography
        AlertDialog(
            onDismissRequest = { showDeleteProjectDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StatusRed) },
            title = {
                Text(
                    "Projekt unwiderruflich löschen?",
                    style = t.headlineSmall.copy(fontSize = t.headlineSmall.fontSize * ds)
                )
            },
            text = {
                Column {
                    Text(
                        "Projekt: $pNum",
                        style = t.bodyMedium.copy(fontSize = t.bodyMedium.fontSize * ds)
                    )
                    Spacer(Modifier.height(Dimensions.SectionSpacing))
                    Text(
                        "Es werden gelöscht:\n" +
                        " • $dmgCount Schäden (inkl. Fotos)\n" +
                        " • $noteCount Notizen (inkl. Audio)\n" +
                        " • $recCount Video-Aufnahmen\n" +
                        " • Berichte (PDF) und Exporte (ZIP/XML)",
                        style = t.bodySmall.copy(fontSize = t.bodySmall.fontSize * ds)
                    )
                    Spacer(Modifier.height(Dimensions.SectionSpacing))
                    Text(
                        "Diese Aktion kann nicht rückgängig gemacht werden.",
                        style = t.bodySmall.copy(fontSize = t.bodySmall.fontSize * ds),
                        color = StatusRed
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteProjectDialog = false
                    viewModel.deleteProjectCompletely()
                }) {
                    Text(
                        "Endgültig löschen",
                        color = StatusRed,
                        style = t.labelLarge.copy(fontSize = t.labelLarge.fontSize * ds)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProjectDialog = false }) {
                    Text(
                        S("cancel"),
                        style = t.labelLarge.copy(fontSize = t.labelLarge.fontSize * ds)
                    )
                }
            }
        )
    }

    if (deletingDamage != null) {
        AlertDialog(
            onDismissRequest = { deletingDamage = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusRed) },
            title = { Text(S("delete_damage_title")) },
            text = { Text("${deletingDamage!!.damageType} - ${String.format("%.2f", deletingDamage!!.position)} m") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDamage(deletingDamage!!)
                    deletingDamage = null
                }) {
                    Text(S("delete"), color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDamage = null }) {
                    Text(S("close"))
                }
            }
        )
    }

    if (deletingNote != null) {
        AlertDialog(
            onDismissRequest = { deletingNote = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusRed) },
            title = { Text(S("delete_note_title")) },
            text = { Text("${String.format("%.2f", deletingNote!!.position)} m - ${deletingNote!!.text.take(50)}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(deletingNote!!)
                    deletingNote = null
                }) {
                    Text(S("delete"), color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingNote = null }) {
                    Text(S("close"))
                }
            }
        )
    }

    if (annotatingDamage != null && annotationPhotoPath.isNotEmpty()) {
        ImageAnnotationDialog(
            photoPath = annotationPhotoPath,
            onDismiss = {
                annotatingDamage = null
                annotationPhotoPath = ""
            },
            onSaved = { savedPath, originalPath, isCopy ->
                val damage = annotatingDamage!!
                val updated = if (isCopy) {
                    damage.copy(photoPath = originalPath, annotatedPhotoPath = savedPath)
                } else {
                    damage.copy(photoPath = savedPath, annotatedPhotoPath = "")
                }
                viewModel.updateDamage(updated)
                annotatingDamage = null
                annotationPhotoPath = ""
            }
        )
    }

    if (deletingVideo != null) {
        AlertDialog(
            onDismissRequest = { deletingVideo = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusRed) },
            title = { Text(S("delete_video_title")) },
            text = { Text(deletingVideo!!.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(deletingVideo!!)
                    deletingVideo = null
                }) {
                    Text(S("delete"), color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVideo = null }) {
                    Text(S("close"))
                }
            }
        )
    }
}

/**
 * Header-Aktion als Icon-MIT-Label (analog Navi-Rail-Einträge): Icon oben,
 * kurzes Label darunter. Gesamte Spalte ist Touch-Target (≥ 56 dp breit/hoch).
 */
@Composable
private fun HeaderAction(
    iconKey: String,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val effectiveTint = if (enabled) tint else DrainQTheme.colors.textTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(horizontal = Dimensions.Space8)
            .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .widthIn(min = Dimensions.TouchMedium)
            .heightIn(min = Dimensions.TouchMedium)
            .padding(horizontal = Dimensions.Space8, vertical = Dimensions.Space4)
    ) {
        DqIcon(iconKey, contentDescription = label, tint = effectiveTint,
            size = Dimensions.DqIconStd)
        Spacer(modifier = Modifier.height(Dimensions.Space4))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = effectiveTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabWithBadge(label: String, count: Int, index: Int, selected: Int, onClick: () -> Unit) {
    Tab(
        selected = selected == index,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (count > 0) {
                    Spacer(modifier = Modifier.width(Dimensions.MediumSpacing))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Text("$count", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    )
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    if (text.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeXSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PhotosTab(photoDamages: List<DamageEntity>, onPhotoClick: (DamageEntity, String) -> Unit) {
    if (photoDamages.isEmpty()) {
        EmptyState(Icons.Default.PhotoLibrary, S("no_photos"))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = Dimensions.PhotoGridMinCell),
            contentPadding = PaddingValues(Dimensions.SectionSpacing),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)
        ) {
            items(photoDamages) { damage ->
                val hasAnnotated = damage.annotatedPhotoPath.isNotEmpty() &&
                        File(damage.annotatedPhotoPath).exists() &&
                        File(damage.annotatedPhotoPath).length() > 0
                Card(
                    onClick = { onPhotoClick(damage, damage.photoPath) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column {
                        if (hasAnnotated) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = File(damage.photoPath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(Dimensions.PhotoThumbnailHeight),
                                    contentScale = ContentScale.Crop
                                )
                                AsyncImage(
                                    model = File(damage.annotatedPhotoPath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(Dimensions.PhotoThumbnailHeight),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            AsyncImage(
                                model = File(damage.photoPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimensions.PhotoThumbnailHeight),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            text = "${String.format("%.1f", damage.position)}m - ${damage.damageType}",
                            modifier = Modifier.padding(Dimensions.SectionSpacing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DamagesTab(
    damages: List<DamageEntity>,
    onPhotoClick: (DamageEntity, String) -> Unit,
    onEdit: (DamageEntity) -> Unit,
    onDelete: (DamageEntity) -> Unit
) {
    if (damages.isEmpty()) {
        EmptyState(Icons.Default.Warning, S("no_damages"))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(Dimensions.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)
        ) {
            items(damages.size) { index ->
                val damage = damages[index]
                Card(
                    modifier = Modifier.heightIn(min = Dimensions.CardMinHeight),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.TouchSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasPhoto = damage.photoPath.isNotEmpty() &&
                                File(damage.photoPath).exists() &&
                                File(damage.photoPath).length() > 0
                        val hasAnnotated = damage.annotatedPhotoPath.isNotEmpty() &&
                                File(damage.annotatedPhotoPath).exists() &&
                                File(damage.annotatedPhotoPath).length() > 0
                        if (hasPhoto || hasAnnotated) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SmallSpacing)) {
                                if (hasPhoto) {
                                    Card(onClick = { onPhotoClick(damage, damage.photoPath) }) {
                                        AsyncImage(
                                            model = File(damage.photoPath),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(Dimensions.DamageThumbnailSize)
                                                .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                if (hasAnnotated) {
                                    Card(onClick = { onPhotoClick(damage, damage.annotatedPhotoPath) }) {
                                        AsyncImage(
                                            model = File(damage.annotatedPhotoPath),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(Dimensions.DamageThumbnailSize)
                                                .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(Dimensions.DamageThumbnailSize)
                                    .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${index + 1} - ${damage.damageType}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${S("position_label")} ${String.format("%.2f", damage.position)} m",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (damage.description.isNotEmpty()) {
                                Text(
                                    text = damage.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dateFmt.format(Date(damage.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                IconButton(onClick = { onEdit(damage) }, modifier = Modifier.size(Dimensions.IconSizeXLarge)) {
                                    Icon(Icons.Default.Edit, contentDescription = S("edit"),
                                        modifier = Modifier.size(Dimensions.IconSizeMedium))
                                }
                                IconButton(onClick = { onDelete(damage) }, modifier = Modifier.size(Dimensions.IconSizeXLarge)) {
                                    Icon(Icons.Default.Delete, contentDescription = S("delete"),
                                        tint = StatusRed, modifier = Modifier.size(Dimensions.IconSizeMedium))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideosTab(files: List<File>, onVideoClick: (File) -> Unit, onDelete: (File) -> Unit) {
    if (files.isEmpty()) {
        EmptyState(Icons.Default.Videocam, S("no_recordings"))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(Dimensions.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)
        ) {
            items(files) { file ->
                Card(
                    onClick = { onVideoClick(file) },
                    modifier = Modifier.heightIn(min = Dimensions.CardMinHeight),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.TouchSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.NavRailIconSize))
                        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleSmall)
                            Text(formatFileSize(file.length()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFmt.format(Date(file.lastModified())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(file) }, modifier = Modifier.size(Dimensions.IconSizeXLarge)) {
                            Icon(Icons.Default.Delete, contentDescription = S("delete"),
                                tint = StatusRed, modifier = Modifier.size(Dimensions.IconSizeMedium))
                        }
                        Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                        Icon(Icons.Default.PlayCircle, contentDescription = S("play"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.IconSizeXLarge))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: List<NoteEntity>,
    onEdit: (NoteEntity) -> Unit,
    onDelete: (NoteEntity) -> Unit,
    onAdd: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
    if (notes.isEmpty()) {
        EmptyState(Icons.Default.Edit, S("no_notes"))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(Dimensions.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)
        ) {
            items(notes) { note ->
                Card(
                    modifier = Modifier.heightIn(min = Dimensions.CardMinHeight),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(Dimensions.TouchSpacing)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (note.audioPath.isNotEmpty() && File(note.audioPath).exists())
                                    Icons.Default.Mic else Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                            Text(
                                text = "${S("position_label")} ${String.format("%.2f", note.position)} m",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = dateFmt.format(Date(note.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { onEdit(note) }, modifier = Modifier.size(Dimensions.IconSizeXLarge)) {
                                Icon(Icons.Default.Edit, contentDescription = S("edit"),
                                    modifier = Modifier.size(Dimensions.IconSizeMedium))
                            }
                            IconButton(onClick = { onDelete(note) }, modifier = Modifier.size(Dimensions.IconSizeXLarge)) {
                                Icon(Icons.Default.Delete, contentDescription = S("delete"),
                                    tint = StatusRed, modifier = Modifier.size(Dimensions.IconSizeMedium))
                            }
                        }
                        if (note.text.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (note.audioPath.isNotEmpty() && File(note.audioPath).exists()) {
                            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                            AudioPlaybackRow(audioPath = note.audioPath)
                        }
                    }
                }
            }
        }
    }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimensions.SectionSpacing)
        ) {
            Icon(Icons.Default.Add, contentDescription = S("new_note"),
                modifier = Modifier.size(Dimensions.IconSizeXLarge))
        }
    }
}

@Composable
private fun AudioPlaybackRow(audioPath: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<AndroidMediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.let {
                try { it.stop() } catch (_: Exception) {}
                it.release()
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = {
                if (isPlaying) {
                    player?.let {
                        try { it.stop() } catch (_: Exception) {}
                        it.release()
                    }
                    player = null
                    isPlaying = false
                } else {
                    val mp = AndroidMediaPlayer()
                    mp.setDataSource(audioPath)
                    mp.setOnCompletionListener {
                        isPlaying = false
                        it.release()
                        player = null
                    }
                    mp.prepare()
                    mp.start()
                    player = mp
                    isPlaying = true
                }
            },
            modifier = Modifier.height(Dimensions.IconSizeXLarge),
            contentPadding = PaddingValues(horizontal = Dimensions.TouchSpacing)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSizeSmall)
            )
            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
            Text(
                if (isPlaying) S("stop") else S("play"),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.IconSizeXXLarge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
        else -> "${String.format("%.1f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

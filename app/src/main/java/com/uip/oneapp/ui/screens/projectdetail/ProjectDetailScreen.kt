package com.uip.oneapp.ui.screens.projectdetail

import android.content.Intent
import android.media.MediaPlayer as AndroidMediaPlayer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

    // Export options dialog state
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var exportOptionsAction by remember { mutableStateOf(ExportType.PDF) }
    var exportIncludePhotos by remember { mutableStateOf(true) }
    var exportIncludeXml by remember { mutableStateOf(true) }
    val hasProjectMap = project?.mapImagePath?.let { File(it).exists() } == true
    var exportIncludeMap by remember(hasProjectMap) { mutableStateOf(hasProjectMap) }
    // exportReversed removed - now uses persisted damage sort order from InspectionScreen

    // Edit/delete state
    var editingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var deletingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var deletingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var deletingVideo by remember { mutableStateOf<File?>(null) }
    var annotatingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var annotationPhotoPath by remember { mutableStateOf("") }

    val shareReportTitle = S("share_report")

    // SAF launcher to save file to user-chosen location (USB, SD, etc.)
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

    // Handle export result - show dialog
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

    // Export dialog
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
                    // Share via system chooser
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
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(S("export_share"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    // Open SAF file picker (shows USB, SD, internal, cloud)
                    saveToLauncher.launch(pendingExportFile!!.name)
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(S("export_save_to"))
                }
            }
        )
    }

    // Export options dialog
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
                    // Include photos checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = exportIncludePhotos,
                            onCheckedChange = { exportIncludePhotos = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("export_include_photos"))
                    }

                    // Include XML checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = exportIncludeXml,
                            onCheckedChange = { exportIncludeXml = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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

                    // Include map checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = S("include_map"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasProjectMap)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Direction selection removed - uses damage sort order from InspectionScreen
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
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
                        Icon(Icons.Default.ArrowBack, contentDescription = S("back"))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("project_form/$projectId")
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = S("edit"))
                    }
                    IconButton(onClick = {
                        navController.navigate("inspection/$projectId")
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = S("inspection_action"),
                            tint = StatusGreen)
                    }
                    IconButton(
                        onClick = {
                            exportOptionsAction = ExportType.PDF
                            showExportOptionsDialog = true
                        },
                        enabled = exportProgress == null && project != null
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = S("pdf_export"),
                            tint = if (exportProgress == null) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                    IconButton(
                        onClick = {
                            exportOptionsAction = ExportType.ZIP
                            showExportOptionsDialog = true
                        },
                        enabled = exportProgress == null && project != null
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = S("zip_export"),
                            tint = if (exportProgress == null) MaterialTheme.colorScheme.secondary else Color.Gray)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Export progress bar
            if (exportProgress != null) {
                LinearProgressIndicator(
                    progress = exportProgress!!,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Project summary card
            if (project != null) {
                val p = project!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                        // Video overlay chip removed per user request
                    }
                }
            }

            // Tab row
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

            // Tab content
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
                    onDelete = { deletingNote = it }
                )
            }
        }
    }

    // Fullscreen image dialog
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

    // Video playback dialog
    if (playbackVideo != null) {
        VideoPlaybackDialog(
            videoFile = playbackVideo!!,
            onDismiss = { playbackVideo = null },
            projectId = projectId
        )
    }

    // PDF preview dialog
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

    // Edit damage dialog
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

    // Edit note dialog
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

    // Delete damage confirmation
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

    // Delete note confirmation
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

    // Image annotation dialog
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

    // Delete video confirmation
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

@Composable
private fun TabWithBadge(label: String, count: Int, index: Int, selected: Int, onClick: () -> Unit) {
    Tab(
        selected = selected == index,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (count > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
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
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
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
            columns = GridCells.Adaptive(minSize = 180.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        .height(140.dp),
                                    contentScale = ContentScale.Crop
                                )
                                AsyncImage(
                                    model = File(damage.annotatedPhotoPath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(140.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            AsyncImage(
                                model = File(damage.photoPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            text = "${String.format("%.1f", damage.position)}m - ${damage.damageType}",
                            modifier = Modifier.padding(8.dp),
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
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(damages.size) { index ->
                val damage = damages[index]
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnails (original + annotated)
                        val hasPhoto = damage.photoPath.isNotEmpty() &&
                                File(damage.photoPath).exists() &&
                                File(damage.photoPath).length() > 0
                        val hasAnnotated = damage.annotatedPhotoPath.isNotEmpty() &&
                                File(damage.annotatedPhotoPath).exists() &&
                                File(damage.annotatedPhotoPath).length() > 0
                        if (hasPhoto || hasAnnotated) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (hasPhoto) {
                                    Card(onClick = { onPhotoClick(damage, damage.photoPath) }) {
                                        AsyncImage(
                                            model = File(damage.photoPath),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp)),
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
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

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
                                IconButton(onClick = { onEdit(damage) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = S("edit"),
                                        modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(damage) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = S("delete"),
                                        tint = StatusRed, modifier = Modifier.size(18.dp))
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
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files) { file ->
                Card(
                    onClick = { onVideoClick(file) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleSmall)
                            Text(formatFileSize(file.length()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFmt.format(Date(file.lastModified())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(file) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = S("delete"),
                                tint = StatusRed, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.PlayCircle, contentDescription = S("play"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
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
    onDelete: (NoteEntity) -> Unit
) {
    if (notes.isEmpty()) {
        EmptyState(Icons.Default.Edit, S("no_notes"))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notes) { note ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (note.audioPath.isNotEmpty() && File(note.audioPath).exists())
                                    Icons.Default.Mic else Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                            IconButton(onClick = { onEdit(note) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = S("edit"),
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(note) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = S("delete"),
                                    tint = StatusRed, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (note.text.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (note.audioPath.isNotEmpty() && File(note.audioPath).exists()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AudioPlaybackRow(audioPath = note.audioPath)
                        }
                    }
                }
            }
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
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
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
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
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

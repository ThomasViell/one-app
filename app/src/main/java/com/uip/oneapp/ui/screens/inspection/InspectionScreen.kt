package com.uip.oneapp.ui.screens.inspection

import android.graphics.Bitmap
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.network.HardwareService
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.export.OverlayEntry
import com.uip.oneapp.export.ProjectOverlayInfo
import com.uip.oneapp.export.VideoOverlayProcessor
import com.uip.oneapp.network.DeviceType
import com.uip.oneapp.network.FfmpegRecordingState
import com.uip.oneapp.network.FfmpegRtspRecorder
import com.uip.oneapp.ui.components.FfmpegVideoPlayer
import com.uip.oneapp.ui.components.VideoPlayerPlaceholder
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.screens.settings.SettingsViewModel
import com.uip.oneapp.ui.screens.settings.settingsStore
import com.uip.oneapp.ui.theme.*
import com.uip.oneapp.ui.utils.LocalWindowSizeClass
import com.uip.oneapp.ui.utils.videoWeight
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
@Composable
fun InspectionScreen(
    navController: NavController,
    projectId: Long? = null,
    hardwareService: HardwareService = koinInject(),
    projectRepository: ProjectRepository = koinInject(),
    damageRepository: DamageRepository = koinInject(),
    noteRepository: NoteRepository = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = koinViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val osdSettings = settingsState.toOsdSettings()

    val project by remember(projectId) {
        if (projectId != null) projectRepository.getProjectFlow(projectId)
        else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val damages by remember(projectId) {
        if (projectId != null) damageRepository.getDamagesForProject(projectId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val notes by remember(projectId) {
        if (projectId != null) noteRepository.getNotesForProject(projectId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val hwState by hardwareService.hardwareState.collectAsState()
    val cable = hwState.cableController
    val crawler = hwState.crawlerController
    val conn = hwState.connectionStatus
    var meterValue by remember { mutableStateOf(0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var videoScale by remember { mutableFloatStateOf(1f) }
    var videoOffset by remember { mutableStateOf(Offset.Zero) }

    // Damage dialog state
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var showDamageDialog by remember { mutableStateOf(false) }
    var showAnnotationDialog by remember { mutableStateOf(false) }
    var annotationPhotoPath by remember { mutableStateOf("") }
    var capturedPhotoPath by remember { mutableStateOf("") }
    var capturedAnnotatedPath by remember { mutableStateOf("") }
    var editingDamage by remember { mutableStateOf<DamageEntity?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    val damagesNewestFirstKey = remember { booleanPreferencesKey("damages_newest_first") }
    val damagesNewestFirstPref by context.settingsStore.data.collectAsState(initial = null)
    var damagesNewestFirst by remember { mutableStateOf(true) }
    LaunchedEffect(damagesNewestFirstPref) {
        damagesNewestFirstPref?.let { prefs ->
            damagesNewestFirst = prefs[damagesNewestFirstKey] ?: true
        }
    }
    var notesNewestFirst by remember { mutableStateOf(true) }

    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingFilePath by remember { mutableStateOf<String?>(null) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var exoPlayerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var recordingStartTime by remember { mutableLongStateOf(0L) }
    var recordingElapsed by remember { mutableStateOf("00:00") }
    var showProjectName by remember { mutableStateOf(false) }
    var recordingProjectName by remember { mutableStateOf("") }

    // Overlay burn-in state
    val overlayEntries = remember { mutableStateListOf<OverlayEntry>() }
    var isProcessingOverlay by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var lastRecordedFilePath by remember { mutableStateOf<String?>(null) }

    // OSD Phase 4: live overlay state
    var findingFlash by remember { mutableStateOf<String?>(null) }
    var isStreamPaused by remember { mutableStateOf(false) }

    // Phase 5: FFmpegRtspRecorder for OSD burn-in recording
    val ffmpegRecorder = remember { FfmpegRtspRecorder(context) }
    val ffmpegRecState by ffmpegRecorder.state.collectAsState()
    val isFfmpegRecording = ffmpegRecState == FfmpegRecordingState.RECORDING

    DisposableEffect(Unit) {
        onDispose { ffmpegRecorder.stopRecording() }
    }

    // OSD line builders (recomputed when project or meter changes)
    val osdLine1 = buildOsdLine1(project, settingsState.deviceType)
    val osdLine2 = buildOsdLine2(meterValue, osdSettings, crawler.sondeFrequency)

    // Auto-dismiss finding flash after 5 seconds. The flash also drives the
    // burned-in OSD layer in the active recording, so push every change to
    // the recorder (no-op when not recording).
    LaunchedEffect(findingFlash) {
        ffmpegRecorder.updateFinding(findingFlash)
        if (findingFlash != null) {
            kotlinx.coroutines.delay(5_000)
            findingFlash = null
        }
    }

    // Recording duration timer + overlay entry collection
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingStartTime = System.currentTimeMillis()
            showProjectName = true
            overlayEntries.clear()
            while (true) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                recordingElapsed = String.format("%02d:%02d", min, sec)
                // Collect overlay entry each second for burn-in (use Locale.US to avoid comma decimals)
                val timeStr = java.time.LocalTime.now().toString().take(8)
                val meterStr = String.format(java.util.Locale.US, "%.2f", meterValue)
                overlayEntries.add(OverlayEntry(elapsed.toInt(), "${meterStr}m | $timeStr"))
                // Phase 5: update FFmpegRtspRecorder drawtext file with current OSD line2
                ffmpegRecorder.updateOsdLine2(buildOsdLine2(meterValue, osdSettings, crawler.sondeFrequency))
                kotlinx.coroutines.delay(1000)
            }
        } else {
            recordingElapsed = "00:00"
            showProjectName = false
        }
    }

    // Hide project name after 5 seconds
    LaunchedEffect(showProjectName) {
        if (showProjectName) {
            kotlinx.coroutines.delay(5000)
            showProjectName = false
        }
    }

    // Auto-connect to hardware if not already connected
    LaunchedEffect(Unit) {
        if (!hardwareService.isConnected) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val status = hardwareService.probeEndpoints()
                if (status.cableControllerReachable || status.crawlerControllerReachable) {
                    hardwareService.startPolling()
                }
            }
        }
    }

    // Update meter from hardware when available
    LaunchedEffect(cable.meterReading) {
        cable.meterReading?.let { meterValue = it }
    }

    // Build RTSP URL from discovered IP, fallback to saved URL from service
    val rtspUrl = remember(conn.discoveredIp, hardwareService.lastRtspUrl) {
        if (conn.discoveredIp.isNotEmpty()) {
            "rtsp://${conn.discoveredIp}:8554/1234"
        } else hardwareService.lastRtspUrl
    }

    val windowSizeClass = LocalWindowSizeClass.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isFullscreen) 0.dp else 8.dp)
    ) {
        // Left side - Video Area
        Column(
            modifier = Modifier
                .weight(if (isFullscreen) 1f else windowSizeClass.videoWeight)
                .fillMaxHeight()
        ) {
            // Video Player
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = videoScale
                                scaleY = videoScale
                                translationX = videoOffset.x
                                translationY = videoOffset.y
                            }
                    ) {
                        if (rtspUrl.isNotEmpty()) {
                            FfmpegVideoPlayer(
                                rtspUrl = rtspUrl,
                                modifier = Modifier.fillMaxSize(),
                                osdSettings = osdSettings,
                                osdLine1 = osdLine1,
                                osdLine2 = osdLine2,
                                findingFlash = findingFlash,
                                isPaused = isStreamPaused,
                                isFfmpegRecording = isFfmpegRecording,
                                onPlayerReady = { exoPlayerRef = it },
                                onTextureViewReady = { textureViewRef = it }
                            )
                        } else {
                            VideoPlayerPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }

                    // Transparent overlay for double-tap and pinch-to-zoom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (videoScale * zoom).coerceIn(1f, 3f)
                                    // Adjust offset for zoom center
                                    videoOffset = if (newScale == 1f) {
                                        Offset.Zero
                                    } else {
                                        val maxX = (newScale - 1f) * size.width / 2f
                                        val maxY = (newScale - 1f) * size.height / 2f
                                        Offset(
                                            x = (videoOffset.x + pan.x).coerceIn(-maxX, maxX),
                                            y = (videoOffset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                    videoScale = newScale
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { isFullscreen = !isFullscreen })
                            }
                    )

                    // Text Overlay (bottom left) - Meter, Time, Recording
                    // Show when: not recording, OR recording with overlay enabled
                    val showOverlay = !isRecording
                    if (showOverlay) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            val sondeLabel = crawler.sondeFrequency
                            val overlayParts = mutableListOf(
                                "${String.format("%.2f", meterValue)}m"
                            )
                            if (sondeLabel != null) {
                                overlayParts.add("${S("sonde")}: $sondeLabel")
                            }
                            overlayParts.add(java.time.LocalTime.now().toString().take(8))
                            if (isRecording) {
                                overlayParts.add("REC $recordingElapsed")
                            }
                            Text(
                                text = overlayParts.joinToString(" | "),
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Project name overlay (top center, 5 seconds at recording start)
                    if (showProjectName && recordingProjectName.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = recordingProjectName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

        }

        if (!isFullscreen) {
            Spacer(modifier = Modifier.width(8.dp))

            // Right side - Status Panel
            Card(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                // Action Buttons (2×2 compact grid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        onClick = {
                            if (projectId == null) return@OutlinedButton
                            val tv = textureViewRef
                            val dir = File(context.getExternalFilesDir("damages"), "project_$projectId")
                            dir.mkdirs()
                            val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
                            val bitmap = if (tv != null && tv.width > 0) tv.bitmap else null
                            if (bitmap != null) {
                                if (osdSettings.enableOsdBurnIn) {
                                    OsdRenderer.renderBitmap(bitmap, osdSettings, osdLine1, osdLine2)
                                }
                                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                                Log.d("InspectionScreen", "Quick photo saved: ${file.absolutePath}")
                            } else { file.createNewFile() }
                            scope.launch {
                                damageRepository.saveDamage(DamageEntity(projectId = projectId, position = meterValue, damageType = "Foto", photoPath = file.absolutePath))
                            }
                        }
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(S("photo"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        onClick = {
                            if (projectId == null) return@Button
                            editingDamage = null
                            val tv = textureViewRef
                            if (tv != null && tv.width > 0 && tv.height > 0) {
                                val dir = File(context.getExternalFilesDir("damages"), "project_$projectId")
                                dir.mkdirs()
                                val file = File(dir, "dmg_${System.currentTimeMillis()}.jpg")
                                val bitmap = tv.bitmap
                                if (bitmap != null) {
                                    if (osdSettings.enableOsdBurnIn) {
                                        OsdRenderer.renderBitmap(bitmap, osdSettings, osdLine1, osdLine2)
                                    }
                                    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                                    Log.d("InspectionScreen", "Screenshot saved (TextureView): ${file.absolutePath}")
                                } else {
                                    Log.w("InspectionScreen", "TextureView bitmap null, tv=$tv")
                                    file.createNewFile()
                                }
                                capturedPhotoPath = file.absolutePath
                                capturedAnnotatedPath = ""
                                if (isRecording) { exoPlayerRef?.pause(); isStreamPaused = true }
                                showDamageDialog = true
                            } else {
                                capturedPhotoPath = ""
                                capturedAnnotatedPath = ""
                                if (isRecording) { exoPlayerRef?.pause(); isStreamPaused = true }
                                showDamageDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(S("damage"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        onClick = {
                            if (projectId == null) return@OutlinedButton
                            editingNote = null
                            showNoteDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Note, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(S("note"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    if (!isRecording) {
                        Button(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            onClick = {
                                if (projectId == null || rtspUrl.isEmpty()) return@Button
                                showRecordingDialog = true
                            },
                            enabled = projectId != null && rtspUrl.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(S("recording"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    } else {
                        Button(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            onClick = {
                                lastRecordedFilePath = recordingFilePath
                                recordingFilePath = null
                                isRecording = false
                                ffmpegRecorder.stopRecording()
                                Log.d("InspectionScreen", "FFmpeg recording stopped after $recordingElapsed")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${S("stop")} $recordingElapsed", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = S("hardware_status"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Light Status - locally tracked via command sending
                val lightValue = when {
                    !crawler.lightAvailable -> S("status_not_connected")
                    crawler.lightOn == true -> S("light_on") + (crawler.frontLightPower?.let { " ($it%)" } ?: "")
                    crawler.lightOn == false -> S("light_off")
                    else -> S("light_off")
                }
                StatusRow(
                    icon = Icons.Default.Lightbulb,
                    label = S("light"),
                    value = lightValue,
                    statusColor = when {
                        !crawler.lightAvailable -> Color.Gray
                        crawler.lightOn == true -> StatusGreen
                        else -> StatusRed
                    },
                    action = {
                        SmallActionButton(
                            text = if (crawler.lightOn == true) S("light_level") else S("light_on"),
                            onClick = {
                                Log.d("InspectionScreen", "Licht button clicked")
                                hardwareService.cycleLightPower()
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Probe/Sonde Status
                val sondeValue = when {
                    crawler.laserOn == null -> S("no_data")
                    crawler.laserOn -> S("light_on") + (crawler.sondeFrequency?.let { " ($it)" } ?: "")
                    else -> S("light_off")
                }
                StatusRow(
                    icon = Icons.Default.Sensors,
                    label = S("sonde"),
                    value = sondeValue,
                    statusColor = when (crawler.laserOn) {
                        true -> StatusGreen
                        false -> StatusRed
                        null -> Color.Gray
                    },
                    action = {
                        SmallActionButton(
                            text = if (crawler.laserOn == true) S("frequency") else S("light_on"),
                            onClick = {
                                Log.d("InspectionScreen", "Sonde button clicked")
                                hardwareService.cycleFrequency()
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Meter - Absolute
                StatusRow(
                    icon = Icons.Default.Straighten,
                    label = S("meter_absolute"),
                    value = if (cable.meterReading != null) "${String.format("%.2f", meterValue)} m" else S("no_data"),
                    statusColor = if (cable.meterReading != null) MeterBlue else Color.Gray,
                    action = {
                        SmallActionButton(
                            text = "0",
                            onClick = {
                                Log.d("InspectionScreen", "Absolut reset clicked")
                                hardwareService.resetMeterAbsolute()
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Meter - Relative (Strecke)
                StatusRow(
                    icon = Icons.Default.Straighten,
                    label = S("meter_distance"),
                    value = cable.currentDistance?.let { String.format("%.2f m", it) } ?: S("no_data"),
                    statusColor = if (cable.currentDistance != null) MeterBlue else Color.Gray,
                    action = {
                        SmallActionButton(
                            text = "0",
                            onClick = {
                                Log.d("InspectionScreen", "Strecke reset clicked")
                                hardwareService.resetMeterRelative()
                            }
                        )
                    }
                )

                // Battery
                cable.batteryLevel?.let { battery ->
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusRow(
                        icon = Icons.Default.BatteryStd,
                        label = S("battery"),
                        value = "$battery%",
                        statusColor = when {
                            battery > 50 -> StatusGreen
                            battery > 20 -> StatusYellow
                            else -> StatusRed
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = S("last_damages"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            damagesNewestFirst = !damagesNewestFirst
                            scope.launch {
                                context.settingsStore.edit { prefs ->
                                    prefs[damagesNewestFirstKey] = damagesNewestFirst
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (damagesNewestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val sortedDamages = if (damagesNewestFirst) damages else damages.reversed()

                if (damages.isEmpty()) {
                    Text(
                        text = S("no_damages_recorded"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sortedDamages.take(5).forEach { damage ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(damage.id) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            editingDamage = damage
                                            capturedPhotoPath = damage.photoPath
                                            capturedAnnotatedPath = damage.annotatedPhotoPath
                                            showDamageDialog = true
                                        }
                                    )
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnails (original + annotated side by side)
                            val hasPhoto = damage.photoPath.isNotEmpty() &&
                                    File(damage.photoPath).exists() &&
                                    File(damage.photoPath).length() > 0
                            val hasAnnotated = damage.annotatedPhotoPath.isNotEmpty() &&
                                    File(damage.annotatedPhotoPath).exists() &&
                                    File(damage.annotatedPhotoPath).length() > 0
                            if (hasPhoto || hasAnnotated) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (hasPhoto) {
                                        AsyncImage(
                                            model = File(damage.photoPath),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (hasAnnotated) {
                                        AsyncImage(
                                            model = File(damage.annotatedPhotoPath),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${String.format("%.1f", damage.position)}m  ${damage.damageType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (damage.description.isNotEmpty()) {
                                    Text(
                                        text = damage.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (damages.size > 5) {
                        Text(
                            text = "... +${damages.size - 5} weitere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Notes section
                if (notes.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = S("notes"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { notesNewestFirst = !notesNewestFirst },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (notesNewestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val sortedNotes = if (notesNewestFirst) notes else notes.reversed()

                    sortedNotes.take(3).forEach { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(note.id) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            editingNote = note
                                            showNoteDialog = true
                                        }
                                    )
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (note.audioPath.isNotEmpty() && File(note.audioPath).exists())
                                    Icons.Default.Mic else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${String.format("%.1f", note.position)}m  ${note.text.ifEmpty { S("voice_note") }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (notes.size > 3) {
                        Text(
                            text = "... +${notes.size - 3} weitere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Project Info
                if (project != null) {
                    Card(
                        onClick = {
                            navController.navigate("project_form/${project!!.id}")
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val p = project!!
                            Text(
                                text = p.projectNumber.ifEmpty { "Projekt" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (p.auftraggeber.isNotEmpty()) {
                                Text(
                                    text = p.auftraggeber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (p.durchmesser.isNotEmpty()) {
                                    Text(
                                        text = "DN ${p.durchmesser}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (p.inspektionslaenge.isNotEmpty()) {
                                    Text(
                                        text = "${p.inspektionslaenge} m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    navController.navigate("projects")
                                }
                            )
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = S("no_project"),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = S("create_new_project"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        } // end if (!isFullscreen) for status panel
    }

    // Damage Dialog
    if (showDamageDialog && projectId != null) {
        DamageDialog(
            photoPath = capturedPhotoPath,
            annotatedPhotoPath = capturedAnnotatedPath,
            currentMeter = meterValue,
            projectId = projectId,
            existingDamage = editingDamage,
            onSave = { damage ->
                scope.launch {
                    if (damage.id > 0) {
                        damageRepository.updateDamage(damage)
                    } else {
                        damageRepository.saveDamage(damage)
                    }
                }
                findingFlash = damage.damageType.ifEmpty { damage.mainCode.ifEmpty { "OBS" } }
                showDamageDialog = false
                editingDamage = null
                if (isRecording) { exoPlayerRef?.play(); isStreamPaused = false }
            },
            onDismiss = {
                showDamageDialog = false
                editingDamage = null
                if (isRecording) { exoPlayerRef?.play(); isStreamPaused = false }
            },
            onOpenAnnotation = { path ->
                annotationPhotoPath = path
                showAnnotationDialog = true
            }
        )
    }

    // Image Annotation Dialog
    if (showAnnotationDialog && annotationPhotoPath.isNotEmpty()) {
        ImageAnnotationDialog(
            photoPath = annotationPhotoPath,
            onDismiss = { showAnnotationDialog = false },
            onSaved = { savedPath, originalPath, isCopy ->
                if (isCopy) {
                    capturedPhotoPath = originalPath
                    capturedAnnotatedPath = savedPath
                } else {
                    capturedPhotoPath = savedPath
                    capturedAnnotatedPath = ""
                }
                showAnnotationDialog = false
            }
        )
    }

    // Recording mode dialog
    if (showRecordingDialog && projectId != null) {
        AlertDialog(
            onDismissRequest = { showRecordingDialog = false },
            title = { Text(S("start_recording_title")) },
            text = { Text(S("recording_mode_question")) },
            confirmButton = {
                TextButton(onClick = {
                    showRecordingDialog = false
                    val projNr = project?.projectNumber?.ifEmpty { "Projekt_$projectId" } ?: "Projekt_$projectId"
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    val dir = File(context.getExternalFilesDir("recordings"), "project_$projectId")
                    dir.mkdirs()
                    recordingProjectName = projNr
                    if (rtspUrl.isNotEmpty()) {
                        // FfmpegRtspRecorder burns OSD directly during recording
                        val file = File(dir, "${projNr}_${ts}.mp4")
                        recordingFilePath = file.absolutePath
                        ffmpegRecorder.startRecording(
                            rtspUrl = rtspUrl,
                            outputFile = file,
                            osdSettings = osdSettings,
                            initialLine1 = osdLine1,
                            initialLine2 = buildOsdLine2(meterValue, osdSettings, crawler.sondeFrequency),
                            initialFinding = findingFlash ?: ""
                        )
                        Log.d("InspectionScreen", "FFmpeg recording with OSD burn-in: ${file.absolutePath}")
                    } else {
                        val file = File(dir, "${projNr}_${ts}.mp4")
                        recordingFilePath = file.absolutePath
                        Log.d("InspectionScreen", "Recording queued (no RTSP): ${file.absolutePath}")
                    }
                    isRecording = true
                }) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S("with_overlay"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecordingDialog = false
                    val projNr = project?.projectNumber?.ifEmpty { "Projekt_$projectId" } ?: "Projekt_$projectId"
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    val dir = File(context.getExternalFilesDir("recordings"), "project_$projectId")
                    dir.mkdirs()
                    recordingProjectName = projNr
                    if (rtspUrl.isNotEmpty()) {
                        val file = File(dir, "${projNr}_${ts}.mp4")
                        recordingFilePath = file.absolutePath
                        // "Without overlay" means no app-side drawing at all — neither
                        // the static OSD bars nor the damage flash. (Hardware OSD from
                        // the camera, if any, is part of the RTSP stream and is recorded
                        // as-is regardless of these flags.)
                        val noOsdSettings = osdSettings.copy(
                            enableOsdBurnIn = false,
                            enableFindingBurnIn = false
                        )
                        ffmpegRecorder.startRecording(
                            rtspUrl = rtspUrl,
                            outputFile = file,
                            osdSettings = noOsdSettings,
                            initialLine1 = "",
                            initialLine2 = ""
                        )
                        Log.d("InspectionScreen", "FFmpeg recording without OSD: ${file.absolutePath}")
                    } else {
                        val file = File(dir, "${projNr}_${ts}.mp4")
                        recordingFilePath = file.absolutePath
                        Log.d("InspectionScreen", "Recording queued (no RTSP): ${file.absolutePath}")
                    }
                    isRecording = true
                }) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S("without_overlay"))
                }
            }
        )
    }

    // Note Dialog
    if (showNoteDialog && projectId != null) {
        NoteDialog(
            currentMeter = meterValue,
            projectId = projectId,
            existingNote = editingNote,
            onSave = { note ->
                scope.launch {
                    if (note.id > 0) {
                        noteRepository.updateNote(note)
                    } else {
                        noteRepository.saveNote(note)
                    }
                }
                showNoteDialog = false
                editingNote = null
            },
            onDismiss = {
                showNoteDialog = false
                editingNote = null
            }
        )
    }

    // Video processing dialog (overlay burn-in)
    if (isProcessingOverlay) {
        AlertDialog(
            onDismissRequest = { /* not dismissible while processing */ },
            title = { Text(S("video_processing_title")) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(S("video_processing_message"))
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = processingProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(processingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }
}

// ── OSD line builders ──────────────────────────────────────────────────────────

private fun buildOsdLine1(project: ProjectEntity?, deviceType: DeviceType): String {
    val parts = mutableListOf(deviceType.displayName)
    project?.let { p ->
        if (p.projectNumber.isNotEmpty()) parts.add(p.projectNumber)
        if (p.auftraggeber.isNotEmpty()) parts.add(p.auftraggeber)
        val startEnd = buildString {
            if (p.startpunkt.isNotEmpty()) append(p.startpunkt)
            if (p.startpunkt.isNotEmpty() && p.endpunkt.isNotEmpty()) append(" -> ")
            if (p.endpunkt.isNotEmpty()) append(p.endpunkt)
        }
        if (startEnd.isNotEmpty()) parts.add(startEnd)
    }
    return parts.joinToString(" | ")
}

private fun buildOsdLine2(
    meterValue: Float,
    osdSettings: com.uip.oneapp.export.OsdSettings,
    sondeFrequency: String?
): String {
    val parts = mutableListOf<String>()
    if (osdSettings.showMeterValue) {
        parts.add(String.format(java.util.Locale.US, "%.2fm", meterValue))
    }
    if (osdSettings.showDate) {
        parts.add(java.time.LocalDate.now().toString())
    }
    if (osdSettings.showInclination && sondeFrequency != null) {
        parts.add(sondeFrequency)
    }
    return parts.joinToString(" | ")
}



@Composable
fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    statusColor: Color,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = statusColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(4.dp))
            action()
        }
    }
}

@Composable
fun SmallActionButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(26.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

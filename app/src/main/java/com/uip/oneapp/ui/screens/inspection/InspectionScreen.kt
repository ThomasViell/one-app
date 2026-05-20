package com.uip.oneapp.ui.screens.inspection

import android.graphics.Bitmap
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.res.painterResource
import com.uip.oneapp.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.uip.oneapp.ui.components.InspectionOsd
import com.uip.oneapp.ui.components.VideoPlayerPlaceholder
import com.uip.oneapp.hardware.HardwareKeyBus
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.navigation.LocalNavRailVisible
import com.uip.oneapp.ui.screens.settings.SettingsViewModel
import com.uip.oneapp.ui.screens.settings.settingsStore
import com.uip.oneapp.ui.theme.*
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
    var showControls by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(0L) }

    // NavRail im Cinema-Mode mit showControls synchronisieren â€” Rail fÃ¤hrt rein/raus wie
    // das rechte Panel. Beim Verlassen der Route Rail wieder dauerhaft zeigen.
    val navRailVisibleState = LocalNavRailVisible.current
    LaunchedEffect(showControls) { navRailVisibleState.value = showControls }
    DisposableEffect(Unit) { onDispose { navRailVisibleState.value = true } }
    var videoScale by remember { mutableFloatStateOf(1f) }
    var videoOffset by remember { mutableStateOf(Offset.Zero) }
    var sliderUi by remember { mutableFloatStateOf((crawler.frontLightPower ?: 0).coerceAtLeast(0).toFloat()) }

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

    // Cinema-Mode auto-hide: controls disappear after 5 s of inactivity
    LaunchedEffect(showControls, lastInteractionMs) {
        if (showControls) {
            kotlinx.coroutines.delay(Dimensions.ControlsAutoHideMs)
            if (System.currentTimeMillis() - lastInteractionMs >= Dimensions.ControlsAutoHideMs) {
                showControls = false
            }
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
    LaunchedEffect(crawler.frontLightPower) {
        val lvl = crawler.frontLightPower
        if (lvl != null && lvl >= 0) sliderUi = lvl.toFloat()
    }

    // VideoSource aus dem HardwareService: kann VideoSource.Rtsp (Netzwerk-Stream)
    // oder VideoSource.LocalBitmap (V4L2-Direct) sein. FÃ¼r Backward-Compat flieÃŸt
    // conn.discoveredIp weiter in eine konstruierte URL ein, falls die Implementation
    // selbst noch keine videoSource published hat.
    val collectedVideoSource by hardwareService.videoSource.collectAsState()
    val videoSource = remember(collectedVideoSource, conn.discoveredIp) {
        when (val s = collectedVideoSource) {
            is com.uip.oneapp.network.VideoSource.LocalBitmap -> s
            is com.uip.oneapp.network.VideoSource.Rtsp -> s
            com.uip.oneapp.network.VideoSource.None -> {
                if (conn.discoveredIp.isNotEmpty()) {
                    com.uip.oneapp.network.VideoSource.Rtsp("rtsp://${conn.discoveredIp}:8554/1234")
                } else com.uip.oneapp.network.VideoSource.None
            }
        }
    }
    // rtspUrl wird weiter unten an einigen Stellen fÃ¼r `enabled`-Checks und das
    // Recorder-Modul gebraucht â€” wir leiten es aus videoSource ab.
    // TODO Phase P5+: MP4-Aufnahme aus VideoSource.LocalBitmap (MediaCodec-basiert)
    // â€” aktuell ist `rtspUrl` im Lokal-Modus leer und der Aufnahme-Button daher
    // disabled. Smoke-Test-Scope deckt nur Live-Video + Sonde/Licht/Meter ab.
    val rtspUrl = remember(videoSource) {
        (videoSource as? com.uip.oneapp.network.VideoSource.Rtsp)?.url ?: ""
    }

    // Cinema-Mode: Video ist immer full-bleed. Steuer-Panel slides von rechts rein.
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Layer 1: Video full-bleed background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds()
        ) {
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
                when (val src = videoSource) {
                    is com.uip.oneapp.network.VideoSource.Rtsp -> {
                        FfmpegVideoPlayer(
                            rtspUrl = src.url,
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
                    }
                    is com.uip.oneapp.network.VideoSource.LocalBitmap -> {
                        com.uip.oneapp.ui.components.LocalBitmapVideoPlayer(
                            frameFlow = src.flow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    com.uip.oneapp.network.VideoSource.None -> {
                        VideoPlayerPlaceholder(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // Layer 2: Gesture overlay â€” pinch-to-zoom + single-tap toggles panel + double-tap zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (videoScale * zoom).coerceIn(1f, 3f)
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
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            if (showControls) lastInteractionMs = System.currentTimeMillis()
                        },
                        onDoubleTap = {
                            if (videoScale > 1f) {
                                videoScale = 1f
                                videoOffset = Offset.Zero
                            } else {
                                videoScale = 2f
                            }
                        }
                    )
                }
        )

        // Layer 3: OSD-Overlay â€” persistent, immer sichtbar unabhÃ¤ngig vom Panel-Status
        InspectionOsd(
            distanceMeters = meterValue,
            sondeMode = crawler.sondeFrequency ?: "â€”",
            lightLevel = crawler.frontLightPower ?: 0,
            voltage = cable.batteryLevel?.let { it / 100f * 12.6f } ?: 0f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Dimensions.OsdPadding)
        )

        // Project name overlay (top center, 5 seconds at recording start)
        if (showProjectName && recordingProjectName.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Dimensions.PanelEdgePadding)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(Dimensions.OverlayCornerRadius))
                    .padding(horizontal = Dimensions.ProjectOverlayHPadding, vertical = Dimensions.ProjectOverlayVPadding)
            ) {
                Text(
                    text = recordingProjectName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Layer 4: Slide-in control panel (Cinema-Mode â€” right edge, tap-on-demand)
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(Dimensions.PanelSlideDuration, easing = FastOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(Dimensions.PanelSlideDuration, easing = FastOutSlowInEasing)
            )
        ) {
            Card(
                modifier = Modifier
                    .width(Dimensions.PanelWidth)
                    .fillMaxHeight()
                    .padding(Dimensions.PanelEdgePadding),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Dimensions.PanelContentPadding)
                ) {
                    // â”€â”€ Action Buttons (2Ã—2) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.ActionButtonSpacing)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f).height(Dimensions.TouchMedium),
                            contentPadding = PaddingValues(horizontal = Dimensions.ButtonIconSpacing),
                            onClick = {
                                lastInteractionMs = System.currentTimeMillis()
                                if (projectId == null) return@OutlinedButton
                                val tv = textureViewRef
                                val dir = File(context.getExternalFilesDir("damages"), "project_$projectId")
                                dir.mkdirs()
                                val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
                                val bitmap = if (tv != null && tv.width > 0) tv.bitmap else null
                                if (bitmap != null) {
                                    // Always render the app-OSD onto the saved photo, even in
                                    // hardware-OSD mode â€” the camera bar isn't part of the
                                    // TextureView capture, so without this the photo would be
                                    // bare. Caller does not provide damage data for the quick
                                    // photo path, so finding is null.
                                    val photoSettings = osdSettings.copy(enableOsdBurnIn = true)
                                    OsdRenderer.renderBitmap(bitmap, photoSettings, osdLine1, osdLine2)
                                    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                                    Log.d("InspectionScreen", "Quick photo saved: ${file.absolutePath}")
                                } else { file.createNewFile() }
                                scope.launch {
                                    damageRepository.saveDamage(DamageEntity(projectId = projectId, position = meterValue, damageType = "Foto", photoPath = file.absolutePath))
                                }
                            }
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_one_camera), contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeSmall), tint = Color.Unspecified)
                            Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                            Text(S("photo"), fontSize = Dimensions.ButtonLabelFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Button(
                            modifier = Modifier.weight(1f).height(Dimensions.TouchMedium),
                            contentPadding = PaddingValues(horizontal = Dimensions.ButtonIconSpacing),
                            onClick = {
                                lastInteractionMs = System.currentTimeMillis()
                                if (projectId == null) return@Button
                                editingDamage = null
                                val tv = textureViewRef
                                if (tv != null && tv.width > 0 && tv.height > 0) {
                                    val dir = File(context.getExternalFilesDir("damages"), "project_$projectId")
                                    dir.mkdirs()
                                    val file = File(dir, "dmg_${System.currentTimeMillis()}.jpg")
                                    val bitmap = tv.bitmap
                                    if (bitmap != null) {
                                        // Same reasoning as the quick-photo path: app-OSD
                                        // must always render onto the captured photo, even
                                        // in hardware-OSD mode. Damage data is added later
                                        // by burnOsdIntoPhoto() in the damage dialog onSave.
                                        val photoSettings = osdSettings.copy(enableOsdBurnIn = true)
                                        OsdRenderer.renderBitmap(bitmap, photoSettings, osdLine1, osdLine2)
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
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeSmall))
                            Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                            Text(S("damage"), fontSize = Dimensions.ButtonLabelFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.ActionButtonSpacing)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f).height(Dimensions.TouchMedium),
                            contentPadding = PaddingValues(horizontal = Dimensions.ButtonIconSpacing),
                            onClick = {
                                lastInteractionMs = System.currentTimeMillis()
                                if (projectId == null) return@OutlinedButton
                                editingNote = null
                                showNoteDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Note, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeSmall))
                            Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                            Text(S("note"), fontSize = Dimensions.ButtonLabelFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        if (!isRecording) {
                            Button(
                                modifier = Modifier.weight(1f).height(Dimensions.TouchMedium),
                                contentPadding = PaddingValues(horizontal = Dimensions.ButtonIconSpacing),
                                onClick = {
                                    lastInteractionMs = System.currentTimeMillis()
                                    if (projectId == null || rtspUrl.isEmpty()) return@Button
                                    showRecordingDialog = true
                                },
                                enabled = projectId != null && rtspUrl.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_one_play_start), contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeSmall), tint = Color.Unspecified)
                                Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                                Text(S("recording"), fontSize = Dimensions.ButtonLabelFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        } else {
                            Button(
                                modifier = Modifier.weight(1f).height(Dimensions.TouchMedium),
                                contentPadding = PaddingValues(horizontal = Dimensions.ButtonIconSpacing),
                                onClick = {
                                    lastInteractionMs = System.currentTimeMillis()
                                    lastRecordedFilePath = recordingFilePath
                                    recordingFilePath = null
                                    isRecording = false
                                    ffmpegRecorder.stopRecording()
                                    Log.d("InspectionScreen", "FFmpeg recording stopped after $recordingElapsed")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_one_play_stop), contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeSmall), tint = Color.Unspecified)
                                Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                                Text("${S("stop")} $recordingElapsed", fontSize = Dimensions.ButtonLabelFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    // â”€â”€ Sonde Frequency Picker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Text(
                        text = S("sonde"),
                        fontSize = Dimensions.OsdSmallFontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    val sondeOptions = listOf(
                        Triple(0, S("sonde_off"), "Off"),
                        Triple(1, "512 Hz", "512 Hz"),
                        Triple(2, "640 Hz", "640 Hz"),
                        Triple(3, "33 kHz", "33 kHz")
                    )
                    val currentSondeIdx = sondeOptions.indexOfFirst {
                        if (it.first == 0) crawler.laserOn == false || crawler.sondeFrequency == it.third
                        else crawler.sondeFrequency == it.third
                    }.coerceAtLeast(0)
                    val nextSondeIdx = (currentSondeIdx + 1) % sondeOptions.size
                    val currentSondeLabel = sondeOptions[currentSondeIdx].second
                    val nextSondeLabel = sondeOptions[nextSondeIdx].second
                    Button(
                        onClick = {
                            lastInteractionMs = System.currentTimeMillis()
                            hardwareService.sendFrequency(sondeOptions[nextSondeIdx].first)
                        },
                        modifier = Modifier.fillMaxWidth().height(Dimensions.TouchLarge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentSondeIdx > 0) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentSondeIdx > 0) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${S("sonde")}: $currentSondeLabel",
                                fontSize = Dimensions.ButtonLabelFontSize,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "â†’ $nextSondeLabel",
                                fontSize = Dimensions.OsdSmallFontSize,
                                color = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    // â”€â”€ Light Level Picker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(
                                id = if (sliderUi > 0f) R.drawable.ic_one_light_on else R.drawable.ic_one_light_off
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSizeMedium),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                        Text(
                            text = "${S("light")}: ${sliderUi.toInt()}",
                            fontSize = Dimensions.OsdSmallFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Slider(
                        value = sliderUi,
                        onValueChange = { sliderUi = it; lastInteractionMs = System.currentTimeMillis() },
                        onValueChangeFinished = { hardwareService.sendLightPower(sliderUi.toInt()) },
                        valueRange = 0f..200f,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.TouchLarge)
                    )

                    // Battery
                    cable.batteryLevel?.let { battery ->
                        Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))
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

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    // â”€â”€ Meter Reset â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Text(
                        text = S("meter_absolute"),
                        fontSize = Dimensions.OsdSmallFontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cable.meterReading != null) {
                        Text(
                            text = "${String.format("%.2f", meterValue)} m",
                            fontSize = Dimensions.ButtonLabelFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = MeterBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                    Button(
                        modifier = Modifier.fillMaxWidth().height(Dimensions.MeterResetHeight),
                        onClick = {
                            lastInteractionMs = System.currentTimeMillis()
                            Log.d("InspectionScreen", "Absolut reset clicked")
                            hardwareService.resetMeterAbsolute()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_one_reset), contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium), tint = Color.Unspecified)
                        Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                        Text(
                            text = "${S("meter_absolute")} â†’ 0",
                            fontSize = Dimensions.ButtonLabelFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.TouchSpacing))
                    Button(
                        modifier = Modifier.fillMaxWidth().height(Dimensions.MeterResetHeight),
                        onClick = {
                            lastInteractionMs = System.currentTimeMillis()
                            Log.d("InspectionScreen", "Strecke reset clicked")
                            hardwareService.resetMeterRelative()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_one_reset), contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium), tint = Color.Unspecified)
                        Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                        Text(
                            text = "${S("meter_distance")} â†’ 0",
                            fontSize = Dimensions.ButtonLabelFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SectionSpacing))

                    // â”€â”€ Damage List â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                lastInteractionMs = System.currentTimeMillis()
                                damagesNewestFirst = !damagesNewestFirst
                                scope.launch {
                                    context.settingsStore.edit { prefs ->
                                        prefs[damagesNewestFirstKey] = damagesNewestFirst
                                    }
                                }
                            },
                            modifier = Modifier.size(Dimensions.SortButtonSize)
                        ) {
                            Icon(
                                if (damagesNewestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSizeSmall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

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
                                    .padding(vertical = Dimensions.ListItemVerticalPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val hasPhoto = damage.photoPath.isNotEmpty() &&
                                        File(damage.photoPath).exists() &&
                                        File(damage.photoPath).length() > 0
                                val hasAnnotated = damage.annotatedPhotoPath.isNotEmpty() &&
                                        File(damage.annotatedPhotoPath).exists() &&
                                        File(damage.annotatedPhotoPath).length() > 0
                                if (hasPhoto || hasAnnotated) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SmallItemSpacing)) {
                                        if (hasPhoto) {
                                            AsyncImage(
                                                model = File(damage.photoPath),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(Dimensions.ThumbnailSize)
                                                    .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        if (hasAnnotated) {
                                            AsyncImage(
                                                model = File(damage.annotatedPhotoPath),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(Dimensions.ThumbnailSize)
                                                    .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(Dimensions.ThumbnailSize)
                                            .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.IconSizeMedium),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
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

                    // â”€â”€ Notes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (notes.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SectionSpacing))

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
                                onClick = {
                                    lastInteractionMs = System.currentTimeMillis()
                                    notesNewestFirst = !notesNewestFirst
                                },
                                modifier = Modifier.size(Dimensions.SortButtonSize)
                            ) {
                                Icon(
                                    if (notesNewestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.IconSizeSmall),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))

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
                                    .padding(vertical = Dimensions.SmallItemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (note.audioPath.isNotEmpty() && File(note.audioPath).exists())
                                        Icons.Default.Mic else Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.IconSizeMedium),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(Dimensions.MediumSpacing))
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

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    // â”€â”€ Project Info â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                    .padding(Dimensions.ProjectInfoPadding)
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
                                Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)) {
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
                                    .padding(Dimensions.ProjectInfoPadding)
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
                } // end Column (panel content)
            } // end Card
        } // end AnimatedVisibility
    } // end Box (cinema-mode root)

    // Damage Dialog
    if (showDamageDialog && projectId != null) {
        DamageDialog(
            photoPath = capturedPhotoPath,
            annotatedPhotoPath = capturedAnnotatedPath,
            currentMeter = meterValue,
            projectId = projectId,
            existingDamage = editingDamage,
            onSave = { damage ->
                val flashText = buildFindingFlashText(damage)
                scope.launch {
                    if (damage.id > 0) {
                        damageRepository.updateDamage(damage)
                    } else {
                        damageRepository.saveDamage(damage)
                    }
                    // Re-render the saved photo with the full OSD + damage block burned
                    // in. Done here (not at capture time) because we only have the damage
                    // details after the dialog is confirmed. Hardware-OSD users wanted
                    // the photo to be self-explanatory in the report â€” capture-time
                    // bitmap had no app-side overlay at all.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        burnOsdIntoPhoto(damage.photoPath, osdSettings, osdLine1,
                            buildOsdLine2(damage.position, osdSettings, crawler.sondeFrequency),
                            flashText)
                        burnOsdIntoPhoto(damage.annotatedPhotoPath, osdSettings, osdLine1,
                            buildOsdLine2(damage.position, osdSettings, crawler.sondeFrequency),
                            flashText)
                    }
                }
                findingFlash = flashText
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
                        // FfmpegRtspRecorder burns OSD directly during recording.
                        // "Mit Overlay" means: force app-OSD on top of whatever the
                        // camera is rendering. Otherwise project name, corrected meter
                        // value etc. would not appear in the video â€” the camera-side
                        // hardware OSD only knows date / raw meter / time.
                        val file = File(dir, "${projNr}_${ts}.mp4")
                        recordingFilePath = file.absolutePath
                        val withOverlaySettings = osdSettings.copy(
                            enableOsdBurnIn = true,
                            enableFindingBurnIn = true
                        )
                        ffmpegRecorder.startRecording(
                            rtspUrl = rtspUrl,
                            outputFile = file,
                            osdSettings = withOverlaySettings,
                            initialLine1 = osdLine1,
                            initialLine2 = buildOsdLine2(meterValue, withOverlaySettings, crawler.sondeFrequency),
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
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                    Spacer(modifier = Modifier.width(Dimensions.ButtonIconSpacing))
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
                        // "Without overlay" means no app-side drawing at all â€” neither
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
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                    Spacer(modifier = Modifier.width(Dimensions.ButtonIconSpacing))
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
                    Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))
                    LinearProgressIndicator(
                        progress = processingProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
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

// â”€â”€ OSD line builders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

/**
 * Builds the rich finding-flash label for the on-screen / burned-in overlay.
 * Prefers the DIN code + readable name, falls back to legacy damageType.
 * Always includes the position; appends a short description if present.
 */
internal fun buildFindingFlashText(damage: com.uip.oneapp.data.local.entity.DamageEntity): String {
    val label = when {
        damage.mainCodeName.isNotEmpty() && damage.mainCode.isNotEmpty() ->
            "${damage.mainCode} ${damage.mainCodeName}"
        damage.mainCodeName.isNotEmpty() -> damage.mainCodeName
        damage.mainCode.isNotEmpty()     -> damage.mainCode
        damage.damageType.isNotEmpty()   -> damage.damageType
        else -> "OBS"
    }
    val pos  = String.format(java.util.Locale.US, "%.2fm", damage.position)
    val desc = damage.description.trim().take(80)
    val tail = if (desc.isNotEmpty()) " - $desc" else ""
    val cls  = damage.damageClass?.let { " [Klasse $it]" } ?: ""
    return "$label @ $pos$cls$tail"
}

/**
 * Burns the OSD bars + an optional finding line into [photoPath] in-place.
 * Forces enableOsdBurnIn so the photo is overlaid even when the live recorder
 * runs in hardware-OSD mode (camera-side overlay), because the photo is a
 * separate artifact that needs to be self-explanatory in the report.
 */
internal fun burnOsdIntoPhoto(
    photoPath: String,
    osdSettings: com.uip.oneapp.export.OsdSettings,
    line1: String,
    line2: String,
    finding: String?
) {
    if (photoPath.isEmpty()) return
    val file = java.io.File(photoPath)
    if (!file.exists() || file.length() == 0L) return
    val bmp = android.graphics.BitmapFactory.decodeFile(photoPath) ?: return
    try {
        val mutable = if (bmp.isMutable) bmp
                      else bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val forced = osdSettings.copy(enableOsdBurnIn = true)
        com.uip.oneapp.export.OsdRenderer.renderBitmap(mutable, forced, line1, line2, finding)
        java.io.FileOutputStream(photoPath).use { out ->
            mutable.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        if (mutable !== bmp) mutable.recycle()
    } finally {
        bmp.recycle()
    }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.StatusRowMinHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = statusColor,
            modifier = Modifier.size(Dimensions.IconSizeMedium)
        )
        Spacer(modifier = Modifier.width(Dimensions.MediumSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = Dimensions.OsdSmallFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = Dimensions.ButtonLabelFontSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
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
        modifier = Modifier.height(Dimensions.TouchMedium),
        contentPadding = PaddingValues(horizontal = Dimensions.PanelContentPadding, vertical = Dimensions.SmallSpacing)
    ) {
        Text(text, fontSize = Dimensions.OsdSmallFontSize, maxLines = 1)
    }
}


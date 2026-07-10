package com.uip.oneapp.ui.screens.inspection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.uip.oneapp.network.internal.CameraHead
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.network.DeviceType
import com.uip.oneapp.network.FfmpegRecordingState
import com.uip.oneapp.network.FfmpegRtspRecorder
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.components.FfmpegVideoPlayer
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.components.InspectionOsd
import com.uip.oneapp.ui.components.VideoPlayerPlaceholder
import com.uip.oneapp.ui.hardware.HardwareKeyBus
import com.uip.oneapp.ui.hardware.HwButton
import com.uip.oneapp.ui.hardware.HwButtonOrder
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.screens.projects.cameraTypePrefill
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
    noteRepository: NoteRepository = koinInject(),
    // Welle 5: geteilter Ein-Encoder-Arbiter (Ausschluss RTSP-Server ↔ lokale Aufnahme).
    encoderArbiter: com.uip.oneapp.network.CameraEncoderArbiter = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = koinViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val osdSettings = settingsState.toOsdSettings()

    // Schnellaufnahme (Feedback #8/#6): Wird die Inspektion ohne Projekt geöffnet,
    // legen wir einen Tages-Bucket an bzw. verwenden ihn wieder. Ab dann hängt alles
    // (Foto/Video/Schaden/Notiz) an dieser effektiven Projekt-ID — nichts liegt lose.
    // Louis 10-07: Bucket-Label lokalisiert (M3: DE „Schnellaufnahme", EN „Quick capture")
    // und erkannten Kamerakopf beim Anlegen mitgeben (M2: sonst bleibt kameratyp im PDF leer,
    // weil Quick Capture das ProjectFormScreen-Prefill umgeht). Labels hier im Composable-
    // Scope auflösen; Kopf frisch beim Anlegen lesen (gleiche Regel wie cameraTypePrefill,
    // UNKNOWN → leer, nie raten).
    val quickBucketLabel = S("quick_capture_bucket")
    val cameraC10Label = S("camera_c10")
    val cameraC18Label = S("camera_c18")
    var effectiveProjectId by remember(projectId) { mutableStateOf(projectId) }
    LaunchedEffect(projectId) {
        effectiveProjectId = projectId ?: run {
            val head = CameraHead.from(hardwareService.hardwareState.value.cableController.cameraId)
            val camLabel = cameraTypePrefill(head, "", cameraC10Label, cameraC18Label) ?: ""
            projectRepository.getOrCreateQuickProjectId(quickBucketLabel, camLabel)
        }
    }

    val project by remember(effectiveProjectId) {
        val pid = effectiveProjectId
        if (pid != null) projectRepository.getProjectFlow(pid)
        else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val damages by remember(effectiveProjectId) {
        val pid = effectiveProjectId
        if (pid != null) damageRepository.getDamagesForProject(pid)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val notes by remember(effectiveProjectId) {
        val pid = effectiveProjectId
        if (pid != null) noteRepository.getNotesForProject(pid)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val hwState by hardwareService.hardwareState.collectAsState()
    val cable = hwState.cableController
    val crawler = hwState.crawlerController
    val conn = hwState.connectionStatus
    var meterValue by remember { mutableStateOf(0f) }
    var showControls by remember { mutableStateOf(false) }
    // Unteres Bedien-Band: nicht mehr permanent — fährt nur auf Video-Tipp ein und
    // blendet nach ~4 s Inaktivität bzw. erneutem Tipp wieder aus.
    var showBottomBar by remember { mutableStateOf(false) }
    var lastBottomBarMs by remember { mutableLongStateOf(0L) }
    var lastInteractionMs by remember { mutableLongStateOf(0L) }
    var videoScale by remember { mutableFloatStateOf(1f) }
    var videoOffset by remember { mutableStateOf(Offset.Zero) }
    var lightLevel by remember { mutableStateOf((crawler.frontLightPower ?: 0).coerceIn(0, 100)) }

    // Akku-Anzeige wie die OEM-App (com.bominwell.robot): aus dem ANDROID-SYSTEM-AKKU,
    // nicht aus dem seriellen Protokoll. ACTION_BATTERY_CHANGED ist sticky → liefert
    // den aktuellen Wert sofort bei der Registrierung und danach live bei Änderungen.
    var batteryPct by remember { mutableStateOf<Int?>(null) }
    var batteryCharging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) batteryPct = (level * 100 / scale).coerceIn(0, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver.onReceive(context, sticky)
        onDispose { context.unregisterReceiver(receiver) }
    }

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

    // Auto-Ausblenden der Bedienelemente (Feedback Louis #2): reaktiv aus demselben
    // settingsStore-Flow. Default AUS → unteres Bedienband bleibt dauerhaft sichtbar.
    val controlsAutoHideKey = remember { booleanPreferencesKey("controls_auto_hide") }
    var controlsAutoHide by remember { mutableStateOf(false) }
    LaunchedEffect(damagesNewestFirstPref) {
        damagesNewestFirstPref?.let { prefs ->
            controlsAutoHide = prefs[controlsAutoHideKey] ?: false
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

    // Hardtasten-Popups exakt wie Original-App: Licht-Slider, Sonde-Frequenzauswahl, Power-Dialog.
    var showLightPopup by remember { mutableStateOf(false) }
    var showSondePopup by remember { mutableStateOf(false) }
    var showPowerDialog by remember { mutableStateOf(false) }
    LaunchedEffect(showLightPopup, lightLevel) {
        if (showLightPopup) { kotlinx.coroutines.delay(4000); showLightPopup = false }
    }

    // OSD Phase 4: live overlay state
    var findingFlash by remember { mutableStateOf<String?>(null) }
    var isStreamPaused by remember { mutableStateOf(false) }

    // Foto-Quittung: kurzer weißer Kamera-Blitz nach dem Auslösen (Feedback: optische
    // Rückmeldung beim Klick auf die Foto-Taste).
    var showPhotoFlash by remember { mutableStateOf(false) }
    LaunchedEffect(showPhotoFlash) {
        if (showPhotoFlash) {
            kotlinx.coroutines.delay(140)
            showPhotoFlash = false
        }
    }

    // Phase 5: FFmpegRtspRecorder for OSD burn-in recording
    val ffmpegRecorder = remember { FfmpegRtspRecorder(context) }
    val ffmpegRecState by ffmpegRecorder.state.collectAsState()
    val isFfmpegRecording = ffmpegRecState == FfmpegRecordingState.RECORDING

    // #15 Lokal-Aufnahme: im V4L2/LocalBitmap-Modus (kein RTSP) Frames aufnehmen + zu MP4 muxen.
    // Welle 5: der Recorder wird EINMAL anhand FeatureFlags.useHardwareRecorder gewählt (HW-Encoder
    // oder alter LocalBitmapRecorder); ein Flag-Flip wirkt erst auf die nächste Aufnahme.
    val localRecorder = remember { com.uip.oneapp.network.RecorderFactory.create(context, encoderArbiter) }
    val localRecState by localRecorder.state.collectAsState()
    // Pause (nur Lokal-Pfad/ONE): Aufnahme angehalten, Datei bleibt offen.
    val isRecordingPaused = localRecState == com.uip.oneapp.network.RecordingState.PAUSED

    DisposableEffect(Unit) {
        onDispose {
            ffmpegRecorder.stopRecording()
            localRecorder.cancel()
        }
    }

    // Aufnahme stoppen — Lokal-Recorder ODER RTSP-Recorder, je nach Modus.
    val doStopRecording: () -> Unit = {
        lastInteractionMs = System.currentTimeMillis()
        recordingFilePath = null
        isRecording = false
        if (localRecorder.isRecording) {
            localRecorder.stop { /* MP4 fertig; VideosTab scannt beim Öffnen neu */ }
        } else {
            ffmpegRecorder.stopRecording()
        }
    }

    // OSD line builders (recomputed when project or meter changes).
    // Dual-Modus (Welle 4): „TWO" entfernt → Produktname ist immer DeviceType.ONE.
    val osdLine1 = buildOsdLine1(project, DeviceType.ONE)
    val osdLine2 = buildOsdLine2(meterValue, osdSettings)

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
            while (true) {
                if (localRecorder.isPaused) {
                    // Pause: Startzeit mitschieben, damit der Timer stehen bleibt —
                    // die Pausenzeit zählt nicht zur Aufnahmedauer (Video enthält sie nicht).
                    recordingStartTime += 1000
                    kotlinx.coroutines.delay(1000)
                    continue
                }
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                recordingElapsed = String.format("%02d:%02d", min, sec)
                // Phase 5: update FFmpegRtspRecorder drawtext file with current OSD line2
                ffmpegRecorder.updateOsdLine2(buildOsdLine2(meterValue, osdSettings))
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

    // Cinema-Mode auto-hide: controls disappear after 5 s of inactivity — nur bei
    // Auto-Ausblenden AN (Feedback Louis #2), sonst bleiben die Bedienelemente sichtbar.
    LaunchedEffect(showControls, lastInteractionMs, controlsAutoHide) {
        if (controlsAutoHide && showControls) {
            kotlinx.coroutines.delay(Dimensions.ControlsAutoHideMs)
            if (System.currentTimeMillis() - lastInteractionMs >= Dimensions.ControlsAutoHideMs) {
                showControls = false
            }
        }
    }

    // Unteres Band auto-hide: nach ~4 s Inaktivität wieder einfahren — nur bei Auto-Ausblenden AN.
    LaunchedEffect(showBottomBar, lastBottomBarMs, controlsAutoHide) {
        if (controlsAutoHide && showBottomBar) {
            kotlinx.coroutines.delay(4000L)
            // Nicht einfahren, solange das Sonde-Popup offen ist (es hängt an der Leiste).
            if (System.currentTimeMillis() - lastBottomBarMs >= 4000L && !showSondePopup) {
                showBottomBar = false
            }
        }
    }

    // Auto-Ausblenden AUS (Default, Feedback Louis #2): unteres Bedienband dauerhaft einblenden/halten.
    LaunchedEffect(controlsAutoHide) {
        if (!controlsAutoHide) showBottomBar = true
    }

    // Hardware-Lifecycle (M13): an den Activity-Lebenszyklus koppeln statt nur einmalig zu starten.
    // Re-Init bei ON_RESUME (Rückkehr aus dem Background), Stop bei ON_PAUSE und beim Verlassen der
    // Inspektion (onDispose) — so bleibt der V4L2/Serial-State nach App-Wechsel nicht unkontrolliert.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (!hardwareService.isConnected) {
                        val status = hardwareService.probeEndpoints()
                        if (status.cableControllerReachable || status.crawlerControllerReachable) {
                            hardwareService.startPolling()
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> hardwareService.stopPolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hardwareService.stopPolling()
        }
    }

    // Update meter from hardware when available
    LaunchedEffect(cable.meterReading) {
        cable.meterReading?.let { meterValue = it }
    }

    // VideoSource aus dem HardwareService: kann VideoSource.Rtsp (Netzwerk-Stream)
    // oder VideoSource.LocalBitmap (V4L2-Direct) sein. Für Backward-Compat fließt
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
    // rtspUrl wird weiter unten an einigen Stellen für `enabled`-Checks und das
    // Recorder-Modul gebraucht — wir leiten es aus videoSource ab.
    // Hinweis: Im Lokal-Modus (ONE, VideoSource.LocalBitmap) ist rtspUrl leer; die Aufnahme
    // läuft dort über LocalBitmapRecorder (JPEG-FIFO → FFmpeg image2pipe → libx264), ist also
    // NICHT disabled. Offene Lücke (siehe Audit): im Lokal-Modus wird derzeit kein OSD ins MP4
    // eingebrannt — Mit/Ohne-Overlay-Auswahl wirkt dort nicht.
    val rtspUrl = remember(videoSource) {
        (videoSource as? com.uip.oneapp.network.VideoSource.Rtsp)?.url ?: ""
    }

    // Aktuelles Live-Frame des lokalen V4L2-Streams (ONE-internal). Im LocalBitmap-
    // Modus existiert kein TextureView, daher wird hier der Foto-/Schaden-Screenshot
    // hergenommen (Feedback #8: "kein Bild vom Video gespeichert").
    val emptyFrameFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Bitmap?>(null) }
    val frameFlow = (videoSource as? com.uip.oneapp.network.VideoSource.LocalBitmap)?.flow ?: emptyFrameFlow
    val localFrame by frameFlow.collectAsState()

    // SA-Design: Inter-Typeface für Foto-/Frame-OSD-Burn-in (Fallback MONOSPACE).
    val osdTypeface = remember { com.uip.oneapp.util.DqFonts.osdTypeface(context) }

    // ── Hardtasten (F1–F8) + Softbutton-Leiste: EINE gemeinsame Aktionsliste ──────
    // Foto-Aufnahme als wiederverwendbare Aktion (identisch zum Foto-Button im Panel).
    val doPhoto: () -> Unit = {
        val pid = effectiveProjectId
        if (pid != null) {
            showPhotoFlash = true
            val tv = textureViewRef
            val dir = File(context.getExternalFilesDir("damages"), "project_$pid")
            dir.mkdirs()
            val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            // tv.bitmap kann trotz vorhandener TextureView null sein (Canvas-Overlay-Player
            // rendert nicht in diese View) — dann auf das Live-Frame des V4L2-Streams zurückfallen.
            val bitmap = (if (tv != null && tv.width > 0) tv.bitmap else null)
                         ?: localFrame?.copy(Bitmap.Config.ARGB_8888, true)
            if (bitmap != null) {
                val photoSettings = osdSettings.copy(enableOsdBurnIn = true)
                OsdRenderer.renderBitmap(bitmap, photoSettings, osdLine1, osdLine2, typeface = osdTypeface)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            } else { file.createNewFile() }
            scope.launch {
                damageRepository.saveDamage(DamageEntity(projectId = pid, position = meterValue, damageType = "Foto", photoPath = file.absolutePath))
            }
        }
    }

    // Schaden erfassen als wiederverwendbare Aktion (identisch zum früheren Schaden-
    // Button im rechten Panel) — jetzt als Kachel im unteren Bedien-Band.
    val doDamage: () -> Unit = doDamage@{
        lastInteractionMs = System.currentTimeMillis()
        val pid = effectiveProjectId ?: return@doDamage
        editingDamage = null
        val tv = textureViewRef
        // Screenshot aus TextureView (RTSP) ODER dem aktuellen V4L2-Live-Frame.
        // tv.bitmap kann trotz vorhandener TextureView null sein (Canvas-Overlay-Player
        // rendert nicht in diese View) — dann auf das Live-Frame des V4L2-Streams zurückfallen.
        val bitmap = (if (tv != null && tv.width > 0 && tv.height > 0) tv.bitmap else null)
                     ?: localFrame?.copy(Bitmap.Config.ARGB_8888, true)
        if (bitmap != null) {
            val dir = File(context.getExternalFilesDir("damages"), "project_$pid")
            dir.mkdirs()
            val file = File(dir, "dmg_${System.currentTimeMillis()}.jpg")
            // app-OSD immer einbrennen; Schadensdaten ergänzt burnOsdIntoPhoto() im Dialog-onSave.
            val photoSettings = osdSettings.copy(enableOsdBurnIn = true)
            OsdRenderer.renderBitmap(bitmap, photoSettings, osdLine1, osdLine2, typeface = osdTypeface)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            Log.d("InspectionScreen", "Screenshot saved: ${file.absolutePath}")
            capturedPhotoPath = file.absolutePath
        } else {
            Log.w("InspectionScreen", "No frame available for screenshot (tv=$tv, localFrame=${localFrame != null})")
            capturedPhotoPath = ""
        }
        capturedAnnotatedPath = ""
        if (isRecording) { exoPlayerRef?.pause(); isStreamPaused = true }
        showDamageDialog = true
    }

    // Hardtaste UND positionsgleicher Softbutton lösen dieselbe Aktion aus.
    val runHwButton: (HwButton) -> Unit = { b ->
        lastInteractionMs = System.currentTimeMillis()
        when (b) {
            HwButton.POWER -> { /* Kurzdruck ohne Funktion; Langdruck (Softbutton) öffnet Beenden-Dialog */ }
            HwButton.LIGHT -> {
                // Wie Original (changeLightPower), Deckel jetzt bei 100 % (Louis #4): 0 → 30 → 60 → 100 → 0.
                lightLevel = nextLightLevel(lightLevel)
                hardwareService.sendLightPower(lightLevel)
                showLightPopup = true
            }
            HwButton.SONDE -> {
                // Autotest-Befund T9: Das Sonde-Popup wird nur innerhalb der unteren
                // Bedienleiste gerendert. F2 bei ausgeblendeter Leiste blieb wirkungslos.
                // Fix: Leiste einblenden (wie F1/Licht immer wirksam), dann Popup zeigen.
                showBottomBar = true
                lastBottomBarMs = System.currentTimeMillis()
                showSondePopup = true
            }
            HwButton.RECORD ->
                if (isRecording && localRecorder.isRecording) {
                    // Laufende Lokal-Aufnahme: F3/Aufnahme-Taste = Pause/Weiter-Toggle
                    // (eine durchgehende Datei, wie Original-App).
                    if (localRecorder.isPaused) localRecorder.resume() else localRecorder.pause()
                } else if (effectiveProjectId != null && !isRecording &&
                    (rtspUrl.isNotEmpty() || videoSource is com.uip.oneapp.network.VideoSource.LocalBitmap)
                ) showRecordingDialog = true
            HwButton.RECORD_STOP -> if (isRecording) doStopRecording()
            HwButton.PHOTO -> doPhoto()
            HwButton.GALLERY -> effectiveProjectId?.let { navController.navigate("project_detail/$it") }
            HwButton.SETTINGS -> navController.navigate("settings")
        }
    }

    // Hardtasten-Events der ONE (F1–F8) konsumieren.
    LaunchedEffect(Unit) {
        HardwareKeyBus.events.collect { runHwButton(it) }
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

        // Layer 2: Gesture overlay — pinch-to-zoom + single-tap toggles panel + double-tap zoom
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
                            // Ein Tipp blendet Panel UND unteres Band gemeinsam ein/aus.
                            // Toggle wird aus dem Band abgeleitet (kürzerer Auto-Hide),
                            // damit beide nach dem Wegblenden zuverlässig wieder erscheinen.
                            // Auto-Ausblenden AUS (Default): unteres Band bleibt dauerhaft sichtbar,
                            // der Tipp schaltet dann nur das rechte Panel (Feedback Louis #2).
                            val show = if (controlsAutoHide) !showBottomBar else !showControls
                            showControls = show
                            showBottomBar = if (controlsAutoHide) show else true
                            if (show) {
                                val now = System.currentTimeMillis()
                                lastInteractionMs = now
                                lastBottomBarMs = now
                            }
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

        // Layer 2b: Zurück-Affordanz (Feedback #7). In der Inspektion ist die linke
        // Navigationsleiste ausgeblendet (Vollbild), daher IMMER sichtbar: aus einem
        // Projekt zurück ins Verzeichnis, sonst zurück zur Herkunft (Home/Tab).
        IconButton(
            onClick = {
                if (projectId != null) {
                    navController.navigate("project_detail/$projectId") {
                        popUpTo("inspection/{projectId}") { inclusive = true }
                    }
                } else if (!navController.popBackStack()) {
                    navController.navigate("home")
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimensions.OsdPadding)
                // ~25 % größer (Kreis + Icon + Touch-Target) und gleiche Transparenz wie
                // unteres Band / rechtes Panel (bgPanel @ 0.30) — leichter zu treffen.
                .size(Dimensions.BackButtonSize)
                .background(DrainQTheme.colors.bgPanel.copy(alpha = 0.30f), RoundedCornerShape(50))
        ) {
            DqIcon(
                key = "back",
                tint = Color.White,
                size = Dimensions.NavRailIconSize
            )
        }

        // Layer 3: OSD-Overlay — persistent, immer sichtbar unabhängig vom Panel-Status
        InspectionOsd(
            distanceMeters = meterValue,
            sondeMode = crawler.sondeFrequency ?: "—",
            lightLevel = crawler.frontLightPower ?: 0,
            voltage = cable.batteryLevel?.let { it / 100f * 12.6f } ?: 0f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimensions.OsdPadding, bottom = 84.dp)
        )

        // Popups erscheinen direkt ÜBER der jeweiligen Taste (wie Original showUpView):
        // horizontal mittig über dem Anker, knapp darüber.
        val popupDensity = LocalDensity.current
        val popupGapPx = with(popupDensity) { 8.dp.roundToPx() }
        val abovePositionProvider = remember(popupGapPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                    val y = anchorBounds.top - popupContentSize.height - popupGapPx
                    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                    return IntOffset(x.coerceIn(0, maxX), y.coerceAtLeast(0))
                }
            }
        }

        // Layer 3b: Softbutton-Leiste (Feedback #1/#3) — Reihenfolge Power, Licht(F1),
        // Sonde(F2), Aufnahme(F3), Stop(F4), Foto(F5), Schaden, Galerie(F6), Tag/Nacht(F7),
        // Einstellungen(F8). Hardtaste und positionsgleicher Softbutton lösen dieselbe
        // Aktion aus. Nicht mehr permanent: fährt nur auf Video-Tipp von unten ein.
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(Dimensions.PanelSlideDuration, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(Dimensions.PanelSlideDuration, easing = FastOutSlowInEasing)
            )
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Space16, vertical = Dimensions.Space16),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HwButtonOrder.forEach { b ->
                // SA-Design: 112-dp-Kacheln, BgPanel; zentrale Aufnahme-Taste in Amber.
                // Logik (gemeinsame Aktionsliste + Popups) bleibt unverändert.
                val isRecord = b == HwButton.RECORD
                // Kachelfläche 70 % transparent (Alpha 0.30); Icon/Label bleiben voll deckend.
                val tileColor = (if (isRecord) DrainQTheme.colors.amber else DrainQTheme.colors.bgPanel)
                    .copy(alpha = 0.30f)
                val contentColor = if (isRecord) DrainQTheme.colors.onAmber else DrainQTheme.colors.textPrimary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimensions.SoftButtonHeight)
                        .background(tileColor, RoundedCornerShape(16.dp))
                        .pointerInput(b) {
                            detectTapGestures(
                                onTap = { runHwButton(b) },
                                // Power: Langdruck = Beenden-Dialog (wie Original-Shutdown).
                                onLongPress = { if (b == HwButton.POWER) showPowerDialog = true }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (b) {
                                HwButton.POWER -> Icons.Default.PowerSettingsNew
                                HwButton.LIGHT -> Icons.Default.Lightbulb
                                HwButton.SONDE -> Icons.Default.GraphicEq
                                HwButton.RECORD -> when {
                                    isRecordingPaused -> Icons.Default.PlayArrow
                                    isRecording && localRecState == com.uip.oneapp.network.RecordingState.RECORDING -> Icons.Default.Pause
                                    else -> Icons.Default.FiberManualRecord
                                }
                                HwButton.RECORD_STOP -> Icons.Default.StopCircle
                                HwButton.PHOTO -> Icons.Default.CameraAlt
                                HwButton.GALLERY -> Icons.Default.PhotoLibrary
                                HwButton.SETTINGS -> Icons.Default.Settings
                            },
                            contentDescription = b.name,
                            tint = contentColor,
                            modifier = Modifier.size(Dimensions.DqIconToolbar)
                        )
                        Spacer(Modifier.height(Dimensions.Space4))
                        Text(
                            text = when (b) {
                                HwButton.POWER -> S("power")
                                HwButton.LIGHT -> S("light")
                                HwButton.SONDE -> S("sonde")
                                HwButton.RECORD -> when {
                                    isRecordingPaused -> S("record_resume")
                                    isRecording && localRecState == com.uip.oneapp.network.RecordingState.RECORDING -> S("record_pause")
                                    else -> S("record")
                                }
                                HwButton.RECORD_STOP -> S("stop")
                                HwButton.PHOTO -> S("photo")
                                HwButton.GALLERY -> S("gallery")
                                HwButton.SETTINGS -> S("settings_title")
                            },
                            color = contentColor,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }

                    // Licht-Popup: vertikaler Slider direkt über der Licht-Taste (wie Original).
                    if (b == HwButton.LIGHT && showLightPopup) {
                        Popup(
                            popupPositionProvider = abovePositionProvider,
                            onDismissRequest = { showLightPopup = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            HideSystemBarsInDialog()
                            Surface(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp).width(56.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "$lightLevel%",
                                        color = Color.White,
                                        fontSize = Dimensions.OsdSmallFontSize
                                    )
                                    Box(
                                        modifier = Modifier.height(150.dp).width(44.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Slider(
                                            value = lightLevel.toFloat(),
                                            onValueChange = {
                                                lightLevel = it.toInt().coerceIn(0, 100)
                                                hardwareService.sendLightPower(lightLevel)
                                            },
                                            valueRange = 0f..100f,
                                            modifier = Modifier
                                                .requiredWidth(150.dp)
                                                .rotate(-90f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sonde-Popup: Frequenzliste vertikal direkt über der Sonde-Taste (wie Original).
                    if (b == HwButton.SONDE && showSondePopup) {
                        Popup(
                            popupPositionProvider = abovePositionProvider,
                            onDismissRequest = {
                                showSondePopup = false
                                // Auto-hide-Timer der Leiste neu anstoßen.
                                lastBottomBarMs = System.currentTimeMillis()
                            },
                            properties = PopupProperties(focusable = true)
                        ) {
                            HideSystemBarsInDialog()
                            Surface(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    // M8: TX-Auswahl aus der EINEN Quelle (SondeFrequency) — konsistent mit der RX-Anzeige.
                                    val sondeOptions = com.uip.oneapp.network.internal.SondeFrequency.selectableCodes
                                        .map { com.uip.oneapp.network.internal.SondeFrequency.name(it) to it } +
                                        (S("sonde_off") to com.uip.oneapp.network.internal.SondeFrequency.OFF)
                                    sondeOptions.forEach { (label, f) ->
                                        // Louis #2: aktuell aktive Frequenz (RX-Anzeige) grün + fett hervorheben.
                                        val active = isSondeFrequencyActive(f, crawler.sondeFrequency)
                                        TextButton(onClick = {
                                            hardwareService.sendFrequency(f)
                                            showSondePopup = false
                                            // Auto-hide-Timer der Leiste neu anstoßen.
                                            lastBottomBarMs = System.currentTimeMillis()
                                        }) {
                                            Text(
                                                label,
                                                color = if (active) OsdColorGreen else Color.White,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Schaden-Kachel direkt nach Foto (keine Hardtaste) — gleiche Dq-Optik,
                // 70 % transparente Fläche. Löst dieselbe Schaden-Aktion wie zuvor im Panel.
                if (b == HwButton.PHOTO) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimensions.SoftButtonHeight)
                            .background(
                                DrainQTheme.colors.bgPanel.copy(alpha = 0.30f),
                                RoundedCornerShape(16.dp)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { doDamage() })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "DAMAGE",
                                tint = DrainQTheme.colors.textPrimary,
                                modifier = Modifier.size(Dimensions.DqIconToolbar)
                            )
                            Spacer(Modifier.height(Dimensions.Space4))
                            Text(
                                text = S("damage"),
                                color = DrainQTheme.colors.textPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        }

        // Live-Status-Chips oben rechts: Kamerakopf-Chip DIREKT ÜBER der Zeile
        // mit REC (nur während Aufnahme) + Batterie.
        // DqStatusChip + SA-Tokens; read-only Spiegel der bestehenden Zustände.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Dimensions.OsdPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space8),
            horizontalAlignment = Alignment.End
        ) {
            // Kamerakopf-Chip — Live-Anzeige (NICHT im Projekt gespeichert), gespeist aus
            // der seriellen GROUP_CAMERA-Telemetrie (debounced payload[4]).
            // Detektion (C10=0x01/C18=0x02) hat Vorrang; bei UNKNOWN greift der manuelle
            // Projekt-Wert (Dropdown im Projektformular) als Fallback — nie geraten.
            // Das manuelle Dropdown bleibt der Override für den Bericht.
            val headLabel = when (CameraHead.from(cable.cameraId)) {
                CameraHead.C10 -> "C10"
                CameraHead.C18 -> "C18"
                CameraHead.UNKNOWN -> project?.kameratyp?.takeIf { it.isNotBlank() }
            }
            headLabel?.let { label ->
                DqStatusChip(
                    text = label,
                    color = DrainQTheme.colors.success,
                    showDot = false,
                    iconKey = "camera"
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording || localRecState == com.uip.oneapp.network.RecordingState.FINISHING) {
                    val recLabel = when {
                        localRecState == com.uip.oneapp.network.RecordingState.FINISHING -> S("encoding")
                        isRecordingPaused -> "PAUSE"
                        else -> "REC"
                    }
                    val recColor = if (isRecordingPaused) DrainQTheme.colors.amber else DrainQTheme.colors.error
                    DqStatusChip(text = recLabel, color = recColor, showDot = !isRecordingPaused)
                }
                // Nur EIN Chip in der Ecke: Akku des Android-Systems (immer sichtbar).
                // < 20 % = error, sonst success. Beim Laden Lade-Icon statt Akku-Icon.
                // Hinweis: der serielle cable.batteryLevel/GROUP_CAMERA-Pfad ist hierfür tot.
                batteryPct?.let { pct ->
                    DqStatusChip(
                        text = "$pct%",
                        color = if (pct < 20) DrainQTheme.colors.error else DrainQTheme.colors.success,
                        showDot = false,
                        iconKey = if (batteryCharging) "battery_charging" else "battery"
                    )
                }
            }
        }

        // Power-Langdruck: Beenden-Dialog (wie Original-Shutdown).
        if (showPowerDialog) {
            AlertDialog(
                onDismissRequest = { showPowerDialog = false },
                title = { HideSystemBarsInDialog(); Text(S("exit_app_title")) },
                confirmButton = {
                    TextButton(onClick = {
                        showPowerDialog = false
                        (context as? android.app.Activity)?.finishAffinity()
                    }) { Text(S("exit_app")) }
                },
                dismissButton = {
                    TextButton(onClick = { showPowerDialog = false }) { Text(S("cancel")) }
                }
            )
        }

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

        // Layer 4: Slide-in control panel (Cinema-Mode — right edge, tap-on-demand).
        // Oben verankert und nur ~60 % hoch, damit der untere rechte Bereich frei
        // bleibt und die unteren Band-Buttons dort voll erreichbar sind.
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopEnd),
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
                    .fillMaxHeight(0.6f)
                    .padding(Dimensions.PanelEdgePadding),
                // Gleiche Transparenz wie die unteren Band-Kacheln (bgPanel @ 0.30),
                // damit das Videobild gleich stark durchscheint. Inhalt nutzt
                // onSurface-Tokens und bleibt gut lesbar.
                colors = CardDefaults.cardColors(
                    containerColor = DrainQTheme.colors.bgPanel.copy(alpha = 0.30f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Dimensions.PanelContentPadding)
                ) {
                    // Schließen-Affordanz (Louis #3): Das Panel ließ sich bisher nur über den
                    // Zurück-Pfeil (verlässt den Screen) oder einen Tipp auf die freie Videofläche
                    // schließen — ein Tipp AUF das Panel wird von den Buttons geschluckt. Ein X in
                    // der Kopfzeile schließt zuverlässig, ohne die Inspektion zu verlassen. Das
                    // Video-Tipp-Toggle bleibt zusätzlich erhalten; das untere Band bleibt unberührt.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionMs = System.currentTimeMillis()
                                showControls = false
                            },
                            modifier = Modifier.size(Dimensions.TouchMin) // 48 dp Touch-Target (≥ 40 dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S("close"),
                                modifier = Modifier.size(Dimensions.IconSizeLarge),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Notiz-Aktion aus der Hauptbedienung entfernt (Feedback Louis #4): Der Schaden
                    // mit Freitext ist der primäre Erfassungsweg. Notiz inkl. Audionotiz bleibt im
                    // Projekt-Detail (Reiter „Notizen") und hier über die Notizliste (Doppeltipp)
                    // erfass-/abrufbar — keine Funktion, kein Datenverlust.

                    // Battery (nur wenn die Hardware einen Wert liefert — auf der ONE
                    // kommt der echte Akkustand aus dem Android-System-Chip oben rechts)
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

                    // ── Meter Reset ───────────────────────────────────────────────────
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
                        Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                        Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                        Text(
                            text = "${S("meter_absolute")} → 0",
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
                        Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                        Spacer(Modifier.width(Dimensions.ButtonIconSpacing))
                        Text(
                            text = "${S("meter_distance")} → 0",
                            fontSize = Dimensions.ButtonLabelFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SectionSpacing))

                    // ── Damage List ───────────────────────────────────────────────────
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
                                text = S("and_more_damages").replace("{count}", (damages.size - 5).toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── Notes ─────────────────────────────────────────────────────────
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
                                text = S("and_more_notes").replace("{count}", (notes.size - 3).toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    // ── Project Info ──────────────────────────────────────────────────
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
                                    text = p.projectNumber.ifEmpty { S("project_fallback") },
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

        // Foto-Quittung: weißer Blitz über allem (kurz)
        if (showPhotoFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.7f))
            )
        }

        // Aufnahme-Modus-Auswahl als In-Window-Overlay (KEIN Dialog-/Popup-Fenster).
        // Befund 0.4.1 (ONE, RK3588 + launcher3, On-Device verifiziert): Jedes separate
        // Fenster (AlertDialog/Popup) löst beim Fenster-Übergang ein "Unstash" der
        // launcher3-System-Taskbar aus. Die App fordert die Leiste zwar als unsichtbar an
        // (dumpsys: ITYPE_EXTRA_NAVIGATION_BAR invisible), launcher3 zeigt sie danach aber
        // trotzdem — controller.hide(systemBars()) ist dann ein No-Op und die Leiste bleibt
        // dauerhaft sichtbar. Bereits das Öffnen+Schließen des alten Dialogs OHNE Aufnahme
        // genügte. Ein Overlay im selben Activity-Fenster erzeugt keinen Fensterwechsel, die
        // Taskbar bleibt eingezogen. (Die Aufnahme-Logik selbst ist unverändert.)
        val recordingPid = effectiveProjectId
        if (showRecordingDialog && recordingPid != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { showRecordingDialog = false }) },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        // Taps auf die Karte selbst dürfen das Overlay nicht schließen.
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Dimensions.PanelContentPadding)) {
                        Text(
                            S("start_recording_title"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Dimensions.TouchSpacing))
                        Text(
                            S("recording_mode_question"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Dimensions.TouchSpacing))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8, Alignment.End)
                        ) {
                            // Ohne Einblendung
                            TextButton(onClick = {
                                showRecordingDialog = false
                                val projNr = project?.projectNumber?.ifEmpty { "Projekt_$recordingPid" } ?: "Projekt_$recordingPid"
                                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                val dir = File(context.getExternalFilesDir("recordings"), "project_$recordingPid")
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
                                        initialLine2 = "",
                                        sdResolution = project?.videoQuality == "SD"
                                    )
                                    Log.d("InspectionScreen", "FFmpeg recording without OSD: ${file.absolutePath}")
                                    isRecording = true
                                } else {
                                    val file = File(dir, "${projNr}_${ts}.mp4")
                                    recordingFilePath = file.absolutePath
                                    // Ohne Overlay: kein OSD-Burn-in, nur ggf. SD-Skalierung.
                                    val started = localRecorder.start(
                                        file.absolutePath, frameFlow, com.uip.oneapp.network.RecorderConfig.TARGET_FPS,
                                        sdResolution = project?.videoQuality == "SD",
                                        meterProvider = { meterValue }
                                    )
                                    Log.d("InspectionScreen", "Lokal-Aufnahme gestartet=$started: ${file.absolutePath}")
                                    // Welle 5: nur bei tatsächlichem Start (HW-Encoder evtl. blockiert) aufnehmen.
                                    isRecording = started
                                }
                            }) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                                Spacer(modifier = Modifier.width(Dimensions.ButtonIconSpacing))
                                Text(S("without_overlay"))
                            }
                            // Mit Einblendung
                            TextButton(onClick = {
                                showRecordingDialog = false
                                val projNr = project?.projectNumber?.ifEmpty { "Projekt_$recordingPid" } ?: "Projekt_$recordingPid"
                                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                val dir = File(context.getExternalFilesDir("recordings"), "project_$recordingPid")
                                dir.mkdirs()
                                recordingProjectName = projNr
                                if (rtspUrl.isNotEmpty()) {
                                    // FfmpegRtspRecorder burns OSD directly during recording.
                                    // "Mit Overlay" means: force app-OSD on top of whatever the
                                    // camera is rendering. Otherwise project name, corrected meter
                                    // value etc. would not appear in the video — the camera-side
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
                                        initialLine2 = buildOsdLine2(meterValue, withOverlaySettings),
                                        initialFinding = findingFlash ?: "",
                                        sdResolution = project?.videoQuality == "SD"
                                    )
                                    Log.d("InspectionScreen", "FFmpeg recording with OSD burn-in: ${file.absolutePath}")
                                    isRecording = true
                                } else {
                                    val file = File(dir, "${projNr}_${ts}.mp4")
                                    recordingFilePath = file.absolutePath
                                    // M3: Lokal-Aufnahme MIT eingebranntem OSD (Live-Zeilen via Provider).
                                    val localOverlay = osdSettings.copy(enableOsdBurnIn = true, enableFindingBurnIn = true)
                                    val started = localRecorder.start(
                                        file.absolutePath, frameFlow, com.uip.oneapp.network.RecorderConfig.TARGET_FPS,
                                        sdResolution = project?.videoQuality == "SD",
                                        osdSettings = localOverlay,
                                        typeface = osdTypeface,
                                        osdLine1Provider = { osdLine1 },
                                        osdLine2Provider = { buildOsdLine2(meterValue, localOverlay) },
                                        findingProvider = { findingFlash },
                                        meterProvider = { meterValue }
                                    )
                                    Log.d("InspectionScreen", "Lokal-Aufnahme (OSD) gestartet=$started: ${file.absolutePath}")
                                    // Welle 5: nur bei tatsächlichem Start (HW-Encoder evtl. blockiert) aufnehmen.
                                    isRecording = started
                                }
                            }) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(Dimensions.IconSizeMedium))
                                Spacer(modifier = Modifier.width(Dimensions.ButtonIconSpacing))
                                Text(S("with_overlay"))
                            }
                        }
                    }
                }
            }
        }
    } // end Box (cinema-mode root)

    // Damage Dialog
    val damageDialogPid = effectiveProjectId
    if (showDamageDialog && damageDialogPid != null) {
        DamageDialog(
            photoPath = capturedPhotoPath,
            annotatedPhotoPath = capturedAnnotatedPath,
            currentMeter = meterValue,
            projectId = damageDialogPid,
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
                    // the photo to be self-explanatory in the report — capture-time
                    // bitmap had no app-side overlay at all.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        burnOsdIntoPhoto(damage.photoPath, osdSettings, osdLine1,
                            buildOsdLine2(damage.position, osdSettings),
                            flashText, osdTypeface)
                        burnOsdIntoPhoto(damage.annotatedPhotoPath, osdSettings, osdLine1,
                            buildOsdLine2(damage.position, osdSettings),
                            flashText, osdTypeface)
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

    // Recording mode dialog → siehe In-Window-Overlay oben (Box im cinema-mode root).
    // Bewusst KEIN AlertDialog mehr: ein separates Fenster unstasht auf der ONE die
    // launcher3-Taskbar, die danach nicht mehr eingezogen werden kann (Befund 0.4.1).

    // Note Dialog
    val noteDialogPid = effectiveProjectId
    if (showNoteDialog && noteDialogPid != null) {
        NoteDialog(
            currentMeter = meterValue,
            projectId = noteDialogPid,
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
    osdSettings: com.uip.oneapp.export.OsdSettings
): String {
    val parts = mutableListOf<String>()
    if (osdSettings.showMeterValue) {
        // Rundungs-/Reset-Artefakte nie als "-0.00m" einbrennen (Autotest-Befund 07.06.).
        val m = if (kotlin.math.abs(meterValue) < 0.005f) 0f else meterValue
        parts.add(String.format(java.util.Locale.US, "%.2fm", m))
    }
    if (osdSettings.showDate) {
        parts.add(java.time.LocalDate.now().toString())
    }
    return parts.joinToString(" | ")
}

/**
 * Builds the finding-flash label for the on-screen / burned-in overlay.
 * Schadensbezeichnung (Preset) + Position + optionale Kurzbeschreibung.
 * (DIN-Code-Logik entfernt — CEO-Beschluss 2026-06-07.)
 */
internal fun buildFindingFlashText(damage: com.uip.oneapp.data.local.entity.DamageEntity): String {
    val label = damage.damageType.ifEmpty { "OBS" }
    val pos  = String.format(java.util.Locale.US, "%.2fm", damage.position)
    val desc = damage.description.trim().take(80)
    val tail = if (desc.isNotEmpty()) " - $desc" else ""
    return "$label @ $pos$tail"
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
    finding: String?,
    typeface: android.graphics.Typeface? = null
) {
    if (photoPath.isEmpty()) return
    val file = java.io.File(photoPath)
    if (!file.exists() || file.length() == 0L) return
    val bmp = android.graphics.BitmapFactory.decodeFile(photoPath) ?: return
    try {
        val mutable = if (bmp.isMutable) bmp
                      else bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val forced = osdSettings.copy(enableOsdBurnIn = true)
        com.uip.oneapp.export.OsdRenderer.renderBitmap(mutable, forced, line1, line2, finding, typeface = typeface)
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


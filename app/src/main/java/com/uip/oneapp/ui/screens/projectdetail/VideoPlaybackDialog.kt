package com.uip.oneapp.ui.screens.projectdetail

import android.graphics.Bitmap
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqButtonStyle
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.screens.inspection.DamageDialog
import com.uip.oneapp.ui.screens.inspection.ImageAnnotationDialog
import com.uip.oneapp.ui.screens.inspection.NoteDialog
import com.uip.oneapp.ui.theme.Amber
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.DrainQTheme
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uip.oneapp.network.MeterTrackV3
import com.uip.oneapp.network.MeterTrackReaderV3
import com.uip.oneapp.network.lookupMeterV3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlaybackDialog(
    videoFile: File,
    onDismiss: () -> Unit,
    projectId: Long = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val damageRepository: DamageRepository = koinInject()
    val noteRepository: NoteRepository = koinInject()

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var showDamageDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showAnnotationDialog by remember { mutableStateOf(false) }
    var capturedPhotoPath by remember { mutableStateOf("") }
    var capturedAnnotatedPath by remember { mutableStateOf("") }
    var annotationPhotoPath by remember { mutableStateOf("") }
    var meterTrack by remember { mutableStateOf(MeterTrackV3.EMPTY) }
    var currentMeterForDialog by remember { mutableStateOf<Float?>(null) }
    var playerReady by remember { mutableStateOf(false) }

    LaunchedEffect(videoFile) {
        // Welle 5: NUR v3 (Zeitachse) lesen; v1/v2-Sidecars → EMPTY → leeres Pflichtfeld (nie 0.00).
        meterTrack = withContext(Dispatchers.IO) { MeterTrackReaderV3.read(videoFile) }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.fromUri(videoFile.toUri())
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
    }

    // Invariante (Welle 4b, Selbstschutz): Videodauer ≈ letzterFrameIndex / fps.
    // Weicht sie um mehr als ein Frame ab, ist die Zeitbasis der Spur verletzt →
    // laut warnen statt den Fehler im Bericht zu verstecken.
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) playerReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    LaunchedEffect(meterTrack, playerReady) {
        // EMPTY (v1/v2/keine Spur) übersprungen — sonst würde jede Altaufnahme falsch warnen.
        if (playerReady && meterTrack.samples.isNotEmpty()) {
            val durationMs = exoPlayer.duration
            if (durationMs > 0) {
                val expected = meterTrack.expectedDurationMs()
                // VFR: Videodauer ≈ letzte Meter-PTS (beide aus derselben Uhr). Toleranz großzügig.
                if (kotlin.math.abs(durationMs - expected) > 250) {
                    android.util.Log.w(
                        "VideoPlaybackDialog",
                        "Meter-Spur v3 Zeitbasis-Abweichung: video=${durationMs}ms " +
                            "erwartet=${expected}ms (lastTUs=${meterTrack.samples.last().tUs})"
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    fun captureFrame(): String? {
        val pv = playerViewRef ?: return null
        val textureView = findTextureViewIn(pv)
        val bitmap = textureView?.bitmap ?: return null
        val dir = File(context.getExternalFilesDir("damages"), "project_$projectId")
        dir.mkdirs()
        val file = File(dir, "video_frame_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    Dialog(
        onDismissRequest = {
            exoPlayer.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        HideSystemBarsInDialog()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    // Aus XML inflaten: surface_type=texture_view ist nur als XML-Attribut
                    // setzbar. Die Default-SurfaceView lieferte KEIN TextureView-Bitmap →
                    // captureFrame() (Foto/Schaden aus dem Video) war immer null.
                    (android.view.LayoutInflater.from(ctx)
                        .inflate(com.uip.oneapp.R.layout.player_view_texture, null) as PlayerView).apply {
                        player = exoPlayer
                        useController = true
                        playerViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Close button
            IconButton(
                onClick = {
                    exoPlayer.stop()
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimensions.PanelEdgePadding)
                    .size(48.dp)
            ) {
                DqIcon(key = "close", tint = Amber, size = Dimensions.DqIconLarge)
            }

            // File name label
            Text(
                text = videoFile.name,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Dimensions.PanelEdgePadding)
                    .background(DrainQTheme.colors.osdBg)
                    .padding(horizontal = Dimensions.SectionSpacing, vertical = Dimensions.SmallSpacing)
            )

            // Action buttons (only when projectId is set)
            if (projectId > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Dimensions.VideoControlsBottomPadding)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(Dimensions.OverlayCornerRadius))
                        .padding(horizontal = Dimensions.PanelEdgePadding, vertical = Dimensions.SectionSpacing),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.TouchSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo button
                    DqButton(
                        text = S("photo"),
                        iconKey = "camera",
                        style = DqButtonStyle.Secondary,
                        onClick = {
                            exoPlayer.pause()
                            currentMeterForDialog = lookupMeterV3(meterTrack, exoPlayer.currentPosition)
                            val path = captureFrame()
                            if (path != null) {
                                capturedPhotoPath = path
                                capturedAnnotatedPath = ""
                                showDamageDialog = true
                            } else {
                                exoPlayer.play()
                            }
                        },
                    )

                    // Damage button
                    DqButton(
                        text = S("damage"),
                        iconKey = "alert",
                        style = DqButtonStyle.Primary,
                        onClick = {
                            exoPlayer.pause()
                            currentMeterForDialog = lookupMeterV3(meterTrack, exoPlayer.currentPosition)
                            val path = captureFrame()
                            capturedPhotoPath = path ?: ""
                            capturedAnnotatedPath = ""
                            showDamageDialog = true
                        },
                    )

                    // Note button
                    DqButton(
                        text = S("note"),
                        iconKey = "edit",
                        style = DqButtonStyle.Secondary,
                        onClick = {
                            exoPlayer.pause()
                            currentMeterForDialog = lookupMeterV3(meterTrack, exoPlayer.currentPosition)
                            showNoteDialog = true
                        },
                    )
                }
            }
        }
    }

    // Damage Dialog
    if (showDamageDialog && projectId > 0) {
        DamageDialog(
            photoPath = capturedPhotoPath,
            annotatedPhotoPath = capturedAnnotatedPath,
            currentMeter = currentMeterForDialog,
            projectId = projectId,
            onSave = { damage ->
                scope.launch {
                    damageRepository.saveDamage(damage)
                }
                showDamageDialog = false
                exoPlayer.play()
            },
            onDismiss = {
                showDamageDialog = false
                exoPlayer.play()
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

    // Note Dialog
    if (showNoteDialog && projectId > 0) {
        NoteDialog(
            currentMeter = currentMeterForDialog,
            projectId = projectId,
            onSave = { note ->
                scope.launch {
                    noteRepository.saveNote(note)
                }
                showNoteDialog = false
                exoPlayer.play()
            },
            onDismiss = {
                showNoteDialog = false
                exoPlayer.play()
            }
        )
    }
}

private fun findTextureViewIn(viewGroup: ViewGroup): TextureView? {
    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i)
        if (child is TextureView) return child
        if (child is ViewGroup) {
            val found = findTextureViewIn(child)
            if (found != null) return found
        }
    }
    return null
}

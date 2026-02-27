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
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.screens.inspection.DamageDialog
import com.uip.oneapp.ui.screens.inspection.ImageAnnotationDialog
import com.uip.oneapp.ui.screens.inspection.NoteDialog
import com.uip.oneapp.ui.theme.StatusRed
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.fromUri(videoFile.toUri())
            setMediaItem(item)
            prepare()
            playWhenReady = true
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
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
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = S("close"),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // File name label
            Text(
                text = videoFile.name,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Action buttons (only when projectId is set)
            if (projectId > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo button
                    FilledTonalButton(onClick = {
                        exoPlayer.pause()
                        val path = captureFrame()
                        if (path != null) {
                            scope.launch {
                                damageRepository.saveDamage(
                                    DamageEntity(
                                        projectId = projectId,
                                        position = 0f,
                                        damageType = "Foto",
                                        photoPath = path
                                    )
                                )
                            }
                        }
                        exoPlayer.play()
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("photo"))
                    }

                    // Damage button
                    Button(
                        onClick = {
                            exoPlayer.pause()
                            val path = captureFrame()
                            capturedPhotoPath = path ?: ""
                            capturedAnnotatedPath = ""
                            showDamageDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("damage"))
                    }

                    // Note button
                    FilledTonalButton(onClick = {
                        exoPlayer.pause()
                        showNoteDialog = true
                    }) {
                        Icon(Icons.Default.Note, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("note"))
                    }
                }
            }
        }
    }

    // Damage Dialog
    if (showDamageDialog && projectId > 0) {
        DamageDialog(
            photoPath = capturedPhotoPath,
            annotatedPhotoPath = capturedAnnotatedPath,
            currentMeter = 0f,
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
            currentMeter = 0f,
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

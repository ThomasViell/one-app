package com.uip.oneapp.ui.screens.inspection

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.StatusGreen
import com.uip.oneapp.ui.theme.StatusRed
import java.io.File

private const val TAG = "NoteDialog"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    currentMeter: Float,
    projectId: Long,
    onSave: (NoteEntity) -> Unit,
    onDismiss: () -> Unit,
    existingNote: NoteEntity? = null
) {
    val context = LocalContext.current
    val isEditing = existingNote != null

    var noteText by remember { mutableStateOf(existingNote?.text ?: "") }
    var meterText by remember { mutableStateOf(String.format("%.2f", existingNote?.position ?: currentMeter)) }
    var audioPath by remember { mutableStateOf(existingNote?.audioPath ?: "") }

    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    // Cleanup on dismiss
    DisposableEffect(Unit) {
        onDispose {
            recorder?.let {
                try { it.stop() } catch (_: Exception) {}
                it.release()
            }
            player?.let {
                try { it.stop() } catch (_: Exception) {}
                it.release()
            }
        }
    }

    fun startRecording() {
        val dir = File(context.getExternalFilesDir("notes"), "project_$projectId")
        dir.mkdirs()
        val file = File(dir, "note_${System.currentTimeMillis()}.m4a")
        audioPath = file.absolutePath

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                recorder = mr
                Log.d(TAG, "Recording started: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                release()
            }
        }
    }

    fun stopRecording() {
        recorder?.let {
            try {
                it.stop()
                it.release()
                Log.d(TAG, "Recording stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Stop recording failed", e)
            }
        }
        recorder = null
        isRecording = false
    }

    fun playAudio() {
        if (audioPath.isEmpty() || !File(audioPath).exists()) return
        val mp = MediaPlayer()
        mp.setDataSource(audioPath)
        mp.setOnCompletionListener {
            isPlaying = false
            it.release()
            player = null
        }
        mp.prepare()
        mp.start()
        isPlaying = true
        player = mp
    }

    fun stopPlaying() {
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
        isPlaying = false
    }

    Dialog(
        onDismissRequest = {
            if (isRecording) stopRecording()
            if (isPlaying) stopPlaying()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .fillMaxHeight(0.8f)
                .imePadding(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEditing) S("edit_note") else S("create_note"))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isRecording) stopRecording()
                            if (isPlaying) stopPlaying()
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = S("close"))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (isRecording) stopRecording()
                                if (isPlaying) stopPlaying()
                                val meter = meterText.replace(",", ".").toFloatOrNull() ?: currentMeter
                                if (noteText.isBlank() && audioPath.isEmpty()) return@TextButton
                                onSave(
                                    NoteEntity(
                                        id = existingNote?.id ?: 0,
                                        projectId = projectId,
                                        position = meter,
                                        text = noteText,
                                        audioPath = audioPath,
                                        createdAt = existingNote?.createdAt ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(S("save"))
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Meter position
                    OutlinedTextField(
                        value = meterText,
                        onValueChange = { meterText = it },
                        label = { Text(S("field_position")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Text note
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(S("note")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text(S("note_placeholder")) }
                    )

                    // Audio recording section
                    Text(
                        text = S("voice_note_title"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Record button
                        if (!isRecording) {
                            FilledTonalButton(
                                onClick = {
                                    if (!hasAudioPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@FilledTonalButton
                                    }
                                    startRecording()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = StatusRed
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(S("record"))
                            }
                        } else {
                            Button(
                                onClick = { stopRecording() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusRed
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(S("stop"))
                            }
                        }

                        // Play button (if audio exists)
                        val hasAudio = audioPath.isNotEmpty() && File(audioPath).exists() && File(audioPath).length() > 0
                        if (hasAudio && !isRecording) {
                            if (!isPlaying) {
                                FilledTonalButton(onClick = { playAudio() }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(S("play"))
                                }
                            } else {
                                FilledTonalButton(onClick = { stopPlaying() }) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(S("stop"))
                                }
                            }
                        }
                    }

                    // Recording/playback status
                    if (isRecording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = S("recording_active"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusRed
                            )
                        }
                    } else if (audioPath.isNotEmpty() && File(audioPath).exists() && File(audioPath).length() > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = S("voice_note_available"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusGreen
                            )
                        }
                    }

                    // Bottom save button (accessible when keyboard is shown)
                    Button(
                        onClick = {
                            if (isRecording) stopRecording()
                            if (isPlaying) stopPlaying()
                            val meter = meterText.replace(",", ".").toFloatOrNull() ?: currentMeter
                            if (noteText.isBlank() && audioPath.isEmpty()) return@Button
                            onSave(
                                NoteEntity(
                                    id = existingNote?.id ?: 0,
                                    projectId = projectId,
                                    position = meter,
                                    text = noteText,
                                    audioPath = audioPath,
                                    createdAt = existingNote?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(S("save"))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

package com.uip.oneapp.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uip.oneapp.ui.theme.StatusGreen
import com.uip.oneapp.ui.theme.StatusRed
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

private const val TAG = "VlcVideoPlayer"

@Composable
fun VlcVideoPlayer(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    recordingFilePath: String? = null,
    overlayText: String? = null,
    onLayoutReady: (VLCVideoLayout) -> Unit = {},
    onMediaPlayerReady: (MediaPlayer) -> Unit = {},
    onConnected: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var playerState by remember { mutableStateOf(PlayerState.IDLE) }
    var errorMessage by remember { mutableStateOf("") }
    // Track recording state
    var activeRecording by remember { mutableStateOf(false) }
    // Remember target path and pre-existing files for rename after stop
    var targetPath by remember { mutableStateOf<String?>(null) }
    var recordingDir by remember { mutableStateOf<File?>(null) }
    var preExistingFiles by remember { mutableStateOf<Set<String>>(emptySet()) }

    val libVLC = remember {
        LibVLC(context, arrayListOf(
            "--no-audio",
            "--rtsp-tcp",
            "--network-caching=300",
            "--live-caching=300",
            "--file-caching=300",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--drop-late-frames",
            "--skip-frames",
            "--avcodec-skiploopfilter=4",
            "--avcodec-hurry-up",
            "--no-stats"
        ))
    }

    // Single player - display and recording on same RTSP connection
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    // Expose player for external control (pause/resume)
    LaunchedEffect(Unit) {
        onMediaPlayerReady(mediaPlayer)
    }

    // Listen for VLC events
    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    playerState = PlayerState.BUFFERING
                    Log.d(TAG, "Opening stream...")
                }
                MediaPlayer.Event.Buffering -> {
                    if (event.buffering >= 100f) {
                        playerState = PlayerState.READY
                    } else {
                        playerState = PlayerState.BUFFERING
                    }
                }
                MediaPlayer.Event.Playing -> {
                    playerState = PlayerState.READY
                    onConnected()
                    Log.d(TAG, "Playing")
                }
                MediaPlayer.Event.EncounteredError -> {
                    playerState = PlayerState.ERROR
                    errorMessage = "VLC playback error"
                    onError(errorMessage)
                    Log.e(TAG, "Playback error")
                }
                MediaPlayer.Event.Stopped -> {
                    playerState = PlayerState.IDLE
                }
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose {
            mediaPlayer.setEventListener(null)
        }
    }

    // Handle recording via VLC's built-in record() function
    // No sout needed - VLC records the current stream directly while display continues
    LaunchedEffect(recordingFilePath) {
        if (recordingFilePath != null) {
            activeRecording = true
            val filePath = recordingFilePath.replace("\\", "/")
            val file = File(filePath)
            val dir = file.parentFile
            targetPath = filePath
            recordingDir = dir

            if (dir != null && !dir.exists()) dir.mkdirs()

            // Remember existing files so we can identify the new VLC-created file later
            preExistingFiles = dir?.listFiles()?.map { it.name }?.toSet() ?: emptySet()

            // Start recording current stream to directory
            val success = mediaPlayer.record(dir?.absolutePath?.replace("\\", "/"))
            Log.d(TAG, "record() started: success=$success, dir=${dir?.absolutePath}")

        } else if (activeRecording) {
            activeRecording = false

            // Stop recording
            mediaPlayer.record(null)
            Log.d(TAG, "record() stopped")

            // Give VLC time to finalize the file
            delay(500)

            // Find the new file VLC created and rename to our target name
            val dir = recordingDir
            val target = targetPath
            if (dir != null && target != null) {
                val newFiles = dir.listFiles()?.filter { it.name !in preExistingFiles }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
                Log.d(TAG, "Found ${newFiles.size} new files after recording")
                if (newFiles.isNotEmpty()) {
                    val vlcFile = newFiles.first()
                    // Keep VLC's extension (typically .ts)
                    val ext = vlcFile.extension
                    val targetFile = if (ext.isNotEmpty()) {
                        File(target.substringBeforeLast(".") + ".$ext")
                    } else {
                        File(target)
                    }
                    val renamed = vlcFile.renameTo(targetFile)
                    Log.d(TAG, "Renamed ${vlcFile.name} -> ${targetFile.name}: $renamed")
                } else {
                    Log.w(TAG, "No new recording file found in ${dir.absolutePath}")
                }
            }
            targetPath = null
            recordingDir = null
        }
    }

    // Cleanup on disposal
    DisposableEffect(rtspUrl) {
        onDispose {
            if (activeRecording) {
                mediaPlayer.record(null)
            }
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // VLC video surface
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    onLayoutReady(layout)
                    // Use TextureView (4th param=true) to allow screenshot capture via getBitmap()
                    mediaPlayer.attachViews(layout, null, false, true)

                    val media = Media(libVLC, Uri.parse(rtspUrl))
                    media.setHWDecoderEnabled(true, true)
                    media.addOption(":network-caching=0")
                    media.addOption(":live-caching=0")
                    media.addOption(":rtsp-tcp")
                    media.addOption(":clock-jitter=0")
                    media.addOption(":clock-synchro=0")
                    mediaPlayer.media = media
                    media.release()

                    playerState = PlayerState.BUFFERING
                    mediaPlayer.play()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status overlay
        when (playerState) {
            PlayerState.IDLE, PlayerState.BUFFERING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (playerState == PlayerState.BUFFERING) "Buffering..." else "Verbinde...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            PlayerState.ERROR -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = StatusRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Stream-Fehler",
                        color = StatusRed,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = errorMessage,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            PlayerState.READY -> {
                // Top-end indicators
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (recordingFilePath != null) {
                        val infiniteTransition = rememberInfiniteTransition(label = "rec")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "recAlpha"
                        )
                        Text(
                            text = "● REC",
                            color = StatusRed.copy(alpha = alpha),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("|", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = "● LIVE (VLC)",
                        color = StatusGreen,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

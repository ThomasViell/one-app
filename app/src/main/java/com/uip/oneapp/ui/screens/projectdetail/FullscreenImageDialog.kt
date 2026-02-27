package com.uip.oneapp.ui.screens.projectdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uip.oneapp.ui.localization.S
import coil.compose.AsyncImage
import java.io.File

@Composable
fun FullscreenImageDialog(
    photoPath: String,
    caption: String = "",
    onDismiss: () -> Unit,
    onDoubleTap: (() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (newScale == 1f) {
                                Offset.Zero
                            } else {
                                val maxX = (newScale - 1f) * size.width / 2f
                                val maxY = (newScale - 1f) * size.height / 2f
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                            scale = newScale
                        }
                    }
                    .pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = {
                            if (onDoubleTap != null) {
                                onDoubleTap()
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        })
                    },
                contentScale = ContentScale.Fit
            )

            // Close button
            IconButton(
                onClick = onDismiss,
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

            // Caption
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

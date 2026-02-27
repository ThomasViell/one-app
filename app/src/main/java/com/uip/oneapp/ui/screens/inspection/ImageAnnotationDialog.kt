package com.uip.oneapp.ui.screens.inspection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uip.oneapp.ui.localization.S
import java.io.File
import java.io.FileOutputStream

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnnotationDialog(
    photoPath: String,
    onDismiss: () -> Unit,
    onSaved: (savedPath: String, originalPath: String, isCopy: Boolean) -> Unit
) {
    val originalBitmap = remember(photoPath) {
        BitmapFactory.decodeFile(photoPath)
    } ?: run {
        onDismiss()
        return
    }

    val imageBitmap = remember(originalBitmap) { originalBitmap.asImageBitmap() }

    var paths by remember { mutableStateOf(listOf<DrawPath>()) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showSaveOptions by remember { mutableStateOf(false) }

    val colors = listOf(
        Color.Red,
        Color.Yellow,
        Color(0xFF00CC00), // Green
        Color.Blue,
        Color.White
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                TopAppBar(
                    title = { Text(S("edit_image")) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = S("close"))
                        }
                    },
                    actions = {
                        // Undo
                        IconButton(
                            onClick = { if (paths.isNotEmpty()) paths = paths.dropLast(1) },
                            enabled = paths.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = S("undo"))
                        }
                        // Save
                        IconButton(onClick = { showSaveOptions = true }) {
                            Icon(Icons.Default.Save, contentDescription = S("save"))
                        }
                    }
                )

                // Color & stroke picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(S("color_label"), style = MaterialTheme.typography.bodySmall)
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selectedColor == color)
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    else Modifier.border(1.dp, Color.Gray, CircleShape)
                                )
                                .pointerInput(color) {
                                    detectTapGestures(
                                        onTap = { selectedColor = color }
                                    )
                                }
                        ) {
                            // Clickable color circle - use detectTapGestures
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Text(S("stroke_label"), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 3f..20f,
                        modifier = Modifier.width(120.dp)
                    )
                }

                // Drawing canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clipToBounds()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(selectedColor, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints = currentPoints + change.position
                                },
                                onDragEnd = {
                                    if (currentPoints.size > 1) {
                                        paths = paths + DrawPath(currentPoints, selectedColor, strokeWidth)
                                    }
                                    currentPoints = emptyList()
                                },
                                onDragCancel = {
                                    currentPoints = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw the image scaled to fit
                        val imgWidth = originalBitmap.width.toFloat()
                        val imgHeight = originalBitmap.height.toFloat()
                        val scaleX = size.width / imgWidth
                        val scaleY = size.height / imgHeight
                        val scale = minOf(scaleX, scaleY)
                        val offsetX = (size.width - imgWidth * scale) / 2f
                        val offsetY = (size.height - imgHeight * scale) / 2f

                        drawIntoCanvas { canvas ->
                            canvas.save()
                            canvas.translate(offsetX, offsetY)
                            canvas.scale(scale, scale)
                            canvas.drawImage(imageBitmap, Offset.Zero, Paint())
                            canvas.restore()
                        }

                        // Draw completed paths
                        fun drawPathList(pathList: List<DrawPath>) {
                            pathList.forEach { drawPath ->
                                if (drawPath.points.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(drawPath.points[0].x, drawPath.points[0].y)
                                        for (i in 1 until drawPath.points.size) {
                                            lineTo(drawPath.points[i].x, drawPath.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = drawPath.color,
                                        style = Stroke(
                                            width = drawPath.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                        drawPathList(paths)

                        // Draw current path
                        if (currentPoints.size >= 2) {
                            val path = Path().apply {
                                moveTo(currentPoints[0].x, currentPoints[0].y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = selectedColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Save options dialog
    if (showSaveOptions) {
        AlertDialog(
            onDismissRequest = { showSaveOptions = false },
            title = { Text(S("save_image_title")) },
            text = { Text(S("save_image_question")) },
            confirmButton = {
                TextButton(onClick = {
                    showSaveOptions = false
                    val savedPath = saveAnnotatedImage(originalBitmap, paths, canvasSize, photoPath, asCopy = false)
                    onSaved(savedPath, photoPath, false)
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S("overwrite_original"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveOptions = false
                    val savedPath = saveAnnotatedImage(originalBitmap, paths, canvasSize, photoPath, asCopy = true)
                    onSaved(savedPath, photoPath, true)
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S("save_as_copy"))
                }
            }
        )
    }
}

private fun saveAnnotatedImage(
    originalBitmap: Bitmap,
    paths: List<DrawPath>,
    canvasSize: IntSize,
    originalPath: String,
    asCopy: Boolean
): String {
    if (canvasSize.width == 0 || canvasSize.height == 0) return originalPath

    // Create a mutable copy of the original bitmap
    val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)

    // Calculate scale factors (canvas coords -> image coords)
    val imgWidth = originalBitmap.width.toFloat()
    val imgHeight = originalBitmap.height.toFloat()
    val scaleX = canvasSize.width.toFloat() / imgWidth
    val scaleY = canvasSize.height.toFloat() / imgHeight
    val scale = minOf(scaleX, scaleY)
    val offsetX = (canvasSize.width - imgWidth * scale) / 2f
    val offsetY = (canvasSize.height - imgHeight * scale) / 2f

    // Draw each path onto the bitmap
    paths.forEach { drawPath ->
        if (drawPath.points.size >= 2) {
            val paint = android.graphics.Paint().apply {
                color = drawPath.color.toArgb()
                strokeWidth = drawPath.strokeWidth / scale
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }
            val path = android.graphics.Path().apply {
                val first = drawPath.points[0]
                moveTo(
                    (first.x - offsetX) / scale,
                    (first.y - offsetY) / scale
                )
                for (i in 1 until drawPath.points.size) {
                    val pt = drawPath.points[i]
                    lineTo(
                        (pt.x - offsetX) / scale,
                        (pt.y - offsetY) / scale
                    )
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    // Save
    val outputFile = if (asCopy) {
        val dir = File(originalPath).parentFile!!
        val name = File(originalPath).nameWithoutExtension
        File(dir, "${name}_annotated.jpg")
    } else {
        File(originalPath)
    }

    FileOutputStream(outputFile).use { out ->
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    resultBitmap.recycle()

    return outputFile.absolutePath
}

package com.uip.oneapp.ui.components

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kiosk-sicherer In-App-Bildauswahl-Dialog (CEO 2026-06-07): Der System-Dateipicker ist
 * im Kiosk eine Falle (fremde Vollbild-App ohne Zurück-Navigation). Dieser Dialog bleibt
 * im App-Fenster und listet Bilder von USB-Stick, Download, DCIM und Pictures als Galerie.
 * Typischer Endkunden-Weg: Firmenlogo per USB-Stick aufs Gerät bringen.
 */
@Composable
fun ImagePickerDialog(
    title: String,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val c = DrainQTheme.colors
    var images by remember { mutableStateOf<List<File>?>(null) } // null = Scan läuft
    var scanKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(scanKey) {
        images = null
        images = withContext(Dispatchers.IO) { scanImages(context) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(Dimensions.OverlayCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.padding(Dimensions.Space16)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // Stick erst nach dem Öffnen eingesteckt → neu einlesen
                    IconButton(onClick = { scanKey++ }) {
                        DqIcon("refresh", tint = c.textSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        DqIcon("close", tint = c.textSecondary)
                    }
                }

                when {
                    images == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    images!!.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            S("image_picker_empty"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.Space8),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Space8),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(images!!, key = { it.absolutePath }) { file ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                    .clickable { onPick(file) }
                                    .padding(Dimensions.Space4)
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                )
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bilder von USB-Sticks (StorageManager, wie UsbExportService) + Download/DCIM/Pictures. */
private fun scanImages(context: Context): List<File> {
    val exts = setOf("png", "jpg", "jpeg", "webp", "bmp")
    val roots = mutableListOf<File>()

    val sm = context.getSystemService(StorageManager::class.java)
    if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        sm.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { it.directory }
            .forEach { roots += it }
    }
    val internal = Environment.getExternalStorageDirectory()
    roots += File(internal, "Download")
    roots += File(internal, "DCIM")
    roots += File(internal, "Pictures")

    val out = LinkedHashSet<File>()
    fun scan(dir: File, depth: Int) {
        if (out.size >= 300 || depth > 2 || !dir.isDirectory) return
        val children = dir.listFiles() ?: return
        for (f in children.sortedBy { it.name.lowercase() }) {
            if (out.size >= 300) return
            if (f.isFile && f.extension.lowercase() in exts && f.length() > 0) out += f
            else if (f.isDirectory && !f.name.startsWith(".")) scan(f, depth + 1)
        }
    }
    roots.forEach { scan(it, 0) }
    return out.toList()
}

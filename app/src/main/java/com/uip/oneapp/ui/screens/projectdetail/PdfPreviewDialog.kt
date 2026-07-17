package com.uip.oneapp.ui.screens.projectdetail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import com.uip.oneapp.ui.theme.DrainQTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interne Composable: Scaffold + Inhalt des PDF-Vorschau-Dialogs.
 * Wird von PdfPreviewDialog (mit Dialog-Wrapper) und im Screenshot-Test
 * (renderInline=true, ohne Dialog-Wrapper) aufgerufen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPreviewContent(
    bitmaps: List<Bitmap>,
    isLoading: Boolean,
    renderError: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DqHeader(
                title = S("pdf_preview_title"),
                actions = {
                    IconButton(onClick = onDismiss) {
                        DqIcon("close", tint = DrainQTheme.colors.textSecondary, size = Dimensions.DqIconToolbar)
                    }
                    if (!isLoading && renderError == null && bitmaps.isNotEmpty()) {
                        DqButton(
                            text = S("export_start"),
                            iconKey = "download",
                            onClick = { onDismiss(); onExport() },
                            modifier = Modifier.padding(end = Dimensions.Space8),
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DrainQTheme.colors.bgWindow)
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(Dimensions.TouchSpacing))
                        Text(
                            S("pdf_preview_loading"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                renderError != null -> {
                    Text(
                        text = renderError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Dimensions.PanelEdgePadding)
                    )
                }
                bitmaps.isEmpty() -> {
                    Text(
                        text = S("pdf_preview_empty"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Dimensions.TouchSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.TouchSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(bitmaps) { index, bitmap ->
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CardElevationHigh),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                            Text(
                                text = "${index + 1} / ${bitmaps.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Dimensions.SmallSpacing)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    // State-Hoisting für Screenshot-Tests: vorgefertigte Seiten umgehen PdfRenderer,
    // der in layoutlib (Paparazzi) nicht gemockt ist. Im Produktionspfad immer null.
    initialBitmaps: List<Bitmap>? = null,
    // Screenshot-Tests: Dialog-Wrapper weglassen (layoutlib rendert Dialog-Layouts verschoben).
    renderInline: Boolean = false,
) {
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(initialBitmaps ?: emptyList()) }
    var isLoading by remember { mutableStateOf(initialBitmaps == null) }
    var renderError by remember { mutableStateOf<String?>(null) }

    // Seiten-Bitmaps beim Schließen freigeben: 1,5x-A4-ARGB sind ~4,5 MB pro Seite —
    // ein 20-Seiten-Bericht hielte sonst ~90 MB bis zum nächsten GC.
    DisposableEffect(Unit) {
        onDispose { if (initialBitmaps == null) bitmaps.forEach { runCatching { it.recycle() } } }
    }

    if (initialBitmaps == null) LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val result = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = 1.5f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    result.add(bmp)
                }
                renderer.close()
                fd.close()
                bitmaps = result
            } catch (e: Exception) {
                renderError = e.message
                    ?: com.uip.oneapp.ui.localization.LocalizationManager.getString("pdf_render_error")
            } finally {
                isLoading = false
            }
        }
    }

    if (renderInline) {
        PdfPreviewContent(bitmaps = bitmaps, isLoading = isLoading, renderError = renderError, onDismiss = onDismiss, onExport = onExport)
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            HideSystemBarsInDialog()
            PdfPreviewContent(bitmaps = bitmaps, isLoading = isLoading, renderError = renderError, onDismiss = onDismiss, onExport = onExport)
        }
    }
}

package com.uip.oneapp.ui.screens.projects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uip.oneapp.maps.OfflineMapManager
import com.uip.oneapp.maps.OfflineMapRenderer
import com.uip.oneapp.ui.localization.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Interactive fullscreen map picker with dynamic tile loading.
 *
 * - Tiles fetched lazily as the viewport moves (pan or pinch-out reveals
 *   more area).
 * - Single tile-zoom level (no z switch); pinch scales the rendered tile
 *   size on canvas. Tradeoff: pinching way in shows chunkier pixels, but
 *   the picker stays simple and predictable.
 * - Single tap places the marker.
 * - Camera tracked in world-pixel coordinates at the fixed tile-zoom level,
 *   which lets every conversion (tap→lat/lon, lat/lon→canvas px) reuse the
 *   same Web-Mercator math as OsmStaticMapService.
 */
private const val TILE_ZOOM = 16
private const val TILE_SIZE = 256

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLon: Double?,
    offlineManager: OfflineMapManager,
    offlineRenderer: OfflineMapRenderer,
    onDismiss: () -> Unit,
    onConfirm: (lat: Double, lon: Double) -> Unit
) {
    val centerLat0 = initialLat ?: 48.137154
    val centerLon0 = initialLon ?: 11.576124

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val n = (1 shl TILE_ZOOM).toDouble()

    // Initial camera position in world pixel space at TILE_ZOOM
    val initCamPxX = remember {
        ((centerLon0 + 180.0) / 360.0 * n) * TILE_SIZE
    }
    val initCamPxY = remember {
        val latRad = Math.toRadians(centerLat0)
        ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n) * TILE_SIZE
    }
    var camPxX by remember { mutableStateOf(initCamPxX) }
    var camPxY by remember { mutableStateOf(initCamPxY) }
    var scale by remember { mutableStateOf(1f) }

    // Picked marker — null = user hasn't tapped; show initial coords as marker.
    var pickedLat by remember { mutableStateOf(initialLat ?: centerLat0) }
    var pickedLon by remember { mutableStateOf(initialLon ?: centerLon0) }

    // Tile cache. Using state-backed map so tile arrivals trigger redraw.
    val tileCache = remember { mutableStateMapOf<Pair<Int, Int>, Bitmap>() }
    val tilesLoading = remember { mutableStateMapOf<Pair<Int, Int>, Boolean>() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var offlineFallback by remember { mutableStateOf<Bitmap?>(null) }
    var loadedAtLeastOneTile by remember { mutableStateOf(false) }

    // When viewport changes, queue downloads for any visible-but-uncached tiles.
    LaunchedEffect(camPxX, camPxY, scale, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val s = scale.coerceAtLeast(0.001f)
        val halfWworld = canvasSize.width / 2.0 / s
        val halfHworld = canvasSize.height / 2.0 / s
        val xMin = floor((camPxX - halfWworld) / TILE_SIZE).toInt()
        val xMax = floor((camPxX + halfWworld) / TILE_SIZE).toInt()
        val yMin = floor((camPxY - halfHworld) / TILE_SIZE).toInt()
        val yMax = floor((camPxY + halfHworld) / TILE_SIZE).toInt()

        val maxTile = (1 shl TILE_ZOOM) - 1
        for (x in xMin..xMax) {
            for (y in yMin..yMax) {
                if (x < 0 || y < 0 || x > maxTile || y > maxTile) continue
                val key = x to y
                if (tileCache.containsKey(key) || tilesLoading[key] == true) continue
                tilesLoading[key] = true
                coroutineScope.launch(Dispatchers.IO) {
                    val bm = downloadOnlineTile(x, y, TILE_ZOOM)
                    if (bm != null) {
                        tileCache[key] = bm
                        loadedAtLeastOneTile = true
                    }
                    tilesLoading[key] = false
                }
            }
        }
    }

    // Offline fallback: if no tile loaded after ~2s and we have a covering
    // MapsForge map, render it once for the original center.
    LaunchedEffect(initialLat, initialLon) {
        kotlinx.coroutines.delay(2000)
        if (!loadedAtLeastOneTile && offlineFallback == null) {
            val covering = offlineManager.findMapCovering(centerLat0, centerLon0)
            if (covering != null) {
                val tmp = java.io.File(context.cacheDir, "map_picker_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    offlineRenderer.render(covering.file, centerLat0, centerLon0, tmp).onSuccess {
                        offlineFallback = BitmapFactory.decodeFile(it.absolutePath)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(S("pick_on_map")) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        TextButton(onClick = { onConfirm(pickedLat, pickedLon) }) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(S("apply_location"))
                        }
                    }
                )
                Text(
                    S("tap_to_set_marker"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C28)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(0.25f, 4f)
                                    // Pan in canvas px → world px (divide by current scale)
                                    camPxX -= pan.x / scale
                                    camPxY -= pan.y / scale
                                    scale = newScale
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { tap ->
                                    val s = scale.coerceAtLeast(0.001f)
                                    val cw = canvasSize.width / 2.0
                                    val ch = canvasSize.height / 2.0
                                    val worldPxX = camPxX + (tap.x - cw) / s
                                    val worldPxY = camPxY + (tap.y - ch) / s
                                    val tileFracX = worldPxX / TILE_SIZE
                                    val tileFracY = worldPxY / TILE_SIZE
                                    pickedLon = tileFracX / n * 360.0 - 180.0
                                    val latRad = atan(sinh(PI * (1 - 2 * tileFracY / n)))
                                    pickedLat = Math.toDegrees(latRad)
                                })
                            }
                    ) {
                        val s = scale.coerceAtLeast(0.001f)
                        val cw = size.width / 2.0
                        val ch = size.height / 2.0
                        val tileSizeOnCanvas = TILE_SIZE * s

                        val halfWworld = size.width / 2.0 / s
                        val halfHworld = size.height / 2.0 / s
                        val xMin = floor((camPxX - halfWworld) / TILE_SIZE).toInt()
                        val xMax = floor((camPxX + halfWworld) / TILE_SIZE).toInt()
                        val yMin = floor((camPxY - halfHworld) / TILE_SIZE).toInt()
                        val yMax = floor((camPxY + halfHworld) / TILE_SIZE).toInt()

                        val anyTileVisible = tileCache.isNotEmpty()

                        // Draw cached tiles
                        for (tx in xMin..xMax) {
                            for (ty in yMin..yMax) {
                                val bm = tileCache[tx to ty] ?: continue
                                val tileWorldX = (tx * TILE_SIZE).toDouble()
                                val tileWorldY = (ty * TILE_SIZE).toDouble()
                                val canvasX = cw + (tileWorldX - camPxX) * s
                                val canvasY = ch + (tileWorldY - camPxY) * s
                                val tileSz = tileSizeOnCanvas.toInt().coerceAtLeast(1)
                                drawImage(
                                    image = bm.asImageBitmap(),
                                    dstOffset = IntOffset(canvasX.toInt(), canvasY.toInt()),
                                    dstSize = IntSize(tileSz, tileSz)
                                )
                            }
                        }

                        // If we have an offline fallback and zero online tiles, render it
                        // centered on the initial point at its native size.
                        if (!anyTileVisible) {
                            offlineFallback?.let { fb ->
                                val bmW = fb.width.toFloat()
                                val bmH = fb.height.toFloat()
                                val baseScale = minOf(size.width / bmW, size.height / bmH) * s
                                val drawW = (bmW * baseScale).toInt()
                                val drawH = (bmH * baseScale).toInt()
                                val ox = (size.width - drawW) / 2
                                val oy = (size.height - drawH) / 2
                                drawImage(
                                    image = fb.asImageBitmap(),
                                    dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                                    dstSize = IntSize(drawW, drawH)
                                )
                            }
                        }

                        // Draw marker
                        val markerTileFracX = (pickedLon + 180.0) / 360.0 * n
                        val markerLatRad = Math.toRadians(pickedLat)
                        val markerTileFracY = (1.0 -
                            ln(tan(markerLatRad) + 1.0 / cos(markerLatRad)) / PI) / 2.0 * n
                        val markerWorldX = markerTileFracX * TILE_SIZE
                        val markerWorldY = markerTileFracY * TILE_SIZE
                        val markerCanvasX = cw + (markerWorldX - camPxX) * s
                        val markerCanvasY = ch + (markerWorldY - camPxY) * s
                        drawCircle(
                            color = Color.White,
                            radius = with(density) { 12.dp.toPx() },
                            center = Offset(markerCanvasX.toFloat(), markerCanvasY.toFloat())
                        )
                        drawCircle(
                            color = Color(0xFFFF3B30),
                            radius = with(density) { 8.dp.toPx() },
                            center = Offset(markerCanvasX.toFloat(), markerCanvasY.toFloat())
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = with(density) { 12.dp.toPx() },
                            center = Offset(markerCanvasX.toFloat(), markerCanvasY.toFloat()),
                            style = Stroke(width = with(density) { 1.dp.toPx() })
                        )
                    }

                    // Show small spinner if no tiles yet and no fallback
                    if (tileCache.isEmpty() && offlineFallback == null) {
                        CircularProgressIndicator()
                    }
                }

                Text(
                    "%.5f, %.5f".format(pickedLat, pickedLon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun downloadOnlineTile(x: Int, y: Int, zoom: Int): Bitmap? {
    return try {
        val url = URL("https://tile.openstreetmap.org/$zoom/$x/$y.png")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "DrainQ ONE/1.0")
        val bm = BitmapFactory.decodeStream(conn.inputStream)
        conn.disconnect()
        bm
    } catch (_: Exception) {
        null
    }
}

package com.uip.oneapp.maps

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

private const val TAG = "OfflineMapRenderer"

/**
 * Renders a static 3×3 tile snippet from a local MapsForge .map file to a JPEG —
 * same output contract as OsmStaticMapService.downloadAndSaveMap, but offline.
 *
 * AndroidGraphicFactory must be initialised exactly once per process before any
 * call. OneApp does that on startup.
 */
class OfflineMapRenderer(private val context: Context) {

    companion object {
        private const val ZOOM: Byte = 15
        private const val GRID = 3
        private const val TILE_SIZE = 256

        @Volatile
        private var initialised = false

        /** Idempotent — safe to call from Application.onCreate or lazily. */
        fun ensureInitialised(app: Application) {
            if (!initialised) {
                synchronized(this) {
                    if (!initialised) {
                        AndroidGraphicFactory.createInstance(app)
                        initialised = true
                    }
                }
            }
        }
    }

    suspend fun render(
        mapFile: File,
        lat: Double,
        lon: Double,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!initialised) {
            (context.applicationContext as? Application)?.let { ensureInitialised(it) }
        }
        try {
            val mapStore = MapFile(mapFile)
            val multiMapDataStore = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_FIRST).apply {
                addMapDataStore(mapStore, false, false)
            }

            val displayModel = DisplayModel().apply { setFixedTileSize(TILE_SIZE) }
            val labelStore = TileBasedLabelStore(GRID * GRID * 4)
            val tileCache = InMemoryTileCache(GRID * GRID + 2)

            val renderer = DatabaseRenderer(
                multiMapDataStore,
                AndroidGraphicFactory.INSTANCE,
                tileCache,
                labelStore,
                true,
                true,
                null
            )

            // RendererJob takes a RenderThemeFuture, not the raw XmlRenderTheme.
            // The future loads + parses the theme asynchronously; we call run() to
            // execute it inline on this thread before kicking off render jobs.
            val themeFuture = RenderThemeFuture(
                AndroidGraphicFactory.INSTANCE,
                InternalRenderTheme.DEFAULT,
                displayModel
            )
            themeFuture.run()

            val n = 1 shl ZOOM.toInt()
            val centerTileX = floor((lon + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(lat)
            val centerTileY = floor(
                (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
            ).toInt()
            val offset = GRID / 2

            val totalPx = TILE_SIZE * GRID
            val stitched = Bitmap.createBitmap(totalPx, totalPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            canvas.drawColor(Color.LTGRAY) // fallback for any failed tile

            for (row in 0 until GRID) {
                for (col in 0 until GRID) {
                    val tx = centerTileX - offset + col
                    val ty = centerTileY - offset + row
                    if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue
                    val tile = Tile(tx, ty, ZOOM, TILE_SIZE)
                    val job = RendererJob(
                        tile, multiMapDataStore, themeFuture,
                        displayModel, 1.0f, false, false
                    )
                    try {
                        val tileBitmap: TileBitmap = renderer.executeJob(job)
                        val androidBitmap = toAndroidBitmap(tileBitmap)
                        if (androidBitmap != null) {
                            canvas.drawBitmap(
                                androidBitmap,
                                (col * TILE_SIZE).toFloat(),
                                (row * TILE_SIZE).toFloat(),
                                null
                            )
                        }
                        tileBitmap.decrementRefCount()
                    } catch (e: Throwable) {
                        Log.w(TAG, "Tile render failed @ $tx/$ty: ${e.message}")
                    }
                }
            }

            // Marker — fractional offset so it lands on the exact GPS point
            val tileXFrac = (lon + 180.0) / 360.0 * n
            val tileYFrac = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
            val markerPxX = ((tileXFrac - (centerTileX - offset)) * TILE_SIZE).toFloat()
            val markerPxY = ((tileYFrac - (centerTileY - offset)) * TILE_SIZE).toFloat()
            val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED;   style = Paint.Style.FILL }
            canvas.drawCircle(markerPxX, markerPxY, 13f, outerPaint)
            canvas.drawCircle(markerPxX, markerPxY,  9f, innerPaint)

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { stitched.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            stitched.recycle()
            mapStore.close()

            Result.success(outputFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Offline render failed", e)
            Result.failure(e)
        }
    }

    // Inverse — currently unused, kept for future tile-y → lat conversion.
    @Suppress("unused")
    private fun tileYToLat(y: Double, z: Int): Double {
        val nInv = PI - 2.0 * PI * y / (1 shl z)
        return Math.toDegrees(atan(sinh(nInv)))
    }

    /**
     * MapsForge's AndroidTileBitmap.getAndroidBitmap() is protected, so we can't
     * call it directly. Workaround: round-trip the tile through its own
     * compress() method (writes PNG to a stream) then decode back. Slow-ish but
     * the tile count is tiny (9 max) and this avoids brittle reflection.
     */
    private fun toAndroidBitmap(tile: TileBitmap): Bitmap? {
        return try {
            val baos = ByteArrayOutputStream(64 * 1024)
            tile.compress(baos)
            val bytes = baos.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Throwable) {
            Log.w(TAG, "toAndroidBitmap failed: ${e.message}")
            null
        }
    }
}

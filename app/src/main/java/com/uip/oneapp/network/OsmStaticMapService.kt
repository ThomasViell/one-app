package com.uip.oneapp.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.uip.oneapp.maps.OfflineMapManager
import com.uip.oneapp.maps.OfflineMapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class OsmStaticMapService(
    // Both nullable so existing call sites without offline support keep compiling.
    // AppModule wires real instances; tests can leave them null.
    private val offlineManager: OfflineMapManager? = null,
    private val offlineRenderer: OfflineMapRenderer? = null
) {

    companion object {
        private const val ZOOM = 15
        private const val TILE_SIZE = 256
        private const val GRID = 3 // 3x3 tiles
    }

    /**
     * Downloads a 3×3 grid of OSM tiles centered on the given coordinates,
     * stitches them, draws a marker, and saves to [outputFile] as JPEG.
     */
    suspend fun downloadAndSaveMap(
        lat: Double,
        lon: Double,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        // Online-first — keeps the freshest tiles for users with internet.
        // If online fails (typical on camera-only Wi-Fi), fall back to an
        // offline MapsForge map if one covers this point.
        val online = tryOnline(lat, lon, outputFile)
        if (online.isSuccess) return@withContext online

        val mgr = offlineManager
        val renderer = offlineRenderer
        if (mgr != null && renderer != null) {
            val covering = mgr.findMapCovering(lat, lon)
            if (covering != null) {
                Log.i("OsmStaticMapService",
                    "Online failed — falling back to offline map ${covering.entry.displayName}")
                val offline = renderer.render(covering.file, lat, lon, outputFile)
                if (offline.isSuccess) return@withContext offline
            }
        }
        online // surface the original online error if nothing else worked
    }

    private suspend fun tryOnline(
        lat: Double,
        lon: Double,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val centerX = lonToTileX(lon, ZOOM)
            val centerY = latToTileY(lat, ZOOM)

            val offset = GRID / 2 // 1

            // Download tiles
            val tiles = Array(GRID) { arrayOfNulls<Bitmap>(GRID) }
            var ok = 0
            for (row in 0 until GRID) {
                for (col in 0 until GRID) {
                    val tx = centerX - offset + col
                    val ty = centerY - offset + row
                    val tile = downloadTile(tx, ty, ZOOM)
                    tiles[row][col] = tile
                    if (tile != null) ok++
                }
            }
            // If we couldn't fetch a single tile (typical offline scenario) bail
            // so the caller can try the offline renderer instead of saving a
            // fully-gray placeholder image.
            if (ok == 0) return@withContext Result.failure(Exception("no tiles available (offline?)"))

            // Stitch tiles into one bitmap
            val totalSize = TILE_SIZE * GRID
            val stitched = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)

            for (row in 0 until GRID) {
                for (col in 0 until GRID) {
                    val tile = tiles[row][col]
                    if (tile != null) {
                        canvas.drawBitmap(tile, (col * TILE_SIZE).toFloat(), (row * TILE_SIZE).toFloat(), null)
                        tile.recycle()
                    } else {
                        // Draw grey placeholder for failed tiles
                        val paint = Paint().apply { color = Color.LTGRAY }
                        canvas.drawRect(
                            (col * TILE_SIZE).toFloat(),
                            (row * TILE_SIZE).toFloat(),
                            ((col + 1) * TILE_SIZE).toFloat(),
                            ((row + 1) * TILE_SIZE).toFloat(),
                            paint
                        )
                    }
                }
            }

            // Calculate pixel position of GPS coordinate within the stitched image
            val centerTileX = lonToTileXFrac(lon, ZOOM)
            val centerTileY = latToTileYFrac(lat, ZOOM)

            // Offset from top-left tile corner in pixels
            val markerPixelX = ((centerTileX - (centerX - offset)) * TILE_SIZE).toFloat()
            val markerPixelY = ((centerTileY - (centerY - offset)) * TILE_SIZE).toFloat()

            // Draw white border circle + red fill circle
            val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(markerPixelX, markerPixelY, 13f, outerPaint)
            canvas.drawCircle(markerPixelX, markerPixelY, 9f, innerPaint)

            // Save as JPEG
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                stitched.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            stitched.recycle()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadTile(x: Int, y: Int, zoom: Int): Bitmap? {
        return try {
            val url = URL("https://tile.openstreetmap.org/$zoom/$x/$y.png")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "DrainQ ONE/1.0")
            val bitmap = BitmapFactory.decodeStream(conn.inputStream)
            conn.disconnect()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int =
        floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    private fun lonToTileXFrac(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileYFrac(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }
}

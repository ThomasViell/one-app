package com.uip.oneapp.maps

import android.content.Context
import android.util.Log
import org.mapsforge.map.reader.MapFile
import java.io.File

private const val TAG = "OfflineMapManager"

/**
 * Discovers, inspects and removes locally stored MapsForge .map files.
 * Does *not* perform downloads — that's the worker's job. Pure-Kotlin
 * (no Android service / lifecycle assumptions) so it's cheap to call.
 */
class OfflineMapManager(private val context: Context) {

    private val rootDir: File
        get() = File(context.getExternalFilesDir(null), "maps_offline").apply { mkdirs() }

    data class InstalledMap(
        val entry: OfflineMapCatalog.Entry,
        val file: File,
        val sizeBytes: Long,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    ) {
        fun covers(lat: Double, lon: Double): Boolean =
            lat in minLat..maxLat && lon in minLon..maxLon
    }

    /** File where a given catalog entry would land — used by the worker. */
    fun fileFor(entry: OfflineMapCatalog.Entry): File =
        File(rootDir, OfflineMapCatalog.fileName(entry))

    /** Scans the storage dir and returns one InstalledMap per valid .map file. */
    fun listInstalled(): List<InstalledMap> {
        val files = rootDir.listFiles { f -> f.isFile && f.name.endsWith(".map") } ?: return emptyList()
        val out = mutableListOf<InstalledMap>()
        for (file in files) {
            val entry = catalogEntryFor(file) ?: continue
            val bbox = readBoundingBox(file) ?: continue
            out += InstalledMap(
                entry = entry,
                file = file,
                sizeBytes = file.length(),
                minLat = bbox.minLat,
                maxLat = bbox.maxLat,
                minLon = bbox.minLon,
                maxLon = bbox.maxLon
            )
        }
        return out.sortedBy { it.entry.displayName }
    }

    /** First installed map whose bounding box covers the given point. */
    fun findMapCovering(lat: Double, lon: Double): InstalledMap? =
        listInstalled().firstOrNull { it.covers(lat, lon) }

    fun delete(entry: OfflineMapCatalog.Entry): Boolean {
        val f = fileFor(entry)
        return if (f.exists()) f.delete() else false
    }

    fun isInstalled(entry: OfflineMapCatalog.Entry): Boolean = fileFor(entry).exists()

    private fun catalogEntryFor(file: File): OfflineMapCatalog.Entry? {
        val id = file.nameWithoutExtension.replace('_', '/')
        return OfflineMapCatalog.findById(id)
    }

    private data class Bbox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

    private fun readBoundingBox(file: File): Bbox? {
        return try {
            val mapFile = MapFile(file)
            val bb = mapFile.boundingBox()
            val r = Bbox(bb.minLatitude, bb.maxLatitude, bb.minLongitude, bb.maxLongitude)
            mapFile.close()
            r
        } catch (e: Throwable) {
            Log.w(TAG, "Could not read bounding box of ${file.name}: ${e.message}")
            null
        }
    }
}

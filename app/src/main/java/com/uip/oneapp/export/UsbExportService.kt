package com.uip.oneapp.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.network.FRAG_SUFFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "UsbExportService"

/**
 * USB-Export (CEO-Beschluss 2026-06-07): PC-freier Datenabholweg im Feld —
 * Ersatz für den FTP-Server der Original-App. Kopiert Projektdateien direkt
 * auf einen eingesteckten USB-Stick nach <Stick>/DrainQ/<Projektnummer>/.
 *
 * Schreibzugriff auf den Stick-Root braucht ab Android 11 "Alle Dateien"-Zugriff
 * (MANAGE_EXTERNAL_STORAGE). Die ONE wird sideloaded (kein Play Store) und als
 * dediziertes Feldgerät provisioniert — der Zugriff wird einmalig in den
 * Android-Einstellungen erteilt (Dialog bietet den Sprung dorthin an).
 */
class UsbExportService(private val context: Context) {

    data class UsbVolume(val name: String, val rootDir: File)

    /** Datei-Eintrag für die Einzelauswahl im Dialog. */
    data class ExportFile(
        val zipPath: String,   // relativer Zielpfad, z. B. "fotos/dmg_123.jpg"
        val file: File,
        val category: String,  // fotos | videos | audio | berichte
    )

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Intent auf die "Alle Dateien"-Berechtigungsseite dieser App (API 30+). */
    fun allFilesAccessIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Eingesteckte, beschreibbare USB-/SD-Datenträger. StorageVolume.directory gibt es
     * ab API 30 — die ONE läuft auf Android 12; auf älteren Geräten bleibt die Liste
     * leer und der Dialog zeigt "kein Stick erkannt".
     */
    fun findUsbVolumes(): List<UsbVolume> {
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return sm.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { vol ->
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
                dir?.let { UsbVolume(vol.getDescription(context) ?: "USB", it) }
            }
    }

    /** Alle exportierbaren Dateien eines Projekts (gleiche Quellen wie der ZIP-Export). */
    fun collectProjectFiles(project: ProjectEntity): List<ExportFile> {
        val out = mutableListOf<ExportFile>()
        fun addDir(dirName: String, zipPrefix: String, category: String) {
            val dir = File(context.getExternalFilesDir(dirName), "project_${project.id}")
            if (dir.exists()) {
                // *.frag.mp4 = absturzsichere Aufnahme-Zwischenstände (Recorder-Remux), nie exportieren.
                dir.listFiles()?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(FRAG_SUFFIX) }
                    ?.sortedBy { it.name }?.forEach {
                        out.add(ExportFile("$zipPrefix/${it.name}", it, category))
                    }
            }
        }
        addDir("damages", "fotos", "fotos")
        addDir("recordings", "videos", "videos")
        addDir("notes", "audio", "audio")
        addDir("reports", "berichte", "berichte")
        // Projekt-Kartenbild (falls vorhanden)
        project.mapImagePath?.let { p ->
            val f = File(p)
            if (f.exists() && f.length() > 0) out.add(ExportFile("map.jpg", f, "berichte"))
        }
        return out
    }

    /**
     * Kopiert [files] nach <Stick>/DrainQ/<Projektordner>/<zipPath>.
     * @return Ziel-Verzeichnis bei Erfolg.
     */
    suspend fun export(
        volume: UsbVolume,
        project: ProjectEntity,
        files: List<ExportFile>,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val folderName = project.projectNumber.ifEmpty { "Projekt_${project.id}" }
        val targetRoot = File(File(volume.rootDir, "DrainQ"), folderName)
        targetRoot.mkdirs()
        if (!targetRoot.isDirectory) {
            throw java.io.IOException("Zielordner nicht beschreibbar: ${targetRoot.absolutePath}")
        }

        val totalBytes = files.sumOf { it.file.length() }.coerceAtLeast(1)
        var written = 0L
        files.forEach { ef ->
            val target = File(targetRoot, ef.zipPath)
            target.parentFile?.mkdirs()
            FileInputStream(ef.file).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        written += read
                        onProgress((written.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }
        }
        Log.d(TAG, "USB export done: ${files.size} files -> ${targetRoot.absolutePath}")
        targetRoot
    }
}

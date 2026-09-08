package com.uip.oneapp.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
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
        val zipPath: String,   // relativer Zielpfad, z. B. "fotos/REF0904-H1_20260627_101500_4,10m_Riss.jpg"
        val file: File,
        val category: String,  // fotos | videos | audio | berichte
    )

    fun hasAllFilesAccess(): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    } catch (_: Throwable) { false }

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
        // try/catch: BridgeContext (Paparazzi JVM) throws AssertionError for storage service
        val sm = try {
            context.getSystemService(StorageManager::class.java)
        } catch (_: Throwable) { null } ?: return emptyList()
        return sm.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { vol ->
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
                dir?.let { UsbVolume(vol.getDescription(context) ?: "USB", it) }
            }
    }

    /**
     * Alle exportierbaren Dateien eines Projekts (gleiche Quellen wie der ZIP-Export).
     * Fotos und Notizen bekommen hier ihren sprechenden Namen (Welle usb-namen);
     * Videos, Berichte und map.jpg bleiben unveraendert. Die Namens- und
     * Ausschlusslogik liegt Android-frei in [collectProjectFilesForFolders],
     * hier steht nur die Ordneraufloesung.
     */
    fun collectProjectFiles(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>
    ): List<ExportFile> = collectProjectFilesForFolders(project, damages, notes) { dirName ->
        context.getExternalFilesDir(dirName)!!
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
                    // Wechseldatentraeger (FUSE/exFAT): close() allein garantiert NICHT,
                    // dass die Bytes physisch auf dem Stick landen -> sonst 0-KB-Huellen.
                    // flush() + fsync erzwingen das Zurueckschreiben vor dem Schliessen.
                    output.flush()
                    output.fd.sync()
                }
            }
            // Nie stillschweigend leere Dateien ausliefern: Ziel muss die Quellgroesse haben.
            val expectedLen = ef.file.length()
            val actualLen = target.length()
            if (actualLen != expectedLen) {
                throw java.io.IOException(
                    "USB-Export unvollstaendig: ${ef.zipPath} hat $actualLen von $expectedLen Byte " +
                    "(Stick voll, schreibgeschuetzt oder abgezogen?)"
                )
            }
        }
        Log.d(TAG, "USB export done: ${files.size} files -> ${targetRoot.absolutePath}")
        targetRoot
    }
}

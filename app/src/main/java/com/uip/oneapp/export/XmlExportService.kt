package com.uip.oneapp.export

import android.content.Context
import android.util.Log
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.export.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.core.Persister
import java.io.File
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "XmlExportService"

class XmlExportService(private val context: Context) {

    private val serializer: Serializer = Persister()
    private val isoDateTimeFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    /**
     * Generiert eine XML-Datei für ein Inspektionsprojekt.
     *
     * @param project Das Projekt
     * @param damages Liste der erfassten Schäden
     * @param notes Liste der Notizen
     * @return Die generierte XML-Datei
     */
    suspend fun generateXml(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>
    ): File = withContext(Dispatchers.IO) {

        val dir = File(context.getExternalFilesDir("exports"), "xml")
        dir.mkdirs()

        val fileName = "Inspektion_${project.projectNumber.ifEmpty { project.id.toString() }}.xml"
        val xmlFile = File(dir, sanitizeFileName(fileName))

        val inspection = buildXmlInspection(project, damages, notes)

        // XML mit Encoding-Header schreiben
        val writer = StringWriter()
        serializer.write(inspection, writer)

        val rawXml = writer.toString().replace(Regex(""" class="[^"]*""""), "")
        val xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$rawXml"
        xmlFile.writeText(xmlContent, Charsets.UTF_8)

        Log.d(TAG, "XML generated: ${xmlFile.absolutePath} (${xmlFile.length()} bytes)")
        xmlFile
    }

    private fun buildXmlInspection(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>
    ): XmlInspection {
        return XmlInspection(
            header = XmlHeader(
                version = "1.0",
                generator = "DrainQ ONE",
                generatorVersion = getAppVersion(),
                exportDate = isoDateTimeFmt.format(Date()),
                // Proprietäres DrainQ-Format, kein ISYBAU/DIN-Austauschformat (CEO-Entscheidung B1).
                standard = "DrainQ-XML 1.0"
            ),
            project = XmlProject(
                projectNumber = project.projectNumber,
                client = project.auftraggeber,
                location = project.standortAdresse,
                inspectionDate = project.inspektionsdatum,
                inspector = project.inspektor,
                weather = project.wetter
            ),
            pipe = XmlPipe(
                type = project.leitungstyp,
                material = project.material,
                diameter = project.durchmesser,
                length = project.inspektionslaenge,
                startNode = project.startpunkt,
                endNode = project.endpunkt,
                cameraType = project.kameratyp
            ),
            observations = damages.sortedBy { it.position }.map { damage ->
                XmlObservation(
                    id = damage.id,
                    position = formatPosition(damage.position),
                    positionEnd = damage.positionEnd?.let { formatPosition(it) } ?: "",
                    // M6: strukturierten DIN-Code bevorzugen; Fallback auf Legacy-Text-Split
                    // nur, wenn kein mainCode erfasst ist (z. B. reine Foto-Einträge).
                    code = damage.mainCode.ifEmpty { extractDamageCode(damage.damageType) },
                    codeName = damage.mainCodeName,
                    characterization1 = damage.characterization1 ?: "",
                    characterization2 = damage.characterization2 ?: "",
                    quantification1 = damage.quantification1 ?: "",
                    quantification2 = damage.quantification2 ?: "",
                    clockPositionStart = damage.clockPositionStart ?: "",
                    clockPositionEnd = damage.clockPositionEnd ?: "",
                    damageClass = damage.damageClass?.toString() ?: "",
                    fullCode = damage.fullDinCode,
                    description = damage.description.ifEmpty { damage.readableDescription },
                    photoReference = extractFileName(damage.photoPath),
                    timestamp = isoDateTimeFmt.format(Date(damage.createdAt))
                )
            },
            notes = notes.sortedBy { it.position }.map { note ->
                XmlNote(
                    id = note.id,
                    position = formatPosition(note.position),
                    text = note.text,
                    hasAudio = note.audioPath.isNotEmpty() && File(note.audioPath).exists(),
                    timestamp = isoDateTimeFmt.format(Date(note.createdAt))
                )
            }
        )
    }

    /**
     * Extrahiert den Schadenscode aus dem damageType String.
     * Beispiel: "BAB - Rissbildung" -> "BAB"
     */
    private fun extractDamageCode(damageType: String): String {
        return damageType.split(" - ", " – ", "-").firstOrNull()?.trim() ?: damageType
    }

    /**
     * Extrahiert nur den Dateinamen aus einem Pfad.
     */
    private fun extractFileName(path: String): String {
        if (path.isEmpty()) return ""
        return File(path).name
    }

    /**
     * Formatiert die Meterposition mit 2 Dezimalstellen.
     */
    private fun formatPosition(position: Float): String {
        return String.format(Locale.US, "%.2f", position)
    }

    /**
     * Entfernt ungültige Zeichen aus Dateinamen.
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    /**
     * Gibt die App-Version zurück.
     */
    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
}

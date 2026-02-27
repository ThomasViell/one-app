# Claude.Code Aufgabe: XML-Export für ONE.APP

## Kontext

Die ONE.APP ist eine Android-Tablet-App für Kanalinspektion nach DIN EN 13508-2. Die App generiert bereits PDF-Reports und ZIP-Exporte. Jetzt soll ein **XML-Export** hinzugefügt werden.

## Architektur-Entscheidung

**Eigenständiger XML-Export in der App** (nicht wie MINA, wo XML von Hardware kommt):

```
┌─────────────────────────────────────────────────────────────┐
│  ONE.APP (eigenständig)                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 1. Projekt anlegen                                   │   │
│  │ 2. Inspektion durchführen (Video-Stream, Schäden)   │   │
│  │ 3. Schäden erfassen (DIN EN 13508-2 Codes)          │   │
│  │ 4. Export-Button → PDF + XML werden IN DER APP      │   │
│  │    generiert                                         │   │
│  │ 5. ZIP-Datei teilen (Mail, Cloud, USB)              │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Aufgabe

Implementiere einen XML-Export für Inspektionsprojekte nach DIN EN 13508-2.

---

## Schritt 1: Gradle Dependency hinzufügen

**Datei:** `app/build.gradle.kts`

Füge in den `dependencies`-Block hinzu:

```kotlin
// XML Serialization
implementation("org.simpleframework:simple-xml:2.7.1") {
    exclude(group = "stax", module = "stax-api")
    exclude(group = "xpp3", module = "xpp3")
}
```

---

## Schritt 2: XML-Datenmodell erstellen

**Neue Datei:** `app/src/main/java/com/uip/oneapp/export/model/XmlModels.kt`

```kotlin
package com.uip.oneapp.export.model

import org.simpleframework.xml.*

@Root(name = "Inspection", strict = false)
data class XmlInspection(
    @field:Element(name = "Header")
    var header: XmlHeader = XmlHeader(),
    
    @field:Element(name = "Project")
    var project: XmlProject = XmlProject(),
    
    @field:Element(name = "Pipe")
    var pipe: XmlPipe = XmlPipe(),
    
    @field:ElementList(name = "Observations", inline = false)
    var observations: List<XmlObservation> = emptyList(),
    
    @field:ElementList(name = "Notes", inline = false, required = false)
    var notes: List<XmlNote> = emptyList()
)

@Root(name = "Header", strict = false)
data class XmlHeader(
    @field:Element(name = "Version")
    var version: String = "1.0",
    
    @field:Element(name = "Generator")
    var generator: String = "ONE.APP",
    
    @field:Element(name = "GeneratorVersion")
    var generatorVersion: String = "1.3.0",
    
    @field:Element(name = "ExportDate")
    var exportDate: String = "",
    
    @field:Element(name = "Standard")
    var standard: String = "DIN EN 13508-2:2011"
)

@Root(name = "Project", strict = false)
data class XmlProject(
    @field:Element(name = "ProjectNumber")
    var projectNumber: String = "",
    
    @field:Element(name = "Client")
    var client: String = "",
    
    @field:Element(name = "Location")
    var location: String = "",
    
    @field:Element(name = "InspectionDate")
    var inspectionDate: String = "",
    
    @field:Element(name = "Inspector")
    var inspector: String = "",
    
    @field:Element(name = "Weather", required = false)
    var weather: String = ""
)

@Root(name = "Pipe", strict = false)
data class XmlPipe(
    @field:Element(name = "Type")
    var type: String = "",
    
    @field:Element(name = "Material")
    var material: String = "",
    
    @field:Element(name = "Diameter")
    var diameter: String = "",
    
    @field:Element(name = "Length")
    var length: String = "",
    
    @field:Element(name = "StartNode")
    var startNode: String = "",
    
    @field:Element(name = "EndNode")
    var endNode: String = "",
    
    @field:Element(name = "CameraType", required = false)
    var cameraType: String = ""
)

@Root(name = "Observation", strict = false)
data class XmlObservation(
    @field:Attribute(name = "id")
    var id: Long = 0,
    
    @field:Element(name = "Position")
    var position: String = "",
    
    @field:Element(name = "Code")
    var code: String = "",
    
    @field:Element(name = "Description", required = false)
    var description: String = "",
    
    @field:Element(name = "PhotoReference", required = false)
    var photoReference: String = "",
    
    @field:Element(name = "Timestamp")
    var timestamp: String = ""
)

@Root(name = "Note", strict = false)
data class XmlNote(
    @field:Attribute(name = "id")
    var id: Long = 0,
    
    @field:Element(name = "Position")
    var position: String = "",
    
    @field:Element(name = "Text")
    var text: String = "",
    
    @field:Element(name = "HasAudio")
    var hasAudio: Boolean = false,
    
    @field:Element(name = "Timestamp")
    var timestamp: String = ""
)
```

---

## Schritt 3: XML-Export Service erstellen

**Neue Datei:** `app/src/main/java/com/uip/oneapp/export/XmlExportService.kt`

```kotlin
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
        
        val xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n${writer.toString()}"
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
                generator = "ONE.APP",
                generatorVersion = getAppVersion(),
                exportDate = isoDateTimeFmt.format(Date()),
                standard = "DIN EN 13508-2:2011"
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
                    code = extractDamageCode(damage.damageType),
                    description = damage.description,
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
     * Gibt die App-Version zurück (kann später aus BuildConfig gelesen werden).
     */
    private fun getAppVersion(): String {
        return "1.3.0" // TODO: aus BuildConfig.VERSION_NAME lesen
    }
}
```

---

## Schritt 4: ProjectExportService erweitern

**Datei:** `app/src/main/java/com/uip/oneapp/export/ProjectExportService.kt`

Füge folgende Änderungen hinzu:

### 4a. Property hinzufügen (am Anfang der Klasse)

```kotlin
class ProjectExportService(private val context: Context) {

    private val xmlExportService = XmlExportService(context)  // NEU
    
    // ... bestehender Code ...
```

### 4b. Neue Methode hinzufügen (nach generateZip)

```kotlin
/**
 * Generiert ein ZIP-Archiv mit PDF-Bericht, XML-Datei und allen Medien.
 */
suspend fun generateZipWithXml(
    project: ProjectEntity,
    damages: List<DamageEntity>,
    notes: List<NoteEntity>,
    includePhotos: Boolean = true,
    includeXml: Boolean = true,
    reversed: Boolean = false,
    onProgress: (Float) -> Unit = {}
): File = withContext(Dispatchers.IO) {
    
    // Generate PDF
    onProgress(0.05f)
    val pdfFile = generatePdf(project, damages, notes, includePhotos, reversed)
    onProgress(0.15f)
    
    // Generate XML (optional)
    var xmlFile: File? = null
    if (includeXml) {
        xmlFile = xmlExportService.generateXml(project, damages, notes)
    }
    onProgress(0.25f)

    val dir = File(context.getExternalFilesDir("exports"), "")
    dir.mkdirs()
    val zipFile = File(dir, "Projekt_${project.projectNumber.ifEmpty { project.id.toString() }}.zip")

    // Collect all files to bundle
    val filesToBundle = mutableListOf<Pair<String, File>>()

    // PDF report
    filesToBundle.add("Bericht_${project.projectNumber}.pdf" to pdfFile)
    
    // XML export
    if (xmlFile != null && xmlFile.exists()) {
        filesToBundle.add("Daten_${project.projectNumber}.xml" to xmlFile)
    }

    // Damage photos
    val photosDir = File(context.getExternalFilesDir("damages"), "project_${project.id}")
    if (photosDir.exists()) {
        photosDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { f ->
            filesToBundle.add("fotos/${f.name}" to f)
        }
    }

    // Recordings
    val recordingsDir = File(context.getExternalFilesDir("recordings"), "project_${project.id}")
    if (recordingsDir.exists()) {
        recordingsDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { f ->
            filesToBundle.add("videos/${f.name}" to f)
        }
    }

    // Audio notes
    val notesDir = File(context.getExternalFilesDir("notes"), "project_${project.id}")
    if (notesDir.exists()) {
        notesDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { f ->
            filesToBundle.add("audio/${f.name}" to f)
        }
    }

    // Project info text
    val infoFile = File(dir, "projekt_info.txt")
    infoFile.writeText(buildProjectInfoText(project, damages, notes))
    filesToBundle.add("projekt_info.txt" to infoFile)

    // Calculate total size for progress
    val totalBytes = filesToBundle.sumOf { it.second.length() }.coerceAtLeast(1)
    var bytesWritten = 0L

    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        filesToBundle.forEach { (zipPath, file) ->
            zos.putNextEntry(ZipEntry(zipPath))
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                    bytesWritten += read
                    onProgress(0.25f + 0.75f * (bytesWritten.toFloat() / totalBytes))
                }
            }
            zos.closeEntry()
        }
    }

    // Cleanup temp info file
    infoFile.delete()

    onProgress(1f)
    Log.d(TAG, "ZIP with XML generated: ${zipFile.absolutePath} (${zipFile.length()} bytes, ${filesToBundle.size} files)")
    zipFile
}
```

---

## Schritt 5: Export-Dialog anpassen

**Datei:** `app/src/main/java/com/uip/oneapp/ui/screens/reports/ExportDialog.kt`

Füge eine Checkbox für XML-Export hinzu:

```kotlin
// State für XML-Option
var includeXml by remember { mutableStateOf(true) }

// Im Dialog-Content (nach der Fotos-Checkbox):
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Checkbox(
        checked = includeXml,
        onCheckedChange = { includeXml = it },
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.primary
        )
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
        Text(
            text = "XML-Datei einschließen",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "DIN EN 13508-2 kompatibel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

Beim Export-Aufruf `includeXml` an den ViewModel übergeben.

---

## Schritt 6: ViewModel anpassen

**Datei:** `app/src/main/java/com/uip/oneapp/ui/screens/reports/ReportsViewModel.kt`

Passe die Export-Funktion an, um `includeXml` zu akzeptieren:

```kotlin
fun exportProject(
    includePhotos: Boolean = true,
    includeXml: Boolean = true,
    reversed: Boolean = false,
    onProgress: (Float) -> Unit = {},
    onComplete: (File) -> Unit,
    onError: (Exception) -> Unit
) {
    viewModelScope.launch {
        try {
            val project = currentProject.value ?: throw Exception("Kein Projekt ausgewählt")
            val damages = damageRepository.getDamagesForProject(project.id)
            val notes = noteRepository.getNotesForProject(project.id)
            
            val zipFile = exportService.generateZipWithXml(
                project = project,
                damages = damages,
                notes = notes,
                includePhotos = includePhotos,
                includeXml = includeXml,
                reversed = reversed,
                onProgress = onProgress
            )
            
            onComplete(zipFile)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            onError(e)
        }
    }
}
```

---

## Erwartetes XML-Output

Nach der Implementierung sollte folgendes XML generiert werden:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Inspection>
    <Header>
        <Version>1.0</Version>
        <Generator>ONE.APP</Generator>
        <GeneratorVersion>1.3.0</GeneratorVersion>
        <ExportDate>2026-02-15T14:30:00</ExportDate>
        <Standard>DIN EN 13508-2:2011</Standard>
    </Header>
    <Project>
        <ProjectNumber>2026-001</ProjectNumber>
        <Client>Stadtwerke Musterstadt</Client>
        <Location>Hauptstraße 42, 12345 Musterstadt</Location>
        <InspectionDate>2026-02-15</InspectionDate>
        <Inspector>Max Mustermann</Inspector>
        <Weather>Sonnig, 15°C</Weather>
    </Project>
    <Pipe>
        <Type>Schmutzwasser</Type>
        <Material>Steinzeug</Material>
        <Diameter>300</Diameter>
        <Length>45.50</Length>
        <StartNode>SK001</StartNode>
        <EndNode>SK002</EndNode>
        <CameraType>Push-Kamera</CameraType>
    </Pipe>
    <Observations>
        <Observation id="1">
            <Position>12.45</Position>
            <Code>BAB</Code>
            <Description>Längsriss an der Sohle, ca. 30cm lang</Description>
            <PhotoReference>damage_001.jpg</PhotoReference>
            <Timestamp>2026-02-15T10:23:45</Timestamp>
        </Observation>
        <Observation id="2">
            <Position>23.80</Position>
            <Code>BAF</Code>
            <Description>Wurzeleinwuchs durch Muffe</Description>
            <PhotoReference>damage_002.jpg</PhotoReference>
            <Timestamp>2026-02-15T10:28:12</Timestamp>
        </Observation>
    </Observations>
    <Notes>
        <Note id="1">
            <Position>5.00</Position>
            <Text>Einlaufschacht in gutem Zustand</Text>
            <HasAudio>false</HasAudio>
            <Timestamp>2026-02-15T10:20:00</Timestamp>
        </Note>
    </Notes>
</Inspection>
```

---

## ZIP-Struktur nach Export

```
Projekt_2026-001.zip
├── Bericht_2026-001.pdf
├── Daten_2026-001.xml          ← NEU
├── projekt_info.txt
├── fotos/
│   ├── damage_001.jpg
│   └── damage_002.jpg
├── videos/
│   └── inspektion.mp4
└── audio/
    └── notiz_001.m4a
```

---

## Dateien-Übersicht

### Neue Dateien

| Pfad | Beschreibung |
|------|--------------|
| `app/src/main/java/com/uip/oneapp/export/model/XmlModels.kt` | XML-Datenmodell |
| `app/src/main/java/com/uip/oneapp/export/XmlExportService.kt` | XML-Generierung |

### Zu ändernde Dateien

| Pfad | Änderung |
|------|----------|
| `app/build.gradle.kts` | Simple XML Dependency |
| `app/src/main/java/com/uip/oneapp/export/ProjectExportService.kt` | xmlExportService Property + generateZipWithXml() |
| `app/src/main/java/com/uip/oneapp/ui/screens/reports/ExportDialog.kt` | Checkbox für XML |
| `app/src/main/java/com/uip/oneapp/ui/screens/reports/ReportsViewModel.kt` | includeXml Parameter |

---

## Testen

1. App bauen und starten
2. Bestehendes Projekt mit Schäden öffnen
3. Export-Dialog öffnen
4. "XML-Datei einschließen" aktiviert lassen
5. Export starten
6. ZIP-Datei öffnen und XML validieren

---

**Erstellt:** 15. Februar 2026  
**Projekt:** ONE.APP - NSP3CT Slave Monitor  
**Feature:** XML-Export nach DIN EN 13508-2

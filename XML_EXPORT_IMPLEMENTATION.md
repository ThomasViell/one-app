# XML-Export Integration für ONE.APP

## Projektübersicht

Die ONE.APP ist eine Android-Tablet-App für Kanalinspektion nach DIN EN 13508-2. Diese Dokumentation beschreibt die Integration eines XML-Exports nach Industriestandard.

## Ziel

Integration eines XML-Exports, der kompatibel ist mit:
1. **Primär:** ISYBAU XML (deutscher Standard für Kanalinspektionsdaten)
2. **Sekundär:** Einfaches, gut dokumentiertes XML für universellen Datenaustausch

---

## Architektur-Entscheidung: Eigenständiger Export

### Unterschied zu MINA App

| Aspekt | MINA App | ONE.APP (unser Ansatz) |
|--------|----------|------------------------|
| XML-Erzeugung | Maxprobe-Hardware | App selbst |
| App-Rolle | Nur Empfänger/Viewer | **Erzeuger** des XML |
| Hardware-Abhängigkeit | Zwingend erforderlich | Unabhängig |
| Kodierstandard | WRc (britisch) | DIN EN 13508-2 (deutsch) |

### Vorteile des eigenständigen Exports

1. **Unabhängigkeit** - Funktioniert ohne spezielle Hardware
2. **Flexibilität** - Export jederzeit möglich, auch nachträglich
3. **Deutscher Standard** - DIN EN 13508-2 / ISYBAU für deutsche Auftraggeber
4. **Erweiterbar** - Kann später um ISYBAU 2017 Schema erweitert werden

### Workflow ONE.APP

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

## Referenz: MINA App XML-Export

Die MINA App (Scanprobe) exportiert XML mit folgendem Inhalt:
- Projektinformationen
- Section-Daten (Haltungen)
- WRc-kodierte Observations (Schäden)
- Zeitstempel
- Distanzangaben (Meterpositionen)
- Snapshot-Referenzen (Fotos)

**Zweck:** Import in Drittsoftware wie WinCan, IKAS, etc.

---

## Referenz: IKAS evolution XML-Export (ISYBAU)

IKAS evolution unterstützt:
- **ISYBAU XML** (Versionen 2006, 2013, 2017)
- **DWA-M 150** Format
- Stammdaten + Inspektionsdaten + Zustandsbewertung

---

## ONE.APP Datenstruktur (aktuell)

### ProjectEntity
```kotlin
data class ProjectEntity(
    val id: Long,
    val projectNumber: String,
    // Allgemeine Angaben
    val auftraggeber: String,
    val standortAdresse: String,
    val inspektionsdatum: String,
    val inspektor: String,
    val wetter: String,
    // Leitungsdaten
    val leitungstyp: String,
    val material: String,
    val durchmesser: String,
    val inspektionslaenge: String,
    val startpunkt: String,
    val endpunkt: String,
    // Inspektionsmethode
    val kameratyp: String,
    // Meta
    val createdAt: Long,
    val status: String
)
```

### DamageEntity
```kotlin
data class DamageEntity(
    val id: Long,
    val projectId: Long,
    val position: Float,        // Meterposition
    val damageType: String,     // Schadensart (z.B. "BAB - Rissbildung")
    val description: String,
    val photoPath: String,
    val annotatedPhotoPath: String,
    val createdAt: Long
)
```

### NoteEntity
```kotlin
data class NoteEntity(
    val id: Long,
    val projectId: Long,
    val position: Float,
    val text: String,
    val audioPath: String,
    val createdAt: Long
)
```

---

## Implementierungsplan

### Phase 1: XML-Datenmodell erstellen

**Datei:** `app/src/main/java/com/uip/oneapp/export/model/XmlModels.kt`

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
    
    @field:Element(name = "Weather")
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
    
    @field:Element(name = "CameraType")
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
    
    @field:Element(name = "AudioReference", required = false)
    var audioReference: String = "",
    
    @field:Element(name = "Timestamp")
    var timestamp: String = ""
)
```

### Phase 2: XML-Export Service erweitern

**Datei:** `app/src/main/java/com/uip/oneapp/export/XmlExportService.kt`

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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "XmlExportService"

class XmlExportService(private val context: Context) {

    private val serializer: Serializer = Persister()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY)
    private val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)

    suspend fun generateXml(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>
    ): File = withContext(Dispatchers.IO) {
        
        val dir = File(context.getExternalFilesDir("exports"), "xml")
        dir.mkdirs()
        
        val xmlFile = File(dir, "Inspektion_${project.projectNumber.ifEmpty { project.id.toString() }}.xml")
        
        val inspection = buildXmlInspection(project, damages, notes)
        
        FileOutputStream(xmlFile).use { fos ->
            serializer.write(inspection, fos)
        }
        
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
                generatorVersion = "1.3.0",
                exportDate = dateFmt.format(Date()),
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
            observations = damages.map { damage ->
                XmlObservation(
                    id = damage.id,
                    position = String.format(Locale.US, "%.2f", damage.position),
                    code = extractDamageCode(damage.damageType),
                    description = damage.description,
                    photoReference = extractFileName(damage.photoPath),
                    timestamp = dateFmt.format(Date(damage.createdAt))
                )
            },
            notes = notes.map { note ->
                XmlNote(
                    id = note.id,
                    position = String.format(Locale.US, "%.2f", note.position),
                    text = note.text,
                    audioReference = extractFileName(note.audioPath),
                    timestamp = dateFmt.format(Date(note.createdAt))
                )
            }
        )
    }

    private fun extractDamageCode(damageType: String): String {
        // Extrahiert den Code aus "BAB - Rissbildung" -> "BAB"
        return damageType.split(" - ").firstOrNull()?.trim() ?: damageType
    }

    private fun extractFileName(path: String): String {
        if (path.isEmpty()) return ""
        return File(path).name
    }
}
```

### Phase 3: build.gradle.kts - Simple XML Dependency hinzufügen

**Datei:** `app/build.gradle.kts` (ergänzen in dependencies)

```kotlin
dependencies {
    // ... bestehende dependencies ...
    
    // XML Serialization
    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude(group = "stax", module = "stax-api")
        exclude(group = "xpp3", module = "xpp3")
    }
}
```

### Phase 4: ProjectExportService erweitern

**Datei:** `app/src/main/java/com/uip/oneapp/export/ProjectExportService.kt`

Folgende Methode zur bestehenden Klasse hinzufügen:

```kotlin
private val xmlExportService = XmlExportService(context)

suspend fun generateZipWithXml(
    project: ProjectEntity,
    damages: List<DamageEntity>,
    notes: List<NoteEntity>,
    includePhotos: Boolean = true,
    includeXml: Boolean = true,
    reversed: Boolean = false,
    onProgress: (Float) -> Unit = {}
): File = withContext(Dispatchers.IO) {
    // Generate PDF first
    onProgress(0.05f)
    val pdfFile = generatePdf(project, damages, notes, includePhotos, reversed)
    onProgress(0.15f)
    
    // Generate XML
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

    // ... rest wie in generateZip() ...
}
```

### Phase 5: UI - Export-Dialog erweitern

**Datei:** `app/src/main/java/com/uip/oneapp/ui/screens/reports/ExportDialog.kt`

XML-Option im Export-Dialog hinzufügen:

```kotlin
// Checkbox für XML-Export
var includeXml by remember { mutableStateOf(true) }

// Im Dialog-Content:
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    Checkbox(
        checked = includeXml,
        onCheckedChange = { includeXml = it }
    )
    Text(
        text = "XML-Datei (DIN EN 13508-2)",
        style = MaterialTheme.typography.bodyMedium
    )
}
```

---

## XML-Output Beispiel

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
            <AudioReference></AudioReference>
            <Timestamp>2026-02-15T10:20:00</Timestamp>
        </Note>
    </Notes>
</Inspection>
```

---

## Implementierungs-Reihenfolge für Claude.Code

1. **Simple XML Dependency** zu build.gradle.kts hinzufügen
2. **XML Model-Klassen** erstellen (XmlModels.kt)
3. **XmlExportService** erstellen
4. **ProjectExportService** erweitern (generateZipWithXml)
5. **ExportDialog** erweitern (Checkbox für XML)
6. **ReportsViewModel** anpassen (XML-Option durchreichen)
7. **Testen** mit einem echten Projekt

---

## Zukünftige Erweiterungen (Phase 2)

### ISYBAU-kompatibles XML
Für vollständige ISYBAU-Kompatibilität (Import in WinCan, IKAS, etc.):
- ISYBAU 2017 Schema implementieren
- Zusätzliche Pflichtfelder ergänzen
- Zustandsklassifizierung nach DWA-M 149-3

### DWA-M 150 Export
- Alternatives Exportformat für spezielle Auftraggeber

---

## Dateien-Übersicht (neu zu erstellen)

| Datei | Beschreibung |
|-------|--------------|
| `export/model/XmlModels.kt` | XML-Datenmodell Klassen |
| `export/XmlExportService.kt` | XML-Generierung Service |

## Dateien-Übersicht (zu ändern)

| Datei | Änderung |
|-------|----------|
| `build.gradle.kts` | Simple XML Dependency |
| `export/ProjectExportService.kt` | generateZipWithXml() hinzufügen |
| `ui/screens/reports/ExportDialog.kt` | XML Checkbox |
| `ui/screens/reports/ReportsViewModel.kt` | XML-Option |

---

**Erstellt:** 15. Februar 2026  
**Projekt:** ONE.APP - NSP3CT Slave Monitor  
**Feature:** XML-Export nach DIN EN 13508-2

package com.uip.oneapp.export

import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.network.FRAG_SUFFIX
import com.uip.oneapp.network.JOURNAL_SUFFIX
import com.uip.oneapp.network.METER_SIDECAR_SUFFIX
import com.uip.oneapp.network.RECOVERED_SUFFIX
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sprechende Dateinamen fuer den USB-Export (Welle usb-namen, Z-1).
 *
 * Die Logik ist bewusst ohne Android-Abhaengigkeit (reines JVM): Die Tests
 * aus UsbExportNamesTest.kt laufen als schlichtes JUnit ohne Robolectric,
 * und [collectProjectFilesForFolders] erhaelt die Ordner ueber einen Provider
 * — [UsbExportService] reicht nur noch getExternalFilesDir nach.
 *
 * Form (E-2/E-3, Muster der Video-Namen):
 * `<Projektnr>_<yyyyMMdd_HHmmss>[_<position>m]_<Schadensart>[_markiert].<endung>`
 * Die Schadensart ist das Preset (z. B. "Riss"), die Zuordnung Datei -> Befund
 * stammt aus damages/notes (gleiche Quelle wie der PDF-Export).
 */

/** Zeichen, die die FAT-Spezifikation im Namen verbietet (Hypothese bis G-3/G-4). */
private val FAT_FORBIDDEN = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

/** Maximal zulaessige Laenge eines Einzelsegments bzw. des ganzen Namens. */
private const val SEGMENT_CAP = 40
private const val NAME_CAP = 100

/**
 * Macht ein Namenssegment FAT-tauglich: verbotene Zeichen und Steuerzeichen
 * werden zu `_`, Leerraumlaeufe zu `-`, Endpunkte/Leerzeichen am Ende fallen
 * weg, danach wird auf [SEGMENT_CAP] Zeichen gekuerzt.
 */
fun fatSafe(segment: String): String {
    val trimmed = segment.trimEnd('.', ' ')
    val sb = StringBuilder(trimmed.length)
    var lastWasSpace = false
    for (ch in trimmed) {
        when {
            ch.code < 0x20 || ch in FAT_FORBIDDEN -> {
                sb.append('_'); lastWasSpace = false
            }
            ch == ' ' -> {
                if (!lastWasSpace) sb.append('-')
                lastWasSpace = true
            }
            else -> {
                sb.append(ch); lastWasSpace = false
            }
        }
    }
    return sb.toString().take(SEGMENT_CAP)
}

/**
 * Baut den sprechenden Namen. Die Endung wird nicht ueberprueft — die
 * Ausschlussliste des Exports haelt ungueltige Quellen ohnehin fern.
 */
fun speakingName(
    projectNumber: String,
    createdAtMs: Long,
    position: Float?,
    label: String,
    suffix: String?,
    ext: String
): String {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date(createdAtMs))
    val posPart = position?.let { String.format(Locale.ROOT, "%.2f", it).replace('.', ',') + "m" }
    fun compose(lab: String): String = buildString {
        append(fatSafe(projectNumber))
        append('_').append(time)
        if (posPart != null) append('_').append(posPart)
        append('_').append(lab)
        suffix?.let { append('_').append(fatSafe(it)) }
        append('.').append(ext)
    }
    // Pfadlaengen-Deckel: erst am Etikett kuerzen — Praefix, Zeit und Endung bleiben.
    var lab = fatSafe(label).ifEmpty { "Foto" }
    var name = compose(lab)
    while (name.length > NAME_CAP && lab.length > 1) {
        lab = lab.dropLast(1)
        name = compose(lab)
    }
    return name
}

/** Erzeugermuster vor dieser Welle: praefix_<13stellige ms>[.endung]. */
private val LEGACY_TIMESTAMP = Regex("^[a-z_]+_(\\d{13})(?:_annotated)?\\.\\w+$")

/** Liest den Millisekunden-Zeitstempel aus einem Vorwellen-Dateinamen (z. B. dmg_1751000000000.jpg). */
fun parseLegacyTimestamp(fileName: String): Long? =
    LEGACY_TIMESTAMP.matchEntire(fileName)?.groupValues?.get(1)?.toLongOrNull()

/**
 * Macht [names] innerhalb des Zielordners eindeutig (Z-1, Beleg 3): Der n-te
 * gleichlautende Name bekommt `_n` vor der Endung. Der Vergleich ist
 * kleinschreibungsfaltend — FAT unterscheidet nicht zwischen Gross-/Kleinschreibung.
 */
fun assignUnique(names: List<String>): List<String> {
    val seen = HashSet<String>()
    return names.map { name ->
        if (seen.add(name.lowercase(Locale.ROOT))) {
            name
        } else {
            val dot = name.lastIndexOf('.')
            var counter = 2
            var candidate: String
            do {
                candidate = if (dot >= 0) {
                    name.substring(0, dot) + "_$counter" + name.substring(dot)
                } else {
                    name + "_$counter"
                }
                counter++
            } while (!seen.add(candidate.lowercase(Locale.ROOT)))
            candidate
        }
    }
}

/**
 * Sprechender Zielname fuer eine Quelldatei (E-2). Zuordnung ueber die
 * absoluten Pfade aus damages/notes — dieselben Zeilen, die auch der
 * PDF-Export als Quelle nutzt. Reihenfolge: Schadensfoto, markiertes
 * Schadensfoto, Sprachnotiz, sonst Waise (Foto/Notiz mit Zeit aus dem
 * alten Namen), sonst der alte Name unveraendert.
 *
 * Berater-Auflage 08.09.2026: createdAt ist gemessen immer gefuellt; der
 * `> 0`-Riegel ist der Sicherheitsweg fuer den gemessenen unmoeglichen
 * Fall, damit nie eine 1970-Datierung entsteht (Zeit aus altem Dateinamen).
 */
fun exportNameFor(
    file: File,
    project: ProjectEntity,
    damageByPath: Map<String, DamageEntity>,
    annotatedByPath: Map<String, DamageEntity>,
    noteByPath: Map<String, NoteEntity>
): String {
    val path = file.absolutePath
    val num = project.projectNumber.ifEmpty { "Projekt_${project.id}" }
    val damage = damageByPath[path]
    val annotated = annotatedByPath[path]
    val note = noteByPath[path]
    val legacyTs = parseLegacyTimestamp(file.name)
    val ts = damage?.createdAt?.takeIf { it > 0 }
        ?: annotated?.createdAt?.takeIf { it > 0 }
        ?: note?.createdAt?.takeIf { it > 0 }
        ?: legacyTs
    return when {
        damage != null && ts != null ->
            speakingName(num, ts, damage.position, damage.damageType, null, file.extension)
        annotated != null && ts != null ->
            speakingName(num, ts, annotated.position, annotated.damageType, "markiert", file.extension)
        note != null && ts != null ->
            speakingName(num, ts, note.position, "Notiz", null, file.extension)
        ts != null -> {
            val label = if (file.name.startsWith("note_")) "Notiz" else "Foto"
            val suffix = if (file.nameWithoutExtension.endsWith("_annotated")) "markiert" else null
            speakingName(num, ts, null, label, suffix, file.extension)
        }
        else -> file.name
    }
}

/**
 * Sammelt die exportierbaren Dateien eines Projekts wie
 * [UsbExportService.collectProjectFiles] und benennt dabei Fotos und
 * Notizen sprechend (Z-1); Videos (Z-2), Berichte und map.jpg bleiben
 * unveraendert, die Ausschlussliste unveraendert wirksam.
 *
 * [projectDirOf] darf `null` liefern: Das bedeutet, dass der externe
 * Speicher nicht eingehaengt ist (getExternalFilesDir, Android-Doku, N-1
 * Runde 2). Vor dieser Welle lief dieser Fall still in eine leere Liste
 * (`File(null, ...)` existierte nicht, der Ordner wurde uebersprungen) —
 * der Rueckfall ist absichtlich wieder die leere Liste, kein Absturz.
 */
fun collectProjectFilesForFolders(
    project: ProjectEntity,
    damages: List<DamageEntity>,
    notes: List<NoteEntity>,
    projectDirOf: (String) -> File?
): List<UsbExportService.ExportFile> {
    // Alle vier Ordner aufloesen, bevor etwas gesammelt wird — null heisst
    // "kein externer Speicher" und gilt fuer den ganzen Bestand, nicht fuer
    // einen einzelnen Ordner (N-1: leere Liste statt Absturz im Feld).
    val baseDirs = HashMap<String, File>(4)
    for (dirName in listOf("damages", "recordings", "notes", "reports")) {
        baseDirs[dirName] = projectDirOf(dirName) ?: return emptyList()
    }
    // DAO liefert createdAt DESC: associateBy laesst bei Mehrfachtreffern die
    // juengste Zeile nicht, sondern die aelteste gewinnen — Absicht fuer
    // Mehrfachbefunde an einer Datei (der erste Befund benennt).
    val damageByPath = damages.asSequence()
        .filter { it.photoPath.isNotBlank() }
        .associateBy { File(it.photoPath).absolutePath }
    val annotatedByPath = damages.asSequence()
        .filter { it.annotatedPhotoPath.isNotBlank() }
        .associateBy { File(it.annotatedPhotoPath).absolutePath }
    val noteByPath = notes.asSequence()
        .filter { it.audioPath.isNotBlank() }
        .associateBy { File(it.audioPath).absolutePath }

    val out = mutableListOf<UsbExportService.ExportFile>()
    fun addDir(dir: File, zipPrefix: String, category: String, rename: Boolean) {
        val projectDir = File(dir, "project_${project.id}")
        if (projectDir.exists()) {
            // *.frag.mp4 = absturzsichere Aufnahme-Zwischenstände (Recorder-Remux), nie exportieren.
            // *.meter.jsonl = interne Meter-Spur (Welle 4b), kein Berichtsdatum → nicht exportieren
            // (hält den USB-Stick frei von kryptischen Zusatzdateien).
            projectDir.listFiles()?.filter {
                it.isFile && it.length() > 0 &&
                    !it.name.endsWith(FRAG_SUFFIX) && !it.name.endsWith(METER_SIDECAR_SUFFIX) &&
                    !it.name.endsWith(JOURNAL_SUFFIX) &&   // Welle 5: rohes H.264-Journal nie exportieren
                    !it.name.endsWith(RECOVERED_SUFFIX)    // Welle 5a: Recovery-Marker app-intern (Hinweis steht im PDF)
            }
                ?.sortedBy { it.name }?.forEach {
                    val name = if (rename) {
                        exportNameFor(it, project, damageByPath, annotatedByPath, noteByPath)
                    } else {
                        it.name
                    }
                    out.add(UsbExportService.ExportFile("$zipPrefix/$name", it, category))
                }
        }
    }
    addDir(baseDirs.getValue("damages"), "fotos", "fotos", rename = true)
    addDir(baseDirs.getValue("recordings"), "videos", "videos", rename = false)
    addDir(baseDirs.getValue("notes"), "audio", "audio", rename = true)
    addDir(baseDirs.getValue("reports"), "berichte", "berichte", rename = false)
    project.mapImagePath?.let { p ->
        val f = File(p)
        if (f.exists() && f.length() > 0) out.add(UsbExportService.ExportFile("map.jpg", f, "berichte"))
    }

    val unique = assignUnique(out.map { it.zipPath })
    return out.mapIndexed { i, ef -> ef.copy(zipPath = unique[i]) }
}

package com.uip.oneapp.export

import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Namenslogik des USB-Exports (Welle usb-namen, E-2 bis E-7).
 * Reines JUnit ohne Robolectric — die Entities sind Datenklassen und auch
 * ohne Room instanziierbar; der Service-Teil laeuft ueber die interne
 * Ueberladung mit Ordner-Provider.
 */
class UsbExportNamesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 1751000000000 ms = 27.06.2025 04:53:20 UTC (06:53:20 MESZ) — vor dem Auftragsdatum 09.07.2026. */
    private val ts = 1751000000000L
    private val project = ProjectEntity(id = 42, projectNumber = "REF0904-H1")
    private val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date(ts))

    private fun file(folder: String, name: String): File {
        // Ablage wie im echten Service: <ordner>/project_<id>/, mehrfach aufrufbar.
        val dir = File(File(tmp.root, folder), "project_42")
        dir.mkdirs()
        return File(dir, name).also { it.writeText("x") }
    }

    // ── E-2/E-3: Namensform ────────────────────────────────────────────────────

    @Test
    fun fotoMitZeile_vorDerWelle_bekommtSprechendenNamen() {
        // Beleg 2: eine Zeile, die die App unveraendert seit Migration 8->9 schreibt,
        // mit einer Datei, deren Name aus der Zeit vor dieser Welle stammt.
        val f = file("damages", "dmg_1751000000000.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f.absolutePath, createdAt = ts
        )
        val name = exportNameFor(
            f, project,
            mapOf(f.absolutePath to d), emptyMap(), emptyMap()
        )
        assertEquals("REF0904-H1_${time}_4,10m_Riss.jpg", name)
    }

    @Test
    fun annotiertesFoto_bekommtMarkiertSuffix() {
        val f = file("damages", "dmg_1751000000000_annotated.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            annotatedPhotoPath = f.absolutePath, createdAt = ts
        )
        val name = exportNameFor(
            f, project,
            emptyMap(), mapOf(f.absolutePath to d), emptyMap()
        )
        assertEquals("REF0904-H1_${time}_4,10m_Riss_markiert.jpg", name)
    }

    @Test
    fun sprachnotizMitZeile_bekommtNotizNamen() {
        val f = file("notes", "note_1751000000000.m4a")
        val n = NoteEntity(
            projectId = 42, position = 1.5f,
            audioPath = f.absolutePath, createdAt = ts
        )
        val name = exportNameFor(
            f, project,
            emptyMap(), emptyMap(), mapOf(f.absolutePath to n)
        )
        assertEquals("REF0904-H1_${time}_1,50m_Notiz.m4a", name)
    }

    @Test
    fun waiseMitLesbarerZeit_bekommtFotoName() {
        val f = file("damages", "dmg_1751000000000.jpg")
        val name = exportNameFor(f, project, emptyMap(), emptyMap(), emptyMap())
        assertEquals("REF0904-H1_${time}_Foto.jpg", name)
    }

    @Test
    fun waiseAnnotiert_bekommtMarkiert() {
        val f = file("damages", "video_frame_1751000000000_annotated.jpg")
        val name = exportNameFor(f, project, emptyMap(), emptyMap(), emptyMap())
        assertEquals("REF0904-H1_${time}_Foto_markiert.jpg", name)
    }

    @Test
    fun waiseNotiz_bekommtNotizName() {
        val f = file("notes", "note_1751000000000.m4a")
        val name = exportNameFor(f, project, emptyMap(), emptyMap(), emptyMap())
        assertEquals("REF0904-H1_${time}_Notiz.m4a", name)
    }

    @Test
    fun waiseOhneLesbareZeit_bleibtUnveraendert() {
        // Fremde Datei im Ordner: die Welle darf einen Namen nie schlechter machen.
        val f = file("damages", "fremde_datei.jpg")
        val name = exportNameFor(f, project, emptyMap(), emptyMap(), emptyMap())
        assertEquals("fremde_datei.jpg", name)
    }

    // ── E-5: Eindeutigkeit (Beleg 3) ───────────────────────────────────────────

    @Test
    fun eindeutigkeit_zweiGleicheNamen_bekommtZaehler() {
        val base = speakingName("REF0904-H1", ts, 4.1f, "Riss", null, "jpg")
        val uniq = assignUnique(listOf(base, base))
        assertEquals(listOf(base, base.replace(".jpg", "_2.jpg")), uniq)
    }

    @Test
    fun eindeutigkeit_caseInsensitiv() {
        // FAT unterscheidet nicht zwischen Gross-/Kleinschreibung — der Riegel auch nicht.
        val a = "REF0904-H1_${time}_4,10m_Riss.jpg"
        val b = "REF0904-H1_${time}_4,10m_riss.jpg"
        val uniq = assignUnique(listOf(a, b))
        assertEquals(listOf(a, b.replace(".jpg", "_2.jpg")), uniq)
    }

    @Test
    fun eindeutigkeit_nachFatSafe_gleicheNamensbasis() {
        // "A/B" und "A:B" falten fatSafe auf dieselbe Zeichenkette -> Riegel greift.
        val a = speakingName("REF0904-H1", ts, 4.1f, "A/B", null, "jpg")
        val b = speakingName("REF0904-H1", ts, 4.1f, "A:B", null, "jpg")
        assertEquals(a, b)
        val uniq = assignUnique(listOf(a, b))
        assertEquals(listOf(a, a.replace(".jpg", "_2.jpg")), uniq)
    }

    @Test
    fun determinismus_andereReihenfolge_gleicheNamensmenge() {
        val a = "REF0904-H1_${time}_4,10m_Riss.jpg"
        val b = "REF0904-H1_${time}_4,10m_Bogen.jpg"
        val names = listOf(a, a, b)
        assertEquals(
            assignUnique(names).toSet(),
            assignUnique(names.reversed()).toSet()
        )
    }

    // ── E-4: fatSafe ───────────────────────────────────────────────────────────

    @Test
    fun fatSafe_ersetztVerboteneZeichenUndSteuerzeichen() {
        // Verbotsliste aus der Spezifikation (Messung am FAT-Datentraeger war am PC
        // nicht herstellbar — siehe Bericht; G-3/G-4 pruefen am Geraet nach).
        assertEquals("A_________B", fatSafe("A\\/:*?\"<>|B"))
        assertEquals("A_B", fatSafe("A\tB"))
    }

    @Test
    fun fatSafe_faltetLeerzeichenUndSchneidetEndpunkt() {
        assertEquals("Mit-Leerzeichen", fatSafe("Mit  Leerzeichen"))
        assertEquals("Ende", fatSafe("Ende."))
        assertEquals("Ende", fatSafe("Ende "))
        assertEquals("Ende", fatSafe("Ende.."))
    }

    @Test
    fun fatSafe_laesstKommaUmlautUndBindestrich() {
        // Komma ist in der FAT-Spezifikation erlaubt (Hypothese, am Geraet zu messen).
        assertEquals("Öffnung,Riss", fatSafe("Öffnung,Riss"))
        assertEquals("Riss-1", fatSafe("Riss-1"))
    }

    @Test
    fun fatSafe_kuerztSegmentAuf40Zeichen() {
        assertEquals("A".repeat(40), fatSafe("A".repeat(50)))
    }

    @Test
    fun gesamtname_kuerztAuf100_praefixUndEndungBleiben() {
        val longNum = "P".repeat(50) // fatSafe -> 40 Zeichen
        val name = speakingName(longNum, ts, 4.1f, "A".repeat(50), null, "jpg")
        assertEquals(100, name.length)
        assertTrue(name.startsWith("${"P".repeat(40)}_${time}_4,10m_"))
        assertTrue(name.endsWith(".jpg"))
    }

    // ── E-2: Rueckfaelle ───────────────────────────────────────────────────────

    @Test
    fun leereProjektnummer_faelltAufProjektId() {
        val f = file("damages", "dmg_1751000000000.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f.absolutePath, createdAt = ts
        )
        val p = ProjectEntity(id = 42, projectNumber = "")
        val name = exportNameFor(f, p, mapOf(f.absolutePath to d), emptyMap(), emptyMap())
        assertTrue(name.startsWith("Projekt_42_"))
        assertTrue(name.endsWith("_4,10m_Riss.jpg"))
    }

    @Test
    fun leereSchadensart_faelltAufFoto() {
        val f = file("damages", "dmg_1751000000000.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "",
            photoPath = f.absolutePath, createdAt = ts
        )
        val name = exportNameFor(f, project, mapOf(f.absolutePath to d), emptyMap(), emptyMap())
        assertEquals("REF0904-H1_${time}_4,10m_Foto.jpg", name)
    }

    // ── Berater-Auflage (08.09.2026): createdAt-Rueckfall ──────────────────────

    @Test
    fun createdAtNull_ziehtZeitAusAltemDateinamen() {
        // Messung: createdAt ist bei Altzeilen immer gefuellt (siehe Bericht).
        // Der Rueckfall ist der Sicherheitsweg fuer den gemessenen unmöglichen Fall,
        // damit nie eine 1970-Datierung entsteht.
        val f = file("damages", "dmg_1751000000000.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f.absolutePath, createdAt = 0
        )
        val name = exportNameFor(f, project, mapOf(f.absolutePath to d), emptyMap(), emptyMap())
        assertEquals("REF0904-H1_${time}_4,10m_Riss.jpg", name)
    }

    @Test
    fun createdAtNull_ohneLesbareZeit_bleibtUnveraendert() {
        val f = file("damages", "dmg_alt.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f.absolutePath, createdAt = 0
        )
        val name = exportNameFor(f, project, mapOf(f.absolutePath to d), emptyMap(), emptyMap())
        assertEquals("dmg_alt.jpg", name)
    }

    // ── Service-Ebene: Ordner, Ausschlussliste, Z-2, Eindeutigkeit ─────────────

    @Test
    fun service_benenntNurFotosUndNotizen_videosBleiben() {
        val damages = file("damages", "dmg_1751000000000.jpg")
        val row = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = damages.absolutePath, createdAt = ts
        )
        val note = file("notes", "note_1751000000000.m4a")
        val noteRow = NoteEntity(
            projectId = 42, position = 1.5f,
            audioPath = note.absolutePath, createdAt = ts
        )
        // Z-2: Videos werden nicht umbenannt — auch keins mit waisen-aehnlichem Namen.
        file("recordings", "REF0904-H1_20260627_101500.mp4")
        file("recordings", "rec_1751000000000.mp4")
        file("reports", "Bericht_REF0904-H1.pdf")
        val map = File(tmp.root, "map_quelle.jpg").also { it.writeText("k") }

        val p = project.copy(mapImagePath = map.absolutePath)
        val files = collectProjectFilesForFolders(
            p, listOf(row), listOf(noteRow)
        ) { dirName -> File(tmp.root, dirName) }

        assertEquals(
            listOf(
                "fotos/REF0904-H1_${time}_4,10m_Riss.jpg",
                "videos/REF0904-H1_20260627_101500.mp4",
                "videos/rec_1751000000000.mp4",
                "audio/REF0904-H1_${time}_1,50m_Notiz.m4a",
                "berichte/Bericht_REF0904-H1.pdf",
                "map.jpg"
            ),
            files.map { it.zipPath }
        )
    }

    @Test
    fun service_ausschlusslisteBleibtWirksam() {
        file("damages", "dmg_1751000000000.jpg.frag.mp4")
        file("damages", "dmg_1751000000000.jpg.meter.jsonl")
        file("damages", "x.h264j")
        file("damages", "y.recovered")
        file("notes", "note_1751000000000.m4a.meter.jsonl")
        val files = collectProjectFilesForFolders(project, emptyList(), emptyList()) { dirName ->
            File(tmp.root, dirName)
        }
        assertTrue("Ausschlussliste muss den Stick frei halten, war: ${files.map { it.zipPath }}", files.isEmpty())
    }

    @Test
    fun service_zweiDateienGleicheSekunde_bekommtZaehler() {
        val f1 = file("damages", "dmg_1751000000000.jpg")
        val f2 = file("damages", "dmg_1751000000001.jpg")
        val d1 = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f1.absolutePath, createdAt = ts
        )
        val d2 = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f2.absolutePath, createdAt = ts + 1
        )
        val files = collectProjectFilesForFolders(
            project, listOf(d1, d2), emptyList()
        ) { dirName -> File(tmp.root, dirName) }
        assertEquals(
            listOf(
                "fotos/REF0904-H1_${time}_4,10m_Riss.jpg",
                "fotos/REF0904-H1_${time}_4,10m_Riss_2.jpg"
            ),
            files.map { it.zipPath }
        )
    }

    @Test
    fun service_doppelterAufruf_gleicheNamen() {
        val f = file("damages", "dmg_1751000000000.jpg")
        val d = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = f.absolutePath, createdAt = ts
        )
        val first = collectProjectFilesForFolders(project, listOf(d), emptyList()) { dirName ->
            File(tmp.root, dirName)
        }
        val second = collectProjectFilesForFolders(project, listOf(d), emptyList()) { dirName ->
            File(tmp.root, dirName)
        }
        assertEquals(first.map { it.zipPath }, second.map { it.zipPath })
    }

    // ── B-4 (Runde 2): Nachher-Spalte in §6 des Berichts ─────────────────────

    @Test
    fun service_szenarioAbschnitt6_nachherSpalteAusEchtemLauf() {
        // Die Nachher-Spalte in §6 war von Hand hergeleitet und zeigte fuer die
        // beiden Waisen-Dateien denselben Zielnamen. Der eigene Code vergibt
        // dagegen ueber assignUnique _2 (E-5). Dieser Lauf ist der Beleg: er
        // sammelt das synthetische Projekt aus §6 (id=42, REF0904-H1,
        // Zeitstempel 1751000000000, einschliesslich der beiden Waisen) und
        // schreibt die tatsaechlichen Namen nach build/tmp/b4_scenario_namen.txt
        // (Belegdatei B-4 / L-96-2 — die Spalte steht in dieser Datei, nicht
        // von Hand im Bericht).
        val dmg = file("damages", "dmg_1751000000000.jpg")
        val dmgRow = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            photoPath = dmg.absolutePath, createdAt = ts
        )
        val annotated = file("damages", "dmg_1751000000000_annotated.jpg")
        val annotatedRow = DamageEntity(
            projectId = 42, position = 4.1f, damageType = "Riss",
            annotatedPhotoPath = annotated.absolutePath, createdAt = ts
        )
        // Waisen ohne Zeile (G-2 misst, ob es sie in echten Projekten gibt).
        file("damages", "foto_1751000000000.jpg")
        file("damages", "video_frame_1751000000000.jpg")
        val note = file("notes", "note_1751000000000.m4a")
        val noteRow = NoteEntity(
            projectId = 42, position = 1.5f,
            audioPath = note.absolutePath, createdAt = ts
        )
        file("recordings", "REF0904-H1_20260627_101500.mp4")
        file("reports", "Bericht_REF0904-H1.pdf")
        val map = File(tmp.root, "map_quelle.jpg").also { it.writeText("k") }

        val files = collectProjectFilesForFolders(
            project.copy(mapImagePath = map.absolutePath),
            listOf(dmgRow, annotatedRow), listOf(noteRow)
        ) { dirName -> File(tmp.root, dirName) }

        assertEquals(
            listOf(
                "fotos/REF0904-H1_${time}_4,10m_Riss.jpg",
                "fotos/REF0904-H1_${time}_4,10m_Riss_markiert.jpg",
                "fotos/REF0904-H1_${time}_Foto.jpg",
                "fotos/REF0904-H1_${time}_Foto_2.jpg",
                "videos/REF0904-H1_20260627_101500.mp4",
                "audio/REF0904-H1_${time}_1,50m_Notiz.m4a",
                "berichte/Bericht_REF0904-H1.pdf",
                "map.jpg"
            ),
            files.map { it.zipPath }
        )

        // Rohausgabe dieses Laufs — die §6-Spalte stammt aus dieser Datei.
        val beleg = File("build/tmp/b4_scenario_namen.txt")
        beleg.parentFile?.mkdirs()
        beleg.writeText(files.joinToString("\n") { it.zipPath } + "\n")
    }

    // ── N-1 (Runde 2): externer Speicher nicht eingehaengt ───────────────────

    @Test
    fun service_externerSpeicherNichtEingehaengt_leereListeStattAbsturz() {
        // getExternalFilesDir liefert null, wenn der externe Speicher nicht eingehaengt
        // ist (Android-Doku). Vor der Welle lief der Fall still in eine leere Liste
        // (File(null, ...) existierte nicht); das !! im Service haette daraus einen
        // Absturz im Feld gemacht. Rueckfall ist absichtlich wieder die leere Liste.
        val files = collectProjectFilesForFolders(project, emptyList(), emptyList()) { null }
        assertTrue(files.isEmpty())
    }

    @Test
    fun service_einOrdnerOhneSpeicher_leereListeStattTeilbestand() {
        // null gilt fuer den ganzen Bestand, nicht fuer einen einzelnen Ordner —
        // ein Teilbestand wuerde den Bediener mit halben Exporten taeuschen.
        file("damages", "dmg_1751000000000.jpg")
        val files = collectProjectFilesForFolders(project, emptyList(), emptyList()) { dirName ->
            if (dirName == "notes") null else File(tmp.root, dirName)
        }
        assertTrue(files.isEmpty())
    }

    @Test
    fun parseLegacyTimestamp_liestNur13StelligeMillisekunden() {
        assertEquals(ts, parseLegacyTimestamp("dmg_1751000000000.jpg"))
        assertEquals(ts, parseLegacyTimestamp("foto_1751000000000.jpg"))
        assertEquals(ts, parseLegacyTimestamp("video_frame_1751000000000.jpg"))
        assertEquals(ts, parseLegacyTimestamp("note_1751000000000.m4a"))
        assertEquals(ts, parseLegacyTimestamp("dmg_1751000000000_annotated.jpg"))
        assertEquals(null, parseLegacyTimestamp("dmg_1751000000.jpg"))          // 10-stellig
        assertEquals(null, parseLegacyTimestamp("REF0904-H1_20260627_101500.mp4"))
        assertEquals(null, parseLegacyTimestamp("fremde_datei.jpg"))
    }
}

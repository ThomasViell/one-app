package com.uip.oneapp.export

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.ui.screens.settings.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "ProjectExportService"

class ProjectExportService(private val context: Context) {

    private val xmlExportService = XmlExportService(context)

    /**
     * Generiert eine eigenständige XML-Datei für ein Inspektionsprojekt.
     */
    suspend fun generateXmlExport(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>
    ): File = xmlExportService.generateXml(project, damages, notes)

    private val primaryColor = DeviceRgb(230, 57, 70)
    private val headerBg = DeviceRgb(220, 220, 220)
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    suspend fun generatePdf(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>,
        includePhotos: Boolean = true,
        reversed: Boolean = false,
        includeMap: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir("reports"), "project_${project.id}")
        dir.mkdirs()
        val pdfFile = File(dir, "Bericht_${project.projectNumber.ifEmpty { project.id.toString() }}.pdf")

        val writer = PdfWriter(FileOutputStream(pdfFile))
        val pdf = PdfDocument(writer)
        val document = Document(pdf, PageSize.A4)
        document.setMargins(40f, 40f, 40f, 40f)

        try {
            // === Read company data from settings ===
            val prefs = context.settingsStore.data.first()
            val companyName = prefs[stringPreferencesKey("company_name")] ?: ""
            val companyAddress = prefs[stringPreferencesKey("company_address")] ?: ""

            // === Company logo top-right on page 1 ===
            val logoFile = File(context.filesDir, "company_logo.png")
            if (logoFile.exists() && logoFile.length() > 0) {
                try {
                    val logoData = ImageDataFactory.create(logoFile.absolutePath)
                    val logo = Image(logoData)
                    val maxLogoHeight = 60f
                    val maxLogoWidth = 150f
                    logo.scaleToFit(maxLogoWidth, maxLogoHeight)
                    val scaledWidth = logo.imageScaledWidth
                    val scaledHeight = logo.imageScaledHeight
                    val xPos = PageSize.A4.width - 40f - scaledWidth
                    val yPos = PageSize.A4.height - 40f - scaledHeight
                    logo.setFixedPosition(1, xPos, yPos)
                    document.add(logo)
                } catch (e: Exception) {
                    Log.w(TAG, "Logo embed failed: ${logoFile.absolutePath}", e)
                }
            }

            // === PAGE 1: Cover ===
            document.add(
                Paragraph("INSPEKTIONSBERICHT")
                    .setFontSize(22f)
                    .setBold()
                    .setFontColor(primaryColor)
                    .setTextAlignment(TextAlignment.LEFT)
            )
            document.add(Paragraph("\n"))

            // Company data
            if (companyName.isNotEmpty() || companyAddress.isNotEmpty()) {
                val companyTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
                    .useAllAvailableWidth()

                fun addCompanyRow(label: String, value: String) {
                    companyTable.addCell(Cell().add(Paragraph(label).setBold().setFontSize(10f)).setBackgroundColor(headerBg).setPadding(4f))
                    companyTable.addCell(Cell().add(Paragraph(value).setFontSize(10f)).setPadding(4f))
                }

                if (companyName.isNotEmpty()) addCompanyRow("Firma", companyName)
                if (companyAddress.isNotEmpty()) addCompanyRow("Adresse", companyAddress)

                document.add(Paragraph("Firmendaten").setBold().setFontSize(14f).setFontColor(primaryColor))
                document.add(companyTable)
                document.add(Paragraph("\n"))
            }

            // Project data table
            val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
                .useAllAvailableWidth()

            fun addRow(label: String, value: String) {
                infoTable.addCell(Cell().add(Paragraph(label).setBold().setFontSize(10f)).setBackgroundColor(headerBg).setPadding(4f))
                infoTable.addCell(Cell().add(Paragraph(value).setFontSize(10f)).setPadding(4f))
            }

            addRow("Projekt-Nr.", project.projectNumber)
            addRow("Auftraggeber", project.auftraggeber)
            addRow("Standort/Adresse", project.standortAdresse)
            addRow("Inspektionsdatum", project.inspektionsdatum)
            addRow("Inspektor", project.inspektor)
            addRow("Wetterbedingungen", project.wetter)

            document.add(Paragraph("Allgemeine Angaben").setBold().setFontSize(14f).setFontColor(primaryColor))
            document.add(infoTable)
            document.add(Paragraph("\n"))

            // Pipe data
            val pipeTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
                .useAllAvailableWidth()

            fun addPipeRow(label: String, value: String) {
                pipeTable.addCell(Cell().add(Paragraph(label).setBold().setFontSize(10f)).setBackgroundColor(headerBg).setPadding(4f))
                pipeTable.addCell(Cell().add(Paragraph(value).setFontSize(10f)).setPadding(4f))
            }

            addPipeRow("Leitungstyp", project.leitungstyp)
            addPipeRow("Material", project.material)
            addPipeRow("Durchmesser", if (project.durchmesser.isNotEmpty()) "DN ${project.durchmesser}" else "-")
            addPipeRow("Inspektionslänge", if (project.inspektionslaenge.isNotEmpty()) "${project.inspektionslaenge} m" else "-")
            addPipeRow("Strecke", "${project.startpunkt} → ${project.endpunkt}")
            addPipeRow("Kameratyp", project.kameratyp)

            document.add(Paragraph("Leitungsdaten").setBold().setFontSize(14f).setFontColor(primaryColor))
            document.add(pipeTable)
            document.add(Paragraph("\n"))

            // === Map image (optional) ===
            if (includeMap && project.mapImagePath != null) {
                val mapFile = File(project.mapImagePath)
                if (mapFile.exists() && mapFile.length() > 0) {
                    try {
                        document.add(Paragraph("Standortkarte").setBold().setFontSize(14f).setFontColor(primaryColor))
                        val imgData = ImageDataFactory.create(mapFile.absolutePath)
                        val img = Image(imgData)
                        val maxWidth = PageSize.A4.width - 80f
                        img.scaleToFit(maxWidth, 300f)
                        document.add(img)
                        document.add(Paragraph("\n"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Map image embed failed: ${mapFile.absolutePath}", e)
                    }
                }
            }

            // Summary
            document.add(Paragraph("Zusammenfassung").setBold().setFontSize(14f).setFontColor(primaryColor))
            document.add(Paragraph("Anzahl Schäden: ${damages.size}").setFontSize(10f))
            document.add(Paragraph("Anzahl Notizen: ${notes.size}").setFontSize(10f))

            // === PIPE PROFILE DIAGRAM ===
            addPipeProfilePages(document, pdf, project, damages, includePhotos, reversed)

            // === PAGE N+: Damages ===
            if (damages.isNotEmpty()) {
                document.add(AreaBreak())
                document.add(
                    Paragraph("SCHADENSDOKUMENTATION")
                        .setFontSize(18f)
                        .setBold()
                        .setFontColor(primaryColor)
                )
                document.add(Paragraph("\n"))

                damages.forEachIndexed { index, damage ->
                    document.add(
                        Paragraph("Schaden #${index + 1}")
                            .setFontSize(13f)
                            .setBold()
                            .setFontColor(primaryColor)
                    )

                    val dmgTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 70f)))
                        .useAllAvailableWidth()
                    dmgTable.addCell(Cell().add(Paragraph("Position").setBold().setFontSize(9f)).setBackgroundColor(headerBg).setPadding(3f))
                    dmgTable.addCell(Cell().add(Paragraph("${String.format("%.2f", damage.position)} m").setFontSize(9f)).setPadding(3f))
                    dmgTable.addCell(Cell().add(Paragraph("Schadensart").setBold().setFontSize(9f)).setBackgroundColor(headerBg).setPadding(3f))
                    dmgTable.addCell(Cell().add(Paragraph(damage.damageType).setFontSize(9f)).setPadding(3f))
                    if (damage.description.isNotEmpty()) {
                        dmgTable.addCell(Cell().add(Paragraph("Beschreibung").setBold().setFontSize(9f)).setBackgroundColor(headerBg).setPadding(3f))
                        dmgTable.addCell(Cell().add(Paragraph(damage.description).setFontSize(9f)).setPadding(3f))
                    }
                    dmgTable.addCell(Cell().add(Paragraph("Erfasst").setBold().setFontSize(9f)).setBackgroundColor(headerBg).setPadding(3f))
                    dmgTable.addCell(Cell().add(Paragraph(dateFmt.format(Date(damage.createdAt))).setFontSize(9f)).setPadding(3f))
                    document.add(dmgTable)

                    // Photos (original + annotated side by side if both exist)
                    if (includePhotos) {
                        val photoFile = File(damage.photoPath)
                        val annotatedFile = File(damage.annotatedPhotoPath)
                        val hasOriginal = damage.photoPath.isNotEmpty() && photoFile.exists() && photoFile.length() > 0
                        val hasAnnotated = damage.annotatedPhotoPath.isNotEmpty() && annotatedFile.exists() && annotatedFile.length() > 0

                        if (hasOriginal && hasAnnotated) {
                            // Show both photos side by side in a 2-column table
                            try {
                                val photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
                                    .useAllAvailableWidth()
                                    .setMarginTop(6f)
                                    .setMarginBottom(6f)

                                val origData = ImageDataFactory.create(damage.photoPath)
                                val origImg = Image(origData)
                                val halfWidth = (PageSize.A4.width - 80f - 10f) / 2f
                                origImg.scaleToFit(halfWidth, 200f)
                                val origCell = Cell().add(origImg)
                                    .add(Paragraph("Original").setFontSize(8f).setItalic().setTextAlignment(TextAlignment.CENTER))
                                    .setPadding(2f).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                photoTable.addCell(origCell)

                                val annData = ImageDataFactory.create(damage.annotatedPhotoPath)
                                val annImg = Image(annData)
                                annImg.scaleToFit(halfWidth, 200f)
                                val annCell = Cell().add(annImg)
                                    .add(Paragraph("Markiert").setFontSize(8f).setItalic().setTextAlignment(TextAlignment.CENTER))
                                    .setPadding(2f).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                photoTable.addCell(annCell)

                                document.add(photoTable)
                            } catch (e: Exception) {
                                Log.w(TAG, "Photo pair embed failed", e)
                                document.add(Paragraph("[Fotos konnten nicht geladen werden]").setFontSize(9f).setItalic())
                            }
                        } else if (hasOriginal) {
                            try {
                                val imageData = ImageDataFactory.create(damage.photoPath)
                                val image = Image(imageData)
                                val maxWidth = PageSize.A4.width - 80f
                                val maxHeight = 250f
                                image.scaleToFit(maxWidth, maxHeight)
                                document.add(image.setMarginTop(6f).setMarginBottom(6f))
                            } catch (e: Exception) {
                                Log.w(TAG, "Photo embed failed: ${damage.photoPath}", e)
                                document.add(Paragraph("[Foto konnte nicht geladen werden]").setFontSize(9f).setItalic())
                            }
                        } else if (hasAnnotated) {
                            try {
                                val imageData = ImageDataFactory.create(damage.annotatedPhotoPath)
                                val image = Image(imageData)
                                val maxWidth = PageSize.A4.width - 80f
                                val maxHeight = 250f
                                image.scaleToFit(maxWidth, maxHeight)
                                document.add(image.setMarginTop(6f).setMarginBottom(6f))
                            } catch (e: Exception) {
                                Log.w(TAG, "Photo embed failed: ${damage.annotatedPhotoPath}", e)
                                document.add(Paragraph("[Foto konnte nicht geladen werden]").setFontSize(9f).setItalic())
                            }
                        }
                    }

                    document.add(Paragraph("\n"))
                }
            }

            // === Notes section ===
            if (notes.isNotEmpty()) {
                document.add(AreaBreak())
                document.add(
                    Paragraph("NOTIZEN")
                        .setFontSize(18f)
                        .setBold()
                        .setFontColor(primaryColor)
                )
                document.add(Paragraph("\n"))

                val noteTable = Table(UnitValue.createPercentArray(floatArrayOf(8f, 15f, 57f, 20f)))
                    .useAllAvailableWidth()

                // Header
                listOf("Nr.", "Position", "Text", "Sprachnotiz").forEach { h ->
                    noteTable.addHeaderCell(
                        Cell().add(Paragraph(h).setBold().setFontSize(9f))
                            .setBackgroundColor(headerBg).setPadding(3f)
                    )
                }

                notes.forEachIndexed { index, note ->
                    noteTable.addCell(Cell().add(Paragraph("${index + 1}").setFontSize(9f)).setPadding(3f))
                    noteTable.addCell(Cell().add(Paragraph("${String.format("%.2f", note.position)} m").setFontSize(9f)).setPadding(3f))
                    noteTable.addCell(Cell().add(Paragraph(note.text.ifEmpty { "-" }).setFontSize(9f)).setPadding(3f))
                    val hasAudio = note.audioPath.isNotEmpty() && File(note.audioPath).exists()
                    noteTable.addCell(Cell().add(Paragraph(if (hasAudio) "Ja" else "Nein").setFontSize(9f)).setPadding(3f))
                }

                document.add(noteTable)
            }

            // Footer
            document.add(Paragraph("\n\n"))
            document.add(
                Paragraph("Erstellt mit ONE.APP - ${dateFmt.format(Date())}")
                    .setFontSize(8f)
                    .setItalic()
                    .setTextAlignment(TextAlignment.RIGHT)
            )

        } finally {
            document.close()
        }

        Log.d(TAG, "PDF generated: ${pdfFile.absolutePath} (${pdfFile.length()} bytes)")
        pdfFile
    }

    suspend fun generateZip(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>,
        includePhotos: Boolean = true,
        reversed: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        // Generate PDF first
        onProgress(0.05f)
        val pdfFile = generatePdf(project, damages, notes, includePhotos, reversed)
        onProgress(0.2f)

        val dir = File(context.getExternalFilesDir("exports"), "")
        dir.mkdirs()
        val zipFile = File(dir, "Projekt_${project.projectNumber.ifEmpty { project.id.toString() }}.zip")

        // Collect all files to bundle
        val filesToBundle = mutableListOf<Pair<String, File>>() // zipPath -> file

        // PDF report
        filesToBundle.add("Bericht_${project.projectNumber}.pdf" to pdfFile)

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
                        onProgress(0.2f + 0.8f * (bytesWritten.toFloat() / totalBytes))
                    }
                }
                zos.closeEntry()
            }
        }

        // Cleanup temp info file
        infoFile.delete()

        onProgress(1f)
        Log.d(TAG, "ZIP generated: ${zipFile.absolutePath} (${zipFile.length()} bytes, ${filesToBundle.size} files)")
        zipFile
    }

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
        includeMap: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {

        // Generate PDF
        onProgress(0.05f)
        val pdfFile = generatePdf(project, damages, notes, includePhotos, reversed, includeMap)
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

        // Map image
        if (includeMap && project.mapImagePath != null) {
            val mapFile = File(project.mapImagePath)
            if (mapFile.exists() && mapFile.length() > 0) {
                filesToBundle.add("map.jpg" to mapFile)
            }
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

    // === Pipe Profile Diagram ===

    private data class DamageLayoutEntry(
        val damage: DamageEntity,
        val idealY: Float,
        val adjustedY: Float,
        val meterLabel: String
    )

    private fun addPipeProfilePages(
        document: Document,
        pdf: PdfDocument,
        project: ProjectEntity,
        damages: List<DamageEntity>,
        includePhotos: Boolean = true,
        reversed: Boolean = false
    ) {
        if (damages.isEmpty()) return

        val sortedDamages = damages.sortedBy { it.position }

        // Parse total length
        var totalLength = project.inspektionslaenge.replace(",", ".").toFloatOrNull() ?: 0f
        if (totalLength <= 0f) {
            totalLength = (sortedDamages.maxOfOrNull { it.position } ?: 0f) * 1.1f
        }
        if (totalLength <= 0f) return

        // Labels for start/end (swapped when reversed)
        val topLabel = if (reversed) project.endpunkt else project.startpunkt
        val botLabel = if (reversed) project.startpunkt else project.endpunkt
        val routeLabel = if (reversed) "${project.endpunkt} \u2192 ${project.startpunkt}"
            else "${project.startpunkt} \u2192 ${project.endpunkt}"

        // Layout constants
        val pipeX = 450f
        val pipeTopY = 752f
        val pipeBotY = 60f
        val usableHeight = pipeTopY - pipeBotY
        val minSpacing = 55f
        val photoX = 40f
        val photoSize = 40f
        val textX = if (includePhotos) 130f else 40f
        val labelAreaRight = 415f

        val pipeColor = DeviceRgb(80, 80, 80)
        val pipeInnerColor = DeviceRgb(200, 200, 200)
        val tickColor = DeviceRgb(120, 120, 120)
        val connectorColor = DeviceRgb(150, 150, 150)

        // Build layout entries with overlap prevention
        val entries = sortedDamages.map { damage ->
            val ratio = damage.position / totalLength
            val idealY = if (reversed) pipeBotY + ratio * usableHeight
                else pipeTopY - ratio * usableHeight
            DamageLayoutEntry(
                damage = damage,
                idealY = idealY.coerceIn(pipeBotY, pipeTopY),
                adjustedY = idealY.coerceIn(pipeBotY, pipeTopY),
                meterLabel = String.format("%.2f m", damage.position)
            )
        }.toMutableList()

        // Adjust for overlap
        for (i in 1 until entries.size) {
            val prev = entries[i - 1]
            val curr = entries[i]
            if (reversed) {
                if (curr.adjustedY - prev.adjustedY < minSpacing) {
                    entries[i] = curr.copy(adjustedY = prev.adjustedY + minSpacing)
                }
            } else {
                if (prev.adjustedY - curr.adjustedY < minSpacing) {
                    entries[i] = curr.copy(adjustedY = prev.adjustedY - minSpacing)
                }
            }
        }

        // Paginate: group entries that fit on one page
        val pages = mutableListOf<List<DamageLayoutEntry>>()
        var currentPage = mutableListOf<DamageLayoutEntry>()

        for (entry in entries) {
            if (currentPage.isEmpty()) {
                currentPage.add(entry)
            } else {
                val overflow = if (reversed) {
                    val firstAdj = currentPage.first().adjustedY
                    firstAdj + currentPage.size * minSpacing > pipeTopY
                } else {
                    val firstAdj = currentPage.first().adjustedY
                    firstAdj - currentPage.size * minSpacing < pipeBotY
                }
                if (overflow) {
                    pages.add(currentPage.toList())
                    currentPage = mutableListOf(entry)
                } else {
                    currentPage.add(entry)
                }
            }
        }
        if (currentPage.isNotEmpty()) pages.add(currentPage)

        // Recalculate positions per page
        pages.forEachIndexed { pageIdx, pageEntries ->
            val pageMeterStart = if (pageIdx == 0) 0f else pageEntries.first().damage.position
            val pageMeterEnd = if (pageIdx == pages.size - 1) totalLength else {
                val nextPageFirst = pages.getOrNull(pageIdx + 1)?.firstOrNull()?.damage?.position ?: totalLength
                nextPageFirst
            }
            val pageRange = (pageMeterEnd - pageMeterStart).coerceAtLeast(0.1f)

            // Recalculate Y positions for this page's range
            val layoutEntries = pageEntries.mapIndexed { _, entry ->
                val ratio = (entry.damage.position - pageMeterStart) / pageRange
                val idealY = if (reversed) pipeBotY + ratio * usableHeight
                    else pipeTopY - ratio * usableHeight
                entry.copy(
                    idealY = idealY.coerceIn(pipeBotY, pipeTopY),
                    adjustedY = idealY.coerceIn(pipeBotY, pipeTopY)
                )
            }.toMutableList()

            // Re-apply overlap prevention
            for (i in 1 until layoutEntries.size) {
                val prev = layoutEntries[i - 1]
                val curr = layoutEntries[i]
                if (reversed) {
                    if (curr.adjustedY - prev.adjustedY < minSpacing) {
                        layoutEntries[i] = curr.copy(adjustedY = (prev.adjustedY + minSpacing).coerceAtMost(pipeTopY))
                    }
                } else {
                    if (prev.adjustedY - curr.adjustedY < minSpacing) {
                        layoutEntries[i] = curr.copy(adjustedY = (prev.adjustedY - minSpacing).coerceAtLeast(pipeBotY))
                    }
                }
            }

            // --- Draw the page ---
            document.add(AreaBreak())
            val pageNum = pdf.numberOfPages
            val pdfPage = pdf.getPage(pageNum)
            val canvas = PdfCanvas(pdfPage)

            // Title
            val titleSuffix = if (pages.size > 1) " (${pageIdx + 1}/${pages.size})" else ""
            document.add(
                Paragraph("LEITUNGSVERLAUF$titleSuffix")
                    .setFixedPosition(pageNum, 40f, 790f, 400f)
                    .setFontSize(18f).setBold().setFontColor(primaryColor)
            )
            document.add(
                Paragraph(routeLabel)
                    .setFixedPosition(pageNum, 40f, 774f, 400f)
                    .setFontSize(10f)
            )

            // Draw outer pipe line
            canvas.saveState()
            canvas.setStrokeColor(pipeColor)
            canvas.setLineWidth(6f)
            canvas.moveTo(pipeX.toDouble(), pipeTopY.toDouble())
            canvas.lineTo(pipeX.toDouble(), pipeBotY.toDouble())
            canvas.stroke()

            // Inner pipe line (hollow effect)
            canvas.setStrokeColor(pipeInnerColor)
            canvas.setLineWidth(2f)
            canvas.moveTo(pipeX.toDouble(), pipeTopY.toDouble())
            canvas.lineTo(pipeX.toDouble(), pipeBotY.toDouble())
            canvas.stroke()
            canvas.restoreState()

            // Start/end node markers
            drawCircle(canvas, pipeX, pipeTopY, 5f, pipeColor)
            drawCircle(canvas, pipeX, pipeBotY, 5f, pipeColor)

            // Top label (startpunkt normally, endpunkt when reversed)
            val topMeter = if (reversed) pageMeterEnd else pageMeterStart
            val topNodeLabel = if (topLabel.isNotEmpty())
                "$topLabel (${String.format("%.1f", topMeter)} m)"
            else "${String.format("%.1f", topMeter)} m"
            document.add(
                Paragraph(topNodeLabel)
                    .setFixedPosition(pageNum, pipeX + 14, pipeTopY - 5, 120f)
                    .setFontSize(8f).setBold()
            )

            // Bottom label
            val botMeter = if (reversed) pageMeterStart else pageMeterEnd
            val botNodeLabel = if (botLabel.isNotEmpty())
                "$botLabel (${String.format("%.1f", botMeter)} m)"
            else "${String.format("%.1f", botMeter)} m"
            document.add(
                Paragraph(botNodeLabel)
                    .setFixedPosition(pageNum, pipeX + 14, pipeBotY - 5, 120f)
                    .setFontSize(8f).setBold()
            )

            // Meter scale ticks
            val tickInterval = when {
                totalLength < 20f -> 1f
                totalLength < 50f -> 5f
                totalLength < 200f -> 10f
                else -> 25f
            }

            canvas.saveState()
            canvas.setStrokeColor(tickColor)
            canvas.setLineWidth(0.5f)

            var tick = pageMeterStart + tickInterval
            while (tick < pageMeterEnd) {
                val ratio = (tick - pageMeterStart) / pageRange
                val tickY = if (reversed) pipeBotY + ratio * usableHeight
                    else pipeTopY - ratio * usableHeight
                if (tickY > pipeBotY + 15 && tickY < pipeTopY - 15) {
                    canvas.moveTo((pipeX + 4).toDouble(), tickY.toDouble())
                    canvas.lineTo((pipeX + 12).toDouble(), tickY.toDouble())
                    canvas.stroke()

                    document.add(
                        Paragraph("${tick.toInt()}m")
                            .setFixedPosition(pageNum, pipeX + 14, tickY - 4, 40f)
                            .setFontSize(6f)
                            .setFontColor(tickColor)
                    )
                }
                tick += tickInterval
            }
            canvas.restoreState()

            // Draw each damage entry
            layoutEntries.forEach { entry ->
                val d = entry.damage
                val idealY = entry.idealY
                val adjY = entry.adjustedY

                // Tick mark on pipe at true position (red accent)
                canvas.saveState()
                canvas.setStrokeColor(primaryColor)
                canvas.setLineWidth(2f)
                canvas.moveTo((pipeX - 6).toDouble(), idealY.toDouble())
                canvas.lineTo((pipeX + 6).toDouble(), idealY.toDouble())
                canvas.stroke()
                canvas.restoreState()

                // Small filled circle on pipe at true position
                drawCircle(canvas, pipeX, idealY, 3f, primaryColor)

                // Connecting dashed line from label area to pipe tick
                canvas.saveState()
                canvas.setStrokeColor(connectorColor)
                canvas.setLineWidth(0.5f)
                canvas.setLineDash(3f, 3f)
                canvas.moveTo(labelAreaRight.toDouble(), adjY.toDouble())
                canvas.lineTo((pipeX - 7).toDouble(), idealY.toDouble())
                canvas.stroke()
                canvas.restoreState()

                // Meter label - positioned at the pipe line (right side)
                document.add(
                    Paragraph(entry.meterLabel)
                        .setFixedPosition(pageNum, pipeX - 55, idealY - 4, 50f)
                        .setFontSize(8f).setBold()
                        .setTextAlignment(TextAlignment.RIGHT)
                )

                // Damage type
                document.add(
                    Paragraph(d.damageType)
                        .setFixedPosition(pageNum, textX, adjY + 2, 260f)
                        .setFontSize(9f)
                        .setFontColor(primaryColor)
                )

                // Description (if any)
                if (d.description.isNotEmpty()) {
                    document.add(
                        Paragraph(d.description)
                            .setFixedPosition(pageNum, textX, adjY - 10, 260f)
                            .setFontSize(7f)
                            .setFontColor(connectorColor)
                    )
                }

                // Thumbnail photos
                if (includePhotos) {
                    var photoXOffset = photoX
                    listOf(d.photoPath, d.annotatedPhotoPath).forEach { path ->
                        if (path.isNotEmpty()) {
                            val file = File(path)
                            if (file.exists() && file.length() > 0) {
                                try {
                                    val imgData = ImageDataFactory.create(path)
                                    val img = Image(imgData)
                                    img.scaleToFit(photoSize, photoSize)
                                    img.setFixedPosition(pageNum, photoXOffset, adjY - 22)
                                    document.add(img)
                                    photoXOffset += photoSize + 4
                                } catch (e: Exception) {
                                    Log.w(TAG, "Profile thumb failed: $path", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun drawCircle(canvas: PdfCanvas, cx: Float, cy: Float, r: Float, color: DeviceRgb) {
        canvas.saveState()
        canvas.setFillColor(color)
        canvas.circle(cx.toDouble(), cy.toDouble(), r.toDouble())
        canvas.fill()
        canvas.restoreState()
    }

    private fun buildProjectInfoText(
        project: ProjectEntity,
        damages: List<DamageEntity>,
        notes: List<NoteEntity>
    ): String = buildString {
        appendLine("=== PROJEKT-INFORMATIONEN ===")
        appendLine()
        appendLine("Projekt-Nr.: ${project.projectNumber}")
        appendLine("Auftraggeber: ${project.auftraggeber}")
        appendLine("Standort/Adresse: ${project.standortAdresse}")
        appendLine("Inspektionsdatum: ${project.inspektionsdatum}")
        appendLine("Inspektor: ${project.inspektor}")
        appendLine("Wetter: ${project.wetter}")
        appendLine()
        appendLine("Leitungstyp: ${project.leitungstyp}")
        appendLine("Material: ${project.material}")
        appendLine("Durchmesser: DN ${project.durchmesser}")
        appendLine("Inspektionslänge: ${project.inspektionslaenge} m")
        appendLine("Strecke: ${project.startpunkt} → ${project.endpunkt}")
        appendLine("Kameratyp: ${project.kameratyp}")
        appendLine()
        appendLine("=== SCHÄDEN (${damages.size}) ===")
        damages.forEachIndexed { i, d ->
            appendLine("#${i + 1}: ${String.format("%.2f", d.position)}m - ${d.damageType} - ${d.description}")
        }
        appendLine()
        appendLine("=== NOTIZEN (${notes.size}) ===")
        notes.forEachIndexed { i, n ->
            appendLine("#${i + 1}: ${String.format("%.2f", n.position)}m - ${n.text.ifEmpty { "[Sprachnotiz]" }}")
        }
        appendLine()
        appendLine("Erstellt mit ONE.APP - ${dateFmt.format(Date())}")
    }
}

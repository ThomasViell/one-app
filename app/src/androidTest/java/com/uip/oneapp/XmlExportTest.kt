package com.uip.oneapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uip.oneapp.data.local.AppDatabase
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.export.XmlExportService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XmlExportTest {

    private val TAG = "XmlExportTest"

    @Test
    fun testXmlExportWithRealData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.create(context)

        // Read real projects from DB
        val projects = db.projectDao().getAllProjects().first()
        Log.d(TAG, "Found ${projects.size} projects in database")

        if (projects.isEmpty()) {
            Log.w(TAG, "No projects found - testing with dummy data")
            testXmlExportWithDummyData()
            return@runBlocking
        }

        val project = projects.first()
        Log.d(TAG, "Using project: ${project.projectNumber} (id=${project.id})")

        val damages = db.damageDao().getByProjectId(project.id).first()
        val notes = db.noteDao().getByProjectId(project.id).first()
        Log.d(TAG, "Damages: ${damages.size}, Notes: ${notes.size}")

        val xmlService = XmlExportService(context)
        val xmlFile = xmlService.generateXml(project, damages, notes)

        assertTrue("XML file should exist", xmlFile.exists())
        assertTrue("XML file should not be empty", xmlFile.length() > 0)

        val content = xmlFile.readText()
        Log.d(TAG, "=== GENERATED XML (${xmlFile.length()} bytes) ===")
        Log.d(TAG, content)
        Log.d(TAG, "=== END XML ===")

        // Validate structure
        assertTrue("Should contain XML header", content.startsWith("<?xml"))
        assertTrue("Should contain <Inspection>", content.contains("<Inspection>"))
        assertTrue("Should contain <Header>", content.contains("<Header>"))
        assertTrue("Should contain <Project>", content.contains("<Project>"))
        assertTrue("Should contain <Pipe>", content.contains("<Pipe>"))
        assertTrue("Should contain Observations element", content.contains("Observations"))
        assertTrue("Should contain project number", content.contains(project.projectNumber))
        assertTrue("Should contain DIN standard", content.contains("DIN EN 13508-2"))

        if (damages.isNotEmpty()) {
            assertTrue("Should contain <Observation", content.contains("<Observation"))
            assertTrue("Should contain <Code>", content.contains("<Code>"))
        }

        Log.d(TAG, "XML export test PASSED - file: ${xmlFile.absolutePath}")

        // Also test with a project that has damages (if available)
        val projectWithDamages = projects.firstOrNull { proj ->
            runBlocking { db.damageDao().countByProjectId(proj.id) } > 0
        }
        if (projectWithDamages != null && projectWithDamages.id != project.id) {
            Log.d(TAG, "=== Testing with project that has damages: ${projectWithDamages.projectNumber} ===")
            val dmgs = db.damageDao().getByProjectId(projectWithDamages.id).first()
            val nts = db.noteDao().getByProjectId(projectWithDamages.id).first()
            Log.d(TAG, "Damages: ${dmgs.size}, Notes: ${nts.size}")

            val xmlFile2 = xmlService.generateXml(projectWithDamages, dmgs, nts)
            val content2 = xmlFile2.readText()
            Log.d(TAG, "=== XML WITH DAMAGES (${xmlFile2.length()} bytes) ===")
            Log.d(TAG, content2)
            Log.d(TAG, "=== END XML ===")

            assertTrue("Should contain <Observation", content2.contains("<Observation"))
            assertTrue("Should contain <Code>", content2.contains("<Code>"))
            Log.d(TAG, "XML with damages test PASSED")
        } else {
            Log.d(TAG, "No project with damages found - skipping damage XML test")
        }
    }

    private suspend fun testXmlExportWithDummyData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val project = ProjectEntity(
            id = 999,
            projectNumber = "TEST-001",
            auftraggeber = "Testfirma GmbH",
            standortAdresse = "Teststraße 1, 12345 Teststadt",
            inspektionsdatum = "2026-02-15",
            inspektor = "Test Inspektor",
            wetter = "Sonnig",
            leitungstyp = "Schmutzwasser",
            material = "Steinzeug",
            durchmesser = "300",
            inspektionslaenge = "45.50",
            startpunkt = "SK001",
            endpunkt = "SK002",
            kameratyp = "Push-Kamera"
        )

        val damages = listOf(
            DamageEntity(
                id = 1,
                projectId = 999,
                position = 12.45f,
                damageType = "BAB - Rissbildung",
                description = "Längsriss an der Sohle",
                photoPath = "",
                annotatedPhotoPath = "",
                createdAt = System.currentTimeMillis()
            ),
            DamageEntity(
                id = 2,
                projectId = 999,
                position = 23.80f,
                damageType = "BAF - Wurzeleinwuchs",
                description = "Wurzeleinwuchs durch Muffe",
                photoPath = "",
                annotatedPhotoPath = "",
                createdAt = System.currentTimeMillis()
            )
        )

        val notes = listOf(
            NoteEntity(
                id = 1,
                projectId = 999,
                position = 5.0f,
                text = "Einlaufschacht in gutem Zustand",
                audioPath = "",
                createdAt = System.currentTimeMillis()
            )
        )

        val xmlService = XmlExportService(context)
        val xmlFile = xmlService.generateXml(project, damages, notes)

        assertTrue("XML file should exist", xmlFile.exists())
        val content = xmlFile.readText()
        Log.d(TAG, "=== DUMMY XML (${xmlFile.length()} bytes) ===")
        Log.d(TAG, content)
        Log.d(TAG, "=== END XML ===")

        assertTrue("Should contain BAB", content.contains("BAB"))
        assertTrue("Should contain BAF", content.contains("BAF"))
        assertTrue("Should contain TEST-001", content.contains("TEST-001"))

        Log.d(TAG, "Dummy XML export test PASSED")
    }
}

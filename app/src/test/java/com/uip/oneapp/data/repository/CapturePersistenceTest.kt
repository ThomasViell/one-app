package com.uip.oneapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.data.local.AppDatabase
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.network.internal.CameraHead
import com.uip.oneapp.ui.screens.projects.cameraTypePrefill
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AP11 (Test-Lücken 1–2): Kern-Erfassungs-Workflow gegen eine echte (in-memory) Room-DB.
 *  - Schnellaufnahme-Bucket ist idempotent (genau ein Tages-Bucket).
 *  - Erfasste Schäden werden persistiert und projektbezogen wiedergefunden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CapturePersistenceTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()

    private val projectRepo = ProjectRepository(db.projectDao())
    private val damageRepo = DamageRepository(db.damageDao())

    @After
    fun tearDown() = db.close()

    @Test
    fun quickCaptureBucket_isIdempotent() = runBlocking {
        val first = projectRepo.getOrCreateQuickProjectId()
        val second = projectRepo.getOrCreateQuickProjectId()
        assertEquals("Zweiter Aufruf darf keinen neuen Bucket anlegen", first, second)
        val quickCount = db.projectDao().getAllProjects().first().count { it.status == "QUICK" }
        assertEquals("Genau ein Schnellaufnahme-Bucket", 1, quickCount)
    }

    /**
     * Louis 10-07 / M3 + M2: Das (lokalisierte) Label prägt das projectNumber-Präfix und der
     * beim Anlegen erkannte Kamerakopf landet in kameratyp. Idempotenz gilt pro Label.
     */
    @Test
    fun quickCaptureBucket_storesLabelPrefixAndCameraType() = runBlocking {
        val pid = projectRepo.getOrCreateQuickProjectId(label = "Quick capture", kameratyp = "C10")
        val proj = projectRepo.getProject(pid)!!
        assertTrue(
            "Bucket-Nummer trägt das Label als Präfix: ${proj.projectNumber}",
            proj.projectNumber.startsWith("Quick capture_")
        )
        assertEquals("Kameratyp beim Anlegen gesetzt", "C10", proj.kameratyp)

        val again = projectRepo.getOrCreateQuickProjectId(label = "Quick capture", kameratyp = "C10")
        assertEquals("Gleiches Label am selben Tag → derselbe Bucket", pid, again)
    }

    /**
     * M2-Backfill: Kameratyp wird nachgetragen wenn das Feld leer war.
     * Simuliert den LaunchedEffect in InspectionScreen: getProject → cameraTypePrefill-Gate → updateProject.
     */
    @Test
    fun backfill_setzeKameratypWennFeldLeer() = runBlocking {
        val pid = projectRepo.getOrCreateQuickProjectId(kameratyp = "")
        val proj = projectRepo.getProject(pid)!!
        assertEquals("Ausgangszustand leer", "", proj.kameratyp)

        val label = cameraTypePrefill(CameraHead.C18, proj.kameratyp, "C10", "C18")
        label?.let { projectRepo.updateProject(proj.copy(kameratyp = it)) }

        assertEquals("C18", projectRepo.getProject(pid)!!.kameratyp)
    }

    @Test
    fun backfill_ueberschreibtNichtWennFeldBelegt() = runBlocking {
        val pid = projectRepo.getOrCreateQuickProjectId(kameratyp = "C10")
        val proj = projectRepo.getProject(pid)!!
        assertEquals("C10", proj.kameratyp)

        // cameraTypePrefill liefert null wenn Feld belegt → kein updateProject
        val label = cameraTypePrefill(CameraHead.C18, proj.kameratyp, "C10", "C18")
        label?.let { projectRepo.updateProject(proj.copy(kameratyp = it)) }

        assertEquals("Manueller Wert bleibt erhalten", "C10", projectRepo.getProject(pid)!!.kameratyp)
    }

    @Test
    fun savedDamage_isFoundForItsProject() = runBlocking {
        val pid = projectRepo.getOrCreateQuickProjectId()
        damageRepo.saveDamage(
            DamageEntity(projectId = pid, position = 1.5f, damageType = "Foto", photoPath = "/x.jpg")
        )
        val list = damageRepo.getDamagesForProject(pid).first()
        assertEquals(1, list.size)
        assertEquals("Foto", list.first().damageType)
        assertEquals(1.5f, list.first().position, 0.001f)
    }
}

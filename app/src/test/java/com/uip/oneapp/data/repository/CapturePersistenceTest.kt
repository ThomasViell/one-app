package com.uip.oneapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.data.local.AppDatabase
import com.uip.oneapp.data.local.entity.DamageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

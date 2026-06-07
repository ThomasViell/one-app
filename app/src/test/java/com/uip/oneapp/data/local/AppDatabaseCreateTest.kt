package com.uip.oneapp.data.local

import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.data.local.entity.ProjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AP7 (M4): Nach Entfernen von fallbackToDestructiveMigration muss eine frische Installation die
 * DB weiterhin sauber anlegen (aktuell v9 — DIN/XML-Ausbau 2026-06-07) und persistieren.
 * Sichert ab, dass das Entfernen des destruktiven Fallbacks die Neuanlage nicht bricht.
 *
 * Echte Migrationstests (3→9) laufen instrumentiert (Room MigrationTestHelper, androidTest) und
 * brauchen historische Schema-JSONs — siehe RESULT_BETA_WAVE_1.md. Der Schema-Export
 * (exportSchema=true) macht ab jetzt jede künftige Migration build-seitig erzwungen + testbar.
 */
// Stub-Application statt OneApp — vermeidet WorkManager/Koin/native-Init in der JVM-Testumgebung.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AppDatabaseCreateTest {

    private val db = AppDatabase.create(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = db.close()

    @Test
    fun freshDatabase_createsCurrentVersionAndPersists_withoutDestructiveFallback() = runBlocking {
        val dao = db.projectDao()
        val id = dao.insert(ProjectEntity(projectNumber = "WAVE1-T"))
        val loaded = dao.getById(id)
        assertNotNull("Projekt muss nach Neuanlage gefunden werden", loaded)
        assertEquals("WAVE1-T", loaded!!.projectNumber)
    }
}

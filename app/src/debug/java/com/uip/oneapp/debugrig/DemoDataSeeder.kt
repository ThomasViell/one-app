package com.uip.oneapp.debugrig

import android.content.Context
import android.util.Log
import com.uip.oneapp.data.local.dao.DamageDao
import com.uip.oneapp.data.local.dao.NoteDao
import com.uip.oneapp.data.local.dao.ProjectDao
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity

private const val TAG = "DemoDataSeeder"

/** Fixed project number for the demo — stable across runs so old project can be purged. */
const val DEMO_PROJECT_NUMBER = "DEMO_160726_0900_01"

/**
 * Pure seed data descriptor — used by unit tests to verify determinism without Room.
 */
data class SeedData(
    val project: SeedProject,
    val damages: List<SeedDamage>,
    val notes: List<SeedNote>
)

data class SeedProject(
    val projectNumber: String,
    val auftraggeber: String,
    val standortAdresse: String,
    val inspektor: String,
    val kameratyp: String,
    val material: String,
    val durchmesser: String,
    val leitungstyp: String,
    val inspektionsdatum: String,
    val inspektionslaenge: String
)

data class SeedDamage(val position: Float, val damageType: String, val description: String)
data class SeedNote(val position: Float, val text: String)

/**
 * Builds the deterministic demo data descriptor.  Pure function — no side effects.
 * Call twice, get identical results.
 */
fun buildSeedData(): SeedData = SeedData(
    project = SeedProject(
        projectNumber = DEMO_PROJECT_NUMBER,
        auftraggeber = "Musterstadt Stadtentwässerung",
        standortAdresse = "Hauptstraße 12, Musterstadt",
        inspektor = "M. Muster",
        kameratyp = "C18",
        material = "Steinzeug",
        durchmesser = "200",
        leitungstyp = "Schmutzwasser",
        inspektionsdatum = "16.07.2026",
        inspektionslaenge = "85.00"
    ),
    damages = listOf(
        SeedDamage(12.5f, "Riss", "Längsriss, Breite ca. 2 mm"),
        SeedDamage(34.0f, "Wurzeleinwuchs", "Feinwurzeln im Scheitel"),
        SeedDamage(67.3f, "Scherbenbildung", "Mehrere Scherben, Bereich 8–12 Uhr")
    ),
    notes = listOf(
        SeedNote(0.0f, "Demo-Notiz: Anfang der Haltung geprüft, Einlauf sauber")
    )
)

/**
 * Seeds the database with deterministic demo data.  Idempotent: always purges the previous
 * demo project (by [DEMO_PROJECT_NUMBER]) before inserting fresh rows.
 *
 * Returns the new demo project id so callers can navigate to inspection/{id}.
 */
object DemoDataSeeder {

    suspend fun seed(
        context: Context,
        projectDao: ProjectDao,
        damageDao: DamageDao,
        noteDao: NoteDao
    ): Long {
        purge(context, projectDao)

        val sd = buildSeedData()
        val sp = sd.project

        val projectId = projectDao.insert(
            ProjectEntity(
                projectNumber = sp.projectNumber,
                auftraggeber = sp.auftraggeber,
                standortAdresse = sp.standortAdresse,
                inspektor = sp.inspektor,
                kameratyp = sp.kameratyp,
                material = sp.material,
                durchmesser = sp.durchmesser,
                leitungstyp = sp.leitungstyp,
                inspektionsdatum = sp.inspektionsdatum,
                inspektionslaenge = sp.inspektionslaenge,
                formVideo = true,
                formFoto = true,
                formVisuell = false,
                videoQuality = "HD",
                videoOverlay = true,
                status = "OPEN"
            )
        )

        for (d in sd.damages) {
            damageDao.insert(
                DamageEntity(
                    projectId = projectId,
                    position = d.position,
                    damageType = d.damageType,
                    description = d.description
                )
            )
        }

        for (n in sd.notes) {
            noteDao.insert(
                NoteEntity(
                    projectId = projectId,
                    position = n.position,
                    text = n.text
                )
            )
        }

        Log.i(TAG, "Demo seed complete: projectId=$projectId")
        return projectId
    }

    private suspend fun purge(context: Context, projectDao: ProjectDao) {
        val existing = projectDao.getByProjectNumber(DEMO_PROJECT_NUMBER) ?: return
        projectDao.delete(existing)
        Log.i(TAG, "Purged old demo project id=${existing.id}")
    }
}

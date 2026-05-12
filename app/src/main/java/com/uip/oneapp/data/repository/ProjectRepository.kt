package com.uip.oneapp.data.repository

import android.content.Context
import android.util.Log
import com.uip.oneapp.data.local.dao.ProjectDao
import com.uip.oneapp.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ProjectRepository(private val dao: ProjectDao) {

    fun getAllProjects(): Flow<List<ProjectEntity>> = dao.getAllProjects()

    fun getProjectFlow(id: Long): Flow<ProjectEntity?> = dao.getByIdFlow(id)

    suspend fun getProject(id: Long): ProjectEntity? = dao.getById(id)

    suspend fun saveProject(project: ProjectEntity): Long {
        val projectNumber = generateProjectNumber(project.inspektionsdatum)
        return dao.insert(project.copy(projectNumber = projectNumber))
    }

    suspend fun updateProject(project: ProjectEntity) = dao.update(project)

    suspend fun deleteProject(project: ProjectEntity) = dao.delete(project)

    /**
     * Hard-deletes the project, every linked DB row (damages, notes, inspections,
     * pipes — Room CASCADE handles these via the foreign keys) AND every file
     * on disk that belongs to this project. Irreversible.
     *
     * Files cleaned per project id:
     *   damages/project_$id/    — captured + annotated photos
     *   recordings/project_$id/ — MP4 videos
     *   notes/project_$id/      — audio notes
     *   reports/project_$id/    — generated PDF reports
     *
     * Files cleaned per project number:
     *   exports/Projekt_<projectNumber>.zip
     *   exports/Bericht_<projectNumber>.pdf  (legacy paths from older builds)
     *   exports/xml/Inspektion_<projectNumber>.xml
     *
     * @return summary tuple (bytes freed, files removed) for the report toast
     */
    suspend fun deleteProjectCompletely(
        project: ProjectEntity,
        context: Context
    ): DeleteSummary {
        var bytes = 0L
        var files = 0

        // Per-id directories
        val idDirs = listOf("damages", "recordings", "notes", "reports")
        for (sub in idDirs) {
            val dir = File(context.getExternalFilesDir(sub), "project_${project.id}")
            val (b, f) = deleteRecursively(dir)
            bytes += b
            files += f
        }

        // Per-projectNumber export artifacts
        val exportsDir = context.getExternalFilesDir("exports")
        if (exportsDir != null && project.projectNumber.isNotEmpty()) {
            val patterns = listOf(
                "Projekt_${project.projectNumber}.zip",
                "Bericht_${project.projectNumber}.pdf",
                "Bericht_${project.projectNumber}.zip"
            )
            for (name in patterns) {
                val f = File(exportsDir, name)
                if (f.exists() && f.isFile) {
                    bytes += f.length()
                    if (f.delete()) files += 1
                }
            }
            val xmlFile = File(File(exportsDir, "xml"), "Inspektion_${project.projectNumber}.xml")
            if (xmlFile.exists() && xmlFile.isFile) {
                bytes += xmlFile.length()
                if (xmlFile.delete()) files += 1
            }
        }

        // DB row last — Room CASCADE handles damages / notes / inspections / pipes
        dao.delete(project)
        Log.i("ProjectRepository",
            "Deleted project id=${project.id} (${project.projectNumber}): " +
            "$files files / ${bytes / 1024} KB freed")
        return DeleteSummary(bytesFreed = bytes, filesRemoved = files)
    }

    private fun deleteRecursively(target: File): Pair<Long, Int> {
        if (!target.exists()) return 0L to 0
        var bytes = 0L
        var count = 0
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                val (b, c) = deleteRecursively(child)
                bytes += b
                count += c
            }
            target.delete()
        } else {
            bytes = target.length()
            if (target.delete()) count = 1
        }
        return bytes to count
    }

    data class DeleteSummary(val bytesFreed: Long, val filesRemoved: Int)

    private suspend fun generateProjectNumber(dateStr: String): String {
        val now = LocalDate.now()
        val time = LocalTime.now()
        val datePart = now.format(DateTimeFormatter.ofPattern("ddMMyy"))
        val timePart = time.format(DateTimeFormatter.ofPattern("HHmm"))
        val count = dao.countProjectsOnDate(dateStr) + 1
        val seq = String.format("%03d", count)
        return "${datePart}_${timePart}_$seq"
    }
}

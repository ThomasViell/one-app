package com.uip.oneapp.data.repository

import com.uip.oneapp.data.local.dao.ProjectDao
import com.uip.oneapp.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
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

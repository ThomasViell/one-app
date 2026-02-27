package com.uip.oneapp.data.local.dao

import androidx.room.*
import com.uip.oneapp.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<ProjectEntity?>

    @Query("SELECT COUNT(*) FROM projects WHERE inspektionsdatum = :dateStr")
    suspend fun countProjectsOnDate(dateStr: String): Int
}

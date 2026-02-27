package com.uip.oneapp.data.local.dao

import androidx.room.*
import com.uip.oneapp.data.local.entity.PipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipeDao {

    @Query("SELECT * FROM pipes WHERE projectId = :projectId ORDER BY designation ASC")
    fun getByProjectId(projectId: Long): Flow<List<PipeEntity>>

    @Query("SELECT * FROM pipes WHERE id = :id")
    suspend fun getById(id: Long): PipeEntity?

    @Query("SELECT * FROM pipes WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<PipeEntity?>

    @Query("SELECT COUNT(*) FROM pipes WHERE projectId = :projectId")
    suspend fun countByProject(projectId: Long): Int

    @Insert
    suspend fun insert(pipe: PipeEntity): Long

    @Update
    suspend fun update(pipe: PipeEntity)

    @Delete
    suspend fun delete(pipe: PipeEntity)
}

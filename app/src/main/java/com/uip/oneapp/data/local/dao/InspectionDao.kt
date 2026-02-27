package com.uip.oneapp.data.local.dao

import androidx.room.*
import com.uip.oneapp.data.local.entity.InspectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {

    @Query("SELECT * FROM inspections WHERE pipeId = :pipeId ORDER BY createdAt DESC")
    fun getByPipeId(pipeId: Long): Flow<List<InspectionEntity>>

    @Query("""
        SELECT i.* FROM inspections i
        JOIN pipes p ON i.pipeId = p.id
        WHERE p.projectId = :projectId
        ORDER BY i.createdAt DESC
    """)
    fun getByProjectId(projectId: Long): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: Long): InspectionEntity?

    @Query("SELECT * FROM inspections WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<InspectionEntity?>

    @Insert
    suspend fun insert(inspection: InspectionEntity): Long

    @Update
    suspend fun update(inspection: InspectionEntity)

    @Delete
    suspend fun delete(inspection: InspectionEntity)
}

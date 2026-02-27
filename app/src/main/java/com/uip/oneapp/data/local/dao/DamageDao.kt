package com.uip.oneapp.data.local.dao

import androidx.room.*
import com.uip.oneapp.data.local.entity.DamageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DamageDao {
    @Insert
    suspend fun insert(damage: DamageEntity): Long

    @Update
    suspend fun update(damage: DamageEntity)

    @Delete
    suspend fun delete(damage: DamageEntity)

    @Query("SELECT * FROM damages WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getByProjectId(projectId: Long): Flow<List<DamageEntity>>

    @Query("SELECT COUNT(*) FROM damages WHERE projectId = :projectId")
    suspend fun countByProjectId(projectId: Long): Int

    @Query("SELECT * FROM damages WHERE inspectionId = :inspectionId ORDER BY position ASC")
    fun getByInspectionId(inspectionId: Long): Flow<List<DamageEntity>>

    @Query("SELECT * FROM damages WHERE id = :id")
    suspend fun getById(id: Long): DamageEntity?

    @Query("""
        SELECT damageClass, COUNT(*) as count
        FROM damages
        WHERE inspectionId = :inspectionId AND damageClass IS NOT NULL
        GROUP BY damageClass
    """)
    suspend fun getDamageClassCounts(inspectionId: Long): List<DamageClassCount>
}

data class DamageClassCount(
    val damageClass: Int?,
    val count: Int
)

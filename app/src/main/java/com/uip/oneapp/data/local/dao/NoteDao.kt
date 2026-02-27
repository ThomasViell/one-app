package com.uip.oneapp.data.local.dao

import androidx.room.*
import com.uip.oneapp.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getByProjectId(projectId: Long): Flow<List<NoteEntity>>
}

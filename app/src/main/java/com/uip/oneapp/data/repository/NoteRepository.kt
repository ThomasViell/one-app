package com.uip.oneapp.data.repository

import com.uip.oneapp.data.local.dao.NoteDao
import com.uip.oneapp.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    suspend fun saveNote(note: NoteEntity): Long = dao.insert(note)

    suspend fun updateNote(note: NoteEntity) = dao.update(note)

    fun getNotesForProject(projectId: Long): Flow<List<NoteEntity>> =
        dao.getByProjectId(projectId)

    suspend fun deleteNote(note: NoteEntity) = dao.delete(note)
}

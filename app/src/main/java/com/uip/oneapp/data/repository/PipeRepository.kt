package com.uip.oneapp.data.repository

import com.uip.oneapp.data.local.dao.PipeDao
import com.uip.oneapp.data.local.entity.PipeEntity
import kotlinx.coroutines.flow.Flow

class PipeRepository(private val pipeDao: PipeDao) {

    fun getByProjectId(projectId: Long): Flow<List<PipeEntity>> =
        pipeDao.getByProjectId(projectId)

    fun getByIdFlow(id: Long): Flow<PipeEntity?> =
        pipeDao.getByIdFlow(id)

    suspend fun getById(id: Long): PipeEntity? =
        pipeDao.getById(id)

    suspend fun insert(pipe: PipeEntity): Long =
        pipeDao.insert(pipe)

    suspend fun update(pipe: PipeEntity) =
        pipeDao.update(pipe)

    suspend fun delete(pipe: PipeEntity) =
        pipeDao.delete(pipe)

    suspend fun countByProject(projectId: Long): Int =
        pipeDao.countByProject(projectId)
}

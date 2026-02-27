package com.uip.oneapp.data.repository

import com.uip.oneapp.data.local.dao.InspectionDao
import com.uip.oneapp.data.local.entity.InspectionEntity
import kotlinx.coroutines.flow.Flow

class InspectionRepository(private val inspectionDao: InspectionDao) {

    fun getByPipeId(pipeId: Long): Flow<List<InspectionEntity>> =
        inspectionDao.getByPipeId(pipeId)

    fun getByProjectId(projectId: Long): Flow<List<InspectionEntity>> =
        inspectionDao.getByProjectId(projectId)

    fun getByIdFlow(id: Long): Flow<InspectionEntity?> =
        inspectionDao.getByIdFlow(id)

    suspend fun getById(id: Long): InspectionEntity? =
        inspectionDao.getById(id)

    suspend fun insert(inspection: InspectionEntity): Long =
        inspectionDao.insert(inspection)

    suspend fun update(inspection: InspectionEntity) =
        inspectionDao.update(inspection)

    suspend fun delete(inspection: InspectionEntity) =
        inspectionDao.delete(inspection)
}

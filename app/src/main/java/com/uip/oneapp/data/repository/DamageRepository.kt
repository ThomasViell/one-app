package com.uip.oneapp.data.repository

import com.uip.oneapp.data.local.dao.DamageDao
import com.uip.oneapp.data.local.entity.DamageEntity
import kotlinx.coroutines.flow.Flow

class DamageRepository(private val dao: DamageDao) {

    suspend fun saveDamage(damage: DamageEntity): Long = dao.insert(damage)

    suspend fun updateDamage(damage: DamageEntity) = dao.update(damage)

    fun getDamagesForProject(projectId: Long): Flow<List<DamageEntity>> =
        dao.getByProjectId(projectId)

    suspend fun deleteDamage(damage: DamageEntity) = dao.delete(damage)
}

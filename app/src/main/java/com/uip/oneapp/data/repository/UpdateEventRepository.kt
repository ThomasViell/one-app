package com.uip.oneapp.data.repository

import com.uip.oneapp.data.local.dao.UpdateEventDao
import com.uip.oneapp.data.local.entity.UpdateEventEntity
import com.uip.oneapp.data.local.entity.UpdateEventType
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

open class UpdateEventRepository(private val dao: UpdateEventDao?) {

    fun getAllFlow(): Flow<List<UpdateEventEntity>> = dao!!.getAllFlow()

    suspend fun getRecent(limit: Int = 50): List<UpdateEventEntity> = dao!!.getRecent(limit)

    open suspend fun log(
        type: UpdateEventType,
        fromVersion: String? = null,
        toVersion: String? = null,
        source: String? = null,
        errorMessage: String? = null
    ): Long {
        dao ?: return -1L
        return dao.insert(
            UpdateEventEntity(
                eventType = type.name,
                fromVersion = fromVersion,
                toVersion = toVersion,
                source = source,
                errorMessage = errorMessage
            )
        )
    }

    open suspend fun pruneOldEvents() {
        dao ?: return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        dao.deleteOlderThan(cutoff)
    }
}

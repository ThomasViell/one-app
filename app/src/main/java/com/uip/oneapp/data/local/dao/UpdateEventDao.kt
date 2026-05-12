package com.uip.oneapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uip.oneapp.data.local.entity.UpdateEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateEventDao {

    @Insert
    suspend fun insert(event: UpdateEventEntity): Long

    @Query("SELECT * FROM update_events ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<UpdateEventEntity>>

    @Query("SELECT * FROM update_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<UpdateEventEntity>

    // Retention: keep last 90 days of events to satisfy KRITIS audit requirement
    @Query(
        "DELETE FROM update_events WHERE timestamp < :cutoffMs"
    )
    suspend fun deleteOlderThan(cutoffMs: Long)
}

package com.uip.oneapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UpdateEventType {
    CHECK,
    DOWNLOAD_START,
    DOWNLOAD_OK,
    DOWNLOAD_FAIL,
    INSTALL_INITIATED,
    INSTALL_DONE
}

@Entity(tableName = "update_events")
data class UpdateEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,           // UpdateEventType.name
    val fromVersion: String? = null, // installed version at check time
    val toVersion: String? = null,   // manifest version being acted on
    val source: String? = null,      // manifest URL (no tokens — proxy URL only)
    val errorMessage: String? = null // null on success
)

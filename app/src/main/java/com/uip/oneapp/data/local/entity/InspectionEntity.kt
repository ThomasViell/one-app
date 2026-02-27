package com.uip.oneapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspections",
    foreignKeys = [ForeignKey(
        entity = PipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["pipeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("pipeId")]
)
data class InspectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pipeId: Long,

    // Inspektionsdaten
    val inspectionDate: String = "",
    val inspectorName: String = "",
    val weatherCondition: String = "",

    // Inspektionsrichtung
    val direction: String = "DOWNSTREAM",   // DOWNSTREAM (Stromab), UPSTREAM (Stromauf)
    val method: String = "VIDEO",           // VIDEO, VISUAL, PHOTO

    // Meterdaten
    val startMeter: Float = 0f,
    val endMeter: Float = 0f,

    // Video
    val videoFileName: String = "",
    val videoQuality: String = "HD",
    val videoOverlay: Boolean = true,

    // Kamera
    val cameraType: String = "",
    val cameraSerialNumber: String = "",

    // Status
    val status: String = "IN_PROGRESS",     // IN_PROGRESS, COMPLETED, REVIEWED
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

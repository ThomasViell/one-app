package com.uip.oneapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schadenseintrag der ONE — bewusst schlank (CEO-Beschluss 2026-06-07):
 * Die Erfassung läuft ausschließlich über die hinterlegten, erweiterbaren
 * Schadensbezeichnungen (Presets) + Position + Freitext. Eine Kodierung nach
 * DIN EN 13508-2 / DWA-M 149-2 ist in der ONE nicht vorgesehen (das leisten
 * DrainQ.SA / HMX); die früheren DIN-Felder und die tote pipes/inspections-
 * Hierarchie wurden mit Migration 8→9 entfernt.
 */
@Entity(
    tableName = "damages",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class DamageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,

    // === POSITION ===
    val position: Float,
    val positionEnd: Float? = null,

    // === ERFASSUNG (Preset-Bezeichnung + Freitext) ===
    val damageType: String = "",
    val description: String = "",

    // === MEDIA ===
    val photoPath: String = "",
    val annotatedPhotoPath: String = "",
    val videoTimestamp: Long? = null,

    // === META ===
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

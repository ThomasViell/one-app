package com.uip.oneapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pipes",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class PipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,

    // Haltungsbezeichnung
    val designation: String = "",           // z.B. "KS001-KS002"
    val startNode: String = "",             // Anfangsschacht
    val endNode: String = "",               // Endschacht
    val streetName: String = "",

    // Rohrdaten
    val material: String = "",              // B, GGG, PVC, SPB, STZ, etc.
    val profile: String = "",               // KR (Kreisrund), EI (Eiprofil), MA (Maulprofil)
    val nominalWidth: Int? = null,          // DN (Nennweite in mm)
    val height: Int? = null,                // Profilhoehe (bei Nicht-Kreisprofilen)
    val length: Float? = null,              // Laenge in Metern
    val constructionYear: Int? = null,
    val rehabilitationYear: Int? = null,

    // Lage
    val usage: String = "",                 // SW (Schmutzwasser), RW (Regenwasser), MW (Mischwasser)
    val drainageArea: String = "",          // Einzugsgebiet

    // Status
    val status: String = "PENDING",         // PENDING, IN_PROGRESS, COMPLETED
    val createdAt: Long = System.currentTimeMillis()
)

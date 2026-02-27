package com.uip.oneapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "damages",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId"), Index("inspectionId")]
)
data class DamageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val inspectionId: Long? = null,

    // === POSITION ===
    val position: Float,
    val positionEnd: Float? = null,

    // === LEGACY ONE.APP FELD (fuer bestehende Screens) ===
    val damageType: String = "",

    // === DIN EN 13508-2 CODES ===
    val mainCode: String = "",
    val mainCodeName: String = "",
    val characterization1: String? = null,
    val char1Name: String? = null,
    val characterization2: String? = null,
    val char2Name: String? = null,
    val quantification1: String? = null,
    val quant1Name: String? = null,
    val quantification2: String? = null,
    val quant2Name: String? = null,

    // === POSITION AM ROHR ===
    val clockPositionStart: String? = null,
    val clockPositionEnd: String? = null,
    val jointNumber: Int? = null,
    val continuous: Boolean = false,

    // === KLASSIFIZIERUNG ===
    val damageClass: Int? = null,           // Zustandsklasse 0-4 (DWA-M 149-2)

    // === MEDIA ===
    val description: String = "",
    val photoPath: String = "",
    val annotatedPhotoPath: String = "",
    val videoTimestamp: Long? = null,

    // === LEGACY KOMPATIBILITAET ===
    val legacyDamageType: String? = null,

    // === META ===
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val fullDinCode: String
        get() = buildString {
            if (mainCode.isNotEmpty()) {
                append(mainCode)
                characterization1?.let { append(" $it") }
                characterization2?.let { append(" $it") }
                quantification1?.let { append(" $it") }
                quantification2?.let { append(" $it") }
            }
        }

    val readableDescription: String
        get() = buildString {
            if (mainCode.isNotEmpty()) {
                append("$mainCode - $mainCodeName")
                val details = listOfNotNull(char1Name, char2Name, quant1Name, quant2Name)
                if (details.isNotEmpty()) {
                    append(" (${details.joinToString(", ")})")
                }
            }
        }
}

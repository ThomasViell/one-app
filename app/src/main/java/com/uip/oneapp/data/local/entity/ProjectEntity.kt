package com.uip.oneapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectNumber: String = "",
    // Allgemeine Angaben
    val auftraggeber: String = "",
    val standortAdresse: String = "",
    val inspektionsdatum: String = "",
    val inspektor: String = "",
    val wetter: String = "",
    // Leitungsdaten
    val leitungstyp: String = "",
    val material: String = "",
    val durchmesser: String = "",
    val inspektionslaenge: String = "",
    val startpunkt: String = "",
    val endpunkt: String = "",
    // Inspektionsmethode
    val kameratyp: String = "",
    val formVisuell: Boolean = false,
    val formVideo: Boolean = true,
    val formFoto: Boolean = true,
    // Video-Einstellungen (nach Anlage nicht änderbar)
    val videoQuality: String = "HD",       // "SD" oder "HD"
    val videoOverlay: Boolean = true,      // Projektdaten ins Video einblenden
    // Meta
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "OPEN"
)

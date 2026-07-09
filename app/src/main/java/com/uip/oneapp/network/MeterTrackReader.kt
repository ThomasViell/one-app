package com.uip.oneapp.network

import java.io.File

/**
 * Liest die Meter-Sidecar-Datei eines Videos zurück.
 * Gibt eine leere Liste zurück wenn keine Sidecar existiert (Altaufnahmen → Stufe-1-Fallback).
 */
object MeterTrackReader {

    fun read(videoFile: File): List<MeterSample> {
        val sidecar = File(videoFile.absolutePath + METER_SIDECAR_SUFFIX)
        if (!sidecar.exists()) return emptyList()
        return try {
            sidecar.useLines { lines ->
                lines.mapNotNull { parseLine(it) }
                    .sortedBy { it.positionMs }
                    .toList()
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseLine(line: String): MeterSample? = try {
        val t = Regex(""""t":(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()
        val m = Regex(""""m":([+-]?[\d.]+(?:[Ee][+-]?\d+)?)""")
            .find(line)?.groupValues?.get(1)?.toFloatOrNull()
        if (t != null && m != null) MeterSample(t, m) else null
    } catch (_: Exception) { null }
}

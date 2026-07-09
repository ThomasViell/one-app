package com.uip.oneapp.network

import java.io.File

/**
 * Liest die Meter-Sidecar eines Videos als **v3**-Spur (Zeitachse) zurück.
 *
 * Gibt [MeterTrackV3.EMPTY] zurück, wenn:
 *  - keine Sidecar existiert (Altaufnahmen),
 *  - die Kopfzeile fehlt oder `v != 3` ist (v1/v2 werden bewusst NIE gelesen — ein falscher
 *    Wert ist schlimmer als kein Wert),
 *  - keine gültigen Samples vorhanden sind.
 * → In allen Fällen fällt die Wiedergabe auf das leere Pflichtfeld (Stufe 1) zurück.
 */
object MeterTrackReaderV3 {

    fun read(videoFile: File): MeterTrackV3 {
        val sidecar = File(videoFile.absolutePath + METER_SIDECAR_SUFFIX)
        if (!sidecar.exists()) return MeterTrackV3.EMPTY
        return try {
            sidecar.useLines { lines -> parseLines(lines) }
        } catch (_: Exception) {
            MeterTrackV3.EMPTY
        }
    }

    /** Reine Parse-Logik (testbar ohne Datei). Erste Zeile = Kopf, Rest = Samples. */
    internal fun parseLines(lines: Sequence<String>): MeterTrackV3 {
        val it = lines.iterator()
        if (!it.hasNext()) return MeterTrackV3.EMPTY

        val header = it.next()
        val version = Regex("\"v\":(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
        if (version != METER_SIDECAR_VERSION_V3) return MeterTrackV3.EMPTY

        val tRegex = Regex("\"tUs\":(\\d+)")
        val mRegex = Regex("\"m\":([+-]?[\\d.]+(?:[Ee][+-]?\\d+)?)")
        val samples = ArrayList<MeterSampleV3>()
        while (it.hasNext()) {
            val line = it.next()
            val t = tRegex.find(line)?.groupValues?.get(1)?.toLongOrNull()
            val m = mRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()
            if (t != null && m != null) samples.add(MeterSampleV3(t, m))
        }
        if (samples.isEmpty()) return MeterTrackV3.EMPTY
        samples.sortBy { it.tUs }
        return MeterTrackV3(samples)
    }
}

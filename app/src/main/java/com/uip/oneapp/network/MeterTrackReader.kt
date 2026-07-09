package com.uip.oneapp.network

import java.io.File

/**
 * Liest die Meter-Sidecar-Datei eines Videos zurück.
 *
 * Gibt [MeterTrack.EMPTY] zurück, wenn:
 *  - keine Sidecar existiert (Altaufnahmen),
 *  - die Kopfzeile fehlt oder `v != 2` ist (v1 war Wall-Clock → falsche Zeitbasis;
 *    ein falscher Wert ist schlimmer als kein Wert → NIE lesen),
 *  - die fps ungültig ist.
 * → In allen Fällen fällt die Wiedergabe auf das leere Pflichtfeld (Stufe 1) zurück.
 */
object MeterTrackReader {

    fun read(videoFile: File): MeterTrack {
        val sidecar = File(videoFile.absolutePath + METER_SIDECAR_SUFFIX)
        if (!sidecar.exists()) return MeterTrack.EMPTY
        return try {
            sidecar.useLines { lines -> parseLines(lines) }
        } catch (_: Exception) {
            MeterTrack.EMPTY
        }
    }

    /** Reine Parse-Logik (testbar ohne Datei). Erste Zeile = Kopf, Rest = Samples. */
    internal fun parseLines(lines: Sequence<String>): MeterTrack {
        val it = lines.iterator()
        if (!it.hasNext()) return MeterTrack.EMPTY

        val header = it.next()
        val version = Regex("\"v\":(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
        if (version != METER_SIDECAR_VERSION) return MeterTrack.EMPTY
        val fps = Regex("\"fps\":(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
        if (fps == null || fps <= 0) return MeterTrack.EMPTY

        val fRegex = Regex("\"f\":(\\d+)")
        val mRegex = Regex("\"m\":([+-]?[\\d.]+(?:[Ee][+-]?\\d+)?)")
        val samples = ArrayList<MeterSample>()
        while (it.hasNext()) {
            val line = it.next()
            val f = fRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            val m = mRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()
            if (f != null && m != null) samples.add(MeterSample(f, m))
        }
        if (samples.isEmpty()) return MeterTrack.EMPTY
        samples.sortBy { it.frameIndex }
        return MeterTrack(fps, samples)
    }
}

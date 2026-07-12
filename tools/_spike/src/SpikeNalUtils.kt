package com.uip.oneapp.spike

/**
 * SPIKE (W3c) — Wegwerf-Code. NICHT für Produktion. Nur auf spike/video-rtsp.
 *
 * Hilfsfunktionen für Annex-B-H.264-Bytestrom (Startcode-getrennte NAL-Units).
 */

/** Zerlegt einen Annex-B-Bytestrom in einzelne NAL-Units OHNE Startcodes. */
internal fun splitAnnexB(data: ByteArray, length: Int = data.size): List<ByteArray> {
    val result = ArrayList<ByteArray>(4)
    var i = 0
    var nalStart = -1
    while (i + 3 <= length) {
        val z2 = data[i].toInt() == 0 && data[i + 1].toInt() == 0
        if (z2 && data[i + 2].toInt() == 1) {
            // 3-Byte-Startcode 00 00 01
            if (nalStart in 0 until i) result.add(data.copyOfRange(nalStart, i))
            i += 3
            nalStart = i
        } else if (z2 && i + 3 < length && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
            // 4-Byte-Startcode 00 00 00 01
            if (nalStart in 0 until i) result.add(data.copyOfRange(nalStart, i))
            i += 4
            nalStart = i
        } else {
            i++
        }
    }
    if (nalStart in 0 until length) result.add(data.copyOfRange(nalStart, length))
    return result
}

/** Sucht SPS (NAL-Typ 7) und PPS (NAL-Typ 8) in einem csd/Annex-B-Puffer. */
internal fun findSpsPps(csd: ByteArray): Pair<ByteArray, ByteArray>? {
    var sps: ByteArray? = null
    var pps: ByteArray? = null
    for (nal in splitAnnexB(csd)) {
        if (nal.isEmpty()) continue
        when (nal[0].toInt() and 0x1F) {
            7 -> sps = nal
            8 -> pps = nal
        }
    }
    val s = sps
    val p = pps
    return if (s != null && p != null) Pair(s, p) else null
}

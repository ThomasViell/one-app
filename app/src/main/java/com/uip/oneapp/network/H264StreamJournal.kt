package com.uip.oneapp.network

import android.util.Log
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * **Absturzsicheres H.264-Journal (Welle 5).** Der HW-Encoder-Aufnahmeweg schreibt jede fertige
 * Access-Unit **sofort append-only** in ein Journal `<video>.h264j`. Ein Prozess-Kill hinterlässt
 * damit einen gültigen Präfix; beim Stopp wird das Journal verlustfrei nach MP4 gemuxt
 * (`RecorderJournalMuxer`), beim nächsten App-Start werden verwaiste Journale auto-recovered.
 *
 * Grund für die Eigenkonstruktion statt `MediaMuxer`-live: `MediaMuxer` schreibt den `moov`-Index
 * erst bei `stop()` → ein Kill davor macht die Datei unspielbar (Rückschritt gegenüber Welle 2).
 * Grund gegen den ffmpeg-Fragment-Weg (Welle 2): `-c copy` eines rohen Annex-B-Stroms kann die
 * variablen, echten VFR-PTS nicht tragen → CFR → Zeitraffer zurück.
 *
 * **Framing (Big-Endian), self-framing für positive Torn-Tail-Erkennung:**
 * ```
 * Kopf:   u32 JOURNAL_MAGIC | u16 version | u16 reserved | u32 width | u32 height
 *         u32 spsLen | sps… | u32 ppsLen | pps…
 * Record: u32 RECORD_MAGIC | u32 auLen | u64 ptsUs | u8 keyframe | auLen Bytes Annex-B
 * ```
 * Ein torn tail (halber letzter Record nach abruptem Kill) wird an fehlendem `RECORD_MAGIC` /
 * zu kurzem Rest erkannt und verworfen; alles davor muxt sauber.
 */

/** Datei-Suffix des absturzsicheren H.264-Journals (roher Annex-B-Elementarstrom, gerahmt). */
const val JOURNAL_SUFFIX = ".h264j"

object H264JournalCodec {
    private const val TAG = "H264JournalCodec"

    // "DQJ1" / "REC1" — positive Ints (< 2^31), keine .toInt()-Sonderfälle.
    const val JOURNAL_MAGIC = 0x44514A31
    const val RECORD_MAGIC = 0x52454331
    const val JOURNAL_VERSION = 1

    /** Kopf-Fixteil vor den variablen SPS/PPS: magic+ver+reserved+w+h = 16 Bytes. */
    private const val HEADER_FIXED = 16
    /** Record-Fixteil vor den Nutzbytes: magic+len+pts+kf = 17 Bytes. */
    private const val RECORD_FIXED = 17

    /** Sanity-Deckel für eine Access-Unit (ein HD-IDR ~72 KB) — schützt vor Garbage-Längen. */
    private const val MAX_AU_BYTES = 8 * 1024 * 1024

    /** SPS/PPS-Deckel (real ~30 Bytes) — schützt Recovery vor Garbage-Kopf. */
    private const val MAX_CSD_BYTES = 4096

    data class JournalHeader(val width: Int, val height: Int, val sps: ByteArray, val pps: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is JournalHeader && width == other.width && height == other.height &&
                sps.contentEquals(other.sps) && pps.contentEquals(other.pps)

        override fun hashCode(): Int =
            (width * 31 + height) * 31 + sps.contentHashCode() * 31 + pps.contentHashCode()
    }

    data class JournalRecord(val annexB: ByteArray, val ptsUs: Long, val keyframe: Boolean) {
        override fun equals(other: Any?): Boolean =
            other is JournalRecord && ptsUs == other.ptsUs && keyframe == other.keyframe &&
                annexB.contentEquals(other.annexB)

        override fun hashCode(): Int = (ptsUs.hashCode() * 31 + keyframe.hashCode()) * 31 + annexB.contentHashCode()
    }

    /** Ergebnis eines Record-Lesevorgangs: Nutzdaten, sauberes Ende, oder abgeschnittener Rest. */
    sealed interface RecordResult {
        data class Data(val record: JournalRecord) : RecordResult
        /** Sauberes Datei-Ende exakt an einer Record-Grenze (kein Verlust). */
        data object End : RecordResult
        /** Torn tail (Kill mitten im Record) — dieser und alle folgenden Bytes werden verworfen. */
        data object Torn : RecordResult
    }

    // ── Schreiben ──────────────────────────────────────────────────────────────────────

    fun writeHeader(out: DataOutputStream, width: Int, height: Int, sps: ByteArray, pps: ByteArray) {
        val fixed = ByteBuffer.allocate(HEADER_FIXED).order(ByteOrder.BIG_ENDIAN)
        fixed.putInt(JOURNAL_MAGIC).putShort(JOURNAL_VERSION.toShort()).putShort(0)
            .putInt(width).putInt(height)
        out.write(fixed.array())
        out.writeInt(sps.size); out.write(sps)
        out.writeInt(pps.size); out.write(pps)
        out.flush()
    }

    /** Ein Record als **ein** gepufferter Write + flush (atomar genug; Rest wird als torn erkannt). */
    fun writeRecord(out: DataOutputStream, record: JournalRecord) {
        val head = ByteBuffer.allocate(RECORD_FIXED).order(ByteOrder.BIG_ENDIAN)
        head.putInt(RECORD_MAGIC).putInt(record.annexB.size).putLong(record.ptsUs)
            .put(if (record.keyframe) 1.toByte() else 0.toByte())
        out.write(head.array())
        out.write(record.annexB)
        out.flush()
    }

    // ── Lesen ──────────────────────────────────────────────────────────────────────────

    /** Liest exakt [buf].size Bytes; gibt die tatsächlich gelesene Anzahl zurück (0 = sofort EOF). */
    private fun readBlock(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    /** Kopf lesen. `null` bei falschem Magic oder abgeschnittenem/ungültigem Kopf (Journal unbrauchbar). */
    fun readHeader(input: InputStream): JournalHeader? {
        val fixed = ByteArray(HEADER_FIXED)
        if (readBlock(input, fixed) != HEADER_FIXED) return null
        val bb = ByteBuffer.wrap(fixed).order(ByteOrder.BIG_ENDIAN)
        if (bb.int != JOURNAL_MAGIC) return null
        bb.short /* version */; bb.short /* reserved */
        val width = bb.int
        val height = bb.int
        if (width <= 0 || height <= 0) return null
        val sps = readLenPrefixed(input) ?: return null
        val pps = readLenPrefixed(input) ?: return null
        return JournalHeader(width, height, sps, pps)
    }

    private fun readLenPrefixed(input: InputStream): ByteArray? {
        val lenBuf = ByteArray(4)
        if (readBlock(input, lenBuf) != 4) return null
        val len = ByteBuffer.wrap(lenBuf).order(ByteOrder.BIG_ENDIAN).int
        if (len < 0 || len > MAX_CSD_BYTES) return null
        val data = ByteArray(len)
        return if (readBlock(input, data) == len) data else null
    }

    /** Nächsten Record lesen: [RecordResult.End] (sauber), [RecordResult.Torn], oder Daten. */
    fun readRecord(input: InputStream): RecordResult {
        val fixed = ByteArray(RECORD_FIXED)
        val n = readBlock(input, fixed)
        if (n == 0) return RecordResult.End          // exakt an Record-Grenze → sauberes Ende
        if (n < RECORD_FIXED) return RecordResult.Torn
        val bb = ByteBuffer.wrap(fixed).order(ByteOrder.BIG_ENDIAN)
        if (bb.int != RECORD_MAGIC) return RecordResult.Torn
        val len = bb.int
        if (len < 0 || len > MAX_AU_BYTES) return RecordResult.Torn
        val ptsUs = bb.long
        val keyframe = bb.get().toInt() != 0
        val payload = ByteArray(len)
        if (readBlock(input, payload) != len) return RecordResult.Torn
        return RecordResult.Data(JournalRecord(payload, ptsUs, keyframe))
    }

    /** Vollständig einlesen (für Tests/kleine Journale). Torn tail wird verworfen, [torn]=true. */
    data class Parsed(val header: JournalHeader?, val records: List<JournalRecord>, val torn: Boolean)

    fun parse(input: InputStream): Parsed {
        val header = readHeader(input) ?: return Parsed(null, emptyList(), false)
        val records = ArrayList<JournalRecord>()
        var torn = false
        loop@ while (true) {
            when (val r = readRecord(input)) {
                is RecordResult.Data -> records.add(r.record)
                RecordResult.End -> break@loop
                RecordResult.Torn -> { torn = true; break@loop }
            }
        }
        return Parsed(header, records, torn)
    }
}

/**
 * File-gestützter Journal-Writer. Der Aufnahme-Thread ist einziger Producer: [writeHeader] einmal
 * (aus `onConfig`, VOR dem ersten AU), dann [writeRecord] je AU, am Ende [close].
 */
class H264JournalWriter(private val journalFile: File) {
    @Volatile private var out: DataOutputStream? = null
    @Volatile private var headerWritten = false

    /** true sobald der Kopf (SPS/PPS) steht — erst dann sind Records erlaubt/sinnvoll. */
    val isReady: Boolean get() = headerWritten

    fun writeHeader(width: Int, height: Int, sps: ByteArray, pps: ByteArray): Boolean {
        if (headerWritten) return true
        return try {
            journalFile.parentFile?.mkdirs()
            val s = DataOutputStream(BufferedOutputStream(FileOutputStream(journalFile)))
            H264JournalCodec.writeHeader(s, width, height, sps, pps)
            out = s
            headerWritten = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "Journal-Kopf schreiben fehlgeschlagen: ${e.message}")
            out = null
            false
        }
    }

    fun writeRecord(annexB: ByteArray, ptsUs: Long, keyframe: Boolean) {
        val s = out ?: return
        try {
            H264JournalCodec.writeRecord(s, H264JournalCodec.JournalRecord(annexB, ptsUs, keyframe))
        } catch (e: Exception) {
            Log.w(TAG, "Journal-Record schreiben fehlgeschlagen: ${e.message}")
        }
    }

    fun close() {
        val s = out ?: return
        out = null
        try { s.flush(); s.close() } catch (_: Exception) {}
    }

    companion object { private const val TAG = "H264JournalWriter" }
}

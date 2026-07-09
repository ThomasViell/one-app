package com.uip.oneapp.network

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Welle 5 — Absturzsicheres H.264-Journal: Framing-Roundtrip + positive Torn-Tail-Erkennung.
 *
 * Der Kern der Absturzsicherheit: ein Kill mitten im Schreiben hinterlässt einen gültigen Präfix;
 * der abgeschnittene letzte Record wird verworfen, alles davor bleibt muxbar. Reine JVM-Logik.
 */
class H264JournalCodecTest {

    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1f)
    private val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3c, 0x80.toByte())

    private fun au(size: Int, seed: Int) = ByteArray(size) { ((it + seed) and 0xFF).toByte() }

    private fun writeJournal(records: List<H264JournalCodec.JournalRecord>): ByteArray {
        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)
        H264JournalCodec.writeHeader(out, 1280, 720, sps, pps)
        records.forEach { H264JournalCodec.writeRecord(out, it) }
        out.flush()
        return bos.toByteArray()
    }

    @Test
    fun roundtrip_header_and_records() {
        val records = listOf(
            H264JournalCodec.JournalRecord(au(64, 1), 0L, true),
            H264JournalCodec.JournalRecord(au(20, 2), 40_000L, false),
            H264JournalCodec.JournalRecord(au(30, 3), 80_000L, false),
        )
        val bytes = writeJournal(records)
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(bytes))

        assertNotNull(parsed.header)
        assertEquals(1280, parsed.header!!.width)
        assertEquals(720, parsed.header!!.height)
        assertArrayEquals(sps, parsed.header!!.sps)
        assertArrayEquals(pps, parsed.header!!.pps)
        assertFalse(parsed.torn)
        assertEquals(records, parsed.records)
    }

    @Test
    fun torn_tail_mid_record_drops_only_last() {
        val records = listOf(
            H264JournalCodec.JournalRecord(au(64, 1), 0L, true),
            H264JournalCodec.JournalRecord(au(64, 2), 40_000L, false),
            H264JournalCodec.JournalRecord(au(200, 3), 80_000L, false),
        )
        val bytes = writeJournal(records)
        // Kill mitten im dritten Record (Payload halb geschrieben).
        val truncated = bytes.copyOf(bytes.size - 120)
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(truncated))

        assertNotNull(parsed.header)
        assertTrue("torn tail muss erkannt werden", parsed.torn)
        assertEquals("die zwei vollständigen Records bleiben", records.subList(0, 2), parsed.records)
    }

    @Test
    fun torn_in_record_header_is_detected() {
        val records = listOf(H264JournalCodec.JournalRecord(au(64, 1), 0L, true))
        val bytes = writeJournal(records)
        // Nur wenige Bytes eines zweiten Record-Kopfes anhängen (halber Magic).
        val truncated = bytes.copyOf(bytes.size + 2).also { it[bytes.size] = 0x52; it[bytes.size + 1] = 0x45 }
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(truncated))
        assertTrue(parsed.torn)
        assertEquals(records, parsed.records)
    }

    @Test
    fun garbage_after_records_is_torn_not_data() {
        val records = listOf(H264JournalCodec.JournalRecord(au(64, 1), 0L, true))
        val bytes = writeJournal(records)
        val garbage = bytes + byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(garbage))
        assertTrue(parsed.torn)
        assertEquals(records, parsed.records)
    }

    @Test
    fun clean_end_exactly_at_record_boundary() {
        val records = listOf(
            H264JournalCodec.JournalRecord(au(10, 1), 0L, true),
            H264JournalCodec.JournalRecord(au(10, 2), 40_000L, false),
        )
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(writeJournal(records)))
        assertFalse(parsed.torn)
        assertEquals(2, parsed.records.size)
    }

    @Test
    fun empty_stream_has_no_header() {
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(ByteArray(0)))
        assertNull(parsed.header)
        assertTrue(parsed.records.isEmpty())
    }

    @Test
    fun truncated_header_yields_null_header() {
        val bytes = writeJournal(listOf(H264JournalCodec.JournalRecord(au(10, 1), 0L, true)))
        // Kopf halb (unter HEADER_FIXED + csd) → unbrauchbar.
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(bytes.copyOf(8)))
        assertNull(parsed.header)
        assertTrue(parsed.records.isEmpty())
    }

    @Test
    fun writer_reports_failure_before_header_and_roundtrips_after() {
        val f = File.createTempFile("journal_writer_test", ".h264j")
        try {
            val w = H264JournalWriter(f)
            // Ohne Kopf kein Record — und der Aufrufer MUSS das erfahren (nicht still).
            assertFalse(w.writeRecord(au(10, 1), 0L, true))
            assertTrue(w.writeHeader(1280, 720, sps, pps))
            assertTrue(w.writeRecord(au(10, 1), 0L, true))
            assertTrue(w.writeRecord(au(12, 2), 40_000L, false))
            w.close()

            val parsed = DataInputStream(BufferedInputStream(FileInputStream(f))).use { H264JournalCodec.parse(it) }
            assertNotNull(parsed.header)
            assertFalse(parsed.torn)
            assertEquals(2, parsed.records.size)
        } finally {
            f.delete()
        }
    }

    @Test
    fun wrong_journal_magic_yields_null_header() {
        val bytes = writeJournal(listOf(H264JournalCodec.JournalRecord(au(10, 1), 0L, true)))
        bytes[0] = 0x00  // Magic zerstören
        val parsed = H264JournalCodec.parse(ByteArrayInputStream(bytes))
        assertNull(parsed.header)
    }
}

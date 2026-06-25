package com.uip.oneapp.network.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Sichert die Annex-B-Parser-Primitiven ([splitAnnexB], [findSpsPps]) ab — die Basis der
 * RTSP/H.264-Packetisierung ([RtspVideoServer]). Reines Kotlin, kein Socket/Android; aus dem
 * bewiesenen Spike übernommen, daher hier als Regressionsnetz für die Produktiv-Version.
 *
 * NAL-Header: erstes Byte, `type = byte & 0x1F`. SPS=7 (0x67), PPS=8 (0x68), IDR-Slice=5 (0x65).
 */
class NalUtilsTest {

    private fun u(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun `splits 4-byte startcodes and strips them`() {
        val stream = u(
            0, 0, 0, 1, 0x67, 1, 2,
            0, 0, 0, 1, 0x68, 3,
            0, 0, 0, 1, 0x65, 9, 9, 9,
        )
        val nals = splitAnnexB(stream)
        assertEquals(3, nals.size)
        assertArrayEquals(u(0x67, 1, 2), nals[0])
        assertArrayEquals(u(0x68, 3), nals[1])
        assertArrayEquals(u(0x65, 9, 9, 9), nals[2])
    }

    @Test
    fun `splits 3-byte startcodes`() {
        val stream = u(0, 0, 1, 0x67, 0xA, 0, 0, 1, 0x65, 0xB)
        val nals = splitAnnexB(stream)
        assertEquals(2, nals.size)
        assertArrayEquals(u(0x67, 0xA), nals[0])
        assertArrayEquals(u(0x65, 0xB), nals[1])
    }

    @Test
    fun `data before first startcode is ignored`() {
        // Führende Bytes ohne Startcode gehören zu keiner NAL und werden verworfen.
        val stream = u(0xFF, 0xFF, 0, 0, 0, 1, 0x68, 7)
        val nals = splitAnnexB(stream)
        assertEquals(1, nals.size)
        assertArrayEquals(u(0x68, 7), nals[0])
    }

    @Test
    fun `findSpsPps extracts sps type7 and pps type8`() {
        val csd = u(0, 0, 0, 1, 0x67, 0x42, 0xC0, 0x1F, 0, 0, 0, 1, 0x68, 0xCE, 0x3C, 0x80)
        val sp = findSpsPps(csd)
        assertNotNull(sp)
        assertEquals(7, sp!!.first[0].toInt() and 0x1F)
        assertEquals(8, sp.second[0].toInt() and 0x1F)
        assertArrayEquals(u(0x67, 0x42, 0xC0, 0x1F), sp.first)
        assertArrayEquals(u(0x68, 0xCE, 0x3C, 0x80), sp.second)
    }

    @Test
    fun `findSpsPps returns null when pps missing`() {
        val onlySps = u(0, 0, 0, 1, 0x67, 0x42, 0xC0, 0x1F)
        assertNull(findSpsPps(onlySps))
    }
}

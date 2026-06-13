package com.uip.oneapp.network

import com.uip.oneapp.network.internal.CameraHead
import com.uip.oneapp.network.internal.OneFrameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert das Stream-Reassembly von [OneFrameCodec.drainRxFrames] gegen den realen
 * Empfang ab: zerstückelte und zusammengefasste native Reads dürfen keine Gruppen
 * verlieren (insb. Gruppe 23/24 — der frühere Pro-Chunk-FA-AF-Bug verwarf sie).
 */
class OneFrameCodecTest {

    private fun ints(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // Realer C18-Frame (45 Byte) aus der OEM-Gegenprüfung:
    // FA AF | 002D len | 0004 typ | Gr.21 | Gr.22 | Gr.23 | Gr.24
    private val frameC18 = ints(
        0xFA, 0xAF, 0x00, 0x2D, 0x00, 0x04,
        0x0C, 0x15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x19,
        0x0C, 0x16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x1A,
        0x08, 0x17, 0, 0, 0x01, 0x1C, 0x02, 0x00,
        0x07, 0x18, 0, 0x01, 0x01, 0, 0x1F
    )

    @Test
    fun parsesAllFourGroupsFromCompleteFrame() {
        val r = OneFrameCodec.drainRxFrames(frameC18, frameC18.size)
        assertEquals(45, r.consumed)
        assertEquals(listOf(21, 22, 23, 24), r.frames.map { it.group })
        val g23 = r.frames.first { it.group == 23 }.payload
        assertEquals(6, g23.size)
        assertEquals(0x02, g23[4]) // C18-Markerkandidat (Position 4)
        val g24 = r.frames.first { it.group == 24 }.payload
        assertEquals(listOf(0x00, 0x01, 0x01, 0x00, 0x1F), g24.toList())
    }

    @Test
    fun reassemblesFrameSplitAcrossChunks() {
        // Chunk 1 bricht mitten in Gruppe 23 ab (30 von 45 Byte).
        val c1 = frameC18.copyOfRange(0, 30)
        val c2 = frameC18.copyOfRange(30, frameC18.size)

        // Unvollständig -> nichts konsumiert, keine Sub-Frames (atomar erst bei voller Länge).
        val r1 = OneFrameCodec.drainRxFrames(c1, c1.size)
        assertEquals(0, r1.consumed)
        assertTrue(r1.frames.isEmpty())

        // Akkumulator + Fortsetzung -> vollständiger Frame inkl. Gruppe 23/24.
        val acc = c1 + c2
        val r2 = OneFrameCodec.drainRxFrames(acc, acc.size)
        assertEquals(45, r2.consumed)
        assertEquals(listOf(21, 22, 23, 24), r2.frames.map { it.group })
    }

    @Test
    fun parsesTwoConcatenatedFrames() {
        val two = frameC18 + frameC18
        val r = OneFrameCodec.drainRxFrames(two, two.size)
        assertEquals(90, r.consumed)
        assertEquals(listOf(21, 22, 23, 24, 21, 22, 23, 24), r.frames.map { it.group })
    }

    @Test
    fun skipsLeadingGarbageAndKeepsTrailingPartial() {
        val garbage = ints(0x00, 0xFF, 0x12)            // kein Magic
        val partial = frameC18.copyOfRange(0, 20)       // angefangener zweiter Frame
        val buf = garbage + frameC18 + partial
        val r = OneFrameCodec.drainRxFrames(buf, buf.size)
        // Genau ein vollständiger Frame; consumed hinter ihm, vor dem partiellen Rest.
        assertEquals(listOf(21, 22, 23, 24), r.frames.map { it.group })
        assertEquals(garbage.size + 45, r.consumed)
    }

    @Test
    fun keepsTrailingLoneMagicStartByte() {
        val buf = frameC18 + ints(0xFA)                 // einzelnes FA am Ende
        val r = OneFrameCodec.drainRxFrames(buf, buf.size)
        assertEquals(45, r.consumed)                    // das FA bleibt im Puffer erhalten
        assertEquals(buf.size - r.consumed, 1)
    }

    @Test
    fun ignoresImplausibleLengthAndResyncs() {
        // FA AF mit absurder Länge (0xFFFF) -> resync nach dem Magic, danach echter Frame.
        val bogus = ints(0xFA, 0xAF, 0xFF, 0xFF)
        val buf = bogus + frameC18
        val r = OneFrameCodec.drainRxFrames(buf, buf.size)
        assertEquals(listOf(21, 22, 23, 24), r.frames.map { it.group })
    }

    @Test
    fun cameraHeadMapsMarkerByteDefensively() {
        assertEquals(CameraHead.C10, CameraHead.from(0x01))
        assertEquals(CameraHead.C18, CameraHead.from(0x02))
        // alles andere -> UNKNOWN (nie raten)
        assertEquals(CameraHead.UNKNOWN, CameraHead.from(0x00))
        assertEquals(CameraHead.UNKNOWN, CameraHead.from(0x10)) // altes Fehl-Mapping
        assertEquals(CameraHead.UNKNOWN, CameraHead.from(null))
    }

    @Test
    fun dropsSubFrameWithCorruptXorTrailer() {
        // Meter-Subframe (Group 22, beginnt bei Index 18) verstümmeln: ein Datenbyte kippen,
        // damit der XOR-Trailer nicht mehr passt → der Subframe muss verworfen werden (W3).
        val corrupt = frameC18.copyOf()
        corrupt[20] = 0xFF.toByte()
        val r = OneFrameCodec.drainRxFrames(corrupt, corrupt.size)
        // Group 22 faellt raus, die intakten Gruppen 21/23/24 bleiben erhalten.
        assertEquals(listOf(21, 23, 24), r.frames.map { it.group })
        assertEquals(45, r.consumed) // Frame trotzdem komplett konsumiert (Walk via glen)
    }

    @Test
    fun keepsValidMeterSubFrameUnderXorCheck() {
        // Gegenprobe: der intakte C18-Frame liefert weiterhin alle vier Gruppen inkl. Meter (22).
        val r = OneFrameCodec.drainRxFrames(frameC18, frameC18.size)
        assertTrue(r.frames.any { it.group == OneFrameCodec.GROUP_METER })
        assertEquals(listOf(21, 22, 23, 24), r.frames.map { it.group })
    }
}

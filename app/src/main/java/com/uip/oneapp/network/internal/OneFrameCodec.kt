package com.uip.oneapp.network.internal

/**
 * Wire-Format-Codec für das serielle Steuerprotokoll der NSP3CT-Schiebekamera ONE.
 *
 * 1:1 portiert aus dem verifizierten Smoke-Test (one-smoketest). Reverse-engineered
 * aus der Bominwell-APK com.bominwell.minipush.
 *
 * Sende-Frame:
 *   ┌───────────────────┬────┬────────┬─────────────────┬─────┐
 *   │ Magic (6 Byte)    │ LL │ Group  │ Payload (N B)   │ XOR │
 *   │ FA AF 00 10 00 01 │    │        │                 │     │
 *   └───────────────────┴────┴────────┴─────────────────┴─────┘
 *   LL  = Payload-Länge + 2 (Größe von "command" inkl. selbst und Group)
 *   XOR = XOR über [LL, Group, Payload...]
 *
 * Empfangs-Stream beginnt mit `FA AF`. Danach Sub-Frames `[length][group][payload]`.
 *
 * Bezug: docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md, Phase P3.
 */
object OneFrameCodec {

    private val MAGIC: ByteArray = byteArrayOf(
        0xFA.toByte(), 0xAF.toByte(), 0x00, 0x10, 0x00, 0x01
    )

    val MAGIC_RX_PREFIX: ByteArray = byteArrayOf(0xFA.toByte(), 0xAF.toByte())

    /**
     * Base-Control-Frame (Group 0x01): Sonde-Power + Licht + Frequenz + Btn1..6 + JiMi.
     *
     * Relevante Parameter für die ONE-Schiebekamera:
     *   - power: 1 = Sonde an, 0 = aus  (ControlArgs.Dev_Open/Dev_Close)
     *   - light: 0..200 (Lichtintensität — Hardware sättigt bei 200)
     *   - frequency: 0=Off, 1=512Hz, 2=640Hz, 3=33kHz (Smoke-Test-Mapping)
     *
     * Andere Bytes (btn1..6, jiMi) bleiben Null — bei der ONE nicht genutzt.
     */
    fun baseCommand(
        power: Int = 0,
        light: Int = 0,
        frequency: Int = 0,
        btn1: Int = 0, btn2: Int = 0, btn3: Int = 0,
        btn4: Int = 0, btn5: Int = 0, btn6: Int = 0,
        jiMi: Int = 0
    ): ByteArray {
        val body = ByteArray(12)
        body[0] = 0x0D  // length+1 (wird in wrap() überschrieben)
        body[1] = 0x01  // Group: Base-Control
        body[2] = power.toByte()
        body[3] = light.toByte()
        body[4] = frequency.toByte()
        body[5] = btn1.toByte()
        body[6] = btn2.toByte()
        body[7] = btn3.toByte()
        body[8] = btn4.toByte()
        body[9] = btn5.toByte()
        body[10] = btn6.toByte()
        body[11] = jiMi.toByte()
        return wrap(body)
    }

    private fun wrap(body: ByteArray): ByteArray {
        body[0] = (body.size + 1).toByte()
        var xor = 0
        for (b in body) xor = xor xor b.toInt()
        val out = ByteArray(MAGIC.size + body.size + 1)
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.size)
        System.arraycopy(body, 0, out, MAGIC.size, body.size)
        out[MAGIC.size + body.size] = xor.toByte()
        return out
    }

    /** Sub-Frame im Empfangs-Stream. */
    data class RxSubFrame(val group: Int, val payload: IntArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RxSubFrame) return false
            return group == other.group && payload.contentEquals(other.payload)
        }
        override fun hashCode(): Int = 31 * group + payload.contentHashCode()
    }

    /**
     * Parst EINEN vollständigen Empfangs-Block (beginnt mit FA AF) in Sub-Frames.
     * Leere Liste bei ungültigem Magic oder zu kurzem Puffer.
     *
     * Achtung: erwartet, dass `buf` exakt an einer Frame-Grenze beginnt und den
     * kompletten Frame enthält. Für den realen Stream (zerstückelte/zusammengefasste
     * native Reads) `drainRxFrames` mit Akkumulator verwenden.
     */
    fun parseRxFrames(buf: ByteArray): List<RxSubFrame> {
        if (buf.size < 7) return emptyList()
        if (buf[0] != MAGIC_RX_PREFIX[0] || buf[1] != MAGIC_RX_PREFIX[1]) return emptyList()
        val result = mutableListOf<RxSubFrame>()
        var i = 6
        while (i + 1 < buf.size) {
            val length = buf[i].toInt() and 0xFF
            if (length <= 0 || i + length > buf.size) break
            val group = buf[i + 1].toInt() and 0xFF
            val payload = IntArray(length - 2)
            for (k in payload.indices) payload[k] = buf[i + 2 + k].toInt() and 0xFF
            result.add(RxSubFrame(group, payload))
            i += length
        }
        return result
    }

    /** Plausibilitäts-Obergrenze für die Frame-Gesamtlänge (Schutz gegen Müll-Sync). */
    private const val MAX_FRAME_LEN = 512

    /** Ergebnis von [drainRxFrames]: extrahierte Sub-Frames + Anzahl konsumierter Bytes. */
    data class RxParseResult(val frames: List<RxSubFrame>, val consumed: Int)

    /**
     * Stream-Reassembly für den realen Empfang: `nativeReadSerial` liefert den
     * 45-Byte-Frame zerstückelt oder mehrere Frames in einem Read. Diese Funktion
     * arbeitet auf einem persistenten Akkumulator-Puffer:
     *   - synct auf das Magic `FA AF` (führender Müll wird verworfen),
     *   - liest die deklarierte 16-bit-Gesamtlänge (Big-Endian, Offset +2),
     *   - parst NUR vollständig vorliegende Frames (TLV-Gruppen ab Offset 6),
     *   - lässt einen unvollständigen Rest-Frame im Puffer (consumed < length).
     *
     * Der Aufrufer behält `buf[consumed until length]` für den nächsten Read.
     * Deckt mehrere und partielle Frames pro Read korrekt ab.
     *
     * @param length Anzahl gültiger Bytes in `buf` (erlaubt Wiederverwendung großer Puffer).
     */
    fun drainRxFrames(buf: ByteArray, length: Int = buf.size): RxParseResult {
        val frames = mutableListOf<RxSubFrame>()
        var i = 0
        while (i < length) {
            // 1) Sync auf Magic FA AF
            if (buf[i] != MAGIC_RX_PREFIX[0]) { i++; continue }
            if (i + 1 >= length) break                       // einzelnes FA am Ende -> behalten
            if (buf[i + 1] != MAGIC_RX_PREFIX[1]) { i++; continue }
            // 2) Gesamtlänge (16-bit Big-Endian) inkl. FA AF
            if (i + 4 > length) break                         // Längenfeld noch nicht komplett
            val total = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
            if (total < 7 || total > MAX_FRAME_LEN) { i += 2; continue } // unplausibel -> resync
            if (i + total > length) break                     // Frame noch nicht vollständig
            // 3) TLV-Gruppen ab Offset 6 (nach FA AF | len16 | typ16) walken
            var j = i + 6
            val end = i + total
            while (j + 1 < end) {
                val glen = buf[j].toInt() and 0xFF
                if (glen <= 1 || j + glen > end) break
                val group = buf[j + 1].toInt() and 0xFF
                val payload = IntArray(glen - 2)
                for (k in payload.indices) payload[k] = buf[j + 2 + k].toInt() and 0xFF
                frames.add(RxSubFrame(group, payload))
                j += glen
            }
            i += total
        }
        return RxParseResult(frames, i)
    }

    const val GROUP_STATUS = 21        // [power, light, freq, btn1..btn6]
    const val GROUP_METER = 22         // 32-bit big-endian Distanz in mm
    const val GROUP_CAMERA = 23        // 32-bit big-endian mV + 1 Byte cameraID
    const val GROUP_VERSION = 24       // [isUpgrade, major, minor, patch]
}

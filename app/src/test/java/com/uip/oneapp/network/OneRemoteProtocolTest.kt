package com.uip.oneapp.network

import com.google.gson.Gson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die reine ONE-Remote-Protokolllogik ab (Welle 1, Dual-Modus): Paketbau
 * (Header/Checksumme), TCP-Reassembly (Brace-Matching auf zerstückelten Reads) und
 * Telemetrie-Mapping — ohne Socket, ohne Android. Die Reassembly-Tests simulieren
 * fragmentierte Socket-Reads (Mock-Socket-Ersatz: Byte-Chunks statt echtem TCP).
 */
class OneRemoteProtocolTest {

    private fun u(b: Byte) = b.toInt() and 0xFF

    // ===== Paketbau =====

    @Test
    fun headerIsSdkPrefix() {
        assertArrayEquals(
            intArrayOf(0xFA, 0xAF, 0x00, 0x10, 0x00, 0x01),
            OneRemoteProtocol.HEADER.map { u(it) }.toIntArray()
        )
    }

    @Test
    fun baseCommandLayoutAndClamping() {
        val cmd = OneRemoteProtocol.baseCommand(lightPower = 60, frequency = 2)
        assertEquals(12, cmd.size)
        assertEquals(0x01, u(cmd[1]))
        assertEquals(0x00, u(cmd[2]))
        assertEquals(60, u(cmd[3]))
        assertEquals(2, u(cmd[4]))

        // Clamping: Licht 0..100, Frequenz 0..3.
        val clamped = OneRemoteProtocol.baseCommand(lightPower = 200, frequency = 9)
        assertEquals(100, u(clamped[3]))
        assertEquals(3, u(clamped[4]))
    }

    @Test
    fun packetHasHeaderLengthByteAndXorChecksum() {
        // light=0, freq=0 → command=[13,1,0,0,...], XOR=13^1=12=0x0C
        val p0 = OneRemoteProtocol.baseCommandPacket(0, 0)
        assertEquals(6 + 12 + 1, p0.size)
        assertEquals(0xFA, u(p0[0]))
        assertEquals(0xAF, u(p0[1]))
        assertEquals(0x0D, u(p0[6]))          // len-Byte = command.size + 1 = 13
        assertEquals(0x0C, u(p0.last()))      // Checksumme

        // light=60, freq=2 → 13^1^60^2 = 0x32
        val p1 = OneRemoteProtocol.baseCommandPacket(60, 2)
        assertEquals(60, u(p1[9]))            // command[3] = light
        assertEquals(2, u(p1[10]))            // command[4] = freq
        assertEquals(0x32, u(p1.last()))

        // Checksumme ist tatsächlich das XOR über alle 12 Command-Bytes.
        val command = p1.copyOfRange(6, 18)
        var xor = 0
        for (b in command) xor = xor xor u(b)
        assertEquals(xor, u(p1.last()))
    }

    @Test
    fun packetAsIntListPreservesSignedBytes() {
        val list = OneRemoteProtocol.packetAsIntList(OneRemoteProtocol.baseCommandPacket(0, 0))
        assertEquals(-6, list[0])   // 0xFA as signed byte
        assertEquals(-81, list[1])  // 0xAF as signed byte
    }

    // ===== Zyklen + Labels =====

    @Test
    fun lightPowerCycleWrapsAndRecoversFromUnknown() {
        assertEquals(30, OneRemoteProtocol.nextLightPower(0))
        assertEquals(60, OneRemoteProtocol.nextLightPower(30))
        assertEquals(90, OneRemoteProtocol.nextLightPower(60))
        assertEquals(0, OneRemoteProtocol.nextLightPower(90))
        assertEquals(30, OneRemoteProtocol.nextLightPower(45)) // unbekannt → erste ON-Stufe
    }

    @Test
    fun frequencyCycleWraps() {
        assertEquals(1, OneRemoteProtocol.nextFrequency(0))
        assertEquals(2, OneRemoteProtocol.nextFrequency(1))
        assertEquals(3, OneRemoteProtocol.nextFrequency(2))
        assertEquals(0, OneRemoteProtocol.nextFrequency(3))
    }

    @Test
    fun freqLabels() {
        assertNull(OneRemoteProtocol.freqLabel(0))
        assertEquals("33kHz", OneRemoteProtocol.freqLabel(1))
        assertEquals("640Hz", OneRemoteProtocol.freqLabel(2))
        assertEquals("512Hz", OneRemoteProtocol.freqLabel(3))
    }

    // ===== TCP-Reassembly (Brace-Matching) =====

    @Test
    fun drainSingleObject() {
        val buf = StringBuilder("""{"a":1}""")
        val objs = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(listOf("""{"a":1}"""), objs)
        assertEquals(0, buf.length)
    }

    @Test
    fun drainTwoConcatenatedObjects() {
        val buf = StringBuilder("""{"a":1}{"b":2}""")
        val objs = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(listOf("""{"a":1}""", """{"b":2}"""), objs)
        assertEquals(0, buf.length)
    }

    @Test
    fun drainStripsLeadingGarbage() {
        val buf = StringBuilder("""garbage{"a":1}""")
        val objs = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(listOf("""{"a":1}"""), objs)
        assertEquals(0, buf.length)
    }

    @Test
    fun drainHandlesNestedBraces() {
        val buf = StringBuilder("""{"x":{"y":1}}""")
        val objs = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(listOf("""{"x":{"y":1}}"""), objs)
        assertEquals(0, buf.length)
    }

    @Test
    fun drainKeepsIncompleteRemainderForNextRead() {
        // Erstes Fragment: vollständiges Objekt + Anfang des zweiten.
        val buf = StringBuilder("""{"a":1}{"b":""")
        val first = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(listOf("""{"a":1}"""), first)
        assertEquals("""{"b":""", buf.toString()) // Rest bleibt erhalten

        // Zweites Fragment komplettiert das Objekt.
        buf.append("""2}""")
        val second = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(listOf("""{"b":2}"""), second)
        assertEquals(0, buf.length)
    }

    @Test
    fun drainClearsBufferWhenNoBrace() {
        val buf = StringBuilder("no json here")
        val objs = OneRemoteProtocol.drainJsonObjects(buf)
        assertTrue(objs.isEmpty())
        assertEquals(0, buf.length)
    }

    // ===== Telemetrie-Mapping =====

    @Test
    fun telemetryNullWhenNoMiniPushInfo() {
        assertNull(OneRemoteProtocol.telemetryFrom(null))
    }

    @Test
    fun telemetryMapsMiniPushInfoFields() {
        val t = OneRemoteProtocol.telemetryFrom(
            SdkMiniPushInfo(distance = 12.5f, currentDistance = 3.2f, battery = 80, frequency = 1)
        )!!
        assertEquals(12.5f, t.rawDistance, 0.0001f)
        assertEquals(3.2f, t.currentDistance, 0.0001f)
        assertEquals(80, t.battery)
        assertEquals(1, t.frequency)
        assertEquals("33kHz", t.freqLabel)
    }

    /**
     * Voller Empfangspfad wie über einen Socket — aber mit fragmentierten Byte-Chunks
     * statt echtem TCP: SDK-Push serialisieren, an beliebiger Byte-Grenze splitten,
     * stückweise einspeisen, reassemblen, deserialisieren, mappen.
     */
    @Test
    fun fragmentedSocketReadReassemblesAndParses() {
        val gson = Gson()
        val push = SdkSendData(
            miniPushInfo = SdkMiniPushInfo(distance = 42.0f, currentDistance = 1.5f, battery = 73, frequency = 2)
        )
        val json = gson.toJson(push)
        assertTrue(json.contains("miniPushInfo"))

        // An (etwa) der Mitte zerschneiden → simuliert zwei TCP-Reads.
        val cut = json.length / 2
        val chunk1 = json.substring(0, cut)
        val chunk2 = json.substring(cut)

        val buf = StringBuilder()
        buf.append(chunk1)
        val afterFirst = OneRemoteProtocol.drainJsonObjects(buf)
        assertTrue("Teil-Objekt darf noch nicht geliefert werden", afterFirst.isEmpty())

        buf.append(chunk2)
        val afterSecond = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(1, afterSecond.size)
        assertEquals(0, buf.length)

        val decoded = gson.fromJson(afterSecond[0], SdkSendData::class.java)
        val t = OneRemoteProtocol.telemetryFrom(decoded.miniPushInfo)!!
        assertEquals(42.0f, t.rawDistance, 0.0001f)
        assertEquals(1.5f, t.currentDistance, 0.0001f)
        assertEquals(73, t.battery)
        assertEquals("640Hz", t.freqLabel)
    }

    // ===== Config / RTSP-URL =====

    @Test
    fun configBuildsRtspUrlFromTargetIpByDefault() {
        val cfg = OneHardwareConfig()
        assertEquals("192.168.43.1", cfg.targetIp)
        assertEquals(12345, cfg.tcpPort)
        assertEquals("rtsp://192.168.43.1:8554/1234", cfg.buildRtspUrl())
        assertEquals("rtsp://10.0.0.5:8554/1234", cfg.buildRtspUrl("10.0.0.5"))
    }

    // ===== Clamping-Untergrenze =====

    @Test
    fun baseCommandClampsNegativeInputsToZero() {
        val cmd = OneRemoteProtocol.baseCommand(lightPower = -5, frequency = -1)
        assertEquals(0, u(cmd[3]))
        assertEquals(0, u(cmd[4]))
    }

    // ===== Telemetrie: Sentinel-Verhalten =====

    @Test
    fun telemetryBatteryDefaultsToZeroWhenOmitted() {
        // Push ohne Felder → SDK-Defaults (battery=0, frequency=0). Dokumentiert die
        // Sentinel-Semantik: 0 wird durchgereicht (kein "unbekannt").
        val t = OneRemoteProtocol.telemetryFrom(SdkMiniPushInfo())!!
        assertEquals(0, t.battery)
        assertEquals(0, t.frequency)
        assertNull(t.freqLabel)
    }

    // ===== Absolut-Distanz (Software-Reset) — Akkumulations-Arithmetik =====

    @Test
    fun absoluteResetZeroesDisplayAndIsReconstructibleFromRaw() {
        // Start: kein Offset, Roh=10 → Anzeige 10.
        var offset = 0f
        assertEquals(10f, OneRemoteProtocol.applyAbsoluteOffset(10f, offset), 0.0001f)

        // Reset bei Anzeige 10 → Offset = 10, Anzeige(Roh=10) = 0.
        offset = OneRemoteProtocol.offsetForAbsoluteReset(currentDisplay = 10f, currentOffset = offset)
        assertEquals(10f, offset, 0.0001f)
        assertEquals(0f, OneRemoteProtocol.applyAbsoluteOffset(10f, offset), 0.0001f)

        // Roh wächst auf 15 → Anzeige 5.
        assertEquals(5f, OneRemoteProtocol.applyAbsoluteOffset(15f, offset), 0.0001f)
    }

    @Test
    fun twoConsecutiveAbsoluteResetsAccumulateOffset() {
        var offset = 0f
        // Erster Reset bei Roh=10 (Anzeige 10).
        offset = OneRemoteProtocol.offsetForAbsoluteReset(currentDisplay = 10f, currentOffset = offset)
        // Roh wächst auf 15 → Anzeige 5; zweiter Reset bei Anzeige 5.
        offset = OneRemoteProtocol.offsetForAbsoluteReset(currentDisplay = 5f, currentOffset = offset)
        assertEquals(15f, offset, 0.0001f) // Offset akkumuliert auf den aktuellen Roh-Stand
        assertEquals(0f, OneRemoteProtocol.applyAbsoluteOffset(15f, offset), 0.0001f)
    }

    // ===== Ausgehende Wire-Shape (Geräte-Kontrakt zum DeviceService) =====
    // Pinnt die Gson-Feldnamen, von denen OSD-Toggle und Meter-Reset auf dem Gerät
    // abhängen — ein stilles Umbenennen würde sonst grün bleiben aber das Gerät brechen.

    @Test
    fun videoOverlayOnSerializesShowOsdTrueModeOn() {
        val gson = Gson()
        val json = gson.toJson(
            SdkSendData(
                videoOverlay = SdkVideoOverlay(
                    isShowOSD = true, modeON_OFF = 0,
                    osdHeadStrArr = emptyList(), osdNormalStrArr = emptyList()
                ),
                sendCommand = OneRemoteProtocol.packetAsIntList(OneRemoteProtocol.baseCommandPacket(0, 0))
            )
        )
        assertTrue(json, json.contains("\"isShowOSD\":true"))
        assertTrue(json, json.contains("\"modeON_OFF\":0"))
        assertTrue(json, json.contains("\"videoOverlay\""))
    }

    @Test
    fun videoOverlayOffSerializesShowOsdFalseModeOff() {
        val gson = Gson()
        val json = gson.toJson(
            SdkSendData(
                videoOverlay = SdkVideoOverlay(
                    isShowOSD = false, modeON_OFF = 1,
                    osdHeadStrArr = emptyList(), osdNormalStrArr = emptyList()
                )
            )
        )
        assertTrue(json, json.contains("\"isShowOSD\":false"))
        assertTrue(json, json.contains("\"modeON_OFF\":1"))
    }

    @Test
    fun meterResetSerializesClearDistanceSignal() {
        // SDK setJiMi(1): currentDistance=1.0f = CLEAR_DISTANCE_ON (Reset-Signal).
        val gson = Gson()
        val json = gson.toJson(SdkSendData(miniPushInfo = SdkMiniPushInfo(currentDistance = 1.0f)))
        assertTrue(json, json.contains("\"currentDistance\":1.0"))
        assertTrue(json, json.contains("\"miniPushInfo\""))
    }

    @Test
    fun sendCommandSerializesAsIntArray() {
        val gson = Gson()
        val json = gson.toJson(SdkSendData(sendCommand = listOf(1, 2, 3)))
        assertTrue(json, json.contains("\"sendCommand\":[1,2,3]"))
        // miniPushInfo/videoOverlay sind null → von Gson weggelassen.
        assertTrue(json, !json.contains("miniPushInfo"))
    }

    // ===== Reassembly: Charakterisierung der bekannten String-Limitation =====

    @Test
    fun drainIsNotStringAwareKnownLimitation() {
        // Eine `}` INNERHALB eines String-Werts verfälscht die Tiefenzählung — diese
        // Charakterisierung fixiert das bekannte (aus dem Original übernommene) Verhalten,
        // damit eine spätere Härtung bewusst erfolgt. Real unkritisch: eingehende
        // Telemetrie ist numerisch.
        val buf = StringBuilder("""{"v":"a}b"}""")
        val objs = OneRemoteProtocol.drainJsonObjects(buf)
        // Die erste (String-interne) `}` schließt das Objekt vorzeitig:
        assertEquals(listOf("""{"v":"a}"""), objs)
    }

    // ===== Reassembly über echten Gson-Roundtrip, mehrere Objekte, 3 ungleiche Reads =====

    @Test
    fun twoGsonObjectsReassembleAcrossThreeUnevenReads() {
        val gson = Gson()
        val a = gson.toJson(SdkSendData(miniPushInfo = SdkMiniPushInfo(distance = 1.0f)))
        val b = gson.toJson(SdkSendData(miniPushInfo = SdkMiniPushInfo(distance = 2.0f)))
        val stream = a + b

        // Schnitte: mitten im 1. Objekt, dann über die Objektgrenze hinweg, dann Rest.
        val c1 = stream.substring(0, a.length - 3)
        val c2 = stream.substring(a.length - 3, a.length + 5)
        val c3 = stream.substring(a.length + 5)

        val buf = StringBuilder()
        buf.append(c1)
        assertTrue("Teil-Objekt darf nicht geliefert werden", OneRemoteProtocol.drainJsonObjects(buf).isEmpty())

        buf.append(c2)
        val r2 = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(1, r2.size) // 1. Objekt komplett, Anfang des 2. bleibt gepuffert

        buf.append(c3)
        val r3 = OneRemoteProtocol.drainJsonObjects(buf)
        assertEquals(1, r3.size)
        assertEquals(0, buf.length)

        assertEquals(1.0f, gson.fromJson(r2[0], SdkSendData::class.java).miniPushInfo!!.distance, 0.0001f)
        assertEquals(2.0f, gson.fromJson(r3[0], SdkSendData::class.java).miniPushInfo!!.distance, 0.0001f)
    }
}

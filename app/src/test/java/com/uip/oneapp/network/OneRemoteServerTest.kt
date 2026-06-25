package com.uip.oneapp.network

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die reine Übersetzungslogik des [OneRemoteServer] (Welle 3b, Dual-Modus) ab —
 * **ohne Socket, mit Mock-[HardwareService]**: eingehende Client-Pushes (`sendCommand` /
 * `miniPushInfo` / `videoOverlay`) → die richtigen HardwareService-Aufrufe, und die
 * ausgehende Telemetrie als client-kompatibles JSON. Der echte Netz-Round-Trip Tablet↔ONE
 * ist Geräte-Test (Welle 5).
 */
class OneRemoteServerTest {

    /** Aufzeichnender Mock — keine echte Hardware, keine Sockets. */
    private class FakeHardwareService(initial: OneHardwareState = OneHardwareState()) : HardwareService {
        val state = MutableStateFlow(initial)
        override val hardwareState: StateFlow<OneHardwareState> get() = state
        override val logMessages = MutableStateFlow<List<String>>(emptyList())
        override val isConnected = true
        override var lastRtspUrl = ""
        override val videoSource = MutableStateFlow<VideoSource>(VideoSource.None)

        override suspend fun probeEndpoints() = HardwareConnectionStatus()
        override fun startPolling() {}
        override fun stopPolling() {}
        override fun destroy() {}

        val lightCalls = mutableListOf<Int>()
        val freqCalls = mutableListOf<Int>()
        val overlayCalls = mutableListOf<String?>()
        var relativeResetCount = 0
        var absoluteResetCount = 0

        override fun cycleLightPower() {}
        override fun sendLightPower(power: Int) { lightCalls += power }
        override fun cycleFrequency() {}
        override fun sendFrequency(frequency: Int) { freqCalls += frequency }
        override fun resetMeterAbsolute() { absoluteResetCount++ }
        override fun resetMeterRelative() { relativeResetCount++ }
        override fun sendVideoOverlay(text: String?) { overlayCalls += text }
    }

    private fun newServer(initial: OneHardwareState = OneHardwareState()): Pair<OneRemoteServer, FakeHardwareService> {
        val fake = FakeHardwareService(initial)
        return OneRemoteServer(fake) to fake
    }

    private fun baseCommand(light: Int, freq: Int) =
        SdkSendData(sendCommand = OneRemoteProtocol.packetAsIntList(OneRemoteProtocol.baseCommandPacket(light, freq)))

    // ===== Eingehend: Licht/Frequenz mit Keepalive-Entprellung =====

    @Test
    fun lightChangeAppliesOnceAndDedupesRepeatedKeepalive() {
        val (server, fake) = newServer()
        server.applyClientCommand(baseCommand(60, 0)) // Erst-Sync: Licht 60, Frequenz 0
        server.applyClientCommand(baseCommand(60, 0)) // unveränderter Keepalive → kein Aufruf
        server.applyClientCommand(baseCommand(90, 0)) // Licht-Änderung → ein Aufruf

        assertEquals(listOf(60, 90), fake.lightCalls)
        assertEquals(listOf(0), fake.freqCalls) // Frequenz nur einmal (Erst-Sync), danach unverändert
    }

    @Test
    fun frequencyChangeAppliesOnChangeOnly() {
        val (server, fake) = newServer()
        server.applyClientCommand(baseCommand(0, 1))
        server.applyClientCommand(baseCommand(0, 2))
        server.applyClientCommand(baseCommand(0, 2)) // unverändert → kein Aufruf

        assertEquals(listOf(1, 2), fake.freqCalls)
        assertEquals(listOf(0), fake.lightCalls) // Licht nur einmal (Erst-Sync)
    }

    @Test
    fun invalidOrEmptySendCommandIsIgnored() {
        val (server, fake) = newServer()
        server.applyClientCommand(SdkSendData(sendCommand = listOf(1, 2, 3))) // falsche Länge
        server.applyClientCommand(SdkSendData())                              // gar nichts

        assertTrue(fake.lightCalls.isEmpty())
        assertTrue(fake.freqCalls.isEmpty())
        assertTrue(fake.overlayCalls.isEmpty())
        assertEquals(0, fake.relativeResetCount)
    }

    // ===== Eingehend: Meter-Reset =====

    @Test
    fun relativeMeterResetTranslatesToHardwareCall() {
        val (server, fake) = newServer()
        // Client-Wire des Relativ-Resets: miniPushInfo(currentDistance=1.0) + BaseCommand.
        server.applyClientCommand(
            SdkSendData(
                miniPushInfo = SdkMiniPushInfo(currentDistance = 1.0f),
                sendCommand = OneRemoteProtocol.packetAsIntList(OneRemoteProtocol.baseCommandPacket(0, 0))
            )
        )
        assertEquals(1, fake.relativeResetCount)
        assertEquals(0, fake.absoluteResetCount) // Absolut-Reset ist client-lokal, geht NIE über die Leitung
    }

    // ===== Eingehend: OSD-Toggle =====

    @Test
    fun osdToggleTranslatesShowOsdToOverlayText() {
        val (server, fake) = newServer()
        server.applyClientCommand(SdkSendData(videoOverlay = SdkVideoOverlay(isShowOSD = true)))  // OSD AN → null
        server.applyClientCommand(SdkSendData(videoOverlay = SdkVideoOverlay(isShowOSD = false))) // OSD AUS → ""

        assertEquals(listOf<String?>(null, ""), fake.overlayCalls)
    }

    // ===== Ausgehend: Telemetrie =====

    @Test
    fun currentTelemetryJsonRoundTripsToClientReceivePath() {
        val (server, _) = newServer(
            OneHardwareState(
                cableController = CableControllerState(
                    meterReading = 42.0f,
                    currentDistance = 1.5f,
                    batteryLevel = 73,
                    cameraId = 1
                ),
                crawlerController = CrawlerControllerState(
                    frontLightPower = 90,
                    sondeFrequency = "512 Hz" // internes Label (mit Leerzeichen)
                )
            )
        )

        val json = server.currentTelemetryJson()
        // Genau der Empfangspfad des W1-Clients: deserialisieren + telemetryFrom.
        val decoded = Gson().fromJson(json, SdkSendData::class.java)
        val t = OneRemoteProtocol.telemetryFrom(decoded.miniPushInfo)!!

        assertEquals(42.0f, t.rawDistance, 0.0001f)
        assertEquals(1.5f, t.currentDistance, 0.0001f)
        assertEquals(73, t.battery)
        assertEquals(3, t.frequency)
        assertEquals("512Hz", t.freqLabel)
    }

    // ===== localServerIp — Interface-Auswahl (gemockt) =====

    @Test
    fun localServerIpPrefersWlan0OverOtherInterfaces() {
        val (server, _) = newServer()
        val ip = server.localServerIp { listOf("eth0" to "10.0.0.1", "wlan0" to "192.168.178.49") }
        assertEquals("192.168.178.49", ip)
    }

    @Test
    fun localServerIpPrefersSoftApSubnetOverNamedInterface() {
        // Welle 3a: bei aktivem Tablet-Hotspot heißt das SoftAP-Interface OEM-abhängig (hier swlan0),
        // trägt aber 192.168.43.x — der Subnetz-Treffer schlägt sogar den wlan0-Namen.
        val (server, _) = newServer()
        val ip = server.localServerIp {
            listOf("wlan0" to "192.168.178.49", "swlan0" to "192.168.43.50")
        }
        assertEquals("192.168.43.50", ip)
    }

    @Test
    fun localServerIpPrefersNamedInterfaceOverNonPreferredWhenNoSubnetMatch() {
        // Ohne 43.x-Treffer greift die Namens-Präferenz: ap0 (preferred) schlägt eth0.
        val (server, _) = newServer()
        val ip = server.localServerIp { listOf("eth0" to "10.0.0.1", "ap0" to "192.168.7.1") }
        assertEquals("192.168.7.1", ip)
    }

    @Test
    fun localServerIpPicksFirstPreferredNameWhenNoSubnetMatch() {
        // ap0 und wlan0 sind beide preferred; ohne 43.x-Treffer entscheidet die List-Reihenfolge.
        val (server, _) = newServer()
        val ip = server.localServerIp { listOf("ap0" to "192.168.7.1", "wlan0" to "192.168.178.49") }
        assertEquals("192.168.7.1", ip) // ap0 ist der erste preferred-Treffer in der Liste
    }

    @Test
    fun localServerIpFallsBackToFirstNonLoopbackWhenNoPreferredIface() {
        val (server, _) = newServer()
        val ip = server.localServerIp { listOf("eth0" to "10.0.0.1", "tun0" to "172.16.0.1") }
        assertEquals("10.0.0.1", ip) // erster Nicht-Loopback-Eintrag
    }

    @Test
    fun localServerIpFallsBackToConfigTargetIpWhenNoInterface() {
        val (server, _) = newServer()
        val ip = server.localServerIp { emptyList() }
        assertEquals("192.168.43.1", ip) // OneHardwareConfig().targetIp
    }

    @Test
    fun currentTelemetryJsonReflectsLiveStateChanges() {
        val (server, fake) = newServer()
        // Initial leer → distance 0.
        assertTrue(server.currentTelemetryJson().contains("\"distance\":0.0"))

        fake.state.value = OneHardwareState(
            cableController = CableControllerState(meterReading = 7.5f)
        )
        val t = OneRemoteProtocol.telemetryFrom(
            Gson().fromJson(server.currentTelemetryJson(), SdkSendData::class.java).miniPushInfo
        )!!
        assertEquals(7.5f, t.rawDistance, 0.0001f)
    }
}

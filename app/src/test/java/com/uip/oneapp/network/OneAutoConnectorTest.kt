package com.uip.oneapp.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-Reconnect W1: Zustandsmaschine des [OneAutoConnector] mit Fake-WifiController —
 * App-Start-Trigger, Trigger C (bereits im Netz), Abriss mit genau EINEM Retry,
 * SSID-Präfix-Filter, Setting-Gate, Modus-Gate und Race gegen den manuellen Connect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OneAutoConnectorTest {

    private class FakeStorage : SecretKeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun put(key: String, value: String) { map[key] = value }
        override fun get(key: String): String? = map[key]
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private class FakeWifi : AutoConnectWifi {
        var scanResults: List<WifiNetwork> = emptyList()
        var currentSsid: String? = null
        var bindResult = true
        var bindCalls = 0
        var scanCalls = 0

        data class ConnectCall(val ssid: String, val password: String, val secured: Boolean)
        val connects = mutableListOf<ConnectCall>()
        var lastOnAvailable: (() -> Unit)? = null
        var lastOnUnavailable: (() -> Unit)? = null
        var lastOnLost: (() -> Unit)? = null

        override suspend fun scan(): List<WifiNetwork> {
            scanCalls++
            return scanResults
        }

        override fun connectViaRequest(
            ssid: String,
            password: String,
            secured: Boolean,
            onAvailable: () -> Unit,
            onUnavailable: () -> Unit,
            onLost: () -> Unit,
        ) {
            connects += ConnectCall(ssid, password, secured)
            lastOnAvailable = onAvailable
            lastOnUnavailable = onUnavailable
            lastOnLost = onLost
        }

        override fun currentWifiSsid(): String? = currentSsid

        override fun bindToCurrentWifi(): Boolean {
            bindCalls++
            return bindResult
        }
    }

    private fun net(ssid: String, rssi: Int = -50) =
        WifiNetwork(ssid = ssid, bssid = "aa:bb:cc", level = 3, rssi = rssi, secured = true)

    private fun storeWith(vararg ssids: String): KnownOneStore {
        val store = KnownOneStore(FakeStorage(), clock = { 1L })
        ssids.forEach { store.save(it, "pass-$it") }
        return store
    }

    @Test
    fun `Trigger A — App-Start verbindet automatisch mit bekannter ONE aus dem Scan`() = runTest {
        val wifi = FakeWifi().apply {
            scanResults = listOf(net("OfficeWLAN", -30), net("DrainQ-ONE-abc", -60))
        }
        var chainStarts = 0
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = { chainStarts++ },
        )

        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()

        assertEquals(1, wifi.connects.size)
        assertEquals("DrainQ-ONE-abc", wifi.connects[0].ssid)
        assertEquals("pass-DrainQ-ONE-abc", wifi.connects[0].password)
        assertTrue(wifi.connects[0].secured)
        assertEquals(AutoConnectPhase.CONNECTING, connector.state.value.phase)

        wifi.lastOnAvailable!!.invoke()
        runCurrent()

        assertEquals(AutoConnectPhase.CONNECTED, connector.state.value.phase)
        assertEquals("DrainQ-ONE-abc", connector.state.value.ssid)
        assertEquals(1, chainStarts)
    }

    @Test
    fun `Trigger C — bereits im bekannten Netz bindet nur, kein Specifier-Request`() = runTest {
        val wifi = FakeWifi().apply { currentSsid = "DrainQ-ONE-abc" }
        var chainStarts = 0
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = { chainStarts++ },
        )

        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()

        assertEquals(0, wifi.connects.size)
        assertEquals(0, wifi.scanCalls)
        assertEquals(1, wifi.bindCalls)
        assertEquals(AutoConnectPhase.CONNECTED, connector.state.value.phase)
        assertEquals(1, chainStarts)
    }

    @Test
    fun `Trigger B — Abriss loest genau EINEN Retry aus, danach LOST-Banner`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("DrainQ-ONE-abc")) }
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = {},
        )
        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()
        wifi.lastOnAvailable!!.invoke()
        runCurrent()
        assertEquals(AutoConnectPhase.CONNECTED, connector.state.value.phase)

        // Abriss → Backoff → genau ein zweiter Verbindungsversuch.
        wifi.lastOnLost!!.invoke()
        runCurrent()
        assertEquals(AutoConnectPhase.CONNECTING, connector.state.value.phase)
        assertEquals(1, wifi.connects.size)
        advanceTimeBy(OneAutoConnector.RETRY_BACKOFF_MS)
        runCurrent()
        assertEquals(2, wifi.connects.size)

        // Retry scheitert → LOST (Banner), KEIN weiterer Auto-Versuch.
        wifi.lastOnUnavailable!!.invoke()
        runCurrent()
        assertEquals(AutoConnectPhase.LOST, connector.state.value.phase)
        assertEquals("DrainQ-ONE-abc", connector.state.value.ssid)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, wifi.connects.size)
    }

    @Test
    fun `Retry-Button setzt den verbrauchten Auto-Retry zurueck`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("DrainQ-ONE-abc")) }
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = {},
        )
        // Direkt in den LOST-Zustand fahren (Abriss nach externem Join, Retry scheitert).
        connector.noteExternalJoin("DrainQ-ONE-abc")
        runCurrent()
        connector.noteExternalLoss("DrainQ-ONE-abc")
        runCurrent()
        advanceTimeBy(OneAutoConnector.RETRY_BACKOFF_MS)
        runCurrent()
        wifi.lastOnUnavailable!!.invoke()
        runCurrent()
        assertEquals(AutoConnectPhase.LOST, connector.state.value.phase)
        val connectsBefore = wifi.connects.size

        connector.retry()
        runCurrent()
        assertEquals(connectsBefore + 1, wifi.connects.size)
    }

    @Test
    fun `Setting AUS — kein Scan, kein Verbindungsversuch`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("DrainQ-ONE-abc")) }
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { false },
            startHardwareChain = {},
        )
        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()

        assertEquals(0, wifi.scanCalls)
        assertEquals(0, wifi.connects.size)
        assertEquals(AutoConnectPhase.IDLE, connector.state.value.phase)
    }

    @Test
    fun `DIRECT-Modus — start ist ein No-op`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("DrainQ-ONE-abc")) }
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.DIRECT,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = {},
        )
        connector.start()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(0, wifi.scanCalls)
        assertEquals(0, wifi.connects.size)
    }

    @Test
    fun `Keine gekoppelte ONE — kein Scan (leerer Store)`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("DrainQ-ONE-fremd")) }
        val connector = OneAutoConnector(
            wifi = wifi,
            store = KnownOneStore(FakeStorage()),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = {},
        )
        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()

        assertEquals(0, wifi.scanCalls)
        assertEquals(0, wifi.connects.size)
    }

    @Test
    fun `Keine bekannte ONE in Reichweite — still bleiben (kein LOST-Banner beim Start)`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("OfficeWLAN"), net("DrainQ-ONE-fremd")) }
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = {},
        )
        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()

        assertEquals(1, wifi.scanCalls)
        assertEquals(0, wifi.connects.size)
        assertEquals(AutoConnectPhase.IDLE, connector.state.value.phase)
    }

    @Test
    fun `Race — manueller Connect entwertet spaete Callbacks des Auto-Versuchs`() = runTest {
        val wifi = FakeWifi().apply { scanResults = listOf(net("DrainQ-ONE-abc")) }
        var chainStarts = 0
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = { chainStarts++ },
        )
        connector.start()
        advanceTimeBy(OneAutoConnector.START_DELAY_MS)
        runCurrent()
        assertEquals(1, wifi.connects.size)
        val staleOnAvailable = wifi.lastOnAvailable!!

        // Nutzer startet manuellen Connect → Auto-Versuch wird entwertet …
        connector.noteManualConnectStarted()
        runCurrent()
        assertEquals(AutoConnectPhase.IDLE, connector.state.value.phase)

        // … der späte Callback des überholten Versuchs darf nichts mehr bewirken.
        staleOnAvailable.invoke()
        runCurrent()
        assertEquals(AutoConnectPhase.IDLE, connector.state.value.phase)
        assertEquals(0, chainStarts)
    }

    @Test
    fun `Externer Join — Zustand synchronisiert, Hardware-Kette gestartet, Praefix-Filter aktiv`() = runTest {
        val wifi = FakeWifi()
        var chainStarts = 0
        val connector = OneAutoConnector(
            wifi = wifi,
            store = storeWith("DrainQ-ONE-abc"),
            mode = HardwareMode.WIFI,
            scope = backgroundScope,
            autoConnectEnabled = { true },
            startHardwareChain = { chainStarts++ },
        )

        connector.noteExternalJoin("OfficeWLAN") // kein ONE-Präfix → ignoriert
        runCurrent()
        assertEquals(AutoConnectPhase.IDLE, connector.state.value.phase)
        assertEquals(0, chainStarts)

        connector.noteExternalJoin("DrainQ-ONE-abc")
        runCurrent()
        assertEquals(AutoConnectPhase.CONNECTED, connector.state.value.phase)
        assertEquals(1, chainStarts)
    }
}

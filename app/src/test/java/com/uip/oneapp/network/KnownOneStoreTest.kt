package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-Reconnect W1: Store-Roundtrip mit Fake-Storage (kein Android/Keystore),
 * SSID-Präfix-Filter und bestMatch-Auswahl.
 */
class KnownOneStoreTest {

    private class FakeStorage : SecretKeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun put(key: String, value: String) { map[key] = value }
        override fun get(key: String): String? = map[key]
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private fun net(ssid: String, rssi: Int = -50) =
        WifiNetwork(ssid = ssid, bssid = "aa:bb:cc", level = 3, rssi = rssi, secured = true)

    @Test
    fun `save und get liefern den Eintrag zurueck (Roundtrip)`() {
        val store = KnownOneStore(FakeStorage(), clock = { 42L })
        assertTrue(store.save("DrainQ-ONE-abc123", "geheim99", WifiQr.SECURITY_WPA))

        val entry = store.get("DrainQ-ONE-abc123")!!
        assertEquals("DrainQ-ONE-abc123", entry.ssid)
        assertEquals("geheim99", entry.passphrase)
        assertEquals(WifiQr.SECURITY_WPA, entry.security)
        assertEquals(42L, entry.lastConnectedEpochMs)
        assertTrue(entry.secured)
    }

    @Test
    fun `save lehnt SSIDs ohne ONE-Praefix ab — keine Office-WLANs horten`() {
        val store = KnownOneStore(FakeStorage())
        assertFalse(store.save("OfficeWLAN", "firmenpasswort"))
        assertFalse(store.save("drainq-one-klein", "x")) // Präfix ist case-sensitiv gebrandet
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `all sortiert nach zuletzt verbunden absteigend`() {
        var now = 0L
        val store = KnownOneStore(FakeStorage(), clock = { ++now })
        store.save("DrainQ-ONE-alt", "p1")
        store.save("DrainQ-ONE-neu", "p2")

        assertEquals(listOf("DrainQ-ONE-neu", "DrainQ-ONE-alt"), store.all().map { it.ssid })

        store.touch("DrainQ-ONE-alt")
        assertEquals(listOf("DrainQ-ONE-alt", "DrainQ-ONE-neu"), store.all().map { it.ssid })
    }

    @Test
    fun `forget loescht den Eintrag rueckstandsfrei`() {
        val storage = FakeStorage()
        val store = KnownOneStore(storage)
        store.save("DrainQ-ONE-abc", "geheim99")
        store.forget("DrainQ-ONE-abc")

        assertNull(store.get("DrainQ-ONE-abc"))
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `bestMatch trifft nur exakte bekannte SSIDs und nimmt das staerkste Signal`() {
        val store = KnownOneStore(FakeStorage())
        store.save("DrainQ-ONE-nah", "p1")
        store.save("DrainQ-ONE-fern", "p2")

        val match = store.bestMatch(
            listOf(
                net("OfficeWLAN", rssi = -30),        // stärker, aber unbekannt
                net("DrainQ-ONE-fern", rssi = -80),
                net("DrainQ-ONE-nah", rssi = -55),
                net("DrainQ-ONE-fremd", rssi = -40),  // Präfix, aber nie gekoppelt
            )
        )
        assertEquals("DrainQ-ONE-nah", match?.ssid)
    }

    @Test
    fun `bestMatch liefert null wenn keine bekannte ONE in Reichweite`() {
        val store = KnownOneStore(FakeStorage())
        store.save("DrainQ-ONE-abc", "p")
        assertNull(store.bestMatch(listOf(net("OfficeWLAN"), net("Nachbar"))))
        assertNull(store.bestMatch(emptyList()))
    }

    @Test
    fun `korrupter Eintrag wird uebersprungen statt zu crashen`() {
        val storage = FakeStorage()
        val store = KnownOneStore(storage)
        store.save("DrainQ-ONE-ok", "p")
        storage.map["DrainQ-ONE-kaputt"] = "kein json {"

        assertEquals(listOf("DrainQ-ONE-ok"), store.all().map { it.ssid })
        assertNull(store.get("DrainQ-ONE-kaputt"))
    }

    @Test
    fun `toString maskiert die Passphrase (KRITIS — nie loggen)`() {
        val entry = KnownOne("DrainQ-ONE-abc", "strengGeheim42", WifiQr.SECURITY_WPA, 1L)
        assertFalse(entry.toString().contains("strengGeheim42"))
    }

    @Test
    fun `isOneSsid filtert auf das gebrandete Praefix`() {
        assertTrue(KnownOneStore.isOneSsid("DrainQ-ONE-abc"))
        assertFalse(KnownOneStore.isOneSsid("OfficeWLAN"))
        assertFalse(KnownOneStore.isOneSsid(""))
    }
}

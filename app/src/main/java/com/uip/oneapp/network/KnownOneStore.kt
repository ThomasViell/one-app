package com.uip.oneapp.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// HINWEIS: Diese Datei trennt bewusst zwei Schichten (Muster AccessPointController/SoftAp):
//  - [KnownOneStore] / [KnownOne] = Android-frei, unit-getestet (KEIN android.util.Log).
//  - [AndroidEncryptedStorage] (eigene Datei) = die einzige Android-/Keystore-behaftete
//    Schicht: EncryptedSharedPreferences mit Master-Key im Android Keystore.

/**
 * Persistenz-Seam für geheimes Schlüssel/Wert-Material — im Test ein In-Memory-Fake,
 * in der App [AndroidEncryptedStorage] (verschlüsselt at rest, Keystore-Master-Key).
 */
interface SecretKeyValueStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * Eine einmal per QR gekoppelte ONE (Auto-Reconnect W1). [security] trägt das
 * WIFI-QR-Token ([WifiQr.SECURITY_WPA]/[WifiQr.SECURITY_OPEN]).
 */
@Serializable
data class KnownOne(
    val ssid: String,
    val passphrase: String,
    val security: String = WifiQr.SECURITY_WPA,
    val lastConnectedEpochMs: Long = 0L,
) {
    /** Verschlüsseltes Netz? Steuert den [WifiController]-Pfad (analog [WifiQr.Credentials]). */
    val secured: Boolean
        get() = security.isNotEmpty() && !security.equals(WifiQr.SECURITY_OPEN, ignoreCase = true)

    /** KRITIS: Passphrase darf NIE in Logs/toString landen — auch nicht gekürzt. */
    override fun toString(): String =
        "KnownOne(ssid=$ssid, security=$security, lastConnectedEpochMs=$lastConnectedEpochMs)"
}

/**
 * Speichert pro gekoppelter ONE die WLAN-Zugangsdaten (Auto-Reconnect W1). Nimmt NUR
 * SSIDs mit dem gebrandeten Präfix [SoftApSpec.SSID_PREFIX] an — Office-WLANs werden
 * nie gehortet. Die Ablage selbst ist hinter [SecretKeyValueStore] gekapselt.
 */
class KnownOneStore(
    private val storage: SecretKeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Speichert/aktualisiert die Zugangsdaten einer ONE (setzt [KnownOne.lastConnectedEpochMs]
     * auf jetzt). SSIDs ohne ONE-Präfix werden abgelehnt (false).
     */
    fun save(ssid: String, passphrase: String, security: String = WifiQr.SECURITY_WPA): Boolean {
        if (!isOneSsid(ssid)) return false
        val entry = KnownOne(ssid, passphrase, security, clock())
        storage.put(ssid, json.encodeToString(entry))
        return true
    }

    /** Alle bekannten ONEs, zuletzt verbundene zuerst. */
    fun all(): List<KnownOne> =
        storage.keys().mapNotNull { read(it) }.sortedByDescending { it.lastConnectedEpochMs }

    /** Die bekannte ONE zu [ssid], falls gekoppelt. */
    fun get(ssid: String): KnownOne? = read(ssid)

    /** Kopplung rückstandsfrei löschen. */
    fun forget(ssid: String) = storage.remove(ssid)

    /** Verbindungszeitpunkt aktualisieren (nach jedem erfolgreichen Join). */
    fun touch(ssid: String) {
        val entry = read(ssid) ?: return
        storage.put(ssid, json.encodeToString(entry.copy(lastConnectedEpochMs = clock())))
    }

    /**
     * Beste bekannte ONE in Reichweite: exakter SSID-Match gegen die Scan-Liste, bei
     * mehreren Treffern gewinnt das stärkste Signal. `null` wenn keine in Reichweite.
     */
    fun bestMatch(scanResults: List<WifiNetwork>): KnownOne? {
        val known = all().associateBy { it.ssid }
        return scanResults
            .filter { it.ssid in known }
            .maxByOrNull { it.rssi }
            ?.let { known[it.ssid] }
    }

    private fun read(key: String): KnownOne? = storage.get(key)?.let {
        try { json.decodeFromString<KnownOne>(it) } catch (_: Exception) { null }
    }

    companion object {
        /** Nur gebrandete ONE-Hotspots (`DrainQ-ONE-*`) werden gespeichert/auto-verbunden. */
        fun isOneSsid(ssid: String): Boolean = ssid.startsWith(SoftApSpec.SSID_PREFIX)
    }
}

package com.uip.oneapp.network

import kotlin.random.Random

// HINWEIS: Diese Datei trägt die **reine, Android-freie** SoftAP-Zugangsdaten-Logik des
// privilegierten Tablet-Hotspots (Dual-Modus, Welle 3a). Sie ist voll unit-getestet
// (vgl. [SoftApSpecTest]). Der Android-/Privileg-/SharedPreferences-behaftete Teil liegt
// ausschließlich in `AndroidSoftApStarter.kt` ([AndroidSoftApCredentialStore],
// [AndroidSoftApStarter]) — der einzigen Schicht mit Geräte-Test.

/**
 * Persistente, **gebrandete** SoftAP-Zugangsdaten (Dual-Modus, Welle 3a). Anders als der
 * frühere LocalOnlyHotspot (Plattform würfelt die SSID je Sitzung) setzt der privilegierte
 * SoftAP-Pfad eine **feste, wiedererkennbare** [ssid] und ein **einmalig erzeugtes, persistentes**
 * [passphrase]. Wird als WIFI-QR ([WifiQr.encode]) an das Tablet gekoppelt.
 */
data class SoftApCredentials(val ssid: String, val passphrase: String)

/**
 * Reine (Android-freie) Erzeugungs-/Validierungslogik der gebrandeten SoftAP-Zugangsdaten.
 *  - **SSID:** `DrainQ-ONE-<serial>` — fester, gebrandeter Präfix + bereinigte Geräte-Seriennummer.
 *  - **Passphrase:** zufällig aus einem WPA2- **und** QR-sicheren Alphabet (s. u.).
 *
 * Bewusst ohne Android: die Seriennummer/Zufallsquelle werden hineingereicht
 * ([SoftApCredentialProvisioner] / [AndroidSoftApCredentialStore]), damit die Format-,
 * Längen- und Bereinigungsregeln deterministisch testbar sind.
 */
object SoftApSpec {

    /** Gebrandeter, fester SSID-Präfix — macht den ONE-Hotspot in der WLAN-Liste erkennbar. */
    const val SSID_PREFIX = "DrainQ-ONE-"

    /** IEEE-802.11-Grenze: eine SSID darf höchstens 32 Byte tragen. */
    const val SSID_MAX_BYTES = 32

    /** Länge der erzeugten Passphrase (WPA2-PSK erlaubt 8..63 druckbare ASCII-Zeichen). */
    const val PASSPHRASE_LENGTH = 12

    /**
     * Alphabet der Passphrase. Bewusst OHNE
     *  - mehrdeutige Glyphen `0/O`, `1/l/I` (Vorlesbarkeit), und
     *  - die WIFI-QR-Sonderzeichen `\ ; , : "` sowie Leerzeichen (sonst Escaping/Scanner-Risiko).
     * Alles druckbares ASCII → jede erzeugte Passphrase ist WPA2-gültig und kodiert sauber im QR.
     */
    const val PASSPHRASE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

    /** Bereinigt die Roh-Seriennummer auf `[A-Za-z0-9-]`; leer → stabiler Fallback `device`. */
    fun sanitizeSerial(serial: String?): String {
        val cleaned = serial?.trim().orEmpty().filter {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-'
        }
        return cleaned.ifEmpty { "device" }
    }

    /**
     * Baut die gebrandete SSID. Hält die 32-Byte-Grenze ein, indem ein zu langer Serial-Suffix
     * von HINTEN gekürzt wird (die hinteren Stellen tragen typ. die Geräte-Unterscheidung).
     */
    fun buildSsid(serial: String?): String {
        val suffix = sanitizeSerial(serial)
        val maxSuffix = (SSID_MAX_BYTES - SSID_PREFIX.length).coerceAtLeast(1)
        val trimmed = if (suffix.length > maxSuffix) suffix.takeLast(maxSuffix) else suffix
        return SSID_PREFIX + trimmed
    }

    /** Würfelt eine neue Passphrase aus [PASSPHRASE_ALPHABET]. */
    fun generatePassphrase(random: Random = Random.Default, length: Int = PASSPHRASE_LENGTH): String {
        require(length in 8..63) { "WPA2-Passphrase muss 8..63 Zeichen lang sein" }
        val sb = StringBuilder(length)
        repeat(length) { sb.append(PASSPHRASE_ALPHABET[random.nextInt(PASSPHRASE_ALPHABET.length)]) }
        return sb.toString()
    }

    /** WPA2-PSK-Gültigkeit: 8..63 Zeichen, ausschließlich druckbares ASCII (0x20..0x7E). */
    fun isValidPassphrase(p: String): Boolean =
        p.length in 8..63 && p.all { it.code in 0x20..0x7E }

    /** SSID-Gültigkeit: 1..32 Byte (UTF-8). */
    fun isValidSsid(s: String): Boolean =
        s.toByteArray(Charsets.UTF_8).size in 1..SSID_MAX_BYTES
}

/**
 * Reine Provisionierung der [SoftApCredentials]: leitet die SSID **immer** aus [serial] ab
 * (stabil), übernimmt aber eine bereits persistierte [existingPassphrase] **unverändert**
 * ("einmalig erzeugen + persistent speichern"), sofern sie gültig ist — sonst wird mit [random]
 * eine neue erzeugt. Das Geheimnis wird also nie neu gewürfelt, solange ein gültiges vorliegt.
 */
object SoftApCredentialProvisioner {
    fun provision(serial: String?, existingPassphrase: String?, random: Random): SoftApCredentials {
        val ssid = SoftApSpec.buildSsid(serial)
        val passphrase = existingPassphrase
            ?.takeIf { SoftApSpec.isValidPassphrase(it) }
            ?: SoftApSpec.generatePassphrase(random)
        return SoftApCredentials(ssid, passphrase)
    }
}

/**
 * Seam für das Laden/Erzeugen der persistenten SoftAP-Zugangsdaten — trennt die (testbare)
 * Provisionierungslogik vom Android-/SharedPreferences-Zugriff (vgl. [HotspotStarter]).
 * Android-Impl: [AndroidSoftApCredentialStore].
 */
fun interface SoftApCredentialStore {
    /** Lädt die persistenten Zugangsdaten oder erzeugt + speichert sie einmalig. */
    fun loadOrCreate(): SoftApCredentials
}

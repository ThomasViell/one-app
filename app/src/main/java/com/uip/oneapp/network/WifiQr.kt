package com.uip.oneapp.network

/**
 * Reines (Android-freies) **WIFI-QR-Format** (Dual-Modus, Welle 3a) — die Kopplungs-Brücke
 * zwischen ONE und Tablet:
 *  - **ONE** ([com.uip.oneapp.ui.screens.pairing.PairingScreen]) kodiert die vom
 *    [AccessPointController] gelieferten LocalOnlyHotspot-Zugangsdaten als QR.
 *  - **Tablet** ([com.uip.oneapp.ui.screens.network.NetworkViewModel]) scannt den QR und
 *    parst SSID/Passwort, um per [WifiController] beizutreten.
 *
 * Format (De-facto-Standard, von Android-Kamera/Google-Lens nativ verstanden):
 * `WIFI:T:WPA2;S:<ssid>;P:<pass>;;`. Innerhalb der S-/P-Werte werden die Sonderzeichen
 * `\ ; , : "` mit Backslash escaped (sonst zerbricht das Feld-Parsing). Ohne Android und
 * ohne ZXing voll testbar — die ZXing-Lib rendert/liest nur das hier erzeugte/erwartete
 * String-Payload.
 */
object WifiQr {

    private const val PREFIX = "WIFI:"

    /**
     * Sicherheits-Typ-Token im T-Feld. Der De-facto-Standard kennt für PSK-Netze nur `WPA`
     * (Sammel-Token für WPA **und** WPA2-PSK — LocalOnlyHotspot ist WPA2-PSK); native Scanner
     * (Android-Kamera/Google Lens) erkennen `WPA2` NICHT zuverlässig. Daher kodieren wir `WPA`.
     * Beim Parsen gilt jedes Nicht-`nopass`-Token als verschlüsselt ([Credentials.secured]).
     */
    const val SECURITY_WPA = "WPA"
    const val SECURITY_OPEN = "nopass"

    /** Geparste Zugangsdaten aus einem WIFI-QR. */
    data class Credentials(
        val ssid: String,
        val passphrase: String,
        val security: String,
        val hidden: Boolean,
    ) {
        /** Verschlüsseltes Netz? (alles außer `nopass`/leer). Steuert den [WifiController]-Pfad. */
        val secured: Boolean get() = security.isNotEmpty() && !security.equals(SECURITY_OPEN, ignoreCase = true)
    }

    /**
     * Baut das QR-Payload. Für offene Netze (`security == nopass` oder leeres Passwort) entfällt
     * das P-Feld — exakt wie der Standard es vorsieht.
     */
    fun encode(
        ssid: String,
        passphrase: String,
        security: String = SECURITY_WPA,
        hidden: Boolean = false,
    ): String {
        val open = security.equals(SECURITY_OPEN, ignoreCase = true) || passphrase.isEmpty()
        val sb = StringBuilder(PREFIX)
        sb.append("T:").append(if (open) SECURITY_OPEN else security).append(';')
        sb.append("S:").append(escape(ssid)).append(';')
        if (!open) sb.append("P:").append(escape(passphrase)).append(';')
        if (hidden) sb.append("H:true;")
        sb.append(';')
        return sb.toString()
    }

    /**
     * Parst ein WIFI-QR-Payload. Toleriert beliebige Feld-Reihenfolge und Groß-/Kleinschreibung
     * von Prefix/Keys. Liefert `null`, wenn das Payload kein `WIFI:`-Code ist oder keine SSID trägt.
     */
    fun parse(payload: String): Credentials? {
        val trimmed = payload.trim()
        if (!trimmed.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return null
        val body = trimmed.substring(PREFIX.length)

        var ssid: String? = null
        var passphrase = ""
        var security = SECURITY_WPA
        var hidden = false

        for (field in splitUnescaped(body, ';')) {
            if (field.isEmpty()) continue
            val sep = indexOfUnescaped(field, ':')
            if (sep < 0) continue
            val key = field.substring(0, sep)
            val value = unescape(field.substring(sep + 1))
            when (key.trim().uppercase()) {
                "S" -> ssid = value
                "P" -> passphrase = value
                "T" -> if (value.isNotEmpty()) security = value
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }

        val s = ssid ?: return null
        if (s.isEmpty()) return null
        return Credentials(s, passphrase, security, hidden)
    }

    // ===== Escaping (MECARD-Stil) =====

    private fun escape(value: String): String {
        val sb = StringBuilder(value.length)
        for (c in value) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                sb.append(value[i + 1]); i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    /** Splittet an `delim`, ignoriert mit Backslash escapte Vorkommen. */
    private fun splitUnescaped(s: String, delim: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                cur.append(c).append(s[i + 1]); i += 2; continue
            }
            if (c == delim) {
                out.add(cur.toString()); cur.setLength(0); i++; continue
            }
            cur.append(c); i++
        }
        out.add(cur.toString())
        return out
    }

    /** Index des ersten nicht-escapten `delim`, sonst -1. */
    private fun indexOfUnescaped(s: String, delim: Char): Int {
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                delim -> return i
                else -> i++
            }
        }
        return -1
    }
}

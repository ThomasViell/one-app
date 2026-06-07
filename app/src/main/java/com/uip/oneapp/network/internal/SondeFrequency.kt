package com.uip.oneapp.network.internal

/**
 * Sonde-Ortungsfrequenzen — EINE Quelle der Wahrheit für TX (UI-Auswahl, Frame-Byte 4) und
 * RX (Statusanzeige/OSD). Behebt M8 (zuvor widersprachen sich UI-Auswahl und Anzeige).
 *
 * Codes aus dem OEM-Decompile (one-revers, MiniPushControlHelper / ControlArgs):
 *   0 = Off, 1 = 33 kHz, 2 = 640 Hz, 3 = 512 Hz.
 *
 * Falls die On-Device-Verifikation (V2) zeigt, dass die ONE-Firmware eine andere physische
 * Zuordnung sendet, MUSS nur [name] hier angepasst werden — TX und RX bleiben automatisch
 * konsistent, weil beide diese Tabelle nutzen.
 */
object SondeFrequency {
    const val OFF = 0

    /** Wählbare Frequenz-Codes in Anzeige-Reihenfolge (ohne Off; Off wird separat gerendert). */
    val selectableCodes = listOf(1, 2, 3)

    /** Code → Anzeigename. Einheiten sind sprachneutral (kein i18n nötig). */
    fun name(code: Int): String = when (code) {
        0 -> "Off"
        1 -> "33 kHz"
        2 -> "640 Hz"
        3 -> "512 Hz"
        else -> "Unknown ($code)"
    }
}

package com.uip.oneapp.ui.screens.inspection

import org.junit.Assert.*
import org.junit.Test

/**
 * Stufe 1 — Meter-Eingabeschutz:
 * Foto/Schaden/Notiz aus dem Video dürfen nicht mit 0.00 m gespeichert werden.
 * parseMeterInput gibt null zurück wenn Text leer UND fallback null ist →
 * der Dialog-Speicher-Handler bricht mit `?: return@TextButton` ab.
 */
class MeterInputTest {

    @Test
    fun blank_text_null_fallback_returns_null() {
        // Playback-Pfad ohne Meter-Spur: Speichern wird blockiert.
        assertNull(parseMeterInput("", null))
    }

    @Test
    fun garbage_text_null_fallback_returns_null() {
        assertNull(parseMeterInput("abc", null))
    }

    @Test
    fun valid_dot_decimal_null_fallback_parses() {
        assertEquals(12.5f, parseMeterInput("12.5", null))
    }

    @Test
    fun valid_comma_decimal_null_fallback_parses() {
        // Europäisches Komma wird zu Punkt normiert.
        assertEquals(12.5f, parseMeterInput("12,5", null))
    }

    @Test
    fun blank_text_nonnull_fallback_returns_fallback() {
        // Live-Pfad: meterValue vorbelegt, Nutzer löscht das Feld → Fallback greift.
        assertEquals(5.0f, parseMeterInput("", 5.0f))
    }

    @Test
    fun valid_text_overrides_nonnull_fallback() {
        assertEquals(3.0f, parseMeterInput("3.0", 1.0f))
    }

    @Test
    fun zero_text_is_valid_not_blocked() {
        // Wert 0.00 m muss erlaubt sein wenn der Nutzer ihn explizit eingibt.
        assertEquals(0.0f, parseMeterInput("0", null))
        assertEquals(0.0f, parseMeterInput("0.00", null))
    }

    @Test
    fun whitespace_only_null_fallback_returns_null() {
        assertNull(parseMeterInput("   ", null))
    }
}

package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die reine SoftAP-Spezifikation (Welle 3a, Dual-Modus) ab — SSID-Bildung,
 * Passphrase-Regel und die Start-Vorbedingungen (Modus-Gate + STA/AP-Exklusivität) — ohne
 * Android. Der privilegierte AP-Start selbst ist Geräte-Test (siehe AccessPointController).
 */
class AccessPointSpecTest {

    // ===== SSID =====

    @Test
    fun ssidAppendsSanitizedSerial() {
        assertEquals("DrainQ-ONE-C182026", AccessPointSpec.buildSsid("C18-2026"))
    }

    @Test
    fun ssidFallsBackToPrefixWhenSerialMissing() {
        assertEquals("DrainQ-ONE", AccessPointSpec.buildSsid(null))
        assertEquals("DrainQ-ONE", AccessPointSpec.buildSsid(""))
        // Nur Sonderzeichen → nach Filterung leer → Präfix.
        assertEquals("DrainQ-ONE", AccessPointSpec.buildSsid("--  --"))
    }

    @Test
    fun ssidStripsNonAlphanumeric() {
        assertEquals("DrainQ-ONE-abc123", AccessPointSpec.buildSsid("a b!c-1.2_3"))
    }

    @Test
    fun ssidClampsTo32Octets() {
        val ssid = AccessPointSpec.buildSsid("0123456789012345678901234567890123456789")
        assertEquals(AccessPointSpec.SSID_MAX_LENGTH, ssid.length)
        assertTrue(ssid, ssid.startsWith("DrainQ-ONE-"))
    }

    // ===== Passphrase =====

    @Test
    fun passphraseLengthRule() {
        assertFalse(AccessPointSpec.isValidPassphrase("1234567"))         // 7 → zu kurz
        assertTrue(AccessPointSpec.isValidPassphrase("12345678"))         // 8 → ok
        assertTrue(AccessPointSpec.isValidPassphrase("a".repeat(63)))     // 63 → ok
        assertFalse(AccessPointSpec.isValidPassphrase("a".repeat(64)))    // 64 → zu lang
    }

    @Test
    fun defaultPassphraseIsValid() {
        // Schützt den hartkodierten Default: eine ungültige Länge würde beim Bau der
        // SoftApConfiguration auf dem Gerät werfen.
        assertTrue(AccessPointSpec.isValidPassphrase(AccessPointController.DEFAULT_PASSPHRASE))
    }

    // ===== Start-Gate =====

    @Test
    fun gateAllowsOnlyDirectMode() {
        assertEquals(
            AccessPointSpec.GateResult.NOT_DIRECT_MODE,
            AccessPointSpec.gate(HardwareMode.WIFI, staConnected = false, supportsStaApConcurrency = false)
        )
        // Modus wird zuerst geprüft — auch mit Parallelität bleibt WIFI abgelehnt.
        assertEquals(
            AccessPointSpec.GateResult.NOT_DIRECT_MODE,
            AccessPointSpec.gate(HardwareMode.WIFI, staConnected = true, supportsStaApConcurrency = true)
        )
    }

    @Test
    fun gateBlocksActiveStaWithoutConcurrency() {
        assertEquals(
            AccessPointSpec.GateResult.STA_ACTIVE_NO_CONCURRENCY,
            AccessPointSpec.gate(HardwareMode.DIRECT, staConnected = true, supportsStaApConcurrency = false)
        )
    }

    @Test
    fun gateOkWhenDirectAndNoStaConflict() {
        // Direkt + kein STA aktiv → ok.
        assertEquals(
            AccessPointSpec.GateResult.OK,
            AccessPointSpec.gate(HardwareMode.DIRECT, staConnected = false, supportsStaApConcurrency = false)
        )
        // Direkt + STA aktiv, aber Plattform kann STA+AP parallel → ok.
        assertEquals(
            AccessPointSpec.GateResult.OK,
            AccessPointSpec.gate(HardwareMode.DIRECT, staConnected = true, supportsStaApConcurrency = true)
        )
    }
}

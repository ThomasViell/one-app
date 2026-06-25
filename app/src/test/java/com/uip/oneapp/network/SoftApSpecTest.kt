package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Sichert die reine (Android-freie) SoftAP-Zugangsdaten-Logik ab (Welle 3a, Dual-Modus):
 * gebrandete SSID, WPA2-/QR-sichere Passphrase und die "einmalig erzeugen + wiederverwenden"-
 * Provisionierung. Der privilegierte Plattform-Start ([AndroidSoftApStarter]) ist Geräte-Test.
 */
class SoftApSpecTest {

    // ===== SSID =====

    @Test
    fun ssidIsBrandedWithSanitizedSerial() {
        assertEquals("DrainQ-ONE-RK3588-42", SoftApSpec.buildSsid("RK3588-42"))
    }

    @Test
    fun ssidSanitizesNonAlnumChars() {
        // Leer-/Sonderzeichen fliegen raus, Bindestrich bleibt.
        assertEquals("DrainQ-ONE-ab12-cd", SoftApSpec.buildSsid(" a b:1;2-c\"d "))
    }

    @Test
    fun ssidFallsBackWhenSerialBlankOrNull() {
        assertEquals("DrainQ-ONE-device", SoftApSpec.buildSsid(null))
        assertEquals("DrainQ-ONE-device", SoftApSpec.buildSsid("   "))
        assertEquals("DrainQ-ONE-device", SoftApSpec.buildSsid("@@@"))
    }

    @Test
    fun ssidStaysWithin32Bytes() {
        val ssid = SoftApSpec.buildSsid("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ") // 36 chars
        assertTrue("SSID ${ssid.length}B > 32B", SoftApSpec.isValidSsid(ssid))
        assertTrue(ssid.startsWith(SoftApSpec.SSID_PREFIX))
        // Suffix von hinten gekürzt → die letzten Serial-Stellen bleiben erhalten.
        assertTrue(ssid.endsWith("Z"))
    }

    @Test
    fun ssidIsDeterministicForSameSerial() {
        assertEquals(SoftApSpec.buildSsid("S-100"), SoftApSpec.buildSsid("S-100"))
    }

    // ===== Passphrase =====

    @Test
    fun generatedPassphraseIsWpa2Valid() {
        repeat(50) { i ->
            val p = SoftApSpec.generatePassphrase(Random(i.toLong()))
            assertEquals(SoftApSpec.PASSPHRASE_LENGTH, p.length)
            assertTrue("nicht WPA2-gültig: $p", SoftApSpec.isValidPassphrase(p))
        }
    }

    @Test
    fun generatedPassphraseAvoidsQrSpecialAndAmbiguousChars() {
        val forbidden = setOf('\\', ';', ',', ':', '"', ' ', '0', 'O', '1', 'l', 'I')
        repeat(50) { i ->
            val p = SoftApSpec.generatePassphrase(Random(i * 7L + 1))
            assertTrue("$p enthält verbotenes Zeichen", p.none { it in forbidden })
        }
    }

    @Test
    fun passphraseGenerationIsSeedDeterministic() {
        assertEquals(
            SoftApSpec.generatePassphrase(Random(99)),
            SoftApSpec.generatePassphrase(Random(99)),
        )
        assertNotEquals(
            SoftApSpec.generatePassphrase(Random(1)),
            SoftApSpec.generatePassphrase(Random(2)),
        )
    }

    @Test
    fun isValidPassphraseRejectsTooShortOrNonAscii() {
        assertFalse(SoftApSpec.isValidPassphrase("short")) // < 8
        assertFalse(SoftApSpec.isValidPassphrase("a".repeat(64))) // > 63
        assertFalse(SoftApSpec.isValidPassphrase("validlenéx")) // é nicht ASCII
        assertTrue(SoftApSpec.isValidPassphrase("Abcd2345"))
    }

    // ===== Provisionierung: einmalig erzeugen, dann wiederverwenden =====

    @Test
    fun provisionGeneratesWhenNoExistingPassphrase() {
        val c = SoftApCredentialProvisioner.provision("ABC", existingPassphrase = null, random = Random(5))
        assertEquals("DrainQ-ONE-ABC", c.ssid)
        assertTrue(SoftApSpec.isValidPassphrase(c.passphrase))
    }

    @Test
    fun provisionReusesValidExistingPassphrase() {
        val existing = "Keep2345me"
        val c = SoftApCredentialProvisioner.provision("ABC", existing, Random(5))
        assertEquals(existing, c.passphrase) // NICHT neu gewürfelt
    }

    @Test
    fun provisionRegeneratesInvalidExistingPassphrase() {
        val c = SoftApCredentialProvisioner.provision("ABC", existingPassphrase = "bad", random = Random(5))
        assertNotEquals("bad", c.passphrase)
        assertTrue(SoftApSpec.isValidPassphrase(c.passphrase))
    }

    @Test
    fun provisionAlwaysDerivesSsidFromSerial() {
        val c = SoftApCredentialProvisioner.provision("XY-9", "Keep2345me", Random(5))
        assertEquals("DrainQ-ONE-XY-9", c.ssid)
    }
}

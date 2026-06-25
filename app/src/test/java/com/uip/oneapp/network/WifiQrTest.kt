package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert das reine WIFI-QR-Format ([WifiQr], Welle 3a) ab — Encode, Parse, Escaping und
 * Round-Trip. Das ist der Vertrag zwischen ONE (Encode → QR-Anzeige) und Tablet (Scan → Parse
 * → Join); die ZXing-Lib transportiert nur dieses String-Payload.
 */
class WifiQrTest {

    @Test
    fun encodesStandardWpaPayload() {
        // Standard-Token ist „WPA" (Sammel-Token, deckt WPA2-PSK ab) — von nativen Scannern erkannt.
        assertEquals(
            "WIFI:T:WPA;S:MyNet;P:pass123;;",
            WifiQr.encode("MyNet", "pass123"),
        )
    }

    @Test
    fun encodesOpenNetworkWithoutPasswordField() {
        // Leeres Passwort → offenes Netz, kein P-Feld.
        assertEquals("WIFI:T:nopass;S:Open;;", WifiQr.encode("Open", ""))
        // Explizit nopass → ebenfalls kein P-Feld.
        assertEquals("WIFI:T:nopass;S:Open;;", WifiQr.encode("Open", "x", security = WifiQr.SECURITY_OPEN))
    }

    @Test
    fun escapesSpecialCharactersInSsidAndPassword() {
        val payload = WifiQr.encode("Net;A:B", "p,a\"s\\s")
        assertEquals("WIFI:T:WPA;S:Net\\;A\\:B;P:p\\,a\\\"s\\\\s;;", payload)
    }

    @Test
    fun parsesStandardPayload() {
        val c = WifiQr.parse("WIFI:T:WPA;S:MyNet;P:pass123;;")!!
        assertEquals("MyNet", c.ssid)
        assertEquals("pass123", c.passphrase)
        assertEquals("WPA", c.security)
        assertTrue(c.secured)
        assertFalse(c.hidden)
    }

    @Test
    fun parseStillAcceptsWpa2AndWpa3AsSecured() {
        // Rückwärts-/Vorwärts-kompatibel: andere PSK-Token (WPA2, WPA3, SAE) gelten als gesichert.
        assertTrue(WifiQr.parse("WIFI:T:WPA2;S:X;P:p;;")!!.secured)
        assertTrue(WifiQr.parse("WIFI:T:WPA3;S:X;P:p;;")!!.secured)
        assertTrue(WifiQr.parse("WIFI:T:SAE;S:X;P:p;;")!!.secured)
    }

    @Test
    fun parseToleratesFieldReorderAndHiddenFlag() {
        val c = WifiQr.parse("WIFI:S:Foo;H:true;P:Bar;T:WPA2;;")!!
        assertEquals("Foo", c.ssid)
        assertEquals("Bar", c.passphrase)
        assertTrue(c.hidden)
    }

    @Test
    fun parseOpenNetworkIsNotSecured() {
        val c = WifiQr.parse("WIFI:T:nopass;S:Gast;;")!!
        assertEquals("Gast", c.ssid)
        assertEquals("", c.passphrase)
        assertFalse(c.secured)
    }

    @Test
    fun roundTripsEscapedCredentials() {
        val ssid = "DrainQ;ONE:42"
        val pass = "a,b\"c\\d;e"
        val c = WifiQr.parse(WifiQr.encode(ssid, pass))!!
        assertEquals(ssid, c.ssid)
        assertEquals(pass, c.passphrase)
    }

    @Test
    fun rejectsNonWifiPayloads() {
        assertNull(WifiQr.parse("https://example.com"))
        assertNull(WifiQr.parse(""))
        // WIFI-Prefix, aber ohne SSID → kein verwertbarer Code.
        assertNull(WifiQr.parse("WIFI:T:WPA2;;"))
    }

    @Test
    fun parsePrefixIsCaseInsensitive() {
        assertEquals("X", WifiQr.parse("wifi:S:X;;")?.ssid)
    }
}

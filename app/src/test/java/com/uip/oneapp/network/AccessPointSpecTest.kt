package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert die reine Start-Vorbedingung des Tablet-Hotspots (Welle 3a, Dual-Modus) ab: der
 * Hotspot kommt nur im DIRECT-Modus hoch. Die STA/AP-Exklusivität ist kein Blocker mehr — der
 * privilegierte SoftAP-Pfad ([AccessPointController]/[AndroidSoftApStarter]) legt eine aktive
 * STA-Verbindung für die Hotspot-Dauer still. Der echte Plattform-Start ist Geräte-Test.
 */
class AccessPointSpecTest {

    @Test
    fun gateAllowsOnlyDirectMode() {
        assertEquals(AccessPointSpec.GateResult.OK, AccessPointSpec.gate(HardwareMode.DIRECT))
        assertEquals(AccessPointSpec.GateResult.NOT_DIRECT_MODE, AccessPointSpec.gate(HardwareMode.WIFI))
    }
}

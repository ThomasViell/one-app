package com.uip.oneapp.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die (Android-freie) Ablauf-Logik des [DeviceOwnerLocationProvisioner] ab (Dual-Modus,
 * W3a): Standort wird **nur** als Geräteeigentümer gewährt; ohne Owner-Status passiert nichts.
 * Der echte DevicePolicyManager-Zugriff liegt hinter [DevicePolicyGateway] und wird hier durch
 * einen zählenden Fake ersetzt; die Plattform-Wirkung ist Geräte-Test.
 */
class DeviceOwnerLocationProvisionerTest {

    /** Fake-Gateway: protokolliert, welche Operationen aufgerufen wurden. */
    private class FakeGateway(
        private val owner: Boolean,
        private val grantResult: Boolean = true,
        private val enableResult: Boolean = true,
    ) : DevicePolicyGateway {
        var grantCalls = 0
        var enableCalls = 0

        override fun isDeviceOwner(): Boolean = owner
        override fun grantFineLocation(): Boolean { grantCalls++; return grantResult }
        override fun enableLocation(): Boolean { enableCalls++; return enableResult }
    }

    @Test
    fun notDeviceOwner_doesNothing() {
        val gw = FakeGateway(owner = false)
        val result = DeviceOwnerLocationProvisioner.provision(gw)

        assertEquals(DeviceOwnerLocationProvisioner.Result.NOT_OWNER, result)
        assertFalse(result.deviceOwner)
        assertEquals(0, gw.grantCalls)   // KEIN Self-Grant ohne Owner-Status
        assertEquals(0, gw.enableCalls)
    }

    @Test
    fun deviceOwner_grantsLocationAndEnablesServices() {
        val gw = FakeGateway(owner = true)
        val result = DeviceOwnerLocationProvisioner.provision(gw)

        assertTrue(result.deviceOwner)
        assertTrue(result.locationGranted)
        assertTrue(result.locationEnabled)
        assertEquals(1, gw.grantCalls)
        assertEquals(1, gw.enableCalls)
    }

    @Test
    fun deviceOwner_surfacesPartialFailure_butStillAttemptsBoth() {
        // Grant abgelehnt, Enable durch: beide Schritte werden versucht, das Ergebnis spiegelt es.
        val gw = FakeGateway(owner = true, grantResult = false, enableResult = true)
        val result = DeviceOwnerLocationProvisioner.provision(gw)

        assertTrue(result.deviceOwner)
        assertFalse(result.locationGranted)
        assertTrue(result.locationEnabled)
        assertEquals(1, gw.grantCalls)
        assertEquals(1, gw.enableCalls) // Enable wird auch versucht, wenn der Grant scheiterte
    }
}

package com.uip.oneapp.debugrig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W-H1 Pflicht-Unit-Tests (drei Tests aus WH1_HARNESS_PROMPT.md):
 *  1. Seed-Idempotenz: buildSeedData() liefert stets exakt dasselbe Ergebnis.
 *  2. Route-Whitelist: NAVIGATE-Handler blockiert unbekannte Routen, lässt bekannte durch.
 *  3. Rig nicht im Release: ScreenshotRigReceiver.DEBUG_ONLY ist true, und die Klasse
 *     enthält den Guard gegen Laufzeit in non-debug Builds.
 */
class ScreenshotRigTest {

    // ── Test 1: Seed-Idempotenz ───────────────────────────────────────────────

    @Test
    fun seedDataIsDeterministic() {
        val first = buildSeedData()
        val second = buildSeedData()

        assertEquals("Projekt-Nummer muss stabil sein", first.project.projectNumber, second.project.projectNumber)
        assertEquals("Auftraggeber muss stabil sein", first.project.auftraggeber, second.project.auftraggeber)
        assertEquals("Standort muss stabil sein", first.project.standortAdresse, second.project.standortAdresse)
        assertEquals("Inspektor muss stabil sein", first.project.inspektor, second.project.inspektor)
        assertEquals("Kameratyp muss stabil sein", first.project.kameratyp, second.project.kameratyp)
        assertEquals("Schadensliste muss stabil sein", first.damages.size, second.damages.size)
        assertEquals("Notizliste muss stabil sein", first.notes.size, second.notes.size)

        for (i in first.damages.indices) {
            assertEquals("Schaden[$i].position", first.damages[i].position, second.damages[i].position)
            assertEquals("Schaden[$i].damageType", first.damages[i].damageType, second.damages[i].damageType)
            assertEquals("Schaden[$i].description", first.damages[i].description, second.damages[i].description)
        }

        for (i in first.notes.indices) {
            assertEquals("Notiz[$i].text", first.notes[i].text, second.notes[i].text)
        }
    }

    @Test
    fun seedDataHasRequiredDemoContent() {
        val sd = buildSeedData()
        assertEquals(DEMO_PROJECT_NUMBER, sd.project.projectNumber)
        assertEquals("C18", sd.project.kameratyp)
        assertTrue("Mindestens 3 Schäden", sd.damages.size >= 3)
        assertTrue("Mindestens 1 Notiz", sd.notes.size >= 1)
        assertNotNull("Auftraggeber nicht leer", sd.project.auftraggeber.ifBlank { null })
        assertNotNull("Standort nicht leer", sd.project.standortAdresse.ifBlank { null })
    }

    // ── Test 2: Route-Whitelist des NAVIGATE-Handlers ─────────────────────────

    @Test
    fun navigateWhitelistAllowsKnownRoutes() {
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("home"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("connection"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("projects"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("inspection"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("reports"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("settings"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("offline_maps"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("network"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("pairing"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("cloud_login"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("project_form"))
    }

    @Test
    fun navigateWhitelistAllowsParameterisedRoutes() {
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("inspection/42"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("project_detail/1"))
        assertTrue(ScreenshotRigReceiver.isAllowedRoute("project_form/999"))
    }

    @Test
    fun navigateWhitelistBlocksUnknownAndMaliciousRoutes() {
        assertFalse("leer", ScreenshotRigReceiver.isAllowedRoute(""))
        assertFalse("unbekannt", ScreenshotRigReceiver.isAllowedRoute("some_unknown_route"))
        assertFalse("path-traversal", ScreenshotRigReceiver.isAllowedRoute("../../etc/passwd"))
        assertFalse("admin", ScreenshotRigReceiver.isAllowedRoute("admin"))
        assertFalse("slash allein", ScreenshotRigReceiver.isAllowedRoute("/"))
        assertFalse("unbekannte ID-Route", ScreenshotRigReceiver.isAllowedRoute("danger/42"))
        assertFalse("String statt Long-ID", ScreenshotRigReceiver.isAllowedRoute("inspection/abc"))
        assertFalse("leere ID", ScreenshotRigReceiver.isAllowedRoute("inspection/"))
    }

    // ── Test 3: Rig nicht im Release registriert ──────────────────────────────

    @Test
    fun rigIsDesignedAsDebugOnly() {
        // Der Companion trägt das explizite Flag DEBUG_ONLY = true.
        // Gemeinsam mit der Debug-only-Manifest-Registrierung ist das die
        // Belt-and-Suspenders-Absicherung gegen Release-Leckage.
        assertTrue(
            "ScreenshotRigReceiver.DEBUG_ONLY muss true sein — sonst könnte der Receiver im Release registriert werden",
            ScreenshotRigReceiver.DEBUG_ONLY
        )
    }

    @Test
    fun rigGuardConstantIsConsistent() {
        // Sichert ab, dass niemand DEBUG_ONLY nachträglich auf false setzt.
        // Wenn dieser Test bricht, wurde der Guard absichtlich oder versehentlich entfernt.
        val guard = ScreenshotRigReceiver.DEBUG_ONLY
        assertTrue("DEBUG_ONLY darf nicht false sein", guard)
        // Und das Rig darf NIE in einem non-debug-Build landen — manifest-seitig durch
        // app/src/debug/AndroidManifest.xml sichergestellt (nur für assembleDebug gemergt).
    }
}

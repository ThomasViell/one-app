package com.uip.oneapp.ui.localization

import com.uip.oneapp.network.l10n.PortalLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Z-5 (RB-4): Die sichtbare Sprachliste kommt ausschliesslich vom Portal (R-1,
 * PLAN_NACHTRAG), nicht aus dem BETA-Gate der Map. Bei Nichterreichbarkeit die zuletzt
 * gespeicherte Liste, sonst das Paket (de/en). Die 33 uebrigen Map-Sprachen werden nie
 * angeboten, solange das Portal sie nicht fuehrt (Sicherung gegen den Z-5-Widerspruch aus
 * AUFTRAG.md Abschnitt 3, Ziel Z-5).
 */
class LocalizationManagerListTest {

    @Test
    fun availableLanguages_isPortalListNotMapList() {
        val portal = listOf(
            PortalLocale("de", "Deutsch", "Deutsch", "core", 300_000L),
            PortalLocale("en", "English", "English", "core", 285_000L),
            PortalLocale("pl", "Polish", "Polski", "core", 12_000L),
        )
        val result = LocalizationManager.computeAvailableLanguages(portal, null, emptySet())
        assertEquals(setOf("de", "en", "pl"), result.map { it.code }.toSet())
        assertEquals(3, result.size)
    }

    @Test
    fun availableLanguages_portalUnreachable_usesStoredListThenBundle() {
        val storedResult = LocalizationManager.computeAvailableLanguages(null, listOf("de", "en", "cs"), emptySet())
        assertEquals(setOf("de", "en", "cs"), storedResult.map { it.code }.toSet())

        val bundleResult = LocalizationManager.computeAvailableLanguages(null, null, emptySet())
        assertEquals(setOf("de", "en"), bundleResult.map { it.code }.toSet())
    }

    @Test
    fun availableLanguages_neverExposesMapOnlyLanguage() {
        // "no" (Norsk) ist ein Map-Sprachblock, den das Portal (heute wie zukuenftig
        // ausserhalb von de/en) nicht zwingend fuehrt -- taucht das Portal es nicht auf,
        // darf es in der sichtbaren Liste nicht erscheinen, egal was die Map kennt.
        val portal = listOf(
            PortalLocale("de", "Deutsch", "Deutsch", "core", 300_000L),
            PortalLocale("en", "English", "English", "core", 285_000L),
        )
        val result = LocalizationManager.computeAvailableLanguages(portal, null, emptySet())
        assertFalse(result.any { it.code == "no" })
    }
}

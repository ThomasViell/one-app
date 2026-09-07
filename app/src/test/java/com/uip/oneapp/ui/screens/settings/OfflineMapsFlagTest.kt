package com.uip.oneapp.ui.screens.settings

import com.uip.oneapp.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Auftrag bedienbild Z-4 (5.6): Der Bauzeit-Schalter ist AUS (CEO-Entscheid 06.09.2026).
 *
 * Eine Compose-Semantik-Prüfung ("kein Knoten mit Text offline_maps_title", "Route führt
 * zurück") wurde versucht mit `createComposeRule()` (ui-test-junit4, in build.gradle.kts:251
 * bereits vorhanden). Sie scheitert im JVM-Unit-Test an einem Hamcrest-Versionskonflikt
 * zwischen dem projekteigenen JUnit und der von `ComposeTestRule` intern genutzten
 * Espresso-IdlingResource (`NoSuchMethodError` in `Matchers`, auch nach Ergänzung von
 * `androidx.test.espresso:espresso-core` in testImplementation). Das Beheben dieses
 * Versionskonflikts ist eine Abhängigkeits-Änderung mit Wirkung auf die ganze Testsuite —
 * außerhalb der Reichweite dieser Welle (Abschnitt 9 der PLAN.md nennt keinen Auftrag dazu).
 * Vorschlag für `QUEUE.md`: `androidx.test.espresso`-Version/Hamcrest-Ausschluss klären, dann
 * echte Compose-Semantik-Tests für SettingsScreen/NavGraph nachziehen.
 *
 * Ersatzbeleg für diese Runde: Golden `scr09_settings` (`verify.ps1 -Update`) zeigt den
 * Bildschirm ohne den Eintrag „Offline-Karten" — das ist der pixelgenaue Beweis, dass
 * `showOfflineMaps = false` den Eintrag nicht komponiert (SettingsScreen.kt).
 */
class OfflineMapsFlagTest {

    @Test
    fun `Bauzeit-Schalter offlineMapsScreen ist AUS`() {
        assertEquals(false, FeatureFlags.offlineMapsScreen)
    }
}

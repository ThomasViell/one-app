package com.uip.oneapp.signing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ADR-0005 Abschnitt 4 (CEO-Entscheid 29.07.2026, Variante B): DrainQ.ONE MUSS
 * `android:sharedUserId="android.uid.system"` tragen, sonst liefert der Hotspot nur einen
 * generischen SSID-Namen statt `DrainQ-ONE-<serial>` (Messung RESULT_PLATTFORMSIGNATUR_2026-07-29.md).
 *
 * Anlass: das Attribut ist beim Camera2-Umbau (AP-1..3, 29.07.2026) unbemerkt aus dem
 * Manifest verschwunden und wäre fast als Regression ausgeliefert worden.
 */
class PlatformSigningManifestTest {

    // Gradle-Tests laufen mit CWD = Modul-Root (app/); Projekt-Root ist eine Ebene höher.
    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (File(dir, "gradlew.bat").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    @Test
    fun manifest_declaresSystemSharedUserId_perAdr0005() {
        val manifestFile = File(projectRoot(), "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml nicht gefunden: $manifestFile", manifestFile.exists())

        val manifestText = manifestFile.readText(Charsets.UTF_8)
        assertTrue(
            "android:sharedUserId=\"android.uid.system\" fehlt in AndroidManifest.xml. " +
                "ADR-0005 Abschnitt 4 (docs/adr/0005-platform-signing.md) legt dieses Attribut " +
                "als CEO-Entscheidung fest — ohne System-UID bekommt der Hotspot nur einen " +
                "generischen Namen statt DrainQ-ONE-<serial>. Attribut nicht ohne neue ADR entfernen.",
            manifestText.contains("android:sharedUserId=\"android.uid.system\"")
        )
    }
}

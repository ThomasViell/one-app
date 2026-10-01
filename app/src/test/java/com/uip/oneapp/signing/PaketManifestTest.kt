package com.uip.oneapp.signing

import com.uip.oneapp.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Zwei Pakete aus einem Bau (CEO 01.10.2026, ADR-0005 Nachtrag): NUR das ONE-Paket trägt
 * `android:sharedUserId="android.uid.system"` — ohne das Attribut bekommt der Hotspot auf der
 * ONE nur einen generischen Namen statt `DrainQ-ONE-<serial>` (RESULT_PLATTFORMSIGNATUR_2026-07-29.md).
 * MIT dem Attribut lehnt jedes handelsübliche Tablet die Installation ab, weil seine Firmware
 * nicht mit dem Plattformschlüssel gebaut ist.
 *
 * Ersetzt PlatformSigningManifestTest (der das Attribut im Haupt-Manifest verlangte).
 * Läuft in beiden Varianten (testOneDebugUnitTest, testTabletDebugUnitTest) und prüft dort
 * jeweils das gemischte Manifest der laufenden Variante.
 */
class PaketManifestTest {

    private val attribute = "android:sharedUserId=\"android.uid.system\""

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
    fun hauptManifest_traegtKeineSharedUserId() {
        val manifestFile = File(projectRoot(), "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml nicht gefunden: $manifestFile", manifestFile.exists())

        assertFalse(
            "app/src/main/AndroidManifest.xml enthält android:sharedUserId. Das Attribut gehört " +
                "ausschließlich nach app/src/one/AndroidManifest.xml (ADR-0005 Nachtrag, CEO 01.10.2026) — " +
                "im Haupt-Manifest landet es auch im Tablet-Paket, das dann auf keinem Tablet installierbar ist.",
            manifestFile.readText(Charsets.UTF_8).contains("android:sharedUserId")
        )
    }

    @Test
    fun oneManifest_traegtGenauDieSystemUid() {
        val manifestFile = File(projectRoot(), "app/src/one/AndroidManifest.xml")
        assertTrue("ONE-Overlay-Manifest nicht gefunden: $manifestFile", manifestFile.exists())

        val text = manifestFile.readText(Charsets.UTF_8)
        assertEquals(
            "app/src/one/AndroidManifest.xml muss android:sharedUserId genau einmal tragen.",
            1,
            Regex("android:sharedUserId").findAll(text).count()
        )
        assertTrue(
            "$attribute fehlt in app/src/one/AndroidManifest.xml. ADR-0005: ohne System-UID kein " +
                "Kameradienst-Start, kein Kamerarecht und kein Hotspot DrainQ-ONE-<serial> auf der ONE.",
            text.contains(attribute)
        )
    }

    @Test
    fun gemischtesManifest_sharedUserIdGenauImOnePaket() {
        val config = javaClass.classLoader?.getResourceAsStream("com/android/tools/test_config.properties")
            ?: run {
                fail(
                    "Klassenpfad-Ressource com/android/tools/test_config.properties fehlt — AGP legt sie nur mit " +
                        "testOptions.unitTests.isIncludeAndroidResources = true an. Ohne sie ist das gemischte " +
                        "Manifest der laufenden Variante nicht prüfbar."
                )
                return
            }
        val props = Properties().apply { config.use { load(it) } }
        val raw = props.getProperty("android_merged_manifest")
        assertNotNull("Schlüssel android_merged_manifest fehlt in test_config.properties.", raw)

        val variantSegment = BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }
        val segments = raw!!.split('/', '\\')
        assertTrue(
            "android_merged_manifest ($raw) gehört nicht zur laufenden Variante $variantSegment " +
                "(FLAVOR=${BuildConfig.FLAVOR}, BUILD_TYPE=${BuildConfig.BUILD_TYPE}).",
            variantSegment in segments
        )

        val path = File(raw)
        val merged = if (path.isAbsolute) path else File(System.getProperty("user.dir") ?: ".", raw)
        assertTrue("Gemischtes Manifest nicht gefunden: $merged", merged.exists())

        val text = merged.readText(Charsets.UTF_8)
        when (BuildConfig.FLAVOR) {
            "one" -> assertTrue(
                "Gemischtes Manifest des ONE-Pakets ($variantSegment) trägt $attribute nicht.",
                text.contains(attribute)
            )
            "tablet" -> assertFalse(
                "Gemischtes Manifest des Tablet-Pakets ($variantSegment) trägt android:sharedUserId — " +
                    "das Paket wäre auf keinem handelsüblichen Tablet installierbar.",
                text.contains("android:sharedUserId")
            )
            else -> fail("Unbekannte Produktvariante: ${BuildConfig.FLAVOR}")
        }
    }
}

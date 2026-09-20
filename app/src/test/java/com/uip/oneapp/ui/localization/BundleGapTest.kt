package com.uip.oneapp.ui.localization

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Z-6/RB-5: Jede Paketsprache (DE oder EN) muss vollstaendig sein -- fehlt ein
 * Map-Schluessel im jeweils anderen Paket UND im jeweils anderen Map-Block, ist das eine
 * stille Luecke (L-214). CEO-Entscheid 17.09.2026 (PLAN_NACHTRAG R-4): eine datierte
 * Ausnahmeliste `KNOWN_EN_GAPS` nennt die gemessenen Schluessel NAMENTLICH — 27 am
 * 17.09.2026 (`belege/b0_en_luecken.txt`), **einer** seit 19.09.2026
 * (`update_not_configured`, N-3); die Liste darf nur schrumpfen. Keine Sammelregel; der
 * Bauer uebersetzt keinen dieser Schluessel selbst (Regel 12). Jeder NEUE Fall (nicht in
 * der Liste) macht den Test rot.
 */
class BundleGapTest {

    /**
     * Gemessen 19.09.2026 am Kopf cf5be07 (Runde 2, N-3; belege/r2/n3_erhebung_raw.txt): DE-Map-
     * Schluessel, die weder im EN-Map-Block noch im EN-Paket stehen. Die 26 uebrigen Eintraege
     * der Phase-0-Liste (17.09.2026, 27) sind seither durch das EN-Paket abgedeckt und gestrichen.
     * Namentlich, kein Muster; die Liste darf nur schrumpfen.
     */
    private val KNOWN_EN_GAPS = setOf(
        "update_not_configured", // Luecke seit 17.09.2026, bestaetigt 19.09.2026
    )


    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (File(dir, "gradlew.bat").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun assetKeys(code: String): Set<String> {
        val f = File(projectRoot(), "app/src/main/assets/l10n/$code.json")
        assertTrue("Paket-Asset fehlt: ${f.path}", f.exists())
        val obj = JSONObject(f.readText(Charsets.UTF_8))
        return obj.keys().asSequence().toSet()
    }

    /** Aktuelle EN-Luecken, gemessen: DE-Map-Schluessel ohne EN-Map-Wert und ohne EN-Paketwert. */
    private fun realEnGaps(): Set<String> {
        val enMapKeys = LocalizationManager.enMapKeysForTest()
        val enAssetKeys = assetKeys("en")
        return LocalizationManager.deMapKeysForTest().filter { it !in enMapKeys && it !in enAssetKeys }.toSet()
    }

    @Test
    fun knownEnGaps_areExactlyTheRealGaps_noStaleEntries() {
        // N-3: die Liste ist deckungsgleich mit der gemessenen Trefferliste -- ein Eintrag, den
        // das Paket inzwischen abdeckt, macht den Test rot (die Liste MUSS schrumpfen).
        val stale = KNOWN_EN_GAPS - realEnGaps()
        assertTrue("KNOWN_EN_GAPS enthaelt abgedeckte Schluessel, bitte streichen: $stale", stale.isEmpty())
    }

    @Test
    fun deMapKeys_haveEnglishValue_orAreKnownGap() {
        val deMapKeys = LocalizationManager.deMapKeysForTest()
        val enMapKeys = LocalizationManager.enMapKeysForTest()
        val enAssetKeys = assetKeys("en")

        val newGaps = deMapKeys.filter { key ->
            key !in enMapKeys && key !in enAssetKeys && key !in KNOWN_EN_GAPS
        }
        assertTrue(
            "Neue EN-Luecke(n) ohne Eintrag in KNOWN_EN_GAPS (Regel 12: nicht selbst uebersetzen, " +
                "sondern Schluessel benennen und R-4-Liste bzw. Portal nachziehen): $newGaps",
            newGaps.isEmpty()
        )
    }

    @Test
    fun enAssetKeys_haveGermanValue() {
        // Das Paket-EN kommt vom Portal (Schritt 3a) -- es darf keinen Schluessel tragen,
        // der weder im Paket-DE noch im Map-DE-Block eine deutsche Entsprechung hat.
        val deMapKeys = LocalizationManager.deMapKeysForTest()
        val deAssetKeys = assetKeys("de")
        val enAssetKeys = assetKeys("en")

        val orphaned = enAssetKeys.filter { key -> key !in deAssetKeys && key !in deMapKeys }
        assertTrue("Paket-EN-Schluessel ohne deutsche Entsprechung: $orphaned", orphaned.isEmpty())
    }
}

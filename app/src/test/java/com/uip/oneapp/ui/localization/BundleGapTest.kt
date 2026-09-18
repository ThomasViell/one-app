package com.uip.oneapp.ui.localization

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Z-6/RB-5: Jede Paketsprache (DE oder EN) muss vollstaendig sein -- fehlt ein
 * Map-Schluessel im jeweils anderen Paket UND im jeweils anderen Map-Block, ist das eine
 * stille Luecke (L-214). CEO-Entscheid 17.09.2026 (PLAN_NACHTRAG R-4): eine datierte
 * Ausnahmeliste `KNOWN_EN_GAPS` nennt die am 17.09.2026 gemessenen 27 Schluessel (Quelle:
 * belege/b0_en_luecken.txt) NAMENTLICH -- keine Sammelregel. Der Bauer uebersetzt keinen
 * dieser Schluessel selbst (Regel 12); die Liste schrumpft nur ueber den Portalweg. Jeder
 * NEUE Fall (nicht in der Liste) macht den Test rot.
 */
class BundleGapTest {

    /** Gemessen 17.09.2026, Phase 0 (belege/b0_en_luecken.txt): DE-Map-Schluessel ohne EN-Map. */
    private val KNOWN_EN_GAPS = setOf(
        "address_not_found",
        "address_search_no_internet",
        "hardware_osd",
        "search_address",
        "update_available",
        "update_cancel",
        "update_channel_beta",
        "update_channel_label",
        "update_channel_stable",
        "update_check_now",
        "update_error_hash_mismatch",
        "update_error_install_failed",
        "update_error_network",
        "update_install_now",
        "update_last_check",
        "update_later",
        "update_mandatory_hint",
        "update_no_update",
        "update_not_configured",
        "update_notes_label",
        "update_notification_body",
        "update_notification_title",
        "update_progress_downloading",
        "update_progress_installing",
        "update_progress_verifying",
        "update_section_title",
        "update_size_label",
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

    @Test
    fun knownEnGaps_hasExactly27NamedEntries() {
        assertTrue(
            "KNOWN_EN_GAPS muss genau 27 Eintraege haben (Messung Phase 0); tatsaechlich: ${KNOWN_EN_GAPS.size}",
            KNOWN_EN_GAPS.size == 27
        )
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

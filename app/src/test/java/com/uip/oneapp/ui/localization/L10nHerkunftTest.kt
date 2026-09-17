package com.uip.oneapp.ui.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Z-7: Wert- und Herkunftswaechter fuer fremdsprachige Werte.
 *
 * Jeder Map-Block (auszer de/en) in LocalizationManager.kt und jede Datei
 * unter assets/i18n/ (auszer de.json/en.json) muss einen Eintrag mit
 * passendem SHA-256 in assets/l10n/HERKUNFT.md tragen. Eine Aenderung am
 * fremdsprachigen Wert ohne Herkunftsvermerk macht diesen Test rot.
 */
class L10nHerkunftTest {

    private val foreignCodes = listOf(
        "no", "it", "nl", "fr", "es", "pt", "pl", "cs", "sk", "sl", "hr", "hu",
        "ro", "bg", "el", "da", "sv", "fi", "et", "lv", "lt", "ga", "mt", "ar",
        "ru", "tr", "sr", "sq", "zh", "ja", "ko", "id", "th"
    )

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (File(dir, "gradlew.bat").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun herkunftFile() = File(projectRoot(), "app/src/main/assets/l10n/HERKUNFT.md")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun normalize(text: String) = text.replace("\r\n", "\n")

    private fun mapBlockText(code: String): String {
        val src = File(projectRoot(), "app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt")
            .readText(Charsets.UTF_8)
        val startMarker = "private fun ${code}Translations("
        val start = src.indexOf(startMarker)
        assertTrue("Block fuer $code nicht gefunden", start >= 0)
        val next = src.indexOf("private fun ", start + startMarker.length)
        val end = if (next >= 0) next else src.length
        return normalize(src.substring(start, end))
    }

    private fun assetFileText(code: String): String {
        val f = File(projectRoot(), "app/src/main/assets/i18n/$code.json")
        assertTrue("Datei fuer $code nicht gefunden: ${f.path}", f.exists())
        return normalize(f.readText(Charsets.UTF_8))
    }

    private fun herkunftEntries(): Map<String, String> {
        val f = herkunftFile()
        assertTrue("HERKUNFT.md fehlt: ${f.path}", f.exists())
        val entries = mutableMapOf<String, String>()
        Regex("""^([a-zA-Z0-9_/.:-]+)\s+sha256=([0-9a-f]{64})\s*$""", RegexOption.MULTILINE)
            .findAll(f.readText(Charsets.UTF_8))
            .forEach { entries[it.groupValues[1]] = it.groupValues[2] }
        return entries
    }

    @Test
    fun herkunft_fileExists() {
        assertTrue("HERKUNFT.md muss unter assets/l10n liegen", herkunftFile().exists())
    }

    @Test
    fun mapForeignBlocks_matchHerkunftSha256() {
        val entries = herkunftEntries()
        for (code in foreignCodes) {
            val key = "map:$code"
            val expected = entries[key]
            assertTrue("Kein HERKUNFT-Eintrag fuer $key", expected != null)
            val actual = sha256(mapBlockText(code).toByteArray(Charsets.UTF_8))
            assertEquals("SHA-256 fuer $key weicht ab (Wert ohne Herkunftsvermerk geaendert?)", expected, actual)
        }
    }

    @Test
    fun assetForeignFiles_matchHerkunftSha256() {
        val entries = herkunftEntries()
        for (code in foreignCodes) {
            val key = "i18n/$code.json"
            val expected = entries[key]
            assertTrue("Kein HERKUNFT-Eintrag fuer $key", expected != null)
            val actual = sha256(assetFileText(code).toByteArray(Charsets.UTF_8))
            assertEquals("SHA-256 fuer $key weicht ab (Wert ohne Herkunftsvermerk geaendert?)", expected, actual)
        }
    }

    @Test
    fun noForeignFileWithoutHerkunft() {
        val entries = herkunftEntries()
        val assetsL10n = File(projectRoot(), "app/src/main/assets/l10n")
        val assetsI18n = File(projectRoot(), "app/src/main/assets/i18n")
        val exempt = setOf("de.json", "en.json", "HERKUNFT.md")
        val missing = mutableListOf<String>()
        listOf(assetsL10n, assetsI18n).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.name !in exempt && f.isFile) {
                    val key = if (dir == assetsI18n) "i18n/${f.name}" else f.name
                    if (entries[key] == null) missing.add(key)
                }
            }
        }
        assertTrue("Dateien ohne Herkunftsvermerk: $missing", missing.isEmpty())
    }
}

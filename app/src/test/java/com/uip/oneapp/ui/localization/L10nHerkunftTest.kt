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
 *
 * Die Bloecke werden aus der Quelle erhoben, nicht aus einer Liste — `A-5`
 * (Welle l10n-auflagen, 19.09.2026): eine feste Codeliste prueft nur sich
 * selbst — ein NEUER fremdsprachiger Block ohne Herkunftsvermerk blieb gruen
 * (Lueckenbeweis mit Probeblock MUT-1, belege/z2_luecke_gruen_raw.txt).
 * Deshalb erhebt `discoveredMapCodes()` die Bloecke per Quelltext-Muster aus
 * `LocalizationManager.kt` und `discoveredAssetCodes()` die Dateinamen aus dem
 * Verzeichnis `assets/i18n`. Der Waechter prueft beide Richtungen: Block ohne
 * Herkunftsvermerk wird rot (`noForeignMapBlockWithoutHerkunft`), Herkunftsvermerk
 * ohne Block ebenfalls (`noHerkunftEntryWithoutBlock` — zugleich der Schutz,
 * dass die Erhebung nicht leer laeuft: 0 gegen 33 wuerde sofort rot).
 */
class L10nHerkunftTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (File(dir, "gradlew.bat").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun herkunftFile() = File(projectRoot(), "app/src/main/assets/l10n/HERKUNFT.md")

    private fun localizationManagerFile() =
        File(projectRoot(), "app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun normalize(text: String) = text.replace("\r\n", "\n")

    /** A-5: alle fremdsprachigen Map-Bloecke, aus dem Quelltext erhoben. */
    private fun discoveredMapCodes(): Set<String> {
        val src = localizationManagerFile().readText(Charsets.UTF_8)
        // N-4 (Runde 2, Befund B-5): ohne Sichtbarkeits-Vorgabe und ohne enge Namenslaenge —
        // sonst bleibt ein Block wie `internal fun yyTranslations(` oder ein vierbuchstabiger
        // Code unsichtbar (Lueckenbeweis belege/n4_luecke_gruen_raw.txt, Gegenprobe
        // belege/n4_gegenprobe_rot_raw.txt — beide mit denselben zwei Probebloecken).
        return Regex("""\bfun ([a-zA-Z]{2,8})Translations\(""")
            .findAll(src)
            .map { it.groupValues[1] }
            .filter { it != "de" && it != "en" }
            .toSet()
    }

    /** A-5: alle fremdsprachigen Asset-Dateien, aus dem Verzeichnis erhoben. */
    private fun discoveredAssetCodes(): Set<String> {
        val dir = File(projectRoot(), "app/src/main/assets/i18n")
        assertTrue("assets/i18n fehlt: ${dir.path}", dir.exists())
        return dir.listFiles()
            ?.map { it.name }
            ?.filter { it.endsWith(".json") && it != "de.json" && it != "en.json" }
            ?.map { it.removeSuffix(".json") }
            ?.toSet()
            ?: emptySet()
    }

    private fun mapBlockText(code: String): String {
        val src = localizationManagerFile().readText(Charsets.UTF_8)
        // N-4 (Runde 2, Befund B-5): zuerst die bisherige Schreibweise, sonst die blosse
        // Funktionsform -- bestehende Hashes bleiben bit-identisch, und ein anders sichtbarer
        // Block wird trotzdem gefunden.
        val startMarker = "private fun ${code}Translations("
        val fallbackMarker = "fun ${code}Translations("
        val privateStart = src.indexOf(startMarker)
        val start = if (privateStart >= 0) privateStart else src.indexOf(fallbackMarker)
        assertTrue("Block fuer $code nicht gefunden", start >= 0)
        val marker = if (privateStart >= 0) startMarker else fallbackMarker
        // Grenze zum naechsten Klassenmitglied auf Objektebene (4 Leerzeichen Einzug), nicht
        // nur zur naechsten "private fun" -- sonst reisst der letzte Block (heute "th") alles
        // bis Dateiende mit, auch wenn dort spaeter andersartiger Code (Methoden, Felder)
        // eingefuegt wird, der mit dem Fremdsprachwert nichts zu tun hat (gefunden bei Z-2/Z-4).
        // N-4: um die uebrigen Sichtbarkeiten erweitert.
        val next = Regex("\n    (?=private |internal |public |protected |fun |@)")
            .find(src, start + marker.length)?.range?.last?.plus(1)
        val end = next ?: src.length
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

    private fun herkunftMapCodes(): Set<String> =
        herkunftEntries().keys.filter { it.startsWith("map:") }.map { it.removePrefix("map:") }.toSet()

    @Test
    fun herkunft_fileExists() {
        assertTrue("HERKUNFT.md muss unter assets/l10n liegen", herkunftFile().exists())
    }

    @Test
    fun mapForeignBlocks_matchHerkunftSha256() {
        val entries = herkunftEntries()
        for (code in discoveredMapCodes()) {
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
        for (code in discoveredAssetCodes()) {
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

    /**
     * A-5 (Z-2), Zufluss-Richtung: ein Map-Block in `LocalizationManager.kt` ohne
     * Herkunftsvermerk macht rot. Der Ausgangskopf pruefte nur eine feste Liste —
     * der Probeblock MUT-1 blieb dort gruen (belege/z2_luecke_gruen_raw.txt).
     */
    @Test
    fun noForeignMapBlockWithoutHerkunft() {
        val missing = discoveredMapCodes() - herkunftMapCodes()
        assertTrue("Map-Bloecke ohne Herkunftsvermerk: $missing", missing.isEmpty())
    }

    /**
     * A-5 (Z-2), Gegenrichtung: ein Herkunftsvermerk ohne Block (toter Eintrag)
     * macht rot. Zugleich der Nichtleer-Schutz: laeuft die Erhebung leer, steht
     * hier 0 gegen 33 und der Test bricht.
     */
    @Test
    fun noHerkunftEntryWithoutBlock() {
        val dead = herkunftMapCodes() - discoveredMapCodes()
        assertTrue("HERKUNFT-Eintraege ohne Map-Block: $dead", dead.isEmpty())
    }
}

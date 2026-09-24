package com.uip.oneapp.ui.localization

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * W-33f Z-4: Waechter gegen Doppelschluessel je Sprachblock.
 *
 * mapOf-Semantik: bei einem doppelten Schluessel gewinnt der spaetere Eintrag still —
 * der fruere Wert ist toter Code, und nur das Import-Werkzeug warnt dabei (L10nImportLib,
 * „Doppelschluessel … der spaetere Wert gewinnt“). Am Ausgangskopf 43840dc stand
 * `offline_maps_title` in de (Zeilen 172, 307) und en (1145, 1266) doppelt — wertgleich,
 * deshalb am Verhalten nie sichtbar, aber ungeschuetzt. Dieser Test macht jeden
 * Doppelschluessel rot, mit Block, Schluessel und Dateizeilen.
 *
 * Blockerhebung und Blockgrenze sind wortgleich zu L10nHerkunftTest (dieselben Muster),
 * damit beide Waechter denselben Text sehen.
 *
 * N-3 (Nachbesserung mt-b4-nb, 23.09.2026): der Paar-Parser arbeitet zeilenweise ohne
 * Zeilenanker und zaehlt auch ein zweites Paar auf derselben Zeile (Mutationsbelege
 * _ketten/mt-b4-nb/belege/n3_*.txt; Belegpfade `_ketten/...` = Kettenordner, nicht Repo).
 */
class L10nDoppelschluesselTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (File(dir, "gradlew.bat").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun src(): String =
        File(projectRoot(), "app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt")
            .readText(Charsets.UTF_8)
            .replace("\r\n", "\n")

    /** Wie L10nHerkunftTest.discoveredMapCodes — hier ausdruecklich inklusive de/en. */
    private fun blockCodes(): List<String> =
        Regex("""\bfun ([a-zA-Z]{2,8})Translations\(""")
            .findAll(src())
            .map { it.groupValues[1] }
            .toList()

    /**
     * Wie L10nHerkunftTest.mapBlockText: Start bei „private fun <code>Translations(“
     * (Fallback „fun …“), Grenze beim naechsten Klassenmitglied auf Objektebene
     * (4 Leerzeichen Einzug), sonst Dateiende.
     */
    private fun blockRange(code: String, src: String): IntRange {
        val startMarker = "private fun ${code}Translations("
        val fallbackMarker = "fun ${code}Translations("
        val privateStart = src.indexOf(startMarker)
        val start = if (privateStart >= 0) privateStart else src.indexOf(fallbackMarker)
        assertTrue("Block fuer $code nicht gefunden", start >= 0)
        val marker = if (privateStart >= 0) startMarker else fallbackMarker
        val next = Regex("\n    (?=private |internal |public |protected |fun |@)")
            .find(src, start + marker.length)?.range?.last?.plus(1)
        val end = next ?: src.length
        return start until end
    }

    /**
     * Paare je Zeile innerhalb des Blocks, mit 1-basierter Dateizeile -- ankerfrei (N-3):
     * auch ein zweites Paar auf derselben Zeile wird gezaehlt. Erreicht: Paare der Form
     * "schluessel" to "wert" mit Stringliteral als Wert, beliebig viele je Zeile; Zeilen,
     * die (nach Einzug) mit // beginnen, werden uebersprungen. NICHT erreicht: Paare in
     * Blockkommentaren oder in einem //-Kommentar hinter Code derselben Zeile (wuerden
     * mitgezaehlt), Werte als Rohstring (drei Anfuehrungszeichen) oder als Ausdruck
     * (werden nicht gezaehlt) -- am Kopf 9a80089 je 0 Vorkommen in allen 35 Bloecken,
     * Zaehlung alt = neu (588/325/561/32x289; _ketten/mt-b4-nb/plan_messung/m1_ausgabe.txt).
     */
    private fun pairsInRange(src: String, range: IntRange): List<Pair<String, Int>> {
        val pair = Regex("""["]((?:[^"\\]|\\.)*)["]\s+to\s+["](?:[^"\\]|\\.)*["]""")
        val result = mutableListOf<Pair<String, Int>>()
        var zeilenStart = 0
        src.split("\n").forEachIndexed { index, line ->
            if (!line.trimStart().startsWith("//")) {
                pair.findAll(line)
                    .filter { zeilenStart + it.range.first in range }
                    .forEach { result.add(it.groupValues[1] to index + 1) }
            }
            zeilenStart += line.length + 1
        }
        return result
    }

    /**
     * Nichtleer-Schutz (Regel 36): laeuft die Erhebung leer oder winzig, prueft der
     * Waechter nichts — 0 gefundene Doppelschluessel waeren dann kein Erfolg.
     */
    @Test
    fun erhebungIstNichtLeer() {
        val src = src()
        val codes = blockCodes()
        assertTrue("Erhebung ungewoehnlich klein: ${codes.size} Bloecke (>= 30 erwartet)", codes.size >= 30)
        for (code in codes) {
            val paare = pairsInRange(src, blockRange(code, src)).size
            assertTrue("Block $code mit nur $paare Paaren (>= 250 erwartet)", paare >= 250)
        }
    }

    @Test
    fun keinSchluesselDoppeltJeBlock() {
        val src = src()
        val treffer = mutableListOf<String>()
        for (code in blockCodes()) {
            val zeilenJeSchluessel = mutableMapOf<String, MutableList<Int>>()
            for ((schluessel, zeile) in pairsInRange(src, blockRange(code, src))) {
                zeilenJeSchluessel.getOrPut(schluessel) { mutableListOf() }.add(zeile)
            }
            zeilenJeSchluessel.filterValues { it.size > 1 }.forEach { (schluessel, zeilen) ->
                treffer.add("$code: $schluessel (${zeilen.joinToString(", ")})")
            }
        }
        assertTrue(
            "Doppelschluessel (mapOf: der spaetere Wert gewinnt still): " + treffer.joinToString("; "),
            treffer.isEmpty()
        )
    }
}

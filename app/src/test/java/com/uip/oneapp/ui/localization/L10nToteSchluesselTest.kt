package com.uip.oneapp.ui.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * W-33e Z-4: Waechter gegen tote Schluessel (Schluessel ohne Verbraucher).
 *
 * Kriterium (offengelegt): ein Schluessel gilt als benutzt, wenn `"<schluessel>"`
 * (mit Anfuehrungszeichen) in mindestens einer Datei `app/src/main/java/**/*.kt`
 * AUSSER `LocalizationManager.kt` vorkommt. Tests, Assets, Werkzeuge zaehlen nicht.
 * Ein neu hinzugefuegter, nirgends benutzter Schluessel macht
 * [keinSchluesselOhneVerbraucher] rot.
 *
 * Zahlen am Kopf b46cfa2 (24.09.2026, Kettenordner _ketten/w33e-neu/belege/02_erhebung_kopf.txt):
 * 35 Sprachbloecke, 169 Produktdateien, 571 Treffer des Musters `(?<![A-Za-z0-9_])S\(`
 * (fuer das engere `S("` sind es 560 – die Zahl hier passt zum weiten Muster), 588 de- und
 * 561 en-Eintraege. Am Kopf ist Methode 3 ROT mit 81 Namen – beabsichtigt als Rot-Beweis
 * der Welle W-33e (83 = 81 per Kriterium + 2 von Hand entschieden).
 *
 * Blindstelle, woertlich benannt: das Kriterium sieht Literale, keine Rollen. Ein toter
 * Schluessel, dessen Name zufaellig als Routen-, Icon- oder Ordner-Literal vorkommt
 * (`navigate("inspection")`, Ordner "reports"), bleibt unsichtbar. `inspection` und `reports`
 * sind deshalb von Hand entschieden und mit W-33e entfernt (je 8 Literalstellen, alle
 * Route/Icon/Ordner – PLAN.md 1.2 im Kettenordner). Ein Wieder-Einfuegen dieser oder eines
 * anderen haeufigen Worts (`ok`, `download`) ALS TOTER SCHLUESSEL wuerde dieser Waechter
 * nicht melden. Es gibt KEINE Ausnahmeliste: keiner der 83 bleibt.
 *
 * Methoden 1 und 4 sichern die Erhebung selbst: Methode 1 vergleicht die Quelltext-Lesung
 * der de/en-Bloecke mit `deMapKeysForTest()`/`enMapKeysForTest()` – eine Zeile, die der
 * Parser nicht sieht, der Compiler aber schon (Rohstring als Schluessel), macht rot. Fuer
 * die 33 Bloecke ohne Laufzeitzugang prueft Methode 4 stattdessen die Struktur JE Zeile:
 * Kopfzeile in fester Form, Schlusszeile `    )`, dazwischen nur Leerzeilen, `//`-Kommentare
 * und reine Paarzeilen; hinter der Schlusszeile nur Leerzeilen und `//`-Zeilen. So kann ein
 * `"k" to ("v")` (Klammerwert), ein Wert in der Folgezeile oder ein Paar auf der Kopfzeile
 * in KEINEM Block still durchgehen.
 *
 * Auflage A-1 (CEO 24.09.2026): genau 35 Bloecke – das Blockmuster toleriert Leerraum vor
 * der Klammer (`Translations\s*\(`), damit `fun frTranslations (` nicht als falsche Blockzahl
 * gemeldet wird, sondern der tatsaechliche Befund (Kopfzeile/Schluessel) rot wird.
 */
class L10nToteSchluesselTest {

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

    /** A-1: Leerraum vor der Klammer toleriert; erhebt alle 35 Bloecke aus der Quelle. */
    private fun blockCodes(): List<String> =
        Regex("""\bfun ([a-zA-Z]{2,8})Translations\s*\(""")
            .findAll(src())
            .map { it.groupValues[1] }
            .toList()
            .distinct()

    /**
     * Grenze zum naechsten Klassenmitglied auf Objektebene (4 Leerzeichen Einzug) –
     * wortgleich zu L10nDoppelschluesselTest/L10nHerkunftTest; Start ueber das
     * leerraum-tolerante Muster (A-1), nicht ueber einen festen String.
     */
    private fun blockRange(code: String, src: String): IntRange {
        val start = Regex("""\bfun ${code}Translations\s*\(""").find(src)
        assertTrue("Block fuer $code nicht gefunden", start != null)
        val next = Regex("\n    (?=private |internal |public |protected |fun |@)")
            .find(src, start!!.range.last)?.range?.last?.plus(1)
        val end = next ?: src.length
        return start.range.first until end
    }

    /** Paar-Muster ankerfrei je Zeile wie L10nDoppelschluesselTest.pairsInRange (N-3). */
    private val pairRegex = Regex("""["]((?:[^"\\]|\\.)*)["]\s+to\s+["](?:[^"\\]|\\.)*["]""")

    /** Schluessel je Zeile innerhalb des Blocks mit 1-basierter Dateizeile. */
    private fun keysInRange(src: String, range: IntRange): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var zeilenStart = 0
        src.split("\n").forEachIndexed { index, line ->
            if (!line.trimStart().startsWith("//")) {
                pairRegex.findAll(line)
                    .filter { zeilenStart + it.range.first in range }
                    .forEach { result.add(it.groupValues[1] to index + 1) }
            }
            zeilenStart += line.length + 1
        }
        return result
    }

    /** Literale und S(-Zaehlung im Produktcode ohne LocalizationManager.kt. */
    private fun produktLiterale(): Triple<Set<String>, Int, Int> {
        val root = File(projectRoot(), "app/src/main/java")
        val literalRegex = Regex("\"([A-Za-z0-9_]+)\"")
        val sRegex = Regex("""(?<![A-Za-z0-9_])S\(""")
        val literals = mutableSetOf<String>()
        var dateien = 0
        var sTreffer = 0
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "LocalizationManager.kt" }
            .forEach { f ->
                dateien++
                val text = f.readText(Charsets.UTF_8)
                literalRegex.findAll(text).forEach { literals.add(it.groupValues[1]) }
                sTreffer += sRegex.findAll(text).count()
            }
        return Triple(literals, dateien, sTreffer)
    }

    /**
     * Nichtleer-Schutz (Regel 36) PLUS Parser-sieht-was-der-Compiler-sieht fuer de/en.
     * Am Kopf b46cfa2: 35 Bloecke, 169 Dateien, 571 S(-Treffer, 588/561 Eintraege.
     */
    @Test
    fun erhebungIstNichtLeerUndParserSiehtWasDerCompilerSieht() {
        val src = src()
        val codes = blockCodes()
        assertEquals(
            "Erwartet genau 35 Sprachbloecke, gefunden (${codes.size}): ${codes.joinToString()}",
            35,
            codes.size
        )
        val (_, dateien, sTreffer) = produktLiterale()
        assertTrue("Erhebung ungewoehnlich klein: $dateien Produktdateien (>= 100 erwartet)", dateien >= 100)
        assertTrue(
            "Erhebung ungewoehnlich klein: $sTreffer S(-Aufrufe (>= 500 erwartet; Muster " +
                "(?<![A-Za-z0-9_])S\\(, am Kopf b46cfa2 gemessen 571, fuer S(\\\" waeren es 560)",
            sTreffer >= 500
        )
        val deQuelle = keysInRange(src, blockRange("de", src)).map { it.first }.toSet()
        val enQuelle = keysInRange(src, blockRange("en", src)).map { it.first }.toSet()
        assertTrue("de-Erhebung ungewoehnlich klein: ${deQuelle.size} Schluessel (>= 400 erwartet)", deQuelle.size >= 400)
        assertTrue("en-Erhebung ungewoehnlich klein: ${enQuelle.size} Schluessel (>= 400 erwartet)", enQuelle.size >= 400)
        assertQuelleGleichLaufzeit("de", deQuelle, LocalizationManager.deMapKeysForTest())
        assertQuelleGleichLaufzeit("en", enQuelle, LocalizationManager.enMapKeysForTest())
    }

    private fun assertQuelleGleichLaufzeit(code: String, quelle: Set<String>, laufzeit: Set<String>) {
        val fehltInQuelle = (laufzeit - quelle).sorted()
        val nurInQuelle = (quelle - laufzeit).sorted()
        assertTrue(
            "Quelle != Laufzeit ($code): fehlt in Quelle $fehltInQuelle; nur in Quelle $nurInQuelle",
            fehltInQuelle.isEmpty() && nurInQuelle.isEmpty()
        )
    }

    /** Der de-Block ist Leitmenge: jeder Fremdblock darf nichts fuehren, was de nicht kennt. */
    @Test
    fun alleBloeckeSindTeilmengeVonDe() {
        val src = src()
        val de = keysInRange(src, blockRange("de", src)).map { it.first }.toSet()
        val treffer = mutableListOf<String>()
        for (code in blockCodes()) {
            val fremde = keysInRange(src, blockRange(code, src)).map { it.first }.toSet() - de
            if (fremde.isNotEmpty()) {
                treffer.add("Block $code fuehrt Schluessel ohne de-Entsprechung: ${fremde.sorted()}")
            }
        }
        assertTrue(treffer.joinToString("; "), treffer.isEmpty())
    }

    /**
     * Jeder Schluessel aus der Vereinigung aller Bloecke braucht ein Literal im Produktcode.
     * Am Kopf b46cfa2 ROT mit 81 Namen (Rot-Beweis der Welle). Die zwei Hand-Faelle
     * `inspection`/`reports` sieht dieses Kriterium nicht – Blindstelle oben im KDoc.
     */
    @Test
    fun keinSchluesselOhneVerbraucher() {
        val src = src()
        val (literale, _, _) = produktLiterale()
        val inBloecken = mutableMapOf<String, MutableList<String>>()
        for (code in blockCodes()) {
            keysInRange(src, blockRange(code, src)).forEach { (k, _) ->
                inBloecken.getOrPut(k) { mutableListOf() }.add(code)
            }
        }
        // Vereinigung mit der Laufzeitmenge (guertellingsweise; Methode 1 stellt bereits
        // Quelle == Laufzeit fuer de sicher).
        LocalizationManager.deMapKeysForTest().forEach { k ->
            inBloecken.getOrPut(k) { mutableListOf("de") }
        }
        val tote = inBloecken.keys.filter { it !in literale }.sorted()
        val meldung = tote.joinToString("; ") { k -> "$k (${inBloecken[k]!!.sorted().joinToString()})" }
        assertTrue(
            "Tote Schluessel (Literal \"<schluessel>\" in keiner Produktdatei ausser " +
                "LocalizationManager.kt): [$meldung]",
            tote.isEmpty()
        )
    }

    /**
     * Strukturwächter fuer ALLE Bloecke (auch die 33 ohne Laufzeitzugang): jede Zeile im
     * Block muss der Paar-Parser lesen koennen, sonst koennte neben einem unsichtbaren
     * Schluessel auch ein Doppelschluessel unentdeckt bleiben. Kopfzeile in fester Form
     * (sonst versteckt die Kopfzeile selbst ein Paar), Schlusszeile `    )`, dazwischen und
     * dahinter nur Leer-/Kommentarzeilen bzw. reine Paarzeilen. Am Kopf b46cfa2: 0 Restzeilen.
     */
    @Test
    fun jedeBlockzeileIstFuerDenParserLesbar() {
        val src = src()
        val lines = src.split("\n")
        val zeilenAnfaenge = IntArray(lines.size)
        var off = 0
        for (i in lines.indices) {
            zeilenAnfaenge[i] = off
            off += lines[i].length + 1
        }
        val fehler = mutableListOf<String>()
        val kopfSoll = { code: String -> "    private fun ${code}Translations(): Map<String, String> = mapOf(" }
        val schlussRegex = Regex("^ {4}\\)\\s*$")
        val restRegex = Regex("""^[\s,]*$""")
        for (code in blockCodes()) {
            val range = blockRange(code, src)
            val ersteZeile = indexOfZeile(zeilenAnfaenge, range.first)
            // Bereichsende (exklusiv) zeigt auf das erste Zeichen des naechsten Klassenmitglieds;
            // dessen Zeile gehoert nicht mehr zum Block – die letzte Blockzeile liegt davor.
            val letzteZeile = indexOfZeile(zeilenAnfaenge, (range.last - 1).coerceAtMost(src.length - 1)) - 1
            val kopfText = lines[ersteZeile]
            if (kopfText != kopfSoll(code)) {
                fehler.add("Block $code: Kopfzeile weicht von der festen Form ab: [${ersteZeile + 1}: $kopfText]")
            }
            var schlussIdx = -1
            for (i in ersteZeile + 1..letzteZeile) {
                if (schlussRegex.matches(lines[i])) {
                    schlussIdx = i
                    break
                }
            }
            if (schlussIdx < 0) {
                fehler.add("Block $code: keine Schlusszeile '    )' innerhalb des Blocks gefunden")
                continue
            }
            val schlecht = mutableListOf<String>()
            for (i in ersteZeile + 1 until letzteZeile + 1) {
                val line = lines[i]
                if (i == schlussIdx) continue
                val istLeer = line.isBlank()
                val istKommentar = line.trimStart().startsWith("//")
                val istNachSchluss = i > schlussIdx
                if (istLeer || istKommentar) continue
                if (istNachSchluss) {
                    schlecht.add("${i + 1}: $line")
                    continue
                }
                val rest = StringBuilder()
                var last = 0
                for (m in pairRegex.findAll(line)) {
                    rest.append(line, last, m.range.first)
                    last = m.range.last + 1
                }
                rest.append(line, last, line.length)
                if (!restRegex.matches(rest.toString())) schlecht.add("${i + 1}: $line")
            }
            if (schlecht.isNotEmpty()) {
                fehler.add(
                    "Block $code: ${schlecht.size} Zeile(n) nicht als Paar lesbar (Compiler sieht sie, " +
                        "Parser nicht): [${schlecht.joinToString()}]"
                )
            }
        }
        assertTrue(fehler.joinToString("; "), fehler.isEmpty())
    }

    private fun indexOfZeile(zeilenAnfaenge: IntArray, offset: Int): Int {
        var lo = 0
        var hi = zeilenAnfaenge.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (zeilenAnfaenge[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}

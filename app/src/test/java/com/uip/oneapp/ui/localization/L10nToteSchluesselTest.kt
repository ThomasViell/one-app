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
 * AUSSER `LocalizationManager.kt` vorkommt — seit W-33e-nb (X2) nur noch ausserhalb
 * von Kommentaren. Tests, Assets, Werkzeuge zaehlen nicht. Ein neu hinzugefuegter,
 * nirgends benutzter Schluessel macht [keinSchluesselOhneVerbraucher] rot.
 *
 * Zahlen am Kopf b46cfa2 (24.09.2026, Kettenordner _ketten/w33e-neu/belege/02_erhebung_kopf.txt):
 * 35 Sprachbloecke, 169 Produktdateien, 571 Treffer des Musters `(?<![A-Za-z0-9_])S\(`
 * im Rohtext (fuer das engere `S("` sind es 560 – die Zahl hier passt zum weiten Muster;
 * seit X2 zaehlt der Test nur noch ausserhalb von Kommentaren, Schwelle bleibt 500), 588 de- und
 * 561 en-Eintraege. Am Kopf ist Methode 3 ROT mit 81 Namen – beabsichtigt als Rot-Beweis
 * der Welle W-33e (83 = 81 per Kriterium + 2 von Hand entschieden).
 *
 * Blindstellen, woertlich benannt (Stand W-33e-nb, 24.09.2026): die zwei Luecken aus
 * `_ketten/w33e-neu/PRUEFBERICHT.md` Abschnitt 4/10 (B-1) sind durch zwei neue Pruefungen
 * adressiert — (X1) ein Schluessel, der nur ueber die Sammel-Map `translations`
 * hinzukommt, faellt jetzt unter [sammelMapBestehtNurAusReinenBlockaufrufen]: die Map darf
 * ausschliesslich aus genau 35 Eintraegen der Form `"<code>" to <code>Translations(),`
 * bestehen; (X2) Namen, die nur in Kommentaren stehen, zaehlen nicht mehr als Verbraucher
 * ([ohneKommentare] respektiert Zeichenketten: "https://…" ist kein Kommentar,
 * "${S("…")}"-Vorlagen bleiben Literallieferant). VERBLEIBEND ist genau eine Blindstelle:
 * das Kriterium sieht Literale, keine Rollen. Ein toter Schluessel, dessen Name zufaellig
 * als Routen-, Icon- oder Ordner-Literal vorkommt (`navigate("inspection")`, Ordner
 * "reports"), bleibt unsichtbar. `inspection` und `reports` sind deshalb von Hand
 * entschieden und mit W-33e entfernt (je 8 Literalstellen, alle Route/Icon/Ordner –
 * PLAN.md 1.2 im Kettenordner). Ein Wieder-Einfuegen dieses oder eines anderen haeufigen
 * Worts (`ok`, `download`) ALS TOTER SCHLUESSEL wuerde dieser Waechter nicht melden.
 * Es gibt KEINE Ausnahmeliste: keiner der 83 bleibt.
 *
 * Methoden 1 und 4 sichern die Erhebung selbst: Methode 1 vergleicht die Quelltext-Lesung
 * der de/en-Bloecke mit `deMapKeysForTest()`/`enMapKeysForTest()` – eine Zeile, die der
 * Parser nicht sieht, der Compiler aber schon (Rohstring als Schluessel), macht rot. Fuer
 * die 33 Bloecke ohne Laufzeitzugang prueft Methode 4 stattdessen die Struktur JE Zeile:
 * Kopfzeile in fester Form, Schlusszeile `    )`, dazwischen nur Leerzeilen, `//`-Kommentare
 * und reine Paarzeilen; hinter der Schlusszeile nur Leerzeilen und `//`-Zeilen. So kann ein
 * `"k" to ("v")` (Klammerwert), ein Wert in der Folgezeile oder ein Paar auf der Kopfzeile
 * in KEINEM Block still durchgehen. [sammelMapBestehtNurAusReinenBlockaufrufen] deckt die
 * Sammel-Map in derselben Art ab: die Laufzeit liest `translations` (getString), die
 * Erhebung die Blockfunktionen — jede Map-Zeile ausser der reinen Form wird rot gemeldet.
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

    /**
     * X2 (W-33e-nb, PRUEFBERICHT w33e-neu B-1): entfernt `//`-Zeilenkommentare und
     * `/* … */`-Blockkommentare (KDoc inklusive) aus dem Text und ersetzt ihre Zeichen
     * durch Leerzeichen — Zeichenketten werden respektiert: `//` INNERHALB einer
     * Zeichenkette ("https://…", "${S("…")}"-Vorlagen) ist kein Kommentar und bleibt
     * Literallieferant. Kotlin-Blockkommentare koennen geschachtelt sein, hier gezaehlt.
     * Rohketten ("""…""") und Zeichenliterale ('…') bleiben unangetastet; Zeilenenden
     * bleiben erhalten.
     */
    private fun ohneKommentare(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && i + 2 < text.length && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    val ende = text.indexOf("\"\"\"", i + 3)
                    val stop = if (ende < 0) text.length else ende + 3
                    sb.append(text, i, stop)
                    i = stop
                }
                c == '"' -> {
                    sb.append(c)
                    i++
                    while (i < text.length) {
                        if (text[i] == '\\' && i + 1 < text.length) {
                            sb.append(text[i]).append(text[i + 1])
                            i += 2
                            continue
                        }
                        sb.append(text[i])
                        if (text[i] == '"') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '\'' -> {
                    sb.append(c)
                    i++
                    while (i < text.length) {
                        if (text[i] == '\\' && i + 1 < text.length) {
                            sb.append(text[i]).append(text[i + 1])
                            i += 2
                            continue
                        }
                        sb.append(text[i])
                        if (text[i] == '\'') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    val ende = text.indexOf('\n', i)
                    i = if (ende < 0) text.length else ende
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    sb.append("  ")
                    var tiefe = 1
                    i += 2
                    while (i < text.length && tiefe > 0) {
                        when {
                            text[i] == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                                tiefe++
                                sb.append("  ")
                                i += 2
                            }
                            text[i] == '*' && i + 1 < text.length && text[i + 1] == '/' -> {
                                tiefe--
                                sb.append("  ")
                                i += 2
                            }
                            else -> {
                                sb.append(if (text[i] == '\n') '\n' else ' ')
                                i++
                            }
                        }
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /**
     * Literale und S(-Zaehlung im Produktcode ohne LocalizationManager.kt — seit X2
     * (W-33e-nb) auf kommentarbefreitem Text: `"<schluessel>"` nur in einem Kommentar
     * zaehlt nicht mehr als Verbraucher.
     */
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
                val text = ohneKommentare(f.readText(Charsets.UTF_8))
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
     * X1 (W-33e-nb, PRUEFBERICHT w33e-neu B-1): die Laufzeit liest die Sammel-Map
     * `translations` (getString), die Erhebungsmethoden oben lesen die Blockfunktionen.
     * Ein toter Schluessel, der NUR in der Sammel-Map hinzukommt
     * (`"de" to deTranslations() + ("k" to "v")`, `.plus(`, zusaetzliches Paar), war
     * fuer den Waechter unsichtbar (Beleg `_ketten/w33e-neu/belege/pruef_m15_x1x2.txt`).
     * Diese Pruefung schliesst die Luecke: die Map besteht ausschliesslich aus genau 35
     * Eintraegen der Form `"<code>" to <code>Translations(),` (gleicher Code in Schluessel
     * und Aufruf, 12 Leerzeichen Einzug, Komma am Zeilenende). Ausnahmen: KEINE — am Kopf
     * cded76d haben alle 35 Eintraege diese Form (0 Abweichungen). Jede andere Zeile wird
     * rot MIT ZEILENNUMMER gemeldet; Leer- und `//`-Zeilen innerhalb der Map bleiben
     * erlaubt (wie Methode 4 in den Bloecken).
     */
    @Test
    fun sammelMapBestehtNurAusReinenBlockaufrufen() {
        val src = src()
        val lines = src.split("\n")
        val zeilenAnfaenge = IntArray(lines.size)
        var off = 0
        for (i in lines.indices) {
            zeilenAnfaenge[i] = off
            off += lines[i].length + 1
        }
        val startIdx = src.indexOf("private val translations")
        assertTrue(
            "Sammel-Map 'private val translations' nicht genau einmal gefunden",
            startIdx >= 0 && src.lastIndexOf("private val translations") == startIdx
        )
        val startZeile = indexOfZeile(zeilenAnfaenge, startIdx)
        var mapOfZeile = -1
        for (i in startZeile..startZeile + 2) {
            if (i < lines.size && lines[i].contains("mapOf(")) {
                mapOfZeile = i
                break
            }
        }
        assertTrue(
            "mapOf( der Sammel-Map nicht unmittelbar nach der Deklaration gefunden",
            mapOfZeile >= 0
        )
        val eintragRegex = Regex("""^ {12}"([a-zA-Z]{2,8})" to \1Translations\(\),$""")
        val schlussRegex = Regex("""^ {8}\)\s*$""")
        val fehler = mutableListOf<String>()
        val mapCodes = mutableListOf<String>()
        var i = mapOfZeile + 1
        while (i < lines.size && !schlussRegex.matches(lines[i])) {
            val line = lines[i]
            val m = eintragRegex.find(line)
            if (m != null) {
                mapCodes.add(m.groupValues[1])
            } else if (!line.isBlank() && !line.trimStart().startsWith("//")) {
                fehler.add("${i + 1}: $line")
            }
            i++
        }
        assertTrue(
            "Sammel-Map translations: keine Schlusszeile '        )' nach mapOf( gefunden",
            i < lines.size
        )
        assertTrue(
            "Sammel-Map translations: ${fehler.size} Zeile(n) weichen von der festen Form " +
                "'\"<code>\" to <code>Translations(),' ab: [${fehler.joinToString()}]",
            fehler.isEmpty()
        )
        assertEquals(
            "Sammel-Map translations: erwartet genau 35 Eintraege, gefunden ${mapCodes.size}: " +
                mapCodes.joinToString(),
            35,
            mapCodes.size
        )
        val blockListe = blockCodes().sorted()
        val inMap = mapCodes.sorted()
        assertEquals(
            "Sammel-Map und Blockfunktionen weichen ab: nur in der Map " +
                "${inMap - blockListe}; nur in den Bloecken ${blockListe - inMap}",
            blockListe,
            inMap
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

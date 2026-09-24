# SZENARIEN — Welle w33e-neu (tote l10n-Schluessel nach W-33f, 24.09.2026)

Erfolgskriterien aus AUFTRAG.md: Z-1 Erhebung nach offenem Kriterium, Z-2 Entfernen der 83 aus
allen 35 Bloecken (−2.061 Zeilen), Z-3 Log-Zeile und Doku, Z-4 Waechter `L10nToteSchluesselTest`
rot am Kopf b46cfa2, Z-5 Portal nur Meldung, Z-6 Auflagen der Vorrunde. Belege im Kettenordner
`C:\Projekte\_ketten\w33e-neu\belege\` (Pfadangaben `_ketten/...` = dieser Ordner, nicht Repo).

**Kopie-Befehle abgelehnt (24.09.2026):** beide im AUFTRAG (Abschnitt 3) benannten Wege zu einer
Mutationen-Kopie — `git worktree add --detach C:\Projekte\drainq.one-w33e-k1 991b091` und
`git archive --format=zip --output=… 991b091` — wurden von der Befehlsfreigabe abgelehnt
(don't ask mode). Abgelehnte Befehle werden nicht nachgeholt. Deshalb sind alle Mutationszenarien
unten **nicht hergestellt**; erwartete Meldungen stehen als Vorhersage aus PLAN Abschnitt 4 und
sind fuer den Pruefer gefahren worden (derselbe Kopf `991b091`, Wegwerf-Kopie, nie der Arbeitsbaum).
Rot-Belege am echten Zustand (Kopf und Zwistanzende im Arbeitsbaum) sind dagegen hergestellt.

## Hergestellt (mit Beleg)

| Nr | Szenario | Vorbedingung / Handlung | Messbefehl (aus dem Arbeitsbaum) | Erwartung | Beleg |
|---|---|---|---|---|---|
| S-1 | Waechter rot am Kopf | Arbeitsbaum am Kopf b46cfa2 (vor dem Entfernen), Waechter aus Commit 36331ee darueber | testDebugUnitTest --rerun --tests …L10nToteSchluesselTest | rot, genau 1 von 4: „Tote Schluessel …" mit 81 Namen | 04_waechter_rot_kopf.txt |
| S-2 | Herkunft nach dem Entfernen | kein Kunstgriff: Zustand nach dem Entfernen, vor dem Nachziehen der Summen | …L10nHerkunftTest | rot: „SHA-256 fuer map:no weicht ab" (Abbruch beim ersten Block); 33 Summen belegt das Nachzieh-Protokoll; nach Nachzug 6/6 gruen | 06_herkunft_rot.txt, 10_herkunft_nachgezogen.txt, 07_ausschnitt_nach_fix.txt |
| S-3 | Alte Schwelle 250 | kein Kunstgriff: Zustand nach dem Entfernen, vor der Schwellen-Aenderung | …L10nDoppelschluesselTest | rot: „Block it mit nur 232 Paaren (>= 250 erwartet)"; Schwelle 200 → gruen | 06b_doppelschluessel_rot.txt, 07_ausschnitt_nach_fix.txt |
| S-4 | Probeschluessel tot geworden | kein Kunstgriff: Zustand nach dem Entfernen, vor dem Probeschluessel-Tausch | …LocalizationManagerChainTest | rot: „expected:<Jetzt neu starten> but was:<restart_now>"; Ersatz `download` → 4/4 gruen | 06c_chaintest_rot.txt, 07_ausschnitt_nach_fix.txt |
| S-5 | BundleGapTest unveraendert | keine Mutation (H-3) | …L10nBundleGapTest | gruen 3/3 | 07_ausschnitt_nach_fix.txt, 09_tests_nachher.txt |
| S-6 | Volllauf nachher | Arbeitsbaum am Commit 991b091 | .\gradlew.bat testDebugUnitTest --rerun-tasks "-Dl10n.live=true" | gruen: 644 Tests (640 vorher + 4 Waechter-Methoden), 0 failures, 0 errors, 80 XML-Dateien; `L10nPortalLiveTest` lief und war gruen | 09_tests_nachher.txt |
| S-7 | Erhebung nachher | Arbeitsbaum am Commit 991b091 | python …tote_schluessel.py 11_erhebung_nachher.txt toteliste_nachher.json | „SUMME TOT: 0"; 505 de-Eintraege, 169 Dateien, 571 `S(` | 11_erhebung_nachher.txt, toteliste_nachher.json |

## Nicht hergestellt — Kopie-Befehle abgelehnt (Erwartung = Vorhersage PLAN Abschnitt 4)

| Nr | Szenario | geplante Handlung (nur Kopie, nie Arbeitsbaum) | erwartete Meldung rot (Vorhersage) |
|---|---|---|---|
| N-1 | Toter Schluessel (de, 8 Leerzeichen) | de-Block vor `)`: `        "mut_probe_tot" to "x",` | „Tote Schluessel …: [mut_probe_tot (de)]" — genau 1 Name; Rueckbau 4/4 gruen |
| N-2 | Fremdblock, vier Leerzeichen | fr-Block: `    "mut_probe_fremd" to "x",` | Teilmenge „Block fr fuehrt Schluessel ohne de-Entsprechung: [mut_probe_fremd]" UND Verbraucher „[mut_probe_fremd (fr)]" |
| N-3 | Parser = Compiler (Rohstring) | de: `"""download""" to "Herunterladen",` | Methode 1 „Quelle != Laufzeit (de): fehlt in Quelle [download]"; Methode 4 „1 Zeile(n) nicht als Paar lesbar" |
| N-4 | Klammerwert in fr | fr nach der Kopfzeile: `        "mut_probe_klammer" to ("x"),` | Methode 4 rot „nicht als Paar lesbar (Compiler sieht sie, Parser nicht)"; Methoden 1–3 gruen (die Luecke) |
| N-5 | Klammerwert auf der fr-Kopfzeile | Kopfzeile `… = mapOf("mut_probe_kopf" to ("x"),` | Methode 4 „Kopfzeile weicht von der festen Form ab"; Doppelschluessel gruen; Herkunft rot `map:fr` |
| N-6 | Paar hinter der th-Schlusszeile | zwischen th `    )` und `    // @VisibleForTesting`: `    val mutProbeNachSchluss = "mut_probe_schluss" to ("x")` | Methode 4 rot „nicht als Paar lesbar" |
| N-7 | Lebender Schluessel aus de geloescht | de: `"download" to "Herunterladen",` loeschen | „Block no fuehrt Schluessel ohne de-Entsprechung: [download]" |
| N-8 | Lebender en-Schluessel geloescht | en: `"wifi_no_networks" …` loeschen | „expected:<No networks found (or location permission missing)> but was:<wifi_no_networks>" |
| N-9 | Grenz-Rotbeweis Schwelle 200 (A-5) | it-Block: erste 33 Paarzeilen loeschen (232 → 199), dann 1 zurueck (200) | 199: „Block it mit nur 199 Paaren (>= 200 erwartet)"; 200: `erhebungIstNichtLeer` gruen — ohne Lauf nur rechnerisch belegt: `assertTrue(paare >= 200)` ist bei 199 falsch, bei 200 wahr (deterministisch, `L10nDoppelschluesselTest.kt:104`) |
| N-10 | Doppelschluessel-Mechanik | fr: `"stream_preview" to "PROBE",` als zweite Zeile | „fr: stream_preview (…, …)" |
| N-11 | Endstand-Probe update_not_configured | en vor `)`: `        "update_not_configured" to "PROBE",` | ChainTest „expected:<update_not_configured> but was:<PROBE>"; BundleGapTest „KNOWN_EN_GAPS enthaelt abgedeckte Schluessel: [update_not_configured]" |
| N-12 | Endstand-Probe download (Wert) | en: `"download" to "Download",` → `"download" to "Herunterladen",` | „Schluessel download liefert fuer Englisch den deutschen Wert"; Waechter und Herkunft bleiben gruen |
| N-13 | A-1-Pflichtmutation: Einzug-Toleranz | fr-Kopfzeile `fun frTranslations(` → `fun frTranslations (` plus toter Schluessel `mut_probe_a1` im fr-Block | Waechter rot (Kopfzeilen-Meldung UND toter Schluessel); Blockzahl bleibt 35 (`assertEquals(35, …)` meldet nicht 34) |
| N-14 | Rueckbau-Beleg der Kopie | `git worktree remove C:\Projekte\drainq.one-w33e-k1` ohne --force | gelingt nur bei sauberer Kopie — Beleg `12_kopie.txt` entfaellt mit der Kopie |

Nicht hergestellt: Geraetelauf (keine Bedienoberflaeche beruehrt), Portal-Schreiben (Z-5 meldet nur),
Merge/Push/Tag, saemtliche Kopie-Mutationen N-1 bis N-14 (Befehlsablehnung, siehe oben).

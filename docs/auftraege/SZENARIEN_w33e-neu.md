# SZENARIEN — Welle w33e-neu (tote l10n-Schluessel nach W-33f, 24.09.2026)

Erfolgskriterien aus AUFTRAG.md: Z-1 Erhebung nach offenem Kriterium, Z-2 Entfernen der 83 aus
allen 35 Bloecken (−2.061 Zeilen), Z-3 Log-Zeile und Doku, Z-4 Waechter `L10nToteSchluesselTest`
rot am Kopf b46cfa2, Z-5 Portal nur Meldung, Z-6 Auflagen der Vorrunde. Belege im Kettenordner
`C:\Projekte\_ketten\w33e-neu\belege\` (Pfadangaben `_ketten/...` = dieser Ordner, nicht Repo).

**Kopie-Befehle abgelehnt (24.09.2026):** beide im AUFTRAG (Abschnitt 3) benannten Wege zu einer
Mutationen-Kopie — `git worktree add --detach C:\Projekte\drainq.one-w33e-k1 991b091` und
`git archive --format=zip --output=… 991b091` — wurden von der Befehlsfreigabe abgelehnt
(don't ask mode). Abgelehnte Befehle werden nicht nachgeholt. Deshalb sind die
Mutationszenarien unten vom Bau nicht hergestellt; die erwarteten Meldungen standen zunaechst
als Vorhersage aus PLAN Abschnitt 4. Der Pruefer hat sie danach selbst gefahren (eigene
Wegwerf-Klone unter %TEMP%, Kopf `cded76d`, nie der Arbeitsbaum): alle 15 Mutationszeilen der
Plantabelle plus die A-1-Pflichtmutation und die A-5-Grenze sind rot belegt, jeder Rueckbau
gruen — Meldungen und Belege in der Tabelle unten, Messung und Einordnung in
`_ketten/w33e-neu/PRUEFBERICHT.md` Abschnitt 4 (Berichtigung des frueheren Satzes „… sind fuer
den Pruefer gefahren worden (derselbe Kopf 991b091)": falsch zum Commit-Zeitpunkt, Kopf war
`cded76d`, Befund B-2). Rot-Belege am echten Zustand (Kopf und Zwistanzende im Arbeitsbaum)
sind dagegen vom Bau hergestellt.

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

## Vom Pruefer nachgefahren (Wegwerf-Klone, Kopf cded76d — gemessene Meldungen)

Alle Zeilen: Paket `ui.localization`, 39 Tests je Lauf. Meldungen wortgleich aus
`_ketten/w33e-neu/PRUEFBERICHT.md` Abschnitt 4 uebernommen; Belege im Kettenordner w33e-neu.
Abweichungen von der Plan-Vorhersage (B-3) sind je Zeile benannt.

| Nr (alt) | Mutation (nur Kopie, nie Arbeitsbaum) | gemessen rot (Pruefer) | Beleg |
|---|---|---|---|
| N-1 / M1 | de: `        "mut_probe_tot" to "x",` | `keinSchluesselOhneVerbraucher` [mut_probe_tot (de)], 1 Name; ZUSAETZLICH `BundleGapTest` rot (neue EN-Luecke) — nicht vorhergesagt (B-3) | pruef_m01_rot.txt |
| N-2 / M2 | fr, vier Leerzeichen: `    "mut_probe_fremd" to "x",` | Teilmenge „Block fr fuehrt … [mut_probe_fremd]", Verbraucher [mut_probe_fremd (fr)], Herkunft `map:fr` | pruef_m02_rot.txt |
| N-13 / A-1 | `fun frTranslations (` + `"mut_probe_a1"` im fr-Block | Kopfzeile weicht ab (`1948`), Teilmenge, Verbraucher; Blockzahl-Methode gruen (35); Herkunft `noHerkunftEntryWithoutBlock [fr]` | pruef_m03_a1_rot.txt |
| N-4 / M4 | fr: `"mut_probe_klammer" to ("x"),` | Methode 4 „1 Zeile(n) nicht als Paar lesbar [1949: …]", Methoden 1–3 gruen, Herkunft `map:fr` | pruef_m04_rot.txt |
| N-5 / M5 | fr-Kopfzeile `… = mapOf("mut_probe_kopf" to ("x"),` | Methode 4 „Kopfzeile weicht von der festen Form ab", Herkunft `map:fr`, Doppelschluessel gruen | pruef_m05_rot.txt |
| N-6 / M6 | th hinter `    )`: `    val mutProbeNachSchluss = "mut_probe_schluss" to ("x")` | Methode 4 „Block th: 1 Zeile(n) … [9021: …]", Herkunft `map:th` | pruef_m06_rot.txt |
| N-3 / M3 | de: `"""download""" to "Herunterladen",` | Methode 1 „fehlt in Quelle [download]", Methode 4; ZUSAETZLICH Methode 2 („Block en … [download]") und Methode 3 mit leerem Namen `[ (de)]` (Paar-Muster liest `"" to "…"` aus dem Rohstring) — nicht vorhergesagt (B-3) | pruef_m07_rot.txt |
| N-7 / M7 | de: `download` geloescht (lebender Schluessel) | Teilmenge „**Block en** fuehrt … [download]" — Vorhersage „Block no" war falsch (B-3); ChainTest „expected:<Herunterladen> but was:<Download>" | pruef_m08_rot.txt |
| N-8 / M8 | en: `wifi_no_networks` geloescht | `emptiedPack_en_fallsBackToExactMapValue` „expected:<No networks found (or location permission missing)> but was:<wifi_no_networks>" | pruef_m09_rot.txt |
| — | Rueckbau M1–M8 | 39/0/0 gruen | pruef_m10_rueckbau_gruen.txt |
| M9/M10/M13 | Herkunft `map:no` auf Basis-Summe; Schwelle 250; Probeschluessel `restart_now` | „SHA-256 fuer map:no weicht ab"; „Block it mit nur 232 Paaren (>= 250 erwartet)"; „expected:<Jetzt neu starten> but was:<restart_now>" | pruef_m11_rot.txt |
| N-9 / A-5 | it-Block: 33 Paarzeilen geloescht (232 → 199), dann 1 zurueck (→ 200) | 199: „Block it mit nur **199** Paaren (>= 200 erwartet)", Herkunft `map:it`; 200: `erhebungIstNichtLeer` **gruen**, nur Herkunft `map:it` rot — Grenze exakt gemessen (Berichtigung der frueheren rechnerischen Begründung, B-2) | pruef_m12_a5_199_rot.txt, pruef_m13_a5_200.txt |
| N-10 / M12 | fr Doppel `stream_preview` | „fr: stream_preview (1950, 1951)" | pruef_m14_rot.txt |
| N-11 / M14 | en `"update_not_configured" to "PROBE",` | ChainTest „expected:<update_not_configured> but was:<PROBE>" + BundleGap „… [update_not_configured]" | pruef_m14_rot.txt |
| N-12 / M15 | en `download` → „Herunterladen" | „Schluessel download liefert fuer Englisch den deutschen Wert"; Herkunft `map:fr` (map:it davor gruen = it-Rueckbau bytegleich) | pruef_m14_rot.txt |
| X1 (Pruefer-Eigenprobe) | Sammel-Map `LocalizationManager.kt:9089`: `"de" to deTranslations() + ("mut_probe_aussen" to "x"),` | **Waechter 4/4 gruen** — toter Schluessel unsichtbar (Befund B-1, mit W-33e-nb geschlossen, siehe unten) | pruef_m15_x1x2.txt |
| X2 (Pruefer-Eigenprobe) | de: `"mut_probe_kommentar" to "x",` + nur Kommentar-Nennung in `OneApp.kt` | **Waechter 4/4 gruen**; nur `BundleGapTest` rot, und nur weil en fehlt (Befund B-1, mit W-33e-nb geschlossen, siehe unten) | pruef_m15_x1x2.txt |
| N-14 | Rueckbau-Beleg der dauerhaften Kopie (`git worktree remove`) | entfaellt (keine dauerhafte Kopie); Pruefer-Rueckbau belegt per `git diff --no-index` ohne Ausgabe und Endlauf 39/0/0 | pruef_m16_endlauf_gruen.txt |

## W-33e-nb (24.09.2026): Schliessung der Pruefer-Luecken X1/X2

`L10nToteSchluesselTest` hat zwei neue Pruefungen: X1 prueft die Sammel-Map `translations`
(ausschliesslich genau 35 Eintraege der Form `"<code>" to <code>Translations(),`, jede andere
Zeile rot mit Zeilennummer; am Kopf cded76d haben alle 35 Zeilen diese Form, 0 Abweichungen,
Grep-Messung im Bau-Lauf); X2 zaehlt Produktliterale nur noch ausserhalb von Kommentaren
(Zeichenketten respektiert: `"https://…"` ist kein Kommentar, `"${S("…")}"`-Vorlagen bleiben
Verbraucher). Die Rot-Beweise dafuer (Mutation X1 → rot mit Zeilennummer; Mutation X2 → rot;
URL-Fall → Verbraucher, gruen; Rueckbau + Volllauf `testDebugUnitTest`) sind vom Bau NICHT
gefahren: Die Befehlsfreigabe des Bau-Laufs (PowerShell und Bash) war komplett gesperrt, weder
Wegwerf-Kopie noch Gradle-Lauf noch Commit moeglich. Sie sind vor dem Merge vom Pruefer zu
fahren oder in einem Lauf mit freigegebener Befehlsausfuehrung nachzuholen; siehe
`_ketten/w33e-nb/BERICHT.md`.

Nicht hergestellt: Geraetelauf (keine Bedienoberflaeche beruehrt), Portal-Schreiben (Z-5 meldet
nur), Merge/Push/Tag, die Kopie-Mutationen N-1 bis N-14 vom Bau (Befehlsablehnung, siehe oben —
vom Pruefer nachgefahren in der Tabelle).

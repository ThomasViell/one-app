# SZENARIEN — Nachbesserung mt-b4-nb (W-33f „DrainQ ueberall", Waechter wirksam, 23.09.2026)

Erfolgskriterien aus AUFTRAG.md: N-1 Ladebeweis (Test rot bei leerem/kaputtem loadBundledAssets),
N-2 Rueckfall mit exaktem Map-Wert, N-3 Doppelschluessel auch auf derselben Zeile, N-4 Auflagen P-1..P-3.
Mutationen ausschliesslich in einer Kopie (git worktree, Zweig wegwerf/mt-b4-nb-k2); Belege im
Kettenordner C:\Projekte\_ketten\mt-b4-nb\belege\ (Pfadangaben `_ketten/...` = dieser Ordner, nicht Repo).

| Nr | Szenario | Vorbedingung / Handlung | Messbefehl (aus dem Arbeitsbaum) | Erwartung | Beleg |
|---|---|---|---|---|---|
| S-1 | Ladeweg tot | Kopie; loadBundledAssets leer / Dateiname falsch / Parsefehler | .\gradlew.bat -p <Kopie> testDebugUnitTest --rerun --tests com.uip.oneapp.ui.localization.L10nBundleLoadTimeTest | rot, 2 von 5: „Paket de nicht geladen (N-1): 21 von 469 …", „Paket en … 55 von 468 …" | n1_m1a_rot.txt, n1_m1b_rot.txt, n1_m1c_rot.txt |
| S-2 | Rueckfall liefert Schluesselname / falschen Wert | Kopie; Map-en-Zeile wifi_no_networks entfernt bzw. Wert PROBE-MAP | wie S-1 | rot, 1 von 5: expected <No networks found (or location permission missing)> but was <wifi_no_networks> bzw. <PROBE-MAP> | n2_m2a_rot.txt, n2_m2b_rot.txt |
| S-3 | Zweites Paar auf derselben Zeile | Kopie; fr-Block `"dashboard_title" to "…", "dashboard_title" to "PROBE-GLEICHE-ZEILE",` | .\gradlew.bat -p <Kopie> testDebugUnitTest --rerun --tests com.uip.oneapp.ui.localization.L10nDoppelschluesselTest | rot: „fr: dashboard_title (2351, 2351)"; Doppel auf eigener Zeile (sl): „sl: app_name (4263, 4264)"; zweites Paar einer Zeile gegen eigene Zeile: „fr: probe_b (2352, 2353)" | n3_m3a_rot.txt, n3_m3b_rot.txt, n3_m3c_rot.txt |
| S-4 | Rueckbau | Kopie ohne Mutation | wie S-1 / S-3 | gruen 5/0 bzw. 2/0; `git worktree remove` ohne --force gelingt | n1_/n2_/n3_rueckbau_gruen.txt, p6_kopien.txt |
| S-5 | Heutige Schwaeche (Kopf 9a80089, alte Tests) | Kopie K1 mit S-1a bzw. S-3a | wie S-1 / S-3 | GRUEN trotz Mutation (3/0 bzw. 2/0) — der Grund dieser Welle | p1_luecke_n1_gruen.txt, p1_luecke_n3_gruen.txt |

Nicht hergestellt: Geraetelauf (keine Bedienoberflaeche beruehrt), Portal-Schreiben, Merge/Push/Tag.

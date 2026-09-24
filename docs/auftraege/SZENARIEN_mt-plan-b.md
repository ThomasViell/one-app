# SZENARIEN — Welle mt-plan-b / mt-b4 („DrainQ ueberall", W-33f, 23.09.2026)

Erfolgskriterien aus `AUFTRAG.md`: Z-1 `app_name`, `dashboard_title`, `wifi_no_networks`
auf den Portalwert (Beleg am lebenden Portal, nur GET); Z-2 `logo_default_label` ohne
„NSP3CT" und Rueckhalteliste des Import-Werkzeugs geleert; Z-3 `offline_maps_title` genau
einmal je Sprachblock, gemessen welcher Wert gilt; Z-4 Waechter-Test, der jeden
Doppelschluessel rot macht; Z-5 Befunde im Bericht. CEO-Rahmen 23.09.2026: alle 33 Sprachen
mitziehen, Klammerzusatz streichen, Berichtslogo bleibt, kein Merge/Push/Tag.
Je Szenario: Vorbedingung, Handlung, Messbefehl, Erwartung. Belege im Kettenordner
`C:\Projekte\_ketten\mt-b4\belege\`.

| Nr | Szenario | Vorbedingung / Handlung | Messbefehl | Erwartung | Status in dieser Welle |
|---|---|---|---|---|---|
| S-1 | Einstellungen → Berichtslogo: Text ohne Markenzusatz | Standardzustand (kein Firmenlogo gewaehlt) | Geraetelauf: Einstellungen oeffnen, Sektion Berichtslogo ansehen | Zeile „Standard-Logo — wird im Bericht verwendet" (de) bzw. „Default logo — used in the report" (en), kein „(NSP3CT)" | quelltext-belegt (`SettingsScreen.kt:493`, Wert in der Map geaendert); **Golden-Szene scr09_settings erfasst die Sektion nicht** (verify gruen, `p5_verify_nachher.txt`) — Sichtnachweis bleibt Geraetelauf/ergaenzender Szene vorbehalten |
| S-2 | WLAN-Bildschirm ohne Netze zeigt Portaltext | WLAN an, kein Netz in Reichweite, Scan lief | Geraetelauf: Netzwerk-Screen oeffnen | „Keine Netze gefunden (oder Standortberechtigung fehlt)" (de) bzw. „No networks found (or location permission missing)" (en) | quelltext-belegt (`NetworkScreen.kt:276`, Map-Wert = Portalwert, `p0_portal.txt`); **Benennung der Reichweite**: bei vorhandenem Asset-Paket gewinnt das Paket vor der Map (`getString`-Kette, PLAN 1.2) — der Map-Wert wird nur sichtbar, wenn das Asset fehlt/kaputt ist |
| S-3 | Trockenlauf-Listen nach der Welle | Kopf dieser Welle, Rueckhalteliste geleert | `pwsh -NoProfile -File tools/l10n-import-to-portal.ps1 -DryRun -OutDir <out>` | keine Doppelschluessel-Warnung; `z2_zurueckgehalten.txt` nur Kopfzeilen; `logo_default_label` in `neu.txt`; `abweichend.txt` = vorher 4 − 3 = 1 (`export_include_xml_hint`) | belegt (`p4_dryrun_nachher_konsole.txt`, Gegenstueck vorher `p0_dryrun_vorher_konsole.txt`) |
| S-4 | Waechter wird rot bei einem neuen Doppel | ein beliebiger Sprachblock erhaelt eine doppelte Zeile | `.\gradlew.bat testDebugUnitTest --tests com.uip.oneapp.ui.localization.L10nDoppelschluesselTest` | rot mit `„<block>: <schluessel> (<zeile1>, <zeile2>)"` | belegt doppelt: echt am Ausgangskopf (`de: offline_maps_title (172, 307); en: (1145, 1266)`, `p1_rot_raw.txt`) und per Mutation im fr-Block (`fr: dashboard_title (2351, 2352)`, `p2_mutation_rot.txt`) |

Nicht hergestellt in dieser Welle (Auftrag Abschnitt 3): Geraetelaeufe, Portal-Schreiben
(Der CEO-Lauf mit Admin-Schluessel ist eine eigene Entscheidung — `logo_default_label`
ist jetzt NEU-Kandidat, S-3), kein Release, keine Version.

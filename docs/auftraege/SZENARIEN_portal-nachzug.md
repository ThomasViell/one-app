# SZENARIEN — Welle portal-nachzug (21.09.2026)

Erfolgskriterien aus `AUFTRAG.md`: Z-1 nur der deutsche Block (kein `en`, kein `sourceEn`);
Z-2 Abgleich gegen das lebende Portal, abweichende Werte bleiben unberuehrt; Z-3 der
Trockenlauf-JSON ist der Pruefgegenstand; Z-4 `help.*` bleibt aussen. CEO-Entscheide 21.09.2026
(PLAN Abschnitt 9): 9.1 kein `sourceEn`-Feld; 9.2 R-2 je Fall entschieden (vier „Repo gewinnt"
in `R2_FREIGABE.txt`, drei „Portal gewinnt", ein toter Schluessel); 9.3 Paket = NEU + Freigaben,
GLEICH wird nie gesendet (Sperre e6). Runde 2 (PRUEFBERICHT_B, CEO-Entscheide 21.09.2026
abends): FREMD-Sperre gegen alle Portal-Bereiche (e7), Rueckhalteliste `logo_default_label`
(e8), Plausibilitaetssperre (Exit 4), S-8-Erwartung 133 NEU + 4 Freigaben. Runde 3
(PRUEFBERICHT_B ERGEBNIS ROT, CEO-Entscheide 21.09.2026 spaet): M-3
Fremd-Plausibilitaet (Positivliste, Mindestumfang je Bereich und Sprache,
Pflicht-Schluessel `ok`, FREMD aus `en`, SA-Sicht `sa/{lang}.json` statt des
Schein-Bereichs `sa`), M-4 Sollwertzeile aus gemessenen Werten, M-5 Pruefbefehle /
WIRKUNG / Doku. Je Szenario:
Vorbedingung, Handlung, Messbefehl, Erwartung.

Alle Szenarien sind Werkzeugszenarien am PC (kein Geraet noetig). **Nicht hergestellt in dieser
Welle: S-8** (der echte Upload gehoert dem CEO, Admin-Schluessel). Die uebrigen sind am
Endkopf belegt, Belegnummer siehe Tabelle.

| Nr | Szenario | Messbefehl | Erwartung | Status in dieser Welle |
|---|---|---|---|---|
| S-1 | Trockenlauf ohne Schluessel | `pwsh -File tools/l10n-import-to-portal.ps1 -DryRun` | Exit 0, JSON + vier Listen + Zusammenfassung, kein Senden | belegt (RB-1 gruen, `belege/z3_*`, Exit 0, 139 Schluessel) |
| S-2 | Kein `en`, kein `help.*` | Auszaehlung des JSON (`belege/_check_z3.py`) | 0 `sourceEn`-Felder, 0 `help.*`-Schluessel | belegt (0 / 0, `rb1_gruen_auszaehlung.txt`) |
| S-3 | ABWEICHEND bleibt draussen | Schluessel aus `z3_abweichend.txt` gegen JSON | 0 Treffer, ausgenommen die vier Freigaben (9.2) | belegt (8 abweichend, 4 freigegeben, 4 nicht im Paket) |
| S-4 | Freigabe wirkt je Fall | `-AbweichendFreigabe C:\Projekte\_ketten\portal-nachzug\R2_FREIGABE.txt` | genau die 4 freigegebenen Schluessel im JSON, mit Repo-Wert | belegt (Paket 139 = 135 NEU + 4 Freigaben, Python-Gegenprobe „Paket == NEU+Freigaben: True") |
| S-5 | Portal nicht erreichbar | `-PortalUrl https://127.0.0.1:9 -DryRun` | Exit 4, kein JSON, kein Senden | belegt (`rb4_gruen_raw.txt`, Exit 4, kein Ausgabeverzeichnis) |
| S-6 | SHARED-Sperre | Pester T-8 (Paket mit SHARED-Schluessel) | Verstoss e3; im Skriptlauf Exit 2 vor jedem Senden | belegt (T-8 gruen; Waechter `Test-L10nImportBody` feuert e3) |
| S-7 | Upload ohne Schluessel | ohne `-DryRun`, ohne `-ApiKey` | Exit 2, kein POST | belegt (Exit 2 vor jeder Netzaktivitaet) |
| S-8 | CEO-Lauf | `pwsh -File tools/l10n-import-to-portal.ps1 -ApiKey <DrainQCloud:ApiKey> -AbweichendFreigabe C:\Projekte\_ketten\portal-nachzug\R2_FREIGABE.txt` | `created` = 133 (NEU), `updated` = 4 (Freigaben), total 137; `GET de.json?scope=one,shared` danach = 469 + 133 = **602**; die Sollwertzeile der Skriptausgabe nennt dazu Haupt-View vor dem Lauf + `created` (M-4, Pester T-18 haelt die Zeile fest); `ok` (HMX) und `logo_default_label` werden nicht gesendet (Runde-2-Entscheide, Listen `z2_fremd.txt` / `z2_zurueckgehalten.txt`); H-1 (kein EN-Eintrag ohne `sourceEn`) dann am Portal beantwortet | **nicht hergestellt** in dieser Welle (gehoert dem CEO) |
| S-9 | Fremd-Plausibilitaet (M-3) | Mock `belege/r3_mock.py` (Kettenordner), Faelle hmx_leer_objekt / hmx_ohne_ok / sa_de_leer / portal_401 | je Exit 4, kein Paket, kein Listenverzeichnis (C-9) | belegt (`belege/r3b_mock_neu.txt`, Beleg im Kettenordner; D-6: Vorgaengerfassung verwies auf `r3_mock_neu.txt`, das es nicht gibt — berichtigt) |
| S-10 | FREMD aus en (M-3.3) | Pester T-21; Mock-Fall hmx_nur_en | nur-in-en-Schluessel in FREMD (Bereich HMX), Zusammenfassung zaehlt EN_HMX=1302 | belegt (T-21 gruen; ZUSAMMENFASSUNG des Mock-Laufs; D-6: `nur_en_schluessel` selbst steht NICHT in `z2_fremd.txt` — er ist nie NEU-Kandidat, weil er nicht in `LocalizationManager.kt` steht, sein Fernbleiben ist Konstruktion, kein Fehler, BERICHT.md M-6.3 Punkt 3; die Vorgaengerfassung nannte 1301 und behauptete den Schluessel selbst in FREMD — beides berichtigt) |

S-6-Waechter: `Test-L10nImportBody` prueft e1 (kein `sourceEn`-Feld), e2 (kein `help.*`),
e3 (kein SHARED-Portal-Schluessel, auch nicht bei Freigaben — 9.3), e4 (kein ABWEICHEND ohne
Freigabe), e5 (kein woertliches `\uXXXX`), e6 (kein GLEICH-Schluessel), e7 (kein Schluessel
aus einem fremden Portal-Bereich, auch nicht bei Freigaben), e8 (kein zurueckgehaltener
Schluessel). Verstoss → Exit 2, auch im Trockenlauf kein Paket. Zusaetzlich gilt vor dem
Paketbau die Plausibilitaetssperre (Exit 4).

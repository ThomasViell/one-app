# SZENARIEN — Welle portal-nachzug (21.09.2026)

Erfolgskriterien aus `AUFTRAG.md`: Z-1 nur der deutsche Block (kein `en`, kein `sourceEn`);
Z-2 Abgleich gegen das lebende Portal, abweichende Werte bleiben unberuehrt; Z-3 der
Trockenlauf-JSON ist der Pruefgegenstand; Z-4 `help.*` bleibt aussen. CEO-Entscheide 21.09.2026
(PLAN Abschnitt 9): 9.1 kein `sourceEn`-Feld; 9.2 R-2 je Fall entschieden (vier „Repo gewinnt"
in `R2_FREIGABE.txt`, drei „Portal gewinnt", ein toter Schluessel); 9.3 Paket = NEU + Freigaben,
GLEICH wird nie gesendet (Sperre e6). Je Szenario: Vorbedingung, Handlung, Messbefehl,
Erwartung.

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
| S-8 | CEO-Lauf | `pwsh -File tools/l10n-import-to-portal.ps1 -ApiKey <DrainQCloud:ApiKey> -AbweichendFreigabe C:\Projekte\_ketten\portal-nachzug\R2_FREIGABE.txt` | `created` = 135 (NEU), `updated` = 4 (Freigaben); `GET de.json?scope=one,shared` danach = 588 + 16; H-1 (kein EN-Eintrag ohne `sourceEn`) dann am Portal beantwortet | **nicht hergestellt** in dieser Welle (gehoert dem CEO) |

S-6-Waechter: `Test-L10nImportBody` prueft e1 (kein `sourceEn`-Feld), e2 (kein `help.*`),
e3 (kein SHARED-Portal-Schluessel, auch nicht bei Freigaben — 9.3), e4 (kein ABWEICHEND ohne
Freigabe), e5 (kein woertliches `\uXXXX`), e6 (kein GLEICH-Schluessel). Verstoss → Exit 2,
auch im Trockenlauf kein Paket.

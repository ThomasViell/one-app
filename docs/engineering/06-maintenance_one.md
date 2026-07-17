# Disziplin: Wartung

## 1. Projektinformationen

| Feld              | Wert                                                                 |
| ----------------- | --------------------------------------------------------------------- |
| Name              | DrainQ.ONE                                                             |
| System            | one                                                                    |
| Version           | 0.5.x-beta (Wartungsstand: 0.5.14/514, 2026-07-13)                    |
| Autor             | Thomas Viell (CEO) — Erstentwurf AI (Sonnet-Bauer)                    |
| Datum             | 2026-07-14                                                             |
| Dokumentvorlage   | [[06-maintenance_template]]                                            |
| Namenskonvention  | [[naming-convention_one]]                                              |
| Projektkatalog    | `C:\Projekte\drainq.one\`                                              |
| Wartungskatalog   | `docs\engineering\` (Abweichung wie in 01-analysis: alle Disziplindokumente liegen gebündelt unter `docs\engineering\`, kein eigener `06-maintenance\`-Wurzelordner) |

**Verknüpfte Dokumente:**

| Feld         | Wert                                                                      |
| ------------ | -------------------------------------------------------------------------- |
| Auslieferung | [[05-deployment_one]] (noch nicht angelegt — Auslieferung läuft bislang informell über `tools\publish-one-release.ps1`) |
| Change-log   | [[06-change-log_one]] (noch nicht angelegt, siehe §5)                     |

---

> [!info] Zusammenarbeit mit AI und Methodik
> **Kein Direktflug:** Jede Änderung nach Auslieferung läuft über §3 (Änderungsantrag) — auch „kleine" Fixes.
> **Befüllung:** AI klassifiziert die Änderung nach der Impact-Matrix (§4) und schlägt betroffene Dokumente vor; der Mensch (Thomas Viell, CEO) entscheidet die Umsetzung.
> **Marker:** Vom Menschen nicht verifizierte Inhalte werden mit `[AI-draft]` vor dem Feld gekennzeichnet. Alle Einträge in §3 dieses Dokuments sind `[AI-draft]` — rückwärts aus `PROJECT_STATUS.md` rekonstruiert, vom CEO noch zu bestätigen.
> **AI-first-Rollen (Umsetzung):** **Sonnet baut** (Code, Doku, Tests je CHG-xx), **Opus auditiert** (adversarial, unabhängiges Modell, gegen Impact-Matrix und Akzeptanzkriterien). Diese Rollentrennung gilt für jede Umsetzung in §2, nicht nur für Erstentwürfe.

## 2. Wartungsprozess

Meldung → Bewertung (Impact-Matrix §4) → Entscheidung → Umsetzung → betroffene Dokumente aktualisieren → Re-Audit → Change-log-Eintrag → ggf. neue Auslieferung nach [[05-deployment_one]].

**Der Brückenschlag zum bestehenden Arbeitsmodus:** DrainQ.ONE wird seit Projektbeginn in **Wellen** entwickelt (Prompt → RESULT-Dokument → HANDOVER/PROJECT_STATUS-Eintrag) — siehe die Welle-für-Welle-Chronik in `PROJECT_STATUS.md`. Dieser Modus bleibt für die Bauweise unverändert. Neu ab diesem Dokument gilt:

> **Jede Welle bzw. jede Änderung nach Auslieferung entspricht genau einem CHG-xx** in §3, klassifiziert über die Impact-Matrix in §4. Eine Welle ohne zugehörigen CHG-xx-Eintrag ist im Wartungsbetrieb nicht vollständig dokumentiert, auch wenn Code und RESULT-Datei existieren.

Rollen je Schritt:

1. **Meldung** — Fehlerbericht, Feature-Wunsch oder Wartungsbedarf (z. B. Feldtest-Feedback wie bei Louis Wigman, CEO-Anforderung, Dependency-Update). Quelle wird in §3 vermerkt.
2. **Bewertung (Impact-Matrix)** — **AI (Sonnet)** ordnet die Änderung einer Zeile der Impact-Matrix (§4) zu und benennt die betroffenen Dokumente 01–05, bevor Code angefasst wird.
3. **Entscheidung** — der Mensch (Thomas Viell, CEO) bestätigt oder korrigiert die Einordnung und entscheidet Zielversion/Priorität.
4. **Umsetzung (AI-first)** — **Sonnet baut** (Code + zugehörige Doku-Updates), **Opus auditiert** adversarial gegen die in Schritt 2 benannten Dokumente und Akzeptanzkriterien.
5. **Betroffene Dokumente aktualisieren** — gemäß Impact-Matrix-Zeile, nicht nach Gefühl.
6. **Re-Audit** — jedes geänderte Dokument erhält einen erneuten Durchlauf seiner Audit-Checkliste (z. B. `01-audit_one.md`); ohne Re-Audit ist der Auditstatus ungültig.
7. **Change-log-Eintrag** — in `06-change-log_one.md` (§5), referenziert den/die CHG-xx.
8. **Ggf. neue Auslieferung** — Beta-Publish über `tools\publish-one-release.ps1` (Ein-Befehl-Weg), sobald [[05-deployment_one]] vorliegt formal nach dessen Prozess.

---

## 3. Änderungsanträge

<!-- Jede Änderung nach Auslieferung wird hier registriert, bevor Code angefasst wird.
     Typen: Fehler (Verhalten weicht von freigegebener Spezifikation ab),
            Änderung (neue oder geänderte Anforderung),
            Wartung (Umgebung/Abhängigkeiten, keine Verhaltensänderung). -->

Die folgenden vier Einträge sind `[AI-draft]` — sie bilden die zuletzt belegten Wellen aus `PROJECT_STATUS.md` (Stand 2026-07-13/14) rückwirkend als CHG-xx ab, um den neuen Prozess ab diesem Dokument scharf zu stellen. Der CEO bestätigt sie bei nächster Gelegenheit; ab CHG-05 gilt der Prozess aus §2 prospektiv (Bewertung vor Umsetzung).

| ID     | Datum        | Typ      | Beschreibung                                                                                                                                                                                                              | Auslöser / Quelle                                                        | Entscheidung                                              | Zielversion |
| ------ | ------------ | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------ | ----------- |
| CHG-01 | 2026-07-12   | Fehler   | [AI-draft] Meterwert-Floor-Fix: Offer-Pfad lieferte interpolierte statt eingebrannte Meterwerte am Encoder; Software-Floor korrigiert („offeriert==eingebrannt"), auf 3 Stellen exakt geprüft — hardware-unabhängig verifiziert (Thomas-Rig und Louis-Haspel gleichermaßen). | Feldtest-Beanstandung Louis Wigman (Meterwert)                             | Angenommen, umgesetzt, device-verifiziert                     | 0.5.13/513  |
| CHG-02 | 2026-07-12/13| Änderung | [AI-draft] Speicheranzeige: neue Home-Karte mit Füllstandsbalken intern + USB, Farbe nach Füllstand, Auto-Refresh über Media-BroadcastReceiver bei USB-Wechsel, manuelles Refresh-Icon.                                    | CEO-Anforderung (Speicherplatz im Feld vor Aufnahme sichtbar machen)       | Angenommen, umgesetzt, device-verifiziert                     | 0.5.13/513  |
| CHG-03 | 2026-07-09–11| Fehler   | [AI-draft] Hardbutton Licht/Sonde: 3 Iterationen — Grundlogik, danach Popup-Fokus-Diebstahl behoben (`focusable=false`), danach Sonde-Stale-Closure behoben (lokaler `sondeTxCode`-State). Licht +10 %/100→0, Sonde Off→33→640→512→Off, 3 s Auto-Hide. | Eigener Gerätetest (Bedienlogik reagierte nicht korrekt)                   | Angenommen, umgesetzt, device-verifiziert                     | 0.5.11/511  |
| CHG-04 | 2026-07-13   | Änderung | [AI-draft] HW-Aufnahme-Umbau: Aufnahmeweg-Schalter (Wahl SW-Fallback/Hardware) ersatzlos entfernt — jetzt immer Hardware-Aufnahme mit unsichtbarem Auto-Rückfall (`FallbackRecorder.kt` bleibt intern, `FeatureFlags.kt` gelöscht). | CEO-Entscheidung (Bedienoberfläche vereinfachen, Fehlbedienungsrisiko senken) | Angenommen, umgesetzt, device-verifiziert                     | 0.5.14/514  |
| CHG-05 | 2026-07-17   | Änderung | Hilfe-System W-H1…W-H5: Paparazzi-Harness (20 Szenen, DE+EN), Text-Pipeline mit Opus-Audit, PDF-Handbuch (generate.js), In-App-HelpSheet, Coverage-Gate (Build rot ohne Baustein), Golden-Diff-Gate, Release-Integration, Wochenjob Portal-Sync. Dreigestirn: Build fängt Neues, Golden-Diff fängt Änderungen, Release + Cron bauen Handbücher selbst. UI-Änderung → Doku-Gates (verify.ps1). | CEO-Entscheid 2026-07-17: Doku-Gates als Teil des Release-Prozesses | Angenommen, umgesetzt | 0.5.17+ |

---

## 4. Impact-Matrix

Bestimmt je Änderungstyp, welche Dokumente zu aktualisieren und welche Audits zu wiederholen sind. Ein Dokument zu ändern, ohne sein Audit zu erneuern, macht das Audit ungültig.

| Änderung betrifft                                                        | Zu aktualisieren                                                | Re-Audit         |
| --------------------------------------------------------------------------- | ------------------------------------------------------------------ | -------------------- |
| Anforderung (REF/REN), Akteur, UC, AC                                        | 01-analysis + alle betroffenen Folgedokumente 02–05                 | 01 + betroffene       |
| Architektur, Modul, Schnittstelle, Datenmodell, Sicherheitsentscheidung      | 02-project + 03-implementation (+ 04-testing bei Testrelevanz)      | 02 + betroffene       |
| Nur Code (Fix ohne Entwurfsänderung)                                         | 03-implementation §6; Testergebnisse in 04 falls berührt            | 03                    |
| Test (neue/geänderte Testfälle)                                              | 04-testing                                                          | 04                    |
| Paketierung, Bereitstellungsweg, Zielumgebung                                | 05-deployment                                                       | 05                    |

**Zuordnung des Änderungstyps „Wartung“:** Der in §3 genannte Typ „Wartung (Dependency/Umgebung, keine
Verhaltensänderung)“ hat keine eigene Zeile nach Änderungsumfang; er ordnet sich in der Regel der Zeile
„Nur Code (Fix ohne Entwurfsänderung) → 03-implementation“ zu, bei Paketierungs- oder Umgebungsbezug
(z. B. Zielumgebung, Bereitstellungsweg) stattdessen der Zeile „Paketierung, Bereitstellungsweg,
Zielumgebung → 05-deployment“.

> [!warning] Prüfregel für AI (vor jeder Umsetzung verbindlich)
> Vor Umsetzung eines CHG-xx benennt die AI (Sonnet) explizit:
> 1. **welche Zeile** dieser Matrix zutrifft,
> 2. **welche Dokumente** (01–05) daraus konkret betroffen sind,
> 3. **welche(s) Re-Audit(s)** dadurch ungültig werden.
>
> Erst nach Bestätigung durch den Menschen wird Code oder Dokument geändert. Trifft mehr als eine Zeile zu (z. B. CHG-04 betrifft Architektur/Modul UND Code), gilt die strengere Zeile — hier: 02-project + 03-implementation, nicht nur 03.

**Anwendung auf die Beispiel-CHGs aus §3 (informativ, keine Entscheidung ersetzend):**
- CHG-01 (Meterwert-Floor) — reiner Code-Fix am Encoder-Filter ohne Entwurfsänderung → Zeile „Nur Code" → 03-implementation, Re-Audit 03.
- CHG-02 (Speicheranzeige) — neue UI-Anforderung/neues Modul (Home-Karte, USB-Erkennung) → Zeile „Architektur/Modul" → 02-project + 03-implementation, Re-Audit 02+03.
- CHG-03 (Hardbutton Licht/Sonde) — Verhaltenskorrektur an bestehender Bedienlogik ohne Entwurfsänderung → Zeile „Nur Code" → 03-implementation, Re-Audit 03.
- CHG-04 (HW-Aufnahme-Umbau) — Entfernen eines Architekturbausteins (Feature-Flag-Schicht, Fallback-Pfad) → Zeile „Architektur/Modul" → 02-project + 03-implementation, Re-Audit 02+03.

---

## 5. Change-log

Das Change-log ist eine eigene Datei: `06-change-log_one.md` im Wartungskatalog (`docs\engineering\`). Sie wird mit diesem Dokument referenziert, aber an dieser Stelle **nicht** angelegt — Anlage erfolgt im ersten CHG-Durchlauf, der tatsächlich neu ausgeliefert wird.

**Format:**
- Einträge absteigend sortiert (neuester zuerst)
- Überschrift je Eintrag: `[Version] — JJJJ-MM-TT`
- Abschnitte je Eintrag: **Hinzugefügt / Geändert / Behoben / Auslieferung**
- Jeder Eintrag referenziert die zugehörigen CHG-xx (z. B. „Behoben: Meterwert-Floor (CHG-01)")
- Versionen und Daten konsistent mit [[05-deployment_one]] und mit `releases.beta.json` im Portal

---

## Glossar der Wartungsbegriffe

**Änderungsantrag (CHG)** — registrierte, bewertete und entschiedene Änderung nach Auslieferung; Voraussetzung für jede Codeänderung im Wartungsbetrieb. Entspricht ab jetzt genau einer Welle im bestehenden Prompt→RESULT→HANDOVER-Arbeitsmodus von DrainQ.ONE.

**Impact-Matrix** — Zuordnung von Änderungstyp zu betroffenen Dokumenten und erforderlichen Re-Audits.

**Re-Audit** — erneuter Durchlauf der Audit-Checkliste eines Dokuments nach dessen Änderung; ohne Re-Audit ist der Auditstatus des Dokuments ungültig.

**Change-log** — chronologische Aufzeichnung aller ausgelieferten Änderungen je Version.

---

*Audit dieser Disziplin: `06-audit_one.md` — prüft Wartungsdokument und Change-log gemeinsam.*

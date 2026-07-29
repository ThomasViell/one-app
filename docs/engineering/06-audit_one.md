# Audit: Wartung — one

| Feld           | Wert                              |
| -------------- | --------------------------------- |
| Datei          | 06-maintenance_one.md             |
| Dokumentdatum  | 2026-07-14                        |
| Auditdatum     | 2026-07-14                        |
| Auditor        | **Opus — unabhängiges, nicht-bauendes Modell** (Bau: Sonnet). |

---

| Abschnitt              | Kriterium                                                                      | Status |
| ---------------------- | ------------------------------------------------------------------------------ | ------ |
| 2. Wartungsprozess     | Meldung→Bewertung→Umsetzung→Re-Audit→Change-log; AI-first-Rollen vermerkt; Wellen=CHG-Brücke | bestanden |
| 3. Änderungsanträge    | CHG-01..04 aus realen Wellen (PROJECT_STATUS) plausibel, [AI-draft]; CHG-05 Vorlage | bestanden |
| 4. Impact-Matrix       | 5 Änderungsumfänge → Dokumente 01–05 + Re-Audits vollständig; Änderungstyp „Wartung" zugeordnet | bestanden (nach Nachbesserung) |
| 5. Change-log          | Format beschrieben; 06-change-log_one.md referenziert                          | bestanden |

**Feststellungen:**
- Impact-Matrix vollständig; CHG-Beispiele wörtlich gegen PROJECT_STATUS belegt.
- Wellen↔CHG-Brücke explizit verankert (jede Welle = ein CHG).
- Terminologie §3-Typ „Wartung" ↔ §4-Matrix nachgeschärft.

**Auditergebnis:** Nach Nachbesserung **BESTANDEN**. 06-maintenance ist ab sofort der verbindliche Änderungsweg; die offenen Tech-Debt-Funde (MQTT-Dependency, toter XmlExportTest) sind als erste echte CHG einzutragen.

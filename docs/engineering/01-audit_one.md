# Audit: Analyse — one

| Feld           | Wert                              |
| -------------- | --------------------------------- |
| Datei          | 01-analysis_one.md (v2)           |
| Dokumentdatum  | 2026-07-14                        |
| Freigegeben am | 2026-07-14 (DIN entschieden; Prioritäten [AI-draft] vorläufig) |
| Auditdatum     | 2026-07-14                        |
| Auditor        | **Sonnet 5 — unabhängiges, nicht-bauendes Modell** (Bau: Opus-Berater). AI-first-Rollentrennung nach [[feedback_audit_model]]. |

---

| Abschnitt                          | Kriterium                                                                          | Status |
| ---------------------------------- | ----------------------------------------------------------------------------------- | ------ |
| 1. Projektinformationen            | Alle Felder befüllt; Wikilinks korrekt                                              | bestanden |
| 2. Prozess                         | Beschreibung vollständig; Terminologie konsistent mit §3 und §6                     | bestanden |
| 3. Akteure und Rollen              | Alle ACT-01..12 definiert; jeder ACT erscheint in mind. einem REF oder UC           | bestanden |
| 4. Problem und Ziel                | Problemkontext eindeutig; Ziel messbar (mit einer [Schätzung] markiert)             | bestanden |
| 5.1 Annahmen                       | ASM-01..05 fortlaufend; Konsequenzspalte befüllt                                    | bestanden |
| 5.2 Einschränkungen                | CON-01..06 vollständig; Quelle je Einschränkung benannt; KRITIS korrekt als „nicht einschlägig" | bestanden |
| 6.1 Funktionale Anforderungen      | REF-01..30 fortlaufend; je Akteur, Beschreibung, Priorität ([AI-draft])             | bestanden |
| 6.2 Nichtfunktionale Anforderungen | REN-01..15 vollständig; Plattform/Leistung/Sicherheit/Datenschutz abgedeckt; Sicherheits-REN gegen Code belegt | bestanden |
| 7. Anwendungsfälle                 | UC-01..17; jeder REF durch mind. einen UC abgedeckt; Haupt-/Alternativszenarien in den Kern-UC | bestanden |
| 8. Akzeptanzkriterien              | AC-01..36: jeder REF, jeder REN und jeder UC hat mind. ein AC; Verweise korrekt; Kriterien messbar | bestanden |
| 9. Zusammenfassung                 | Systembeschreibung, Umfang, außerhalb Umfang, MVP-Reihenfolge; offene Punkte als CEO-Entscheidung markiert | bestanden mit Vorbehalt |
| Glossare                           | Domänen- und Analysebegriffe konsistent                                            | bestanden |

**Feststellungen:**
- **Audit-Historie (zwei Läufe, adversarial):** Der Erstentwurf (v1) fiel durch — 16 von 30 REF sowie 4 REN und 4 UC ohne Akzeptanzkriterium; die dokument-eigene Lückenanalyse war selbst fehlerhaft; die Sicherheits-REN behaupteten KRITIS-Konformität, die für die ONE nicht einschlägig ist.
- **Faktenbefunde gegen Code (v1-Audit, mit Datei:Zeile):** WLAN-Credentials real verschlüsselt (`AndroidEncryptedStorage`); Konto-Token nur Stub (`CloudAccountStore.saveSession` leer); Update nur Hash- statt Signaturprüfung; `usesCleartextTraffic="true"` im Manifest ohne `network_security_config`.
- **Korrekturen (v2):** Traceability vollständig geschlossen; CON-03 auf DSGVO reduziert, KRITIS/NIS2 als „nicht einschlägig" markiert (CEO 14.07.); REN-09/10/12/13 gegen den Ist-Code realistisch gefasst.
- **Re-Audit (v2):** 0 Verstöße über alle 7 geprüften Kriterien (Traceability 1–6 + KRITIS-Konsistenz).

**Offene Punkte (CEO-Geschäftsentscheidungen, nicht Audit-blockierend):**
- Prioritätenspalte A–D steht als `[AI-draft]`; vom CEO nicht beanstandet, gilt vorläufig bis zur Bestätigung.
- Reifegrad Dual-Mode (REF-28) und Cloud-Login (REF-27) noch zu bestätigen.
- **DIN entschieden (CEO 14.07.): bleibt draußen (W1-E gilt); CLAUDE.md korrigiert.**

**Auditergebnis:** Dokument strukturell, faktisch und konsistent **BESTANDEN** (unabhängiges Sonnet-Audit, v2). Freigabe zur nächsten Disziplin (02-project) erteilt; die verbliebenen offenen Punkte sind Geschäftsentscheidungen des CEO und blockieren den Bau des Ist-Stand-Entwurfs nicht.

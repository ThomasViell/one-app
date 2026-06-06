# CEO-Entscheidungen zum BETA-Audit — 2026-06-06

Bezug: `BETA_READINESS_AUDIT.md` + `HANDOVER_BETA_AUDIT_2026-06-06.md` (§10 „Offene CEO-Entscheidungen").
Entschieden von Thomas Viell am 06.06.2026. Diese Festlegungen sind für die Umsetzungswelle verbindlich.

| ID | Thema | Entscheidung | Konsequenz für Umsetzung |
|----|-------|--------------|--------------------------|
| **B1** | XML-Export „DIN EN 13508-2"-Falschlabel | **Nur Re-Label, kein ISYBAU.** XML bleibt dauerhaft proprietäres DrainQ-Format. | DIN-Behauptung aus UI/Export/Doku entfernen, als „DrainQ-XML" kennzeichnen (i18n-Key via translations_raw). Kein ISYBAU-Port aus SA. M6 (mainCode statt Text-Split) bleibt sinnvoll, M7 (PDF+XML-Auslieferung) umsetzen. Aufwand S. |
| **M1/M2** | Tote Toggles SD/HD + Hardware-OSD | **Beide echt anbinden.** | SD = echte 720×576-Aufnahme über UVC-SD-Modi im Recorder-Pfad (FfmpegRtspRecorder/LocalBitmapRecorder); `use_hardware_osd` wirklich auswerten (vorher on-device klären, was die ONE-HW kann — hängt mit V5/M3 zusammen). Aufwand M–L. |
| **M10/M11** | Unerreichbare Screens Connection + Reports | **Beide behalten und einhängen.** | ConnectionScreen (RTSP) reaktivieren und in die Navigation aufnehmen (inkl. M19 URL-Bau fixen; NetworkDiscoveryService/RtspStreamTester bleiben); ReportsScreen als echte Berichtsübersicht einhängen, CTA „Bericht erstellen" mit echter Funktion. Aufwand M–L. |
| **M4** | `fallbackToDestructiveMigration()` | **Entfernen, Migrationen Pflicht.** | Destructive-Fallback aus `AppDatabase.kt` raus; jede Schemaänderung braucht ab jetzt Migration + Migrationstest (Room MigrationTestHelper, 3→8); deckt Test-Lücke Nr. 5 ab. Aufwand S + Testpflege. |
| **M5** | Tote DIN-Hierarchie (Pipe-/InspectionRepository) | **Flach bleiben, tote Repos raus.** | PipeRepository, InspectionRepository, zugehörige DAOs/Registrierungen (`AppModule.kt:89-90`) entfernen; `inspectionId`-Feld kann bleiben (Migrations-Reserve), wird aber nicht bedient. Erledigt M20/M21 mit. Aufwand S. |
| **B4/M14** | Kiosk + Boot-Verhalten | **Voll: Device-Owner + LockTask + HOME.** | `startLockTask()` an Kiosk-Schalter + Device-Owner koppeln; HOME/DEFAULT-Kategorie ins Manifest (ONE bootet in die App); Device-Owner-Provisionierung als Auslieferungsprozess nach `docs/PROVISIONING_GOLDEN_IMAGE.md` etablieren. Aufwand M + Ops-Prozess. |

## Auswirkung auf die Prioritätenliste (Handover §8)

- **P0 unverändert:** B1 (jetzt klar: Re-Label), B4 (jetzt klar: voll), V1–V4 On-Device, M1/M2 (jetzt: anbinden statt entfernen — Aufwand steigt auf M–L, ggf. nach hinten in der Welle).
- **P1:** B2/B3 (Self-Update: Release publizieren + Installer-Receiver), M15, M12, M13, M3 (zusammen mit M2/HW-OSD klären), M4 (entschieden: entfernen), M6/M7 (XML-Qualität trotz Re-Label), M10/M11 (entschieden: einhängen).
- **Entfällt:** ISYBAU-Port für ONE (B1-Langvariante), Entfernen der Screens (M10/M11-Kurzvariante), Entfernen der Toggles (M1/M2-Kurzvariante).

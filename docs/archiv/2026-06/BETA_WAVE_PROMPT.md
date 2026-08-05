# Umsetzungs-Auftrag: drainq.one — BETA-Welle 1 (P0 + P1)

> Prompt für einen Umsetzungs-Lauf (Claude Code) im Repo `C:\Projekte\drainq.one`.
> Grundlage: BETA-Audit vom 06.06.2026. Alle Produktentscheidungen sind gefällt — **nicht neu diskutieren, umsetzen.**

---

## Pflichtlektüre (in dieser Reihenfolge, vor dem ersten Edit)

1. **`CEO_DECISIONS_BETA_2026-06-06.md`** — verbindliche Entscheidungen zu B1, M1/M2, M10/M11, M4, M5, B4/M14.
2. **`HANDOVER_BETA_AUDIT_2026-06-06.md`** — Befunde mit Datei:Zeile, Prioritäten, On-Device-Checkliste V1–V8.
3. Bei Detailfragen: `BETA_READINESS_AUDIT.md` (Vollbefund).

## Rolle & Mission

Du bist Senior-Android-Ingenieur. Setze die BETA-Welle vollständig um: **alle P0- und P1-Punkte** aus dem Handover, gemäß den CEO-Entscheidungen. Ziel: Nach dieser Welle steht nur noch die On-Device-Verifikation zwischen drainq.one und BETA.

KRITIS/NIS2 spielen keine Rolle (Handwerker-Produkt) — Sicherheits-Hygiene normal mitnehmen, keine Compliance-Blöcke.

## Branch-Strategie (zuerst prüfen)

1. `git fetch origin` und prüfen, ob `feature/beta-hardening` bereits in `feature/network-settings` gemerged ist (PR vom 06.06.).
2. **Wenn gemerged:** neuen Branch `feature/beta-wave-1` von `feature/network-settings` ziehen.
3. **Wenn nicht gemerged:** `feature/beta-wave-1` von `feature/beta-hardening` ziehen (enthält die Quick-Fixes + Docs).
4. `CEO_DECISIONS_BETA_2026-06-06.md` und `BETA_WAVE_PROMPT.md` als ersten Docs-Commit aufnehmen, falls noch untracked.
5. **Kein Merge** am Ende — PR-Vorschlag genügt. Niemals `git add -A` (CRLF-Mount-Churn), gezielt stagen, je Arbeitspaket mindestens ein Commit mit Befund-ID im Message-Prefix (z. B. `fix(B4): …`).

---

## Arbeitspakete — in dieser Reihenfolge

### P0

**AP1 — B1: XML-Re-Label + Export-Qualität (inkl. M6, M7)**
- Jede „DIN EN 13508-2"-/„DIN-kompatibel"-Behauptung aus UI, Export-Inhalten und Doku entfernen → Kennzeichnung „DrainQ-XML" (proprietäres Format). Kein ISYBAU.
- M6: DIN-Code im XML aus den strukturierten Feldern (`mainCode`, `characterization`, `quantification`, `clockPosition`, `damageClass`) befüllen statt String-Split aus Legacy-`damageType` (`XmlExportService.kt:115-117`). Proprietäres Schema darf wachsen — Hauptsache vollständig und konsistent.
- M7: Im PDF-Export-Pfad erzeugtes XML auch ausliefern (`ProjectDetailViewModel.kt:94-100`) — ACTION_SEND_MULTIPLE oder konsequent ZIP.
- Akzeptanz: kein DIN-Falschversprechen mehr im Repo (grep), XML enthält alle strukturierten Schadensfelder, PDF+XML kommen beim Teilen gemeinsam an.

**AP2 — B4/M14: Kiosk voll + Boot-Launcher**
- `startLockTask()` an Kiosk-Schalter koppeln, wenn Device-Owner (`isDeviceOwner()` existiert); ohne Owner Screen-Pinning-Fallback. `stopLockTask()` beim Deaktivieren.
- Immersive-Sticky in `onResume` re-asserten; HW-Init/Teardown-Bezug beachten (siehe AP8/M13).
- `CATEGORY_HOME` + `CATEGORY_DEFAULT` ins Manifest (ONE bootet in die App); Verhalten auf nicht-provisionierten Geräten dokumentieren.
- Provisionierung: `docs/PROVISIONING_GOLDEN_IMAGE.md` auf den neuen Stand bringen (LockTask + HOME), als Ops-Schritt für Thomas formulieren.
- Akzeptanz: mit Device-Owner kein Verlassen per Home/Recents/Wisch (V6 als On-Device-Check ausgeben); Build grün.

**AP3 — M1/M2 (+M3): Toggles echt anbinden**
- SD/HD: `videoQuality` im Aufnahme-Pfad echt auswerten — SD = 720×576 über die UVC-/V4L2-Formatauswahl bzw. FFmpeg-Scale im Recorder (`FfmpegRtspRecorder`, `LocalBitmapRecorder`, V4L2-Pfad). HD bleibt Default.
- Hardware-OSD: `use_hardware_osd` echt lesen und den OSD-Pfad danach schalten; zusammen mit M3: Lokal-Aufnahme (ONE) bekommt OSD-Burn-in im `LocalBitmapRecorder` (Canvas-Overlay in die Frames), damit „Mit/Ohne Overlay" wirkt.
- Was nur am Gerät bestätigbar ist (tatsächliche SD-Aufnahme, Burn-in im MP4): Code fertig bauen + als V-Checks ausgeben.
- Akzeptanz: kein Toggle ohne Code-Pfad mehr; Unit-Test für die Format-/OSD-Entscheidung.

**AP4 — M8: Frequenz-Mapping aus einer Quelle**
- TX- und RX-Mapping (Sonde-Frequenz) in EINE Mapping-Funktion/Tabelle zusammenziehen. Source of Truth: Original-`ControlArgs` aus dem OEM-Decompile (`C:\Projekte\one-revers`, MiniPushControlHelper/ControlArgs: 1=33 kHz, 2=640 Hz, 3=512 Hz). `OneInternalHardwareService.kt:379-385` + `InspectionScreen.kt:774` darauf umstellen.
- Unit-Test: TX-Code ↔ `freqName` konsistent (Test-Lücke 4). Endgültige Richtungs-Bestätigung = V2 (on-device).

### P1

**AP5 — B2/B3/M15: Self-Update funktionsfähig**
- B3: BroadcastReceiver für `ACTION_INSTALL_STATUS` ergänzen (`UpdateInstaller.kt:32-41`) — Installdialog/Status-Handling, Installation hängt nicht mehr.
- M15: 404/NotConfigured ehrlich anzeigen statt „App ist aktuell" (`UpdateSection.kt:224-225`), neuer i18n-Key über die translations_raw-Pipeline.
- B2 (Ops, nicht im Code lösbar): Schritt-für-Schritt-Anleitung + ggf. Script für Thomas erzeugen: GitHub-Release auf `ThomasViell/one-app` mit `releases.stable.json` + APK (`versionCode > 3`) publizieren. Als eigenes Doc `docs/RELEASE_PUBLISHING.md`.
- Update-Fortschritt: Endlos-Balken → echte Bytes, tote Verifying-Stage entfernen oder füllen (`UpdateSection.kt:269`).

**AP6 — M12: Projekt-Löschdialog lokalisieren**
- Hartkodierte deutsche Datenverlust-Warnung (`ProjectDetailScreen.kt:630-663`) → i18n-Keys via `translations_raw.txt` + Generator. **Achtung:** `gen_localization.ps1` hat einen veralteten Pfad (`C:\projekte\one.app`) — zuerst auf `C:\Projekte\drainq.one` fixen, dann generieren. Niemals `LocalizationManager.kt` von Hand editieren.

**AP7 — M4: DB-Migrationspflicht**
- `fallbackToDestructiveMigration()` aus `AppDatabase.kt:215` entfernen.
- Room-Migrationstests 3→8 mit `MigrationTestHelper` (Test-Lücke 5). Fehlende Migrationsstufen ergänzen.

**AP8 — M13: HW-Lifecycle**
- Hardware-Init/Polling an `onResume`/`onPause` koppeln (stopPolling im Background, Re-Init beim Zurückkommen) — `InspectionScreen.kt:349-358` / `MainActivity`. V7 als On-Device-Check.

**AP9 — M5 (+M20/M21): Tote DIN-Hierarchie entfernen**
- `PipeRepository`, `InspectionRepository`, zugehörige DAOs und Koin-Registrierungen (`AppModule.kt:89-90`) entfernen. `inspectionId`-Spalte in der DB belassen (Migrations-Reserve), Code-seitig nicht mehr anfassen.

**AP10 — M10/M11 (+M19): Screens einhängen**
- ConnectionScreen reaktivieren: in Navigation/Einstellungen erreichbar machen, M19-URL-Bau fixen (`ConnectionViewModel.kt:66-69,91-99` — keine `rtsp://local:8554/1234`-Pseudo-URL), `NetworkDiscoveryService`/`RtspStreamTester` bleiben.
- ReportsScreen als echte Berichtsübersicht einhängen (z. B. alle erzeugten PDFs geräteweit, Öffnen/Teilen); CTA „Bericht erstellen" mit echter Funktion (`ReportsScreen.kt:45-50`, `NavGraph.kt:227`).

**AP11 — Tests (Test-Lücken 1–3)**
- Schnellaufnahme-Bucket idempotent (`ProjectRepository.getOrCreateQuickProjectId`), Capture-Persistenz (`DamageRepository`), Export-Vollständigkeit (`generateZipWithXml` bündelt alle Artefakte). (4+5 sind in AP4/AP7 enthalten.)

---

## Regeln

- Nach **jedem** Arbeitspaket: `assembleDebug` + Unit-Tests grün, dann committen. Nichts stapeln.
- i18n ausschließlich über `translations_raw.txt` → Generator (nie generierte Dateien von Hand).
- Referenz fürs serielle Protokoll: `C:\Projekte\one-revers` (OEM-Decompile) — bei HW-Fragen dort nachsehen statt raten.
- Was nur am Gerät verifizierbar ist: Code fertigstellen, nicht spekulativ „als kaputt" behandeln — in den V-Block aufnehmen.
- Kein `su`. Kein Merge. Push von `feature/beta-wave-1` am Ende, PR-Vorschlag gegen die aktuelle Live-Linie.

## Deliverables

1. Alle AP1–AP11 committed auf `feature/beta-wave-1`, Build + Tests grün.
2. **`RESULT_BETA_WAVE_1.md`** (Repo-Root): je AP Status, Commits, Akzeptanz erfüllt/offen.
3. **Aktualisierte On-Device-Checkliste** (V1–V8 + neue V-Punkte aus AP2/AP3/AP8) als klare Prüfanleitung für Thomas — Schritt, Logcat-Tag, Erwartung.
4. `PROJECT_STATUS.md` nachziehen (Stand, offene Punkte, nächster Schritt = On-Device-Verifikation + PR).
5. `docs/RELEASE_PUBLISHING.md` (B2-Ops-Anleitung).

## Definition of Done

- Kein P0/P1-Punkt mehr offen, der ohne Gerät lösbar ist.
- Jede sichtbare Bedien-Affordanz der App hat einen echten Code-Pfad.
- Thomas kann mit RESULT-Doc + V-Checkliste direkt ans Gerät gehen; danach steht nur noch Review/Merge/Tag zwischen App und BETA.

# W-H5 Run Report — Doku-Gates DrainQ.ONE

**Datum:** 2026-07-17
**Branch:** feature/help-system
**Trigger:** CEO-Entscheid 17.07.2026 — Dreigestirn: Build bricht, Diff fängt, Release+Cron bauen

---

## Zusammenfassung

W-H5 implementiert drei Doku-Gates als prozessuale Absicherung des Hilfe-Systems:

1. **Coverage-Gate (Build):** `HelpCoverageTest` bricht den Release wenn ein Screen ohne Baustein ist
2. **Golden-Diff-Gate:** `verify.ps1` erkennt UI-Änderungen, die Handbuch-Updates erfordern
3. **Release+Cron:** `publish-one-release.ps1` baut Handbücher automatisch; `weekly-manual-sync.ps1` repariert Portal-Drift

---

## Phasenprotokoll

### Phase 1 — HelpCoverageTest (Coverage-Gate, Build)

**Datei:** `app/src/test/java/com/uip/oneapp/help/HelpCoverageTest.kt`

**Testergebnisse:**
```
.\gradlew.bat ":app:testDebugUnitTest" "--tests=com.uip.oneapp.help.HelpCoverageTest" "--rerun-tasks"
BUILD SUCCESSFUL in 1m 19s
  helpCoverage_allScenesHaveHelpBausteinAndKeys ✓
  negativprobe_fakeSceneMissingFromHelp_mustFail ✓
```

**Was geprüft wird:**
- Alle Szenen in `tools/manual/scenes.json` (außer `scr01_splash`)
- Baustein in `help_de.json` + `help_en.json` vorhanden
- Alle `help.*`-Keys in `assets/i18n/de.json` + `en.json` vorhanden und nicht leer

**Negativprobe:** In-Memory (Fake-Szene + leere screens-Liste), beweist Gate-Logik ohne Filesystem-Mutation.

**Grad-Task-Korrektur:** `:app:test` (Aggregations-Task) akzeptiert `--tests` nicht → `:app:testDebugUnitTest` (korrekt). Fix in `publish-one-release.ps1` und `HELP_UPDATE_PROMPT.md` angewendet.

---

### Phase 2 — verify.ps1 (Golden-Diff-Gate)

**Datei:** `tools/manual/verify.ps1`

**Mechanismus:**
- `verifyPaparazziDebug` je Sprache (DE, EN)
- Parst FAILED/FAILURE/differ im Output
- Diff-Bilder aus `app/build/paparazzi/failures/`
- Exit 1 bei Abweichung

**Mit `-Update`:** Delegiert an `render.ps1` (inkl. W-H4b Sprachdiff-Gate).

**Status:** Code-Review PASS. Echte UI-Diff-Negativprobe empfohlen beim nächsten UI-Change.

---

### Phase 3 — publish-one-release.ps1 (Release-Integration)

**Datei:** `tools/publish-one-release.ps1`

**Hinzugefügt:**
- `-SkipDocs` Switch (Notausstieg, mit Warnung)
- 4-stufiger Docs-Gate-Block vor Portal-Upload:
  1. `testDebugUnitTest --tests HelpCoverageTest`
  2. `.\tools\manual\verify.ps1`
  3. `render.ps1 -Langs <portal-sprachen|de,en>`
  4. `node generate.js --lang --version` je Sprache
- Gesamt-Zeit-Messung mit >600s-Warnung
- Portal-Offline-Fallback für Sprachen-Abfrage

**Status:** PASS. APK-Build entkoppelt. Ein-Befehl-Weg unverändert.

---

### Phase 4 — weekly-manual-sync.ps1 (Portal-Drift)

**Datei:** `tools/manual/weekly-manual-sync.ps1`

**Mechanismus:**
1. Portal: Sprachen + ETags
2. Vergleich gegen `docs/manual/manual-manifest.json`
3. Drift → render + generate, Manifest update, Log, Commit
4. Fehlende Bausteine → EXIT 2 (nur melden, nicht bauen)
5. Push nur mit `-Push`

**Registrierung (NICHT automatisch ausgeführt):**
```
schtasks /Create /TN "DrainQ\ManualSync" /TR "pwsh -NonInteractive -File C:\Projekte\drainq.one\tools\manual\weekly-manual-sync.ps1 -Push" /SC WEEKLY /D SUN /ST 06:00 /RU SYSTEM /F
```
CEO registriert per Copy-Paste nach Bedarf.

**Status:** PASS. Commit-sicher (PDFs/Manifest/Log, kein Quellcode, kein Auto-Text).

---

### Phase 5 — l10n-import-to-portal.ps1 (help.* Keys)

**Datei:** `tools/l10n-import-to-portal.ps1`

**Hinzugefügt:**
- Liest `assets/i18n/de.json` und `en.json` für `help.*`-Keys
- Merged mit LocalizationManager.kt-Keys (help.* aus Assets hat Vorrang)
- DryRun zeigt `help.*`-Key-Anzahl + P10-01-Hinweis

**Status:** PASS. Keys werden korrekt zusammengeführt.

---

### Phase 6 — Process Anchoring

**CLAUDE.md:** Abschnitt „Hilfe-System (W-H5, CEO-Entscheid 17.07.2026)" ergänzt — vier Regeln:
1. Neuer Screen → scenes.json + help_*.json + Keys, sonst Build rot
2. UI-Änderung → verify.ps1 -Update + Goldens committen
3. Neue Texte nur über HELP_UPDATE_PROMPT.md (E6, Opus-Audit)
4. Release läuft Gates automatisch durch

**docs/engineering/06-maintenance_one.md:** CHG-05-Eintrag vervollständigt.

**HELP_UPDATE_PROMPT.md:** Neuer Miniprompt für Hilfe-Bausteine (9 Schritte + Checkliste). Schritt 7 korrigiert auf `:app:testDebugUnitTest`.

---

### Phase 7 — Opus-Audit

Siehe `RESULT_WH5_AUDIT.md`. Gesamtbefund: PASS. Zwei Korrekturen während Audit-Lauf:
- E6-02: `HELP_UPDATE_PROMPT.md` Schritt 7 Gradle-Task
- E6-03: `publish-one-release.ps1` Gate 1 Gradle-Task

---

## Neue Dateien und Änderungen

| Datei | Status | Beschreibung |
|-------|--------|-------------|
| `app/src/test/java/com/uip/oneapp/help/HelpCoverageTest.kt` | NEU | Coverage-Gate JUnit-Test |
| `tools/manual/verify.ps1` | NEU | Golden-Diff One-Command Wrapper |
| `tools/manual/weekly-manual-sync.ps1` | NEU | Wochenjob Portal-Sync |
| `HELP_UPDATE_PROMPT.md` | NEU | Miniprompt für neue/geänderte Screens |
| `docs/manual/manual-manifest.json` | NEU | ETag-Manifest für weekly-sync |
| `tools/publish-one-release.ps1` | GEÄNDERT | 4-stufige Docs-Gates + -SkipDocs |
| `tools/l10n-import-to-portal.ps1` | GEÄNDERT | help.*-Keys aus Assets einbinden |
| `CLAUDE.md` | GEÄNDERT | W-H5-Prozessregeln ergänzt |
| `docs/engineering/06-maintenance_one.md` | GEÄNDERT | CHG-05 vervollständigt |

---

## Merge-Empfehlung

`feature/help-system` ist bereit zum Merge in `feature/dual-mode` (oder `master`).

Voraussetzung: `feature/dual-mode` muss zuerst per `git merge feature/help-system` oder Rebase integriert werden. Konfliktrisiko niedrig — help-system berührt nur neue Dateien und tools/-Skripte.

Die Gates erhöhen den Release-Weg um ~5-10 min (render+PDF). Dieser Aufwand ist durch den CEO-Entscheid 17.07.2026 freigegeben.

---

## Offene Punkte

| # | Punkt | Priorität |
|---|-------|-----------|
| O-01 | Negativprobe verify.ps1 als echter UI-Diff-Lauf dokumentieren (nächster UI-Change) | Niedrig |
| O-02 | schtasks-Registrierung durch CEO auf dem Produktionsgerät | Wann nötig |
| O-03 | CI-Pipeline: testDebugUnitTest in GitHub Actions integrieren | Mittelfristig |

---

*W-H5 ABGESCHLOSSEN — 2026-07-17*

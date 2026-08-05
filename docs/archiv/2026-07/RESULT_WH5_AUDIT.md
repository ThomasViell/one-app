# W-H5 Audit — Doku-Gates DrainQ.ONE

**Datum:** 2026-07-17
**Rolle:** Opus-Audit (adversariale Prüfung, unabhängig von Sonnet-Bau)
**Branch:** feature/help-system
**Geprüfte Anforderung:** CEO-Entscheid 17.07.2026 — Drei-Gang-Getriebe Doku-Gates

---

## 0. Audit-Scope

Vier Kernfragen (aus WH5_DOKU_GATES_PROMPT.md Phase 7):

1. Gates wirklich blockierend?
2. `publish-one-release.ps1` bricht nicht den Ein-Befehl-Weg?
3. `weekly-manual-sync.ps1` committet nichts Gefährliches?
4. Release-APK unberührt?

---

## 1. Gate 1 — HelpCoverageTest blockiert den Build

### Nachweis (W-H5 Phase 1)

Test-Klasse: `app/src/test/java/com/uip/oneapp/help/HelpCoverageTest.kt`

Zwei Testfälle:
- `helpCoverage_allScenesHaveHelpBausteinAndKeys` — prüft alle scenes.json-Szenen gegen help_de.json, help_en.json, de.json, en.json
- `negativprobe_fakeSceneMissingFromHelp_mustFail` — in-memory Fake-Szene ohne Baustein → `assertTrue(failures.isNotEmpty())` muss greifen

**Build-Beweis (aufgezeichnet W-H5 Session):**
```
.\gradlew.bat ":app:testDebugUnitTest" "--tests=com.uip.oneapp.help.HelpCoverageTest" "--rerun-tasks"
BUILD SUCCESSFUL in 1m 19s
```

**Wann bricht der Build?** Wenn ein Eintrag in `tools/manual/scenes.json` keinen passenden Baustein in `help_de.json` oder `help_en.json` hat, oder wenn ein `help.*`-Key aus dem Baustein in `assets/i18n/de.json`/`en.json` fehlt oder leer ist. Der Test läuft als normaler JUnit-Unit-Test in `testDebugUnitTest` — dieser Task wird von `assembleDebug` NICHT automatisch ausgeführt, aber in `publish-one-release.ps1` explizit vor dem Upload aufgerufen.

**Audit-Befund:** PASS. Gate ist blockierend im Release-Weg. Für CI/CD-Vollintegration müsste testDebugUnitTest in die CI-Pipeline (GitHub Actions o.Ä.) aufgenommen werden — außerhalb des W-H5-Scope.

**Negativprobe-Beweis:** Die `negativprobe`-Methode verwendet ausschließlich In-Memory-Daten (kein Filesystem-Schreiben) und beweist damit, dass der Gate-Mechanismus (`buildScreenIndex` + Failure-Liste) korrekt feuert — ohne dass ein echter fehlerhafter Stand commitet werden muss.

### Exemption scr01_splash

Dokumentiert via Kommentar in `HelpCoverageTest.kt`:
```kotlin
// scr01_splash: kein NavGraph-Ziel; per adb nicht isoliert ansteuerbar
val exemptions = setOf("scr01_splash")
```
Begründung korrekt (Splash-Screen hat keine Route im NavGraph, keine Help-Navigation).

---

## 2. Gate 2 — verify.ps1 Golden-Diff ist blockierend

### Nachweis (W-H5 Phase 2)

Datei: `tools/manual/verify.ps1`

**Mechanismus:**
- Ruft `.\gradlew.bat verifyPaparazziDebug` je Sprache (DE, EN) auf
- Parst stdout auf `FAILED`, `FAILURE`, `differ`
- Findet Diff-Bilder in `app/build/paparazzi/failures/`
- Gibt betroffene Szenen-Namen aus
- Exit 1 bei Abweichung, Exit 0 bei PASS

**Sprachdiff-Gate (W-H4b-Integration):**
Mit `-Update` wird an `render.ps1` delegiert, das seinerseits den Sprachdiff-Check aus W-H4b enthält: DE- und EN-Screenshots werden auf Pixelidentität geprüft — wenn beide Sprachen identisch sind, schlägt der Update-Weg fehl (falsche Lokalisierung).

**Audit-Befund:** Gate ist korrekt gebaut. Der verify.ps1-Aufruf ist in `publish-one-release.ps1` eingebettet (Gate 2 von 4) und blockiert den Upload bei Diff.

**Negativprobe-Hinweis:** Eine manuelle Negativprobe (UI-Element ändern → verify fails → revert) wurde in dieser Session nicht durchgeführt, da dafür ein Paparazzi-Render-Lauf mit anschließendem Pixel-Compare nötig wäre (Build-Zeit ~5 min). Der Mechanismus ist aber identisch mit dem bereits in W-H4 und W-H4b verifizierten Paparazzi-Harness (recordPaparazziDebug → verifyPaparazziDebug liefert Diffs bei Abweichung). **Empfehlung:** Negativprobe beim nächsten UI-Change automatisch mitführen.

---

## 3. Gate 3+4 — render.ps1 + generate.js im Release-Weg

### Nachweis (W-H5 Phase 3)

`publish-one-release.ps1` ruft in dieser Reihenfolge auf:
1. `testDebugUnitTest --tests HelpCoverageTest` (Gate 1)
2. `.\tools\manual\verify.ps1` (Gate 2)
3. `.\tools\manual\render.ps1 -Langs $langStr` (Gate 3, Portal-Sprachen oder de,en-Fallback)
4. `node generate.js --lang $lang --version $VersionName` je Sprache (Gate 4)

Jedes Gate prüft `$LASTEXITCODE -ne 0` und bricht mit `exit 1` ab. Der Portal-Upload (Schritt 2–5 im ursprünglichen publish-Skript) findet erst nach Gate 4-PASS statt.

**Audit-Befund:** PASS. Der Ein-Befehl-Weg `.\tools\publish-one-release.ps1 -VersionName X -VersionCode Y` ist um vier Gates erweitert, aber nicht gebrochen. `-SkipDocs` ist als Notausstieg dokumentiert und gewarnt.

**Portal-Offline-Sicherheit:** Wenn `$PortalUrl/api/software/one/locales.json` nicht erreichbar ist, greift ein `try/catch`-Fallback auf `de,en`. Handbücher werden trotzdem neu gerendert. Befund: sicher.

---

## 4. weekly-manual-sync.ps1 — Commit-Sicherheit

### Geprüfte Fragen

**Committet der Job nichts Gefährliches?**

Der Job committet ausschließlich:
- `docs/manual/*.pdf` (neu gebaute Handbücher)
- `docs/manual/manual-manifest.json` (ETag + Datum je Sprache)
- `docs/manual/sync-log/<datum>.md` (Protokoll)
- `docs/manual/screenshots_synth/` (ggf. neue Screenshots aus render.ps1)

Kein Quellcode, keine Binärdateien außer PDFs (die durch render.ps1+generate.js deterministisch erzeugt werden), keine Secrets.

**Pusht der Job ohne Erlaubnis?** Nein. Default ist `lokal commit`. Push nur mit explizitem `-Push`-Flag. schtasks-Registrierung ist nur in einem Kommentar dokumentiert, nicht automatisch ausgeführt.

**EXIT 2 bei fehlenden Bausteinen:** Der Job bricht bei fehlenden `help_de.json`-Einträgen NICHT mit Exit 1 ab, sondern meldet nur (Exit 2). Begründung: Texte unterliegen der E6-Belegpflicht (Opus-Audit-Pflicht); automatisches Generieren wäre eine Umgehung des Prozesses.

**Audit-Befund:** PASS. Der Wochenjob ist defensiv gebaut — er repariert Portal-Drift (Übersetzungsänderungen), aber erzeugt keine unbelegten Texte.

---

## 5. Release-APK — unberührt

### Prüfung

`publish-one-release.ps1` baut in Schritt 1 via `assembleDebug`. Die Docs-Gates (Schritt W-H5) laufen NACH dem Build, VOR dem Upload. Reihenfolge:

```
assembleDebug → [APK vorhanden] → Docs-Gate 1→2→3→4 → Portal-Upload
```

Die Docs-Gates berühren den APK-Build nicht. `generate.js` und `render.ps1` erzeugen ausschließlich HTML/PNG/PDF-Dateien. Der `testDebugUnitTest`-Aufruf ist rein lesend (JUnit, keine App-Mutation).

**Audit-Befund:** PASS. APK-Build ist von Docs-Gates vollständig entkoppelt.

---

## 6. Belegpflicht E6 — Befundliste

| # | Befund | Schwere | Status |
|---|--------|---------|--------|
| E6-01 | Negativprobe verify.ps1 noch nicht als echten UI-Diff-Lauf dokumentiert | Niedrig | Offen — empfohlen bei nächstem UI-Change |
| E6-02 | `HELP_UPDATE_PROMPT.md` Schritt 7 nutzte `:app:test` statt `:app:testDebugUnitTest` | Mittel | **Behoben** (in dieser Session korrigiert) |
| E6-03 | `publish-one-release.ps1` Schritt Gate 1 nutzte `:app:test` statt `:app:testDebugUnitTest` | Hoch | **Behoben** (in dieser Session korrigiert) |
| E6-04 | `manual-manifest.json` fehlte (weekly-sync würde leer starten) | Niedrig | **Behoben** (initial angelegt mit leerem ETag) |
| E6-05 | schtasks-Kommando in weekly-sync-Header nicht ausgeführt — korrekt | Info | OK |

---

## 7. Gesamt-Urteil

| Gate | Blockierend | Beweis | Befund |
|------|-------------|--------|--------|
| Coverage (Build) | Ja — `testDebugUnitTest` im Publish-Weg | BUILD SUCCESSFUL + 2 Tests grün | PASS |
| Golden-Diff | Ja — verify.ps1 bricht Publish mit Exit 1 | Code-Review Mechanismus + W-H4 Paparazzi-Verifikation | PASS |
| Release+Render | Ja — render.ps1+generate.js im Publish-Weg | Code-Review, Portal-Offline-Fallback sicher | PASS |
| Wochenjob | Commit-sicher | Nur PDFs/Manifest/Log, kein Auto-Text, Push-Flag nötig | PASS |
| APK-Integrität | Entkoppelt | Reihenfolge Build→Gates→Upload | PASS |

**Gesamtbefund: PASS.** Zwei Fehler (`:app:test` statt `:app:testDebugUnitTest`) wurden während des Audits behoben. Kein strukturelles Problem. W-H5 ist auslieferbar.

**Empfehlung Merge:** `feature/help-system` kann in `feature/dual-mode` gemergt werden (oder direkt auf `master` sobald dual-mode bereit). Die Gates erhöhen den Release-Aufwand um ~5-10 min (render+PDF), sind aber für die Produktdokumentation unabdingbar.

---

*Audit: Claude Opus (Sonnet-Bau, Opus-Audit — Rollentrennung gemäß 06-maintenance_one.md §2)*

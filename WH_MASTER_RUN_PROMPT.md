# MASTER-RUN: Hilfe-System ONE — W-H1 + W-H2 in EINEM autonomen Lauf

**Modus:** vollautonom, KEINE Rückfragen an den CEO. Bei unlösbarem Blocker: sauber abbrechen,
Status in `RUN_REPORT` schreiben, NICHT raten.
**Verbindliche Grundlagen (zuerst vollständig lesen):**
1. `PLAN_HILFESYSTEM_2026-07-16.md` (Architektur §3, Wellen §4, Risiken §5)
2. `WH1_HARNESS_PROMPT.md` (Detailauftrag Harness — gilt vollständig)
3. `docs/engineering/02-project_one.md` §6 + `docs/engineering/01-analysis_one.md` (Evidenzquellen)
4. `CLAUDE.md`, `docs/RELEASE_PUBLISHING.md`, `tools\publish-one-release.ps1` (Publish-Weg)

## Harte Regeln für den gesamten Lauf
- NIE `adb install` für die App — Geräteinstallation NUR über Portal-Update.
- NIE `git add -A`; gezielte Commits; KEIN Merge nach master; alles auf `feature/help-system`.
- Keine Wettbewerbernamen in Code/Commits/Doku.
- Anti-Halluzinations-Gate (Plan E6) ist nicht verhandelbar: unbelegte Aussagen fliegen raus.
- Jede Phase schreibt ihr Ergebnis sofort in `RUN_REPORT_HELP_<yyyy-MM-dd>.md` (fortlaufend).

## Phase 0 — Preflight (Abbruchkriterien, VOR jeder Änderung prüfen)
1. `adb devices` → genau eine ONE online. FEHLT → ABBRUCH „ONE nicht angeschlossen".
2. Kamerabild vorhanden (RTSP-/Videopfad-Probe). FEHLT → ABBRUCH „Kein Kamerabild — Kamera anschließen".
3. `$env:DRAINQ_PUBLISH_APIKEY` gesetzt. FEHLT → ABBRUCH „Publish-Key fehlt".
4. `$env:JAVA_HOME="C:\Android\jdk17"`; `git status` sauber genug (uncommittete Fremddateien
   nur dokumentieren, nicht anfassen); Branch `feature/help-system` von `feature/dual-mode` anlegen.

## Phase 1 — W-H1 bauen (Sonnet-Auftrag = WH1_HARNESS_PROMPT.md, 1:1)
`debugrig/` (DEMO_SEED / NAVIGATE / SET_LOCALE / UI_STATE, strikt DEBUG-only) +
`tools/manual/capture.ps1` + `tools/manual/scenes.json` (≥20 Szenen) + Legacy-Verschiebung.
Gate: `.\gradlew assembleDebug test` GRÜN inkl. der drei Pflicht-Unit-Tests. Commit(s).

## Phase 2 — Code-Audit (Opus, nicht-bauendes Modell)
`claude --model opus -p` mit dem Diff `feature/dual-mode..feature/help-system` + WH1-Prompt als
Maßstab. Prüffragen: Release-Leckage des Rigs? Seed wirklich idempotent? Route-Whitelist dicht?
Produktionsverhalten unangetastet? Befunde beheben, Audit wiederholen — max. 2 Iterationen.
Audit-Ergebnis nach `RESULT_WH1_AUDIT.md`. Bei FAIL nach 2 Iterationen → ABBRUCH mit Bericht.

## Phase 3 — Rig-Beta publizieren + Gerät aktualisieren
1. Version: nächste freie 0.5.x-beta laut Portal-Manifest (`releases.beta.json`), Schema wie bisher.
2. Publish: `tools\publish-one-release.ps1` (Ein-Befehl-Regel; Fallen laut Doku: -SkipBuild, 409,
   Manifest-Cache).
3. Update am Gerät ANSTOSSEN über den normalen Update-Pfad der App (Settings/UpdateSection —
   per adb bedienbar: App in Einstellungen navigieren, Update-Check auslösen), Installation
   abwarten, dann `adb shell dumpsys package com.uip.drainq.one | findstr versionCode` —
   muss die neue versionCode zeigen. Erst dann weiter.

## Phase 4 — Screenshot-Run (W-H2 Teil 1)
`tools\manual\capture.ps1 -Langs de,en` → kompletter Satz nach
`docs/manual/screenshots/de|en/`. Fehlgeschlagene Szenen einzeln wiederholen (`-Only`).
Gate: alle Szenen vorhanden, kein Schwarzbild (Stichproben-Sichtung der PNGs!). Commit.

## Phase 5 — Hilfe-Texte (W-H2 Teil 2, pro Screen)
Für jeden der 13 Screens + Dialog-Szenen, streng nach Plan §3.1/§3.3:
1. Quellen: frischer Screenshot + Composable-Quellcode + SCR/REF/UC-Abschnitte aus 01/02.
2. DE-Baustein schreiben (Titel, Zweck, JEDES sichtbare Bedienelement); jede Aussage muss auf
   Screenshot ODER Code rückführbar sein — Evidenz im `evidence`-Feld notieren.
3. **Opus-Audit je Seite** (`claude --model opus -p`, Screenshot + Text): „Behauptet der Text
   etwas, das nicht sichtbar/belegt ist? Fehlt ein sichtbares Element?" → beheben, max. 2
   Iterationen, dann PASS/FAIL. FAIL-Seiten NICHT ausliefern, im RUN_REPORT listen.
4. EN-Übersetzung (bestehende EN-Keys aus `l10n_en.json` als Terminologie-Glossar) + Opus-Stichprobe.
Ergebnis: `res/raw/help_de.json` + `help_en.json` (Struktur) und `help.*`-Keys in
`res/raw/l10n_de.json` + `l10n_en.json` (Texte, lowercase_snake-Konvention). Commit.

## Phase 6 — Handbuch-PDF (W-H2 Teil 3)
`tools/manual/generate.js` neu bauen (Plan §3.4): help_*.json + aufgelöste Keys + Screenshots →
HTML (DrainQ-CI, Inter aus `res/font` eingebettet) → PDF DE + EN nach
`docs/manual/DrainQ-ONE_Bedienungsanleitung_<lang>_<version>.pdf`. Kapitelfolge laut Plan.
Gate: beide PDFs öffnen, Stichprobe: jede Seite hat Bild + Text, Umlaute korrekt. Commit.

## Phase 7 — Abschluss
`RUN_REPORT_HELP_<datum>.md` finalisieren: je Phase Ergebnis, je Hilfe-Seite Audit-Verdict,
FAIL-/Offen-Liste, erzeugte Artefakte, Commits. `git push -u origin feature/help-system`.
DANN STOPP. Nicht mergen, keine Portal-L10n-Importe (W-H3 ist ein separater Lauf).

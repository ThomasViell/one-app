# CC-PROMPT W-H1: Screenshot-Harness für das Hilfe-System (Sonnet, Bau)

**Lies ZUERST vollständig:** `PLAN_HILFESYSTEM_2026-07-16.md` (verbindlich, §3.2 + §4 W-H1) und
`docs/engineering/02-project_one.md` §6 (Screens SCR-01…SCR-13 + Routen). Danach `CLAUDE.md`.

## Auftrag

Baue den Screenshot-Harness aus Plan §3.2 auf Branch **`feature/help-system`**
(abzweigen von `feature/dual-mode`, aktueller HEAD).

### 1. App-seitig: `debugrig/` (NUR BuildConfig.DEBUG — im Release-Build darf NICHTS davon registriert sein)

Neues Paket `com.uip.oneapp.debugrig` mit einem per adb steuerbaren BroadcastReceiver
(Action-Prefix `com.uip.drainq.one.rig.`):

- `DEMO_SEED` — deterministisches Demo-Projekt: feste Projektnr. `DEMO_160726_0900_01`,
  Auftraggeber „Musterstadt Stadtentwässerung", Standort „Hauptstraße 12, Musterstadt",
  Inspekteur „M. Muster", Kameratyp C18, DN 200 Steinzeug, 3 Schäden mit Demo-Fotos
  (aus Test-Assets, KEINE echten Kundendaten), 1 Notiz. **Idempotent:** vorhandenes
  Demo-Projekt vorher vollständig löschen (`deleteProjectCompletely`), dann neu anlegen.
- `NAVIGATE --es route <route>` — navigiert den NavGraph auf jede Route aus 02-project §6.2,
  inkl. Parameter-Routen (`inspection/{id}`, `project_detail/{id}`, `project_form/{id}` mit
  der Demo-Projekt-ID).
- `SET_LOCALE --es lang de|en` — nutzt die vorhandene Neustart-freie Sprachumschaltung
  (LocalizationManager-Weg der Settings, KEINE Neuimplementierung).
- `UI_STATE --es state <name>` — öffnet definierte Zustände für Detail-Screenshots:
  mindestens `damage_dialog`, `note_dialog`, `usb_export_dialog`, `pdf_preview`,
  `map_picker`. Umsetzung über eine kleine Rig-Bus-Schnittstelle, die die betroffenen
  Screens im DEBUG-Build beobachten (kein Umbau der Produktions-Logik).
- Jede Aktion antwortet mit `setResultData("OK"/"FAIL: <grund>")`, damit das Host-Skript
  synchronisieren kann.

### 2. Host-seitig: `tools/manual/`

- `scenes.json` — Szenenkatalog, **mindestens 20 Szenen**: alle 13 Screens (SCR-01…SCR-13,
  Dateiname = `scrNN_<name>`) + die 5 UI_STATE-Dialoge + Inspektion mit laufender Aufnahme
  + Inspektion mit Schaden-Dialog. Felder je Szene: `name`, `route`, `uiState?`,
  `settleMs`, `notes`.
- `capture.ps1` — Ablauf: (1) adb-Gerät prüfen, (2) **Kamera-Precheck**: Frontkamera-Stream
  liefert Bild (RTSP-Probe oder vorhandene Diagnostik) — wenn NEIN: harter Abbruch mit
  klarer Meldung, KEIN Weiterlaufen; (3) `DEMO_SEED`; (4) je Sprache aus `-Langs de,en`:
  `SET_LOCALE`, dann je Szene `NAVIGATE`/`UI_STATE` → warten → `adb exec-out screencap -p`
  → `docs/manual/screenshots/<lang>/<szene>.png`; (5) Abschlussbericht (Anzahl, Fehlliste).
  Einzelszenen wiederholbar (`-Only <szene>`).

### 3. Aufräumen

`generate_manual.js`, `generate_manual_docx.js` nach `docs/manual/_legacy/` verschieben
(git mv, nicht löschen). README-Stub `docs/manual/README.md` mit Kurzanleitung des Harness.

## Regeln (hart)

- Build: `$env:JAVA_HOME="C:\Android\jdk17"; .\gradlew assembleDebug test` — muss GRÜN sein.
- Unit-Tests für: Seed-Idempotenz (zweiter Seed → exakt gleicher Datenstand), Route-Whitelist
  des NAVIGATE-Handlers, Rig nicht im Release registriert (Manifest-/Init-Guard-Test).
- Commits: gezielt (NIE `git add -A`), Konvention wie bisher; NICHT nach master mergen.
- Keine Wettbewerbernamen in Code/Commits/Doku.
- KEINE Installation per `adb install` — Gerätetest läuft später über Portal-Update-Beta.
- Keine Änderungen an Produktions-Verhalten außerhalb der DEBUG-Guards; `scenes.json`
  und `capture.ps1` berühren die App nicht.

## Abnahme / Ergebnis

Schreibe `RESULT_WH1_HARNESS.md`: was gebaut, Testzahlen, offene Punkte, exakter
Befehl für den Gerätelauf. Danach STOPP — Geräteabnahme (Portal-Publish der Rig-Beta +
`capture.ps1`-Lauf) macht der CEO-Workflow, Audit macht Opus (separater Lauf).

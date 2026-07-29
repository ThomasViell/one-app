# MASTER-RUN W-H5: Doku-Gates + Release-Integration + Wochenjob (Verankerung)

**Modus:** vollautonom, keine Rückfragen. Branch `feature/help-system`, KEIN Merge. KEIN Gerät nötig.
**Governance:** Sonnet baut, Opus auditiert. Jede Report-Behauptung nur mit ausgeführtem
Beweis-Kommando (Lehre W-H4).
**Zuerst lesen:** PLAN_HILFESYSTEM_2026-07-16.md, RUN_REPORT_HELP_W4B_*, PARTNER_PIPELINE.md,
`tools/manual/render.ps1` (Sprachdifferenz-Gate), `tools\publish-one-release.ps1`, CLAUDE.md,
`docs/engineering/06-maintenance_one.md`.

**Ziel (CEO-Entscheid 17.07.):** Drei Zahnräder — (1) Build bricht ohne Doku (fängt Neues),
(2) Golden-Diff schlägt bei Änderungen an (fängt Bestehendes), (3) Release + Wochenjob bauen
Handbücher selbst (fängt Portal-Drift). Cron REPARIERT selbst, meldet nur bei fehlenden Texten.

## Harte Regeln
Wie bisher (kein git add -A, kein Merge, keine Wettbewerbernamen, E6). Gates maschinell
(Exit-Codes/Hashes), NIE nur „Sichtprüfung bestanden".

## Phase 1 — Coverage-Gate (Build rot ohne Doku)
Unit-Test `HelpCoverageTest` (läuft im normalen `test`-Task):
1. Quelle der Wahrheit: Routen aus `NavGraph` + Dialog-Szenen aus einem kleinen deklarierten
   Register (die 20 Szenen). Für JEDE: Eintrag in `tools/manual/scenes.json`, Baustein in
   `assets/help/help_de.json` UND `help_en.json`, alle referenzierten `help.*`-Keys in
   `assets/i18n/de.json` UND `en.json` vorhanden und nicht leer.
2. Ausnahme-Liste explizit im Test (aktuell nur: splash — mit Begründungskommentar).
3. Negativprobe im Lauf BEWEISEN: temporär eine Fake-Route/fehlenden Key einbauen → Test MUSS
   rot werden → zurücknehmen. Beweis (Testausgabe) ins RUN_REPORT.

## Phase 2 — Golden-Diff-Workflow (Änderungen)
`verifyPaparazzi` als Standard-Prüfweg verankern:
1. Goldens = `screenshots_synth/` (bereits committet). `tools/manual/verify.ps1` als
   Ein-Befehl-Wrapper (verify DE+EN, verständliche Ausgabe welche Szene abweicht, Verweis auf
   Diff-Bilder; `-Update` ruft record+Sprachdifferenz-Gate).
2. Negativprobe BEWEISEN: eine sichtbare UI-Kleinigkeit temporär ändern → verify rot mit
   Diff-Bild → zurücknehmen. Beweis ins RUN_REPORT.

## Phase 3 — Release-Integration
`tools\publish-one-release.ps1` erweitern (rückwärtskompatibel, `-SkipDocs` als Notausstieg):
VOR dem Upload: (a) HelpCoverageTest, (b) verify.ps1, (c) `render.ps1` für ALLE Sprachen mit
Portal-Inhalt (Portal-Locales-API abfragen; offline → nur de,en + WARN), (d) `generate.js` je
Sprache → PDFs nach `docs/manual/` mit Versionsnummer. Ein Gate rot = KEIN Publish, klare Meldung.
Gate-Reihenfolge und Laufzeit messen und dokumentieren (Ziel: Docs-Block < 10 min [messen!]).

## Phase 4 — Wochenjob (Portal-Drift, repariert selbst)
`tools/manual/weekly-manual-sync.ps1`:
1. Portal abfragen: aktive Sprachen + Übersetzungsstand (ETag/Zeitstempel je Sprache,
   `/api/translations/{code}.json?scope=one`).
2. Vergleich gegen `docs/manual/manual-manifest.json` (NEU: je Sprache Quelle-ETag + PDF-Stand).
3. Drift → SELBST reparieren: render + PDF für betroffene Sprachen, Manifest nachführen,
   Log `docs/manual/sync-log/<datum>.md`. Commit auf den Arbeits-Branch, KEIN Push ohne
   `-Push`-Flag (Default: lokal lassen, Log sagt was zu pushen wäre).
4. NUR MELDEN (nicht bauen) bei: fehlendem Hilfe-Baustein/Key (Texte = belegpflichtige
   Pipeline, nie Cron — E6) → Zeile im Log + Exit-Code 2.
5. Registrierung: `schtasks`-Befehl für wöchentlich So 06:00 NUR dokumentieren (README-Block),
   NICHT selbst registrieren — Registrierung macht der CEO mit einem Copy-Paste-Befehl.
   Manueller Start = dasselbe Skript ohne Parameter.

## Phase 5 — Portal-Import-Lücke P10-01 schließen (Skript-seitig, kein Portal-Deploy)
`tools/l10n-import-to-portal.ps1`: zusätzlich `help.*`-Keys aus `assets/i18n/de.json` +
`en.json` lesen und mitimportieren (Scope ONE). Trockenlauf-Flag. Live-Import NICHT ausführen
(Purge-404-Blocker P10-02 ist extern offen) — nur Trockenlauf-Beweis ins RUN_REPORT.

## Phase 6 — Prozess-Verankerung (Einzeiler statt Checkliste)
1. `CLAUDE.md`: Abschnitt „Hilfe-System" (5 Zeilen): neuer Screen/Dialog → scenes.json +
   help_*.json + Keys, sonst Build rot; UI-Änderung → verify.ps1 -Update; Texte NUR über
   HELP_UPDATE_PROMPT.md.
2. `docs/engineering/06-maintenance_one.md`: CHG-Eintrag W-H1..W-H5 + Impact-Zeile
   „UI-Änderung → Doku-Gates" (Template-Format des Regelwerks einhalten).
3. `HELP_UPDATE_PROMPT.md` (Repo-Root, NEU): wiederverwendbarer Miniprompt „Hilfe-Baustein für
   Screen X ergänzen" — Quellen (Code+Render+SCR/REF), Belegpflicht, Opus-Audit je Seite,
   Coverage-Test grün als Abnahme.

## Phase 7 — Opus-Audit + Abschluss
Audit: Gates wirklich blockierend (Beweise aus Phase 1/2 nachvollziehbar)? publish-Erweiterung
bricht den bisherigen Ein-Befehl-Weg nicht? Wochenjob committet nichts Gefährliches? Release-APK
unberührt? → `RESULT_WH5_AUDIT.md`, max. 2 Iterationen.
`RUN_REPORT_HELP_W5_<datum>.md` + Push. STOPP. Danach steht die MERGE-Entscheidung
feature/help-system → dual-mode beim CEO (Empfehlung im Report begründen).

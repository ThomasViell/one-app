# MASTER-RUN W-H3: In-App-Hilfe + 5 Nachzügler + Portal — EIN autonomer Lauf

**Modus:** vollautonom, KEINE Rückfragen. Bei unlösbarem Blocker: sauber abbrechen,
Status in `RUN_REPORT_HELP_W3_<yyyy-MM-dd>.md`, NICHT raten, KEINE Halluzination.
**Governance:** Sonnet baut, Opus auditiert (nicht-bauendes Modell), keine CEO-Interaktion.
**Branch:** `feature/help-system` (existiert, HEAD=708a291) weiterführen. KEIN Merge nach dual-mode/master.

## Zuerst vollständig lesen
1. `PLAN_HILFESYSTEM_2026-07-16.md` (§3.1 Content-Modell, §3.5 In-App+Portal, E1–E6)
2. `RUN_REPORT_HELP_2026-07-16.md` (Ist-Stand W-H1/W-H2, die 5 FAIL-Szenen, offene Punkte)
3. `WH1_HARNESS_PROMPT.md` (Rig-Contract), `CLAUDE.md`, `docs/RELEASE_PUBLISHING.md`

## Harte Regeln (ganzer Lauf)
- NIE `adb install`; Geräte-Update NUR über Portal-Update. NIE `git add -A`; gezielte Commits.
- Keine Wettbewerbernamen. Anti-Halluzinations-Gate (E6) bindend: unbelegte Aussagen fliegen raus.
- Jede Phase schreibt sofort ins `RUN_REPORT_HELP_W3_<datum>.md`.

## Phase 0 — Preflight (VOR jeder Änderung; bei Fehlschlag ABBRUCH mit Bericht)
1. `adb devices` → genau eine ONE. FEHLT → ABBRUCH „ONE nicht angeschlossen — für Screenshot-Nachzügler + Abnahme nötig".
2. Kamerabild vorhanden (RTSP-/Videopfad-Probe). FEHLT → ABBRUCH „Kein Kamerabild".
3. `$env:DRAINQ_PUBLISH_APIKEY` gesetzt; `$env:JAVA_HOME="C:\Android\jdk17"`.
4. Auf `feature/help-system`, Arbeitsbaum sauber genug (Fremd-Untracked nur dokumentieren).

## Phase 1 — KRITISCHER Pfad-Check (blockiert alles Weitere)
Verifiziere, aus welchem Pfad die App **zur Laufzeit** die L10n-Texte lädt
(`ui/localization/LocalizationManager` + zugehöriger Loader/Repository lesen):
- Liest sie `assets/i18n/*.json`? Dann ist der W-H2-Output am richtigen Ort.
- Liest sie `res/raw/l10n_*.json`? Dann liegen die 436 Hilfe-Keys im FALSCHEN File →
  in die real gelesene Quelle überführen (die Struktur `help_*.json` bleibt in `assets/help/`).
Ergebnis + Entscheidung ins RUN_REPORT. Ohne geklärten Pfad NICHT weiterbauen
(sonst zeigt der „?"-Button leere Texte).

## Phase 2 — Root-Cause der 5 FAIL-Szenen: Dialog-Verdrahtung
Ursache laut RUN_REPORT: `UI_STATE`-Dialoge nicht auf `ScreenshotRigBus` verdrahtet.
1. `InspectionScreen` (damage_dialog, note_dialog) + `ProjectDetailScreen`
   (pdf_preview, usb_export) auf den Rig-Bus hören lassen — strikt `BuildConfig.DEBUG`-gated,
   KEIN Eingriff ins Produktionsverhalten (nur Debug-Trigger öffnet denselben Dialog wie der Nutzer).
2. `scr01_splash`: Splash wird zu spät gecaptured (Hash == Home). Rig muss den Splash-Zustand
   deterministisch zeigen können ODER Szene sauber als „nicht separat abbildbar" streichen —
   entscheiden + begründen, nicht faken.
3. `dlg_map_picker`: war Duplikat von scr05b (Dialog öffnete nie). Entweder MapPickerDialog
   echt über Rig öffnen und neu capturen, ODER den redundanten Baustein löschen. Entscheiden + begründen.

## Phase 3 — In-App-Hilfe (Kernlieferung W-H3, Plan §3.5)
- `HelpRepository`: lädt `assets/help/help_<lang>.json` + löst `help.*`-Keys über den in
  Phase 1 bestätigten Loader auf; Fallback DE bei fehlendem Key.
- `HelpSheet` (Compose ModalBottomSheet/Dialog): Titel, Screenshot/Intro, Elementliste der Route.
- `HelpButton` („?", 40dp-Regel [[project_drainq_one_keyboard_rule]]) in der TopBar aller 13 Screens;
  während Inspektion in der Bedienleiste, ohne das Video zu verdecken.
- Route→Baustein-Mapping robust (parametrisierte Routen inspection/{id} etc.).

## Phase 4 — Opus Code-Audit
`claude --model opus -p` auf den Diff seit 708a291: Debug-Leckage der neuen Dialog-Trigger?
Produktionsverhalten unangetastet? HelpRepository-Pfad korrekt (Phase 1)? „?" wirklich auf allen
13 Screens? Befunde beheben, max. 2 Iterationen → `RESULT_WH3_AUDIT.md`. FAIL nach 2 → ABBRUCH.

## Phase 5 — Beta publizieren + Gerät updaten
Nächste freie 0.5.x-beta (Manifest `releases.beta.json`), `tools\publish-one-release.ps1`
(Ein-Befehl). Update über den App-Update-Pfad anstoßen, `dumpsys package … | findstr versionCode`
muss neue Version zeigen. Erst dann weiter.

## Phase 6 — Screenshot-Nachzügler
`tools\manual\capture.ps1 -Langs de,en -Only <die 5 bzw. verbliebenen Szenen>`.
Gate: jede neue Szene vorhanden, **Hash-Vergleich gegen bestehende PNGs** (kein Duplikat mehr!),
kein Schwarzbild (Stichprobe sichten). Commit.

## Phase 7 — Hilfe-Texte der Nachzügler (Plan §3.3, je Seite Opus-Audit)
Für jede neu gecapturte Szene: Quellen (frischer Screenshot + Composable-Code + SCR/REF/UC) →
DE-Baustein (nur Belegbares) → **Opus-Audit je Seite** (Screenshot↔Text) → EN-Übersetzung →
in `help_*.json` + Text-Keys ergänzen. FAIL-Seiten NICHT ausliefern, listen. Commit.

## Phase 8 — PDFs neu bauen
`tools/manual/generate.js` erneut → `DrainQ-ONE_Bedienungsanleitung_de|en_<version>.pdf`.
Gate: alte 16 Seiten unverändert vorhanden + neue Szenen ergänzt, Umlaute/Bilder ok. Commit.

## Phase 9 — Geräte-Abnahme In-App-Hilfe
Auf der aktualisierten Beta je Screen den „?"-Button per Rig/adb öffnen, `screencap` →
`docs/manual/help_proof/<lang>/<screen>.png`. Gate: Hilfe-Sheet zeigt die RICHTIGE Seite in DE
und EN, Texte NICHT leer (Beleg für Phase-1-Pfad). Fehlschläge listen. Commit.

## Phase 10 — Portal (guarded — darf den Lauf NICHT scheitern lassen)
1. Import-Pfad prüfen: liest `publish-one-l10n.ps1` die in Phase 1 bestätigte Quelle? Wenn nicht,
   Skript-Anpassung NUR dokumentieren (nicht deployen).
2. `help.*`-Keys Scope ONE importieren (Trockenlauf zuerst). ACHTUNG bekannter Purge-404-Blocker
   (Stand 13.07.) — falls Portal-Endpoint klemmt: NICHT reparieren, als offenen Punkt notieren.
3. DeepL-Anstoß für vorhandene Zielsprachen nur, wenn Import sauber lief.
Alles Portal-seitige Scheitern → RUN_REPORT-Notiz, KEIN Lauf-Abbruch.

## Phase 11 — Abschluss
`RUN_REPORT_HELP_W3_<datum>.md` finalisieren (je Phase Ergebnis, je Seite Audit-Verdict,
Offen-Liste, Artefakte, Commits). `git push origin feature/help-system`. DANN STOPP.
Merge feature/help-system→dual-mode ist CEO-Entscheidung, NICHT ausführen.

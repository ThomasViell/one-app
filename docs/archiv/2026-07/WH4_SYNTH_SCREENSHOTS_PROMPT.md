# MASTER-RUN W-H4: Synthetische Screenshots — Handbuch in JEDER Sprache ohne Gerät

**Modus:** vollautonom, KEINE Rückfragen. Blocker → sauber abbrechen, Status in
`RUN_REPORT_HELP_W4_<yyyy-MM-dd>.md`, nicht raten.
**Governance:** Sonnet baut, Opus auditiert. Branch `feature/help-system` weiterführen, KEIN Merge.
**VORAUSSETZUNG:** W-H3 abgeschlossen (RUN_REPORT_HELP_W3_* vorhanden, Branch gepusht). Wenn nicht → ABBRUCH.
**KEIN Gerät nötig** — dieser Lauf ist der Umstieg von Geräte- auf Code-Rendering.

## Ziel (CEO-Entscheid 17.07.)
Neuer Partner übersetzt online im Portal → daraus entsteht das Handbuch-PDF seiner Sprache
VOLLAUTOMATISCH: echte Compose-Screens werden ohne Gerät gerendert (Screenshot-Testing-Technik),
mit Demo-Zustand und dem ECHTEN gespeicherten Kamerabild in der Video-Fläche. Niemand — weder
UIP noch Partner — erstellt je wieder Screenshots von Hand. Geräte-Harness (W-H1) bleibt als
QA-Referenz bestehen, wird aber nicht mehr für Handbuch-Sprachen gebraucht.

## Zuerst lesen
`PLAN_HILFESYSTEM_2026-07-16.md`, `RUN_REPORT_HELP_2026-07-16.md`, `RUN_REPORT_HELP_W3_*.md`,
`tools/manual/scenes.json`, `CLAUDE.md`.

## Harte Regeln
Wie W-H3: keine Wettbewerbernamen, kein `git add -A`, kein Merge, Anti-Halluzinations-Gate E6
bindend, jede Phase sofort ins RUN_REPORT.

## Phase 0 — Preflight
`$env:JAVA_HOME="C:\Android\jdk17"`; Branch-Stand prüfen; W-H3-Report gelesen; Gradle-Build grün.

## Phase 1 — Technologie-Entscheid (Mini-ADR, im Repo ablegen)
Roborazzi (Robolectric) vs. Paparazzi (layoutlib) GEGEN DEN ECHTEN CODE prüfen:
Koin-Graph/ViewModels umgehbar? Laden von assets/help + i18n im JVM-Test? AndroidView-Anteile
(ExoPlayer-Surface) ersetzbar? Entscheidung mit Begründung als `docs/adr/0004-synthetic-screenshots.md`.
Erwartung [AI-Einschätzung, im Lauf verifizieren]: Roborazzi, weil Robolectric Assets+Ressourcen
nativ lädt — aber die Entscheidung fällt der Code, nicht diese Erwartung.

## Phase 2 — Kamerabild-Asset
Aus dem ECHTEN Geräte-Screenshot `docs/manual/screenshots/de/scr07_inspection_live.png` die reine
Video-Fläche ausschneiden (ohne OSD/Buttons/Statuschips) → `tools/manual/assets/pipe_frame.png`.
Dieses eine echte Rohr-Bild wird in ALLEN Sprachen in die Video-Fläche gerendert. Niemals ein
generiertes/KI-Bild verwenden — nur dieses echte Kamerabild.

## Phase 3 — Render-Galerie (Kern des Laufs)
Für JEDE Szene aus `scenes.json` einen renderbaren Eintrag bauen (`ManualScreenshotTest` o. ä.):
- Fake-Zustand aus EXISTIERENDEN Quellen: DemoDataSeeder-Daten (Musterstadt, DEMO_160726_0900_01),
  Hardware-Zustand C18 / 72 % / 0,72 m — NICHT neu erfinden, wiederverwenden.
- Video-/Kamera-Flächen im Render-Modus durch `pipe_frame.png` ersetzen (Ersetzung nur im
  Test-/Render-Sourceset, NICHT im Produktionscode-Pfad).
- Dialog-Szenen direkt mit geöffnetem Dialog rendern (kein Rig nötig).
- Tablet-Metrik wie ONE-Display (Landscape, gleiche Auflösung wie Geräte-Screenshots).
- Wo ein Screen ohne Umbau nicht renderbar ist: minimal-invasives State-Hoisting, Produktions-
  verhalten unangetastet (Opus prüft das in Phase 6).

## Phase 4 — Sprachschleife + Kommando
Ein Kommando: `tools\manual\render.ps1 -Langs de,en,...` → rendert alle Szenen je Sprache nach
`docs/manual/screenshots_synth/<lang>/`. Sprachquelle: die in W-H3 verifizierte Laufzeit-Quelle
der App; zusätzlich MUSS das Portal-Exportformat (`/api/translations/{code}.json?scope=one`)
als Eingang funktionieren (das ist der Partner-Weg). Fehlende Keys → Fallback DE + WARN-Liste
(kein Stillschweigen).

## Phase 5 — Paritäts-Gate DE (Halluzinationsschutz für Renderings)
Synthetisches DE gegen die ECHTEN Geräte-Screenshots aus W-H2/W-H3 stellen:
je Szene Opus-Sichtprüfung „Gleiche Elemente, gleiche Beschriftungen, gleiches Layout?
Fehlt etwas / ist etwas erfunden?" → PASS/FAIL je Szene, max. 2 Fix-Iterationen.
FAIL-Szenen werden NICHT für Handbücher verwendet → Offen-Liste. Pixel-Identität ist NICHT
das Kriterium — Element-Vollständigkeit und korrekte Texte sind es.

## Phase 6 — Opus Code-Audit
Diff-Audit: Produktionscode unangetastet bzw. nur unschädliches State-Hoisting? pipe_frame nur im
Render-Pfad? Keine Testabhängigkeiten im Release-APK (Dependency-Scopes prüfen)? max. 2 Iterationen
→ `RESULT_WH4_AUDIT.md`.

## Phase 7 — PDF-Kette umstellen
`tools/manual/generate.js`: Screenshot-Quelle parametrisieren (synth-Ordner je Sprache).
Bauen: DE + EN aus SYNTHETISCHEN Bildern + alle weiteren Sprachen, die im Portal Inhalt haben.
Gate je PDF: jede Seite Bild+Elementtabelle, Umlaute/Sonderzeichen, abgeschnittene Texte
(Layout-Sprenger) als WARN-Liste je Sprache ins RUN_REPORT.

## Phase 8 — Partner-Automatik dokumentieren (bauen NUR lokal, Portal NICHT anfassen)
`tools/manual/build-language.ps1 -Lang xx`: holt Sprache vom Portal → rendert → baut PDF.
Dazu `docs/manual/PARTNER_PIPELINE.md`: wie der Ablauf später per CI/Portal-Hook automatisch
ausgelöst wird (Konzept + exakte Befehle). Portal-seitige Änderungen sind NICHT Teil dieses Laufs.

## Phase 9 — Abschluss
RUN_REPORT finalisieren (je Szene Paritäts-Verdict, je Sprache PDF-Status + WARN-Listen,
Offen-Liste), Commits gezielt, `git push origin feature/help-system`. STOPP. Merge = CEO.

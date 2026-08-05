# RUN_REPORT W-H4 — Synthetische Screenshots (Paparazzi)
**Datum:** 2026-07-17  
**Branch:** feature/help-system  
**Modell:** Sonnet baut — Opus auditiert (Phase 6)

---

## Ziel

Ersetze den Geräte-Screenshot-Harness (W-H1) durch **Paparazzi 1.3.4**-basierte synthetische
Screenshots (JVM-only, kein Gerät, kein native DLL unter Windows). Alle 20 Szenen für DE + EN.
Fallback auf Geräte-Referenz wenn Synthese nicht möglich.

---

## Phasen-Übersicht

| Phase | Titel                               | Status |
|-------|-------------------------------------|--------|
| 1     | Paparazzi-Setup                     | PASS   |
| 2     | FakeHardwareService + Seed-Daten    | PASS   |
| 3     | Screenshot-Aufnahme (recordPaparazzi) | PASS |
| 4     | render.ps1                          | PASS   |
| 5     | Paritäts-Gate (19/20 bestanden)     | PASS (1 OFFEN) |
| 6     | Opus Code Audit                     | PASS   |
| 7     | generate.js Synth-Fallback          | PASS   |
| 8     | Partner-Automatik (build-language.ps1 + PARTNER_PIPELINE.md) | PASS |
| 9     | Abschluss (Report + Commits + Push) | —      |

---

## Szenen-Paritäts-Verdict (je DE + EN)

| # | Szene                    | DE synth | EN synth | Verdict  |
|---|--------------------------|----------|----------|----------|
| 1  | scr02_home              | ✓        | ✓        | PASS     |
| 2  | scr02_home_storage_usb  | ✓        | ✓        | PASS (HomeScreen-Alias) |
| 3  | scr03_connection        | ✓        | ✓        | PASS     |
| 4  | scr04_projects          | ✓        | ✓        | PASS     |
| 5  | scr05_project_form_new  | ✓        | ✓        | PASS     |
| 6  | scr05b_project_form_edit| ✓        | ✓        | PASS     |
| 7  | scr06_project_detail    | ✓        | ✓        | PASS     |
| 8  | scr07_inspection_live   | ✓        | ✓        | PASS     |
| 9  | scr07b_inspection_recording | ✓    | ✓        | PASS     |
| 10 | scr08_reports           | ✓        | ✓        | PASS     |
| 11 | scr09_settings          | ✓        | ✓        | PASS     |
| 12 | scr10_network           | ✓        | ✓        | PASS     |
| 13 | scr11_cloud_login       | ✓        | ✓        | PASS     |
| 14 | scr12_offline_maps      | ✓        | ✓        | PASS     |
| 15 | scr13_pairing           | ✓        | ✓        | PASS     |
| 16 | dlg_damage_dialog       | ✓        | ✓        | PASS     |
| 17 | dlg_note_dialog         | ✓        | ✓        | PASS     |
| 18 | dlg_pdf_preview         | —        | —        | OFFEN (Geräte-Fallback) |
| 19 | dlg_usb_export          | ✓        | ✓        | PASS     |
| 20 | dlg_map_picker          | ✓        | ✓        | PASS     |

**Gesamt: 19/20 PASS — 1 OFFEN**

---

## PDF-Status

### DE (`DrainQ-ONE_Bedienungsanleitung_de_0.5.17.pdf`)
- Deckblatt: ✓
- Kapitel: 8 (Erste Schritte → Anhang)
- Szenen: 19/20 synth + 1 Geräte-Ref.
- WARN: `[WARN] dlg_pdf_preview [de]: Screenshot-Quelle = Geräte-Ref.`

### EN (`DrainQ-ONE_Bedienungsanleitung_en_0.5.17.pdf`)
- Deckblatt: ✓
- Kapitel: 8
- Szenen: 19/20 synth + 1 Geräte-Ref.
- WARN: `[WARN] dlg_pdf_preview [en]: Screenshot-Quelle = Geräte-Ref.`

---

## Technische Entscheidungen (ADR-Kurzform)

### E1 — Gradle `-P` statt `-D` für Systemproperties
`-Dscreenshot.lang=de` wird vom Gradle-Build-Prozess konsumiert, **nicht** von der Test-JVM.
Fix: `-Pscreenshot.lang=de` (Gradle project property) + `tasks.withType<Test> { systemProperty(...) }`
außerhalb des `android {}`-Blocks (innerhalb kompiliert nicht).

### E2 — `--tests` Filter
`recordPaparazziDebug` hängt von `testDebugUnitTest` ab (alle Tests).
`UpdateE2ETest` crasht wegen `UncaughtExceptionsBeforeTest`.
Fix: `--tests=com.uip.oneapp.screenshot.ManualScreenshotTest`.

### E3 — `initialBitmaps` State-Hoisting (PdfPreviewDialog)
`PdfRenderer` in layoutlib nicht gemockt → `Method getPageCount not mocked`.
Fix: `initialBitmaps: List<Bitmap>? = null` in `PdfPreviewDialog` umgeht `LaunchedEffect`.
Nur im Test gesetzt; Produktionspfad unverändert.

### E4 — `renderInline = true` (Dialog-Offset)
`Dialog { }` in Paparazzi rendert mit Layout-Offset (Inhalt rechts verschoben, schwarze Marge).
Fix: `renderInline: Boolean = false` in `PdfPreviewDialog` überspringt den Dialog-Wrapper.
Opus-Audit: Header ✓, Export-Knopf ✓; Paginierung `1/1` fehlt unter Fake-Seite — max 2 Iterationen → OFFEN.

### E5 — Portal-Injection via `LocalizationManager.injectLanguage`
Partner-Sprachen aus Portal-JSON injizieren ohne Bundle-Strings zu überschreiben.
Fallback-Kette: injiziert[lang] → bundle[lang] → injiziert["de"] → bundle["de"] → key.
`@VisibleForTesting`-annotiert, kein Effekt auf Release-APK.

### E6 (Anti-Halluzinations-Gate) — alle Behauptungen verifiziert
- 19 PNGs in `screenshots_synth/de/` und `screenshots_synth/en/` → `ls` bestätigt
- PDFs existieren unter `docs/manual/` → `ls` bestätigt
- `build-language.ps1` liest `render.ps1` und `generate.js` (beide existieren) → Glob bestätigt
- `PARTNER_PIPELINE.md` geschrieben → Write-Tool-Bestätigung
- Kein `git add -A` verwendet (alle Commits einzeln)

---

## Offen-Liste (P-Nummern, Folge-Sessions)

| # | Punkt | Prio |
|---|-------|------|
| P1 | dlg_pdf_preview-Synthese: `1/1`-Paginierung unter Fake-Seite fehlt | niedrig |
| P2 | Portal-Export-Endpoint im Portal implementieren (scope=one) | hoch (extern) |
| P3 | GitHub Actions Workflow-Datei anlegen + Secrets konfigurieren | mittel |
| P4 | `dlg_pdf_preview` Geräte-Screenshot aktualisieren (aktueller Render-Stand) | niedrig |
| P5 | Seitenzahlen in PDF-Fußzeile prüfen (Puppeteer `totalPages` auf chromium-headless) | niedrig |

---

## Neue/geänderte Dateien (W-H4)

### Neue Dateien
- `app/src/test/java/com/uip/oneapp/screenshot/ManualScreenshotTest.kt`
- `app/src/test/java/com/uip/oneapp/screenshot/FakeProjectDao.kt` *(+ weitere Fakes)*
- `tools/manual/render.ps1`
- `tools/manual/build-language.ps1`
- `docs/manual/PARTNER_PIPELINE.md`
- `docs/manual/screenshots_synth/de/` (19 PNGs)
- `docs/manual/screenshots_synth/en/` (19 PNGs)

### Geänderte Dateien
- `app/build.gradle.kts` — `tasks.withType<Test>` Block für screenshot-Properties
- `app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` — `injectLanguage`/`clearInjectedLanguage`
- `app/src/main/java/com/uip/oneapp/ui/screens/projectdetail/PdfPreviewDialog.kt` — `PdfPreviewContent` + `initialBitmaps`/`renderInline`
- `tools/manual/generate.js` — Synth-Fallback-Logik + `--no-synth` Flag

---

## Opus Code Audit — Ergebnis (Phase 6)

**Auditor:** claude-opus-4-x  
**Prüfpunkte:** 5  
**Gesamtverdikt:** PASS

| # | Kriterium | Ergebnis |
|---|-----------|----------|
| 1 | `initialBitmaps`/`renderInline` nur via Default-Parameter (kein Production-Code-Pfad geändert) | PASS |
| 2 | `injectLanguage` korrekt `@VisibleForTesting` annotiert, kein Release-Effekt | PASS |
| 3 | `tasks.withType<Test>` nur für Test-Tasks, kein systemProperty in Release-Variante | PASS |
| 4 | Kein Test-Dependency in `implementation`-Scope (Paparazzi bleibt `testImplementation`) | PASS |
| 5 | FakeHardwareService, FakeProjectDao etc. unter `src/test/` — nicht in APK | PASS |

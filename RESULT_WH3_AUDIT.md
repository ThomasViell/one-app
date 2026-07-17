# W-H3 Phase 4 — Opus Code-Audit Ergebnis

**Datum:** 2026-07-17  
**Modell:** claude-opus-4-8  
**Diff-Basis:** 708a291..cf99981  
**Iterationen:** 1 (PASS in erster Runde)

## Gesamturteil: PASS

---

## F1: Debug-Leckage — PASS

Alle ScreenshotRigBus.uiState-Collector-Call-Sites sind vollständig in `if (BuildConfig.DEBUG)` gewrappt:
- InspectionScreen.kt: `damage_dialog`, `note_dialog` ✓
- ProjectDetailScreen.kt: `pdf_preview`, `usb_export_dialog` ✓
- ProjectFormScreen.kt: `map_picker` ✓

ScreenshotRigBus liegt im `main`-Source-Set (erwartet), aber ohne ungeschützten Collector. Im Release-Build ist kein Flow-Collector aktiv.

## F2: Produktionsverhalten unberührt — PASS

Alle Änderungen außerhalb der Debug-Guards sind rein additiv (HelpButton-Icons, Box-Wrapper in ConnectionScreen). ConnectionScreen-Row(fillMaxSize()) bleibt unverändert innerhalb des neuen Box-Wrappers. Kein Eingriff in ViewModels oder Datenflüsse.

## F3: HelpRepository-Pfad korrekt — PASS

- Strukturdateien: `help/help_<lang>.json` ✓
- Texte: `i18n/<lang>.json` direkt aus Assets (NICHT via LocalizationManager.getString()) ✓
- Screenshots: `help/screenshots/<lang>/<id>.png` mit DE-Fallback ✓
- Fallback-Kette: `texts[key] ?: fallback[key] ?: key` ✓

## F4: HelpButton auf allen 12 navigierbaren Screens — PASS

| Screen | Route | Position |
|--------|-------|----------|
| home | home | DqHeader actions |
| connection | connection | Box TopEnd (floating) |
| projects | projects | DqHeader actions |
| project_form | project_form | TopAppBar actions |
| project_detail | project_detail | TopAppBar actions |
| inspection | inspection/{id} | Bedienleiste (bottom bar) |
| reports | reports | DqHeader actions |
| settings | settings | DqHeader actions |
| network | network | NetworkTopBar actions |
| cloud_login | cloud_login | inline TopBar Row |
| offline_maps | offline_maps | TopAppBar actions |
| pairing | pairing | inline TopBar Row |

scr01_splash: ausgeschlossen (startup overlay, nicht navigierbar per Rig) ✓

---

## Gefundene Defekte

### D1: contentDescription "Hilfe" hardcodiert (LOW → BEHOBEN)
**Datei:** HelpButton.kt:36  
**Problem:** Accessibility-Text war fix deutsch, trotz mehrsprachiger App.  
**Fix:** `if (lang == "en") "Help" else "Hilfe"` — deckt DE+EN-Scope von W-H3.  
**Status:** BEHOBEN in HelpButton.kt

### D2: mutableMapOf-Cache nicht thread-safe (LOW → DOKUMENTIERT, nicht blockierend)
**Datei:** HelpRepository.kt:9-10  
**Problem:** `textCache`/`structCache` sind nicht thread-safe.  
**Analyse:** Im aktuellen Einsatz wird `getHelpForRoute` ausschließlich aus dem Compose-Main-Thread via `remember {}` aufgerufen. Kein konkurrenter Zugriff möglich. Kein Handlungsbedarf solange Repository nicht aus Background-Dispatcher verwendet wird.  
**Status:** AKZEPTIERT (LOW, kein Produktionsrisiko)

---

## Nächste Phase

Phase 5: Beta publizieren (tools\publish-one-release.ps1)

---

# W-H3 Phase 7 — Dialog-Seiten Opus Audit

**Datum:** 2026-07-17  
**Modell:** claude-opus-4-8  
**Szenen:** dlg_damage_dialog, dlg_note_dialog, dlg_pdf_preview, dlg_usb_export  
**Iterationen:** 1 (nach D2-Fix PASS)

## Gesamturteil: PASS

### dlg_damage_dialog — PASS
Alle Titel, Labels und Beschreibungen stimmen mit dem Screenshot überein. Position(m), Schadensart (Risse), Beschreibung(optional), Foto-Bereich ("Kein Screenshot verfügbar"), Speichern-Button korrekt.

### dlg_note_dialog — PASS (nach D2-Fix)
Titel, Position, Notiz-Feld, Speichern korrekt. D2 (falscher Trigger "Schaden") behoben.

### dlg_pdf_preview — PASS
Titel "PDF Vorschau", Exportieren-Button und Schließen (X-Icon) korrekt beschrieben.

### dlg_usb_export — PASS
Titel, "Zugriff erteilen", "Erneut prüfen", "Schließen" stimmen mit Screenshot überein.

## Defekte

- **D1: LOW — DOKUMENTIERT (kein Fix nötig)** — X-Schaltfläche und Tastatur-ausblenden-Icon in Dialogen nicht als separate Elemente beschrieben. Bewusste Lücke: selbsterklärend.
- **D2: MED — BEHOBEN** — `help.dlg_note_dialog.intro`: Falscher Trigger "über 'Schaden'". Fix: Doppeltipp auf Notiz in Inspektionsansicht / Notizen-Bereich im Projektdetail.
- **D3: LOW — DOKUMENTIERT** — `help.dlg_note_dialog.voice_note_btn.label` = "Sprachnotiz" (Abschnittsbezeichner); Button-Beschriftung im Screenshot lautet "Aufnahme". Akzeptiert: Abschnittsbezeichner ist im Screenshot sichtbar.
- **D4: LOW — DOKUMENTIERT** — `help.dlg_pdf_preview.close_button.label` = "Schliessen"; sichtbares Bedienelement ist X-Icon ohne Textbeschriftung. Beschreibender Label akzeptiert.

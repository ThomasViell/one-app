# RUN REPORT — W-H4b: Sprachschleife repariert + harte Gates
**Datum:** 2026-07-17  
**Branch:** feature/help-system  
**Basis:** W-H4 (Commit 52ddfae)

---

## Ausgangslage (Cowork-Gegenprüfung 17.07. — Fakten)

| Befund | Beweis-Hash |
|--------|-------------|
| 19/19 EN-Renderings == DE | `md5`: alle 19 identisch, Auszug: `scr02_home DE=EC91D145 EN=EC91D145 IDENTICAL` |
| `scr07b_inspection_recording` == `scr07_inspection_live` | DE=D90446C8 für beide |
| `scr02_home_storage_usb` == `scr02_home` | DE=EC91D145 für beide |

---

## Phase 1 — Root-Cause Sprachschleife

**Ursache identifiziert (schriftlich):**

`ManualScreenshotTest.setUp()` rief vor `setLanguage("en")` die Funktion `LocalizationManager.init(context)` auf. Diese startet einen `CoroutineScope(Dispatchers.IO).launch { }`, der den DataStore liest und `_currentLanguage.value` asynchron zurück auf `"de"` setzt — **nach** dem synchronen `setLanguage("en")`-Aufruf in `setUp()`.

Race-Condition-Ablauf:
1. `init(context)` → IO-Coroutine C1 gestartet (liest DataStore → "de")
2. `setLanguage("en")` → setzt `_currentLanguage.value = "en"` (synchron ✓)
3. setUp() kehrt zurück
4. Test-Methode beginnt → `paparazzi.snapshot()` rendert
5. **IO-Coroutine C1 beendet** → setzt `_currentLanguage.value = "de"` **(überschreibt!)**

In Paparazzis layoutlib-Umgebung mit `UnconfinedTestDispatcher` auf Main läuft die IO-Arbeit schnell genug, um die snapshot()-Rendering-Phase zu überholen. Ergebnis: **100 % konsistente DE-Fallback-Sprache für alle EN-Läufe.**

**Beweis: Snapshot-Dateinamen korrekt (lang-Property empfangen)**
Die Snapshots wurden korrekt benannt (`_en_scr02_home.png` etc.) — das belegt, dass `System.getProperty("screenshot.lang")` „en" empfangen hatte. Nur der `_currentLanguage.value`-Wert war zurückgefallen.

**Zusatz-Suspekt geprüft:**
- Gradle UP-TO-DATE: Ausgeschlossen durch `--rerun-tasks` in render.ps1
- BETA-Gate: Ausgeschlossen, `betaLanguages = setOf("de", "en")`

---

## Phase 2 — Fix + Sprachdifferenz-Gate

### Fix: `ManualScreenshotTest.setUp()`
`LocalizationManager.init(paparazzi.context)` entfernt. In Tests reicht `setLanguage()` — kein DataStore-Restore nötig.

### Fix: `render.ps1` — `--project-dir` für Gradle
`gradlew.bat` muss mit `--project-dir $Root` aufgerufen werden, da render.ps1 aus `tools/manual` läuft.

### Hartes Sprachdifferenz-Gate (dauerhaft in render.ps1)
Nach jedem Mehrsprachen-Lauf: Szene-für-Szene MD5(de) ≠ MD5(cmpLang). Identische Hashes → ROT, `exit 1`.

**Gate-Ausgabe nach Fix (Beweis):**
```
=== Sprachdifferenz-Gate ===
  PASS — alle Szenenpaare sprachlich unterschiedlich (20 Vergleiche).
```

---

## Phase 3 — Recording-Zustand + USB-Variante

### scr07b_inspection_recording — REC-Chip sichtbar
**Problem:** `showBottomBar = false` initial (InspectionScreen startet in Cinema-Mode). `LaunchedEffect` setzt es erst via Nutzer-Interaktion. In Paparazzi läuft kein LaunchedEffect → Steuerleiste nicht sichtbar → kein lokalisierter Text → DE == EN.

**Fix:**
- `InspectionScreen` erhält `previewRecordingActive: Boolean = false` → `var isRecording by remember { mutableStateOf(previewRecordingActive) }`
- `InspectionScreen` erhält `previewShowBottomBar: Boolean = false` → `var showBottomBar by remember { mutableStateOf(previewShowBottomBar) }`
- Test `scr07b_inspection_recording`: `previewRecordingActive = true, previewShowBottomBar = true`
- Test `scr07_inspection_live`: `previewShowBottomBar = true`

**Beweis:**
- Vor Fix: `scr07b` DE=D90446C8 (== `scr07_live`)
- Nach Fix: `scr07b` DE=7F427C4E (REC-Chip sichtbar, DIFF von scr07 DE=4E0661D6)
- Sprachdiff: scr07b DE=7F427C4E ≠ EN=3955D640 ✓

### scr02_home_storage_usb — USB-Balken sichtbar
**Problem 1:** `LaunchedEffect(storageRefreshTick) { withContext(IO) { storageUsb = ... } }` überschreibt den initial gesetzten `previewUsbStorage`-Wert.

**Fix:**
- `HomeScreen` erhält `previewUsbStorage: Pair<String, VolumeUsage>? = null`
- `var storageUsb by remember { mutableStateOf<Pair<String, VolumeUsage>?>(previewUsbStorage) }`
- `LaunchedEffect`: `if (previewUsbStorage != null) return@LaunchedEffect` (IO-Scan skip in preview-mode)
- Test: `VolumeUsage(freeBytes = 12 GB, totalBytes = 32 GB)` als Fake-USB-Volume

**Problem 2 (EN-Translations MapPickerDialog):**
`"pick_on_map"`, `"tap_to_set_marker"`, `"apply_location"` fehlten in `enTranslations()` → DE-Fallback → dlg_map_picker DE==EN.

**Fix:** Drei Keys in `enTranslations()` ergänzt:
- `"pick_on_map"` → "Select location on map"
- `"tap_to_set_marker"` → "Tap on map to set location"
- `"apply_location"` → "Apply"

**Beweis USB:**
- Vor Fix: `scr02_home_storage_usb` DE=EC91D145 (== `scr02_home`)
- Nach Fix: `scr02_home_storage_usb` DE=62E298AF (USB-Balken sichtbar, DIFF von `scr02_home` DE=EC91D145) ✓

---

## Beweis-Hashes VOR/NACH

### VOR Fix (alle IDENTICAL)
```
scr02_home          DE=EC91D145 EN=EC91D145 IDENTICAL
scr07_inspection    DE=D90446C8 EN=D90446C8 IDENTICAL
scr07b_recording    DE=D90446C8 EN=D90446C8 IDENTICAL  (auch == scr07)
dlg_map_picker      DE=C07BB16A EN=C07BB16A IDENTICAL
... alle 19 IDENTICAL
```

### NACH Fix (alle DIFF, 20/20)
```
dlg_damage_dialog.png:          DE=EE197733 EN=6437B000 DIFF
dlg_map_picker.png:             DE=C07BB16A EN=852F3770 DIFF
dlg_note_dialog.png:            DE=22F24BAE EN=2439158F DIFF
dlg_pdf_preview.png:            DE=CE3D0CA9 EN=A739BCED DIFF
dlg_usb_export.png:             DE=D6AD85AA EN=97E02553 DIFF
scr02_home.png:                 DE=EC91D145 EN=26E5F5AB DIFF
scr02_home_storage_usb.png:     DE=62E298AF EN=29E6441F DIFF
scr03_connection.png:           DE=CCF6BDFB EN=A18CE39D DIFF
scr04_projects.png:             DE=74D7DB72 EN=2382DE96 DIFF
scr05_project_form_new.png:     DE=449EDE44 EN=DCC6C400 DIFF
scr05b_project_form_edit.png:   DE=89699901 EN=4D722E0E DIFF
scr06_project_detail.png:       DE=EB20DDCD EN=4607442D DIFF
scr07_inspection_live.png:      DE=4E0661D6 EN=B84059EA DIFF
scr07b_inspection_recording.png: DE=7F427C4E EN=3955D640 DIFF
scr08_reports.png:              DE=450D2561 EN=7E023DF2 DIFF
scr09_settings.png:             DE=8303B378 EN=E8450CB4 DIFF
scr10_network.png:              DE=0778115D EN=07D48828 DIFF
scr11_cloud_login.png:          DE=FFF5609E EN=197E6912 DIFF
scr12_offline_maps.png:         DE=275BB28A EN=4AA5904F DIFF
scr13_pairing.png:              DE=01371699 EN=2BACF79B DIFF
```

---

## Phase 4 — Paritäts-Gate EN (Opus-Sichtprüfung)

Stichprobe: 6 von 20 Szenen, synth-EN vs. Geräte-EN.

| Szene | EN-Labels? | UI-Elemente? | Verdict |
|-------|-----------|--------------|---------|
| dlg_map_picker | ✓ "Select location on map" | Kartenkacheln fehlen im Synth (MapsForge/layoutlib, bekannt) | PASS* |
| scr07_inspection_live | ✓ Power/Light/Probe/Recording/... | Steuerleiste mit EN-Labels, Meterstand 0.72 m | PASS |
| scr07b_inspection_recording | ✓ REC-Chip + EN-Labels | Identische EN-Steuerleiste, REC-Indikator sichtbar | PASS |
| scr09_settings | ✓ "Display & operation" etc. | Gleiche EN-Einstellungs-Elemente | PASS |
| scr04_projects | ✓ "Project", "Search project..." | Gleiche EN-Projektliste | PASS |
| scr02_home_storage_usb | ✓ "Storage", "USB-Stick" | USB-Balken im Synth sichtbar ✓, Geräte-Ref ohne USB | PASS |

*`dlg_map_picker`: Geräte-Referenz-Screenshot war DE (nicht EN); OSM-Tiles fehlen in layoutlib — bekannte Einschränkung.

**DE-Stichprobe:** 2 Szenen (scr09_settings, scr04_projects) → DE-Labels korrekt. ✓

---

## Phase 5 — PDFs neu gebaut

| Datei | Timestamp |
|-------|-----------|
| `DrainQ-ONE_Bedienungsanleitung_de_0.5.17.pdf` | 17.07.2026 11:38:30 |
| `DrainQ-ONE_Bedienungsanleitung_en_0.5.17.pdf` | 17.07.2026 11:38:34 |

EN-PDF jetzt mit echten EN-Screenshots (vorher byte-identisch mit DE).

---

## Offen-Liste

| Punkt | Status |
|-------|--------|
| dlg_pdf_preview-Paginierung | Offen — P1, darf offen bleiben (Prompt) |
| Geräte-Referenz dlg_map_picker in EN | Geräte-Screenshot war DE — bei nächster Geräte-Session neu aufnehmen |
| OSM-Tiles in layoutlib | Strukturelle Einschränkung, nicht fixbar ohne echtes Android |
| Gate wird in W-H5 Teil des Build-Gates | Offen |

---

## Änderungsübersicht (Dateien)

| Datei | Änderung |
|-------|---------|
| `ManualScreenshotTest.kt` | `init()` entfernt, `scr07b/scr07/scr02_home_storage_usb` aktualisiert |
| `InspectionScreen.kt` | `previewRecordingActive`, `previewShowBottomBar` Parameter |
| `HomeScreen.kt` | `previewUsbStorage` Parameter + LaunchedEffect-Guard |
| `LocalizationManager.kt` | EN-Translations: `pick_on_map`, `tap_to_set_marker`, `apply_location` |
| `render.ps1` | `--project-dir` Fix + hartes Sprachdifferenz-Gate |
| `docs/manual/screenshots_synth/de/*.png` | 20 neue DE-Renderings |
| `docs/manual/screenshots_synth/en/*.png` | 20 echte EN-Renderings (vorher DE-Kopien) |
| `docs/manual/DrainQ-ONE_Bedienungsanleitung_de_0.5.17.pdf` | Neu gebaut |
| `docs/manual/DrainQ-ONE_Bedienungsanleitung_en_0.5.17.pdf` | Neu gebaut (jetzt echte EN-Bilder) |

---

**Build:** ✓ grün (0 Errors, nur Pre-existing Warnings)  
**Gate:** ✓ 20/20 Szenen sprachlich unterschiedlich  
**PDF:** ✓ DE+EN neu gebaut

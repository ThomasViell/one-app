# RUN REPORT: Hilfe-System ONE — W-H3 (In-App-Hilfe + 5 Nachzügler + Portal)
**Datum:** 2026-07-17 · **Branch:** feature/help-system · **Basis:** feature/dual-mode (HEAD=708a291)

---

## Phase 0 — Preflight

| Check | Ergebnis |
|-------|----------|
| ONE per ADB (`adb devices`) | ✅ 233b4bd2865177ed |
| Kamera-Device-Node `/dev/video0` | ✅ vorhanden |
| `DRAINQ_PUBLISH_APIKEY` | ✅ gesetzt (EWmXo...) |
| `JAVA_HOME` | ✅ `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` (JDK 17, identisch mit Symlink C:\Android\jdk17) |
| Branch | ✅ `feature/help-system` HEAD=708a291 |
| Uncommittete Dateien | ⚠️ Diverse Prompt-/Status-MDs untracked — dokumentiert, unberührt |

**Phase-0-Ergebnis: PASS**

---

## Phase 1 — Kritischer Pfad-Check

**Befund:** `LocalizationManager` liest aus hartkodierten Kotlin-Maps (`deTranslations()`, `enTranslations()` etc.) — NICHT aus JSON-Dateien.

Die `assets/i18n/de.json` und `assets/i18n/en.json` (mit den 436 `help.*`-Keys aus W-H2) werden zur Laufzeit vom `LocalizationManager` NICHT geladen. `LocalizationManager.getString("help.*")` gibt den Key-Namen zurück (Fallback), keine Texte.

**Entscheidung:** `HelpRepository` liest `assets/i18n/<lang>.json` direkt für `help.*`-Key-Auflösung.
- Struktur aus `assets/help/help_<lang>.json` (Route→Baustein-Mapping, Key-Referenzen)
- Texte aus `assets/i18n/<lang>.json` (für `help.*`-Keys)
- Screenshots aus `assets/help/screenshots/<lang>/<screenshot>.png`
- `LocalizationManager.getString()` für alle anderen UI-Keys (Titel, Labels)
- Portal-Integration: wenn `feature/l10n-portal` gemergt ist, kommen `help.*`-Keys automatisch über denselben Weg — kein Sondersystem nötig (PLAN §3.5 erfüllt)

**Phase-1-Ergebnis: PASS** — L10n-Pfad geklärt, Entscheidung dokumentiert.

---

## Phase 2 — Root-Cause der 5 FAIL-Szenen + Dialog-Verdrahtung

**Root-Cause:** `ScreenshotRigBus.uiState` wurde in InspectionScreen, ProjectDetailScreen und ProjectFormScreen nicht abgehört. Die UI_STATE-Broadcasts vom Harness hatten keinen Empfänger im Compose-Tree.

**Entscheidungen:**
- `scr01_splash`: Splash ist kein NavGraph-Ziel, wird nur einmalig beim App-Start gezeigt. Rig kann Splash nicht deterministisch auslösen. → **Streichen** (in scenes.json als `capturable: false` markiert, Hilfe-Baustein für Splash ausgelassen).
- `dlg_map_picker`: MapPickerDialog wird per `viewModel.showMapPicker` in ProjectFormScreen gesteuert. Rig verdrahtet `map_picker` → `viewModel.openMapPicker()`. → **Echt öffnen** (kein Löschen des Bausteins).

**Umgesetzt:**
| Screen | Dialog | State-Name | Verdrahtung |
|--------|--------|-----------|-------------|
| InspectionScreen | DamageDialog | `damage_dialog` | LaunchedEffect rig-Bus |
| InspectionScreen | NoteDialog | `note_dialog` | LaunchedEffect rig-Bus |
| ProjectDetailScreen | PdfPreviewDialog | `pdf_preview` | viewModel.previewPdf() |
| ProjectDetailScreen | UsbExportDialog | `usb_export_dialog` | showUsbExportDialog = true |
| ProjectFormScreen | MapPickerDialog | `map_picker` | viewModel.openMapPicker() |

_wird nach Commit-SHA ergänzt_

**Phase-2-Ergebnis: PENDING**

---

## Phase 3 — In-App-Hilfe (HelpRepository + HelpSheet + HelpButton)

_wird nach Implementierung ergänzt_

**Phase-3-Ergebnis: PENDING**

---

## Phase 4 — Opus Code-Audit

_wird nach Audit ergänzt_

---

## Phase 5 — Beta publizieren + Gerät updaten

_wird nach Publish ergänzt_

---

## Phase 6 — Screenshot-Nachzügler

_wird nach Capture ergänzt_

---

## Phase 7 — Hilfe-Texte der Nachzügler

_wird nach Textgenerierung ergänzt_

---

## Phase 8 — PDFs neu bauen

_wird nach PDF-Generierung ergänzt_

---

## Phase 9 — Geräte-Abnahme In-App-Hilfe

_wird nach Gerättest ergänzt_

---

## Phase 10 — Portal

_wird nach Portal-Prüfung ergänzt_

---

## Phase 11 — Abschluss

_wird nach Finalisierung ergänzt_

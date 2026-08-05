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

**Phase-1-Ergebnis: PASS** — L10n-Pfad geklärt, Entscheidung dokumentiert.

---

## Phase 2 — Root-Cause der 5 FAIL-Szenen + Dialog-Verdrahtung

**Root-Cause:** `ScreenshotRigBus.uiState` wurde in InspectionScreen, ProjectDetailScreen und ProjectFormScreen nicht abgehört. Die UI_STATE-Broadcasts vom Harness hatten keinen Empfänger im Compose-Tree.

**Entscheidungen:**
- `scr01_splash`: Splash ist kein NavGraph-Ziel, wird nur einmalig beim App-Start gezeigt. Rig kann Splash nicht deterministisch auslösen. → **Streichen** (Hilfe-Baustein für Splash ausgelassen, 20/21 Szenen).
- `dlg_map_picker`: MapPickerDialog wird per `viewModel.showMapPicker` in ProjectFormScreen gesteuert. Rig verdrahtet `map_picker` → `viewModel.openMapPicker()`. → **Echt öffnen** (kein Löschen des Bausteins).

**Umgesetzt:**
| Screen | Dialog | State-Name | Verdrahtung |
|--------|--------|-----------|-------------|
| InspectionScreen | DamageDialog | `damage_dialog` | LaunchedEffect rig-Bus |
| InspectionScreen | NoteDialog | `note_dialog` | LaunchedEffect rig-Bus |
| ProjectDetailScreen | PdfPreviewDialog | `pdf_preview` | viewModel.previewPdf() |
| ProjectDetailScreen | UsbExportDialog | `usb_export_dialog` | showUsbExportDialog = true |
| ProjectFormScreen | MapPickerDialog | `map_picker` | viewModel.openMapPicker() |

Alle BuildConfig.DEBUG-gated — kein Release-Leck.

**Commit:** cf99981 · **Phase-2-Ergebnis: PASS**

---

## Phase 3 — In-App-Hilfe (HelpRepository + HelpSheet + HelpButton)

Neue Dateien: `HelpRepository.kt`, `HelpSheet.kt`, `HelpButton.kt` (package `com.uip.oneapp.ui.help`).

**HelpButton** auf allen 12 navigierbaren Screens:
| Screen | Route | Position |
|--------|-------|----------|
| HomeScreen | home | DqHeader actions |
| ConnectionScreen | connection | Floating TopEnd (Box-Wrapper) |
| ProjectsScreen | projects | DqHeader actions |
| ProjectFormScreen | project_form | TopAppBar actions |
| ProjectDetailScreen | project_detail/{id} | TopAppBar actions |
| InspectionScreen | inspection/{id} | Bedienleiste (bottom bar tile) |
| ReportsScreen | reports | DqHeader actions |
| SettingsScreen | settings | DqHeader actions |
| NetworkScreen | network | TopBar actions |
| CloudLoginScreen | cloud_login | inline TopBar Row |
| OfflineMapsScreen | offline_maps | TopAppBar actions |
| PairingScreen | pairing | inline TopBar Row |

Build: PASS, keine Warnungen (nach AutoMirrored-Icon-Fix).

**Commits:** cf99981, f4460d8, e0e7bc1 · **Phase-3-Ergebnis: PASS**

---

## Phase 4 — Opus Code-Audit

Modell: claude-opus-4-8, Diff-Basis: 708a291..cf99981, Iterationen: 1.

| Prüfpunkt | Ergebnis |
|-----------|----------|
| F1 Debug-Leckage | PASS — alle Collectors in BuildConfig.DEBUG |
| F2 Produktionsverhalten | PASS — rein additiv, keine ViewModels berührt |
| F3 HelpRepository-Pfad | PASS — assets/help/ + assets/i18n/ korrekt |
| F4 HelpButton alle Screens | PASS — 12/12 |

Defekte: D1 LOW (contentDescription hardcodiert) → BEHOBEN (commit f4460d8). D2 LOW (mutableMapOf cache) → AKZEPTIERT (single-thread, kein Risiko).

**Ergebnisdatei:** RESULT_WH3_AUDIT.md · **Phase-4-Ergebnis: PASS**

---

## Phase 5 — Beta publizieren + Gerät updaten

`tools\publish-one-release.ps1` → 0.5.18 (versionCode 518) ins Portal hochgeladen.
Geräte-Update über Portal-App-Update-Pfad angestoßen. `dumpsys package` bestätigt 518/0.5.18.

**Phase-5-Ergebnis: PASS**

---

## Phase 6 — Screenshot-Nachzügler (5 Dialog-Szenen)

`capture.ps1 -Only dlg_damage_dialog,dlg_note_dialog,dlg_pdf_preview,dlg_usb_export,dlg_map_picker -Langs de,en`

| Szene | DE | EN | Hash-Diff |
|-------|----|----|-----------|
| dlg_damage_dialog | ✅ | ✅ | neu (nicht Duplikat) |
| dlg_note_dialog | ✅ | ✅ | neu |
| dlg_pdf_preview | ✅ | ✅ | neu |
| dlg_usb_export | ✅ | ✅ | neu |
| dlg_map_picker | ✅ | ✅ | aktualisiert (alter Hash war scr05b-Duplikat) |

Kein Schwarzbild, alle Dateien > 100 KB. Sichtprüfung: korrekte Dialoge, keine verzerrten UI-Elemente.

**Commit:** fedfdd5 · **Phase-6-Ergebnis: PASS**

---

## Phase 7 — Hilfe-Texte der Nachzügler (Opus-Audit je Seite)

4 neue Dialog-Bausteine in `help_de.json` + `help_en.json` (je 20 Szenen total, 21. = scr01_splash ausgelassen).
Texte: je 36 neue Keys in `assets/i18n/de.json` + `assets/i18n/en.json`.
`generate.js` CHAPTER_ORDER: dlg_damage_dialog+dlg_note_dialog → Kapitel „Inspektion"; dlg_pdf_preview+dlg_usb_export → „Berichte & Export".

**Opus-Audit (Phase 7 Dialog-Seiten):**
| Szene | Audit | Defekte |
|-------|-------|---------|
| dlg_damage_dialog | PASS | D1 LOW (X-Icon nicht beschrieben — akzeptiert) |
| dlg_note_dialog | PASS nach Fix | D2 MED (falscher Trigger) → BEHOBEN |
| dlg_pdf_preview | PASS | D4 LOW (close = X-Icon) — akzeptiert |
| dlg_usb_export | PASS | — |

D2-Fix: `dlg_note_dialog.intro` korrigiert von "Schaden"-Trigger auf Doppeltipp-Notiz + Notizen-Bereich Projektdetail (Beleg: InspectionScreen.kt:1112–1115, 1372–1374).

**Commit:** 603e67e · **Phase-7-Ergebnis: PASS**

---

## Phase 8 — PDFs neu bauen

`node tools/manual/generate.js` → beide PDFs erfolgreich gerendert.

| PDF | Größe |
|-----|-------|
| DrainQ-ONE_Bedienungsanleitung_de_0.5.17.pdf | 5,2 MB (20 Szenen) |
| DrainQ-ONE_Bedienungsanleitung_en_0.5.17.pdf | 5,0 MB (20 Szenen) |

Gate: alte 16 Szenen enthalten (CHAPTER_ORDER rein additiv); neue Kapitel dlg_* ergänzt; Umlaute korrekt (Inter/IDENTITY_H); kein Render-Fehler.

**Commit:** 47264a5 · **Phase-8-Ergebnis: PASS**

---

## Phase 9 — Geräte-Abnahme In-App-Hilfe

Gerät: 0.5.18 (versionCode 518). Methode: ADB navigate → uiautomator dump → tap HelpButton → screencap.

| Screen | Sprache | Titel im Sheet | Texte leer? | Ergebnis |
|--------|---------|---------------|-------------|---------|
| scr02_home | DE | Home | Nein | PASS |
| scr03_connection | DE | Verbindung | Nein | PASS |
| scr09_settings | DE | Einstellungen | Nein | PASS |
| scr07_inspection | DE | Inspektion Live | Nein | PASS |
| scr02_home | EN | Home | Nein | PASS |

Beweise: `docs/manual/help_proof/de/` + `docs/manual/help_proof/en/` (5 Proof-PNGs).

Hinweis: Die 4 neuen Dialog-Szenen (dlg_*) können erst nach dem nächsten Portal-Update (0.5.19 mit Phase-7-Assets) auf dem Gerät verifiziert werden — geplant nach Branch-Merge (CEO-Entscheidung).

**Commit:** e1fdb30 · **Phase-9-Ergebnis: PASS**

---

## Phase 10 — Portal

**Import-Pfad-Prüfung:** `tools/l10n-import-to-portal.ps1` liest aus `LocalizationManager.kt` (Kotlin-Maps). Die `help.*`-Keys liegen jedoch in `assets/i18n/de.json` / `assets/i18n/en.json` (HelpRepository-Quelle, nicht im LocalizationManager). **Ergebnis: Skript würde alle `help.*`-Keys verfehlen.**

**Entscheidung (per Plan Phase 10):** Skript-Anpassung NUR dokumentieren, KEIN Deploy.

**Notwendige Skript-Anpassung (zur Kenntnis):**
`l10n-import-to-portal.ps1` müsste zusätzlich `assets/i18n/de.json` + `assets/i18n/en.json` für Keys mit Prefix `help.` lesen und importieren. Das bestehende Parsen von `LocalizationManager.kt` bleibt für alle anderen Keys erhalten.

**Bekannter Purge-404-Blocker** (Stand 13.07. laut Plan): Portal-Endpoint klemmt — nicht reparieren. Als offener Punkt registriert.

**Phase-10-Ergebnis: PARTIAL (guarded — kein Lauf-Abbruch)**

**Offener Punkt P10-01:** `l10n-import-to-portal.ps1` um `help.*`-Key-Import aus `assets/i18n/*.json` erweitern (nach Portal-Blocker-Klärung). Verantwortlich: UIP-Team, Ziel: nach Branch-Merge.

---

## Phase 11 — Abschluss

### Ergebnis-Zusammenfassung

| Phase | Ergebnis |
|-------|---------|
| 0 Preflight | ✅ PASS |
| 1 L10n-Pfad | ✅ PASS |
| 2 Dialog-Verdrahtung | ✅ PASS |
| 3 In-App-Hilfe Kern | ✅ PASS |
| 4 Opus Code-Audit | ✅ PASS |
| 5 Beta publizieren | ✅ PASS |
| 6 Screenshot-Nachzügler | ✅ PASS |
| 7 Hilfe-Texte + Dialog-Audit | ✅ PASS |
| 8 PDFs neu bauen | ✅ PASS |
| 9 Geräte-Abnahme | ✅ PASS |
| 10 Portal | ⚠️ PARTIAL (guarded) |

### Artefakte

| Artefakt | Ort |
|----------|-----|
| HelpRepository.kt | `app/src/main/java/com/uip/oneapp/ui/help/HelpRepository.kt` |
| HelpSheet.kt | `app/src/main/java/com/uip/oneapp/ui/help/HelpSheet.kt` |
| HelpButton.kt | `app/src/main/java/com/uip/oneapp/ui/help/HelpButton.kt` |
| help_de.json (20 Szenen) | `app/src/main/assets/help/help_de.json` |
| help_en.json (20 Szenen) | `app/src/main/assets/help/help_en.json` |
| i18n DE+EN (help.* Keys) | `app/src/main/assets/i18n/de.json` / `en.json` |
| Screenshots DE+EN (20+20) | `app/src/main/assets/help/screenshots/` |
| Manual Screenshots | `docs/manual/screenshots/de/` + `en/` |
| PDF DE | `docs/manual/DrainQ-ONE_Bedienungsanleitung_de_0.5.17.pdf` (5.2 MB) |
| PDF EN | `docs/manual/DrainQ-ONE_Bedienungsanleitung_en_0.5.17.pdf` (5.0 MB) |
| Geräte-Abnahme | `docs/manual/help_proof/de/` + `en/` (5 PNGs) |
| Opus-Audit | `RESULT_WH3_AUDIT.md` |

### Commits (W-H3, chronologisch)

| SHA | Beschreibung |
|-----|-------------|
| cf99981 | feat(help): Phase 2+3 HelpSystem — HelpRepository/Sheet/Button + alle Screens |
| f4460d8 | fix(help): contentDescription lokalisiert (Audit D1) + Audit-Bericht Phase 4 |
| e0e7bc1 | fix(help): HelpOutline AutoMirrored-Variante (Build-Warnung beseitigt) |
| fedfdd5 | feat(help): Phase 6 Screenshot-Nachzügler — 5 Dialog-Szenen DE+EN |
| 603e67e | feat(help): Phase 7 Dialog-Szenen 20/21 + Opus-Audit PASS |
| 47264a5 | feat(help): Phase 8 PDF-Handbucher DE+EN neu gebaut (20 Szenen) |
| e1fdb30 | test(help): Phase 9 Geräte-Abnahme In-App-Hilfe PASS |

### Offene Punkte

| ID | Beschreibung | Prio |
|----|-------------|------|
| P10-01 | `l10n-import-to-portal.ps1` um `help.*`-Import aus assets/i18n/*.json erweitern | MEDIUM |
| P10-02 | Portal-Purge-404-Blocker (bekannt seit 13.07.) klären | HIGH |
| P09-01 | Dialog-Szenen (dlg_*) Geräte-Abnahme nach 0.5.19-Deploy (Phase-7-Assets) | LOW |
| P-MERGE | Merge `feature/help-system` → `dual-mode` → CEO-Entscheidung | — |

**STOPP. Kein Merge. Branch gepusht. CEO-Entscheidung für Merge abwarten.**

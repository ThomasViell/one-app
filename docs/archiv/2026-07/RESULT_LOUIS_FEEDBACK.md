# Ergebnis: Louis-Feedback DrainQ.ONE (Wellen W0–W8)

**Branch:** `feature/louis-feedback` (abgezweigt von `feature/beta-wave-1` @ `f86cb6e`)
**Stand:** 2026-06-13 · **Build/Tests:** `assembleDebug test` → **BUILD SUCCESSFUL**, alle Unit-Tests grün (Opus-Toolchain, JDK17, offline).
**Quelle:** `FEEDBACK_Kollege_2026-06-13_Analyse.md`, Auftrag `LOUIS_FEEDBACK_WELLEN_PROMPT.md` (+ `FIX_NAVBAR_V2_PROMPT.md` für W1).

> **Geräte-Hinweis:** Das Testgerät `233b4bd2865177ed` war während der Umsetzung **nicht per USB erreichbar** (`adb devices` leer, mehrfach geprüft, kein WLAN-ADB-Pfad). `installDebug` und die On-Device-Vorher/Nachher-Screenshots konnten daher **nicht** ausgeführt werden — sie sind als „offen" markiert und sofort nachholbar, sobald das Gerät hängt: `$env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`. Alle Code-, Build- und Test-Schritte sind vollständig und grün.

---

## Commits (ein Commit pro Welle, gezielt gestaget – nie `git add -A`)

| Welle | Commit | Titel |
|---|---|---|
| W1 | `2dfdb93` | fix(kiosk): System-Leiste über Bedienleiste endgültig unterdrückt (Original-Technik) |
| W2 | `a011373` | feat(inspection): Auto-Ausblenden der Bedienleiste optional (Default aus) |
| W3 | `cc3e674` | fix(meter): Plausibilitätsfilter + Glättung gegen Ausreißer im Meterzähler |
| W4 | `3ee5d7c` | feat(projectdetail): Reiter größer + farbige Icons (Foto/Schaden/Video/Notiz) |
| W5 | `94a5e2e` | feat(nav): Hauptnavigation auf Home/Inspektion/Einstellungen verschlankt |
| W6 | `d879e7c` | feat(inspection): Notiz aus Hauptbedienung entfernt, Schaden als primärer Weg |
| W7 | `b148b8e` | docs: USB-Einrichtung + Sprachwechsel-Neustart in der Bedienungsanleitung |
| W8 | `b619653` | fix(meter): Plausibilitäts-Schwelle an reale Frame-Rate + Recovery-Fenster leeren (Review-Befund) |

---

## W0 — Vorbereitung & grüne Baseline
- `feature/beta-wave-1` war bereits gepusht (HEAD == origin); `feature/louis-feedback` abgezweigt (die bereits vorhandene, ungecommittete Navbar-Arbeit `MainActivity.kt` + `KioskImmersive.kt` wurde übernommen und in W1 committet).
- Baseline `assembleDebug test` grün. App-Baseline + `tools/_louis/`-Ablage angelegt.
- **Status:** ✅ (Ausgangs-Screenshots: offen – Gerät offline)

## W1 — Grauer Balken endgültig weg (Finding 1)
Original-App-Technik (`com.bominwell.minipush`): Legacy-Immersive-Flags `5894` + Re-Hide-Listener auf **jedem** Fenster.
- `ui/components/KioskImmersive.kt:40` — `HideSystemBarsInDialog()` (no-op bei Kiosk AUS via `LocalKioskEnabled`).
- `MainActivity.kt` — Activity-Seite: `applyLegacyImmersive()` (Z.230), `OnSystemUiVisibilityChangeListener` (Z.72), `applyNavigationMode()`=0 (Z.263), `LocalKioskEnabled`-Provider (Z.149).
- Helfer in **alle** separaten Fenster eingesetzt (Befund vorher: 0 Aufrufe):
  - `ui/screens/inspection/DamageDialog.kt:82` (+ ExposedDropdown Z.317), `NoteDialog.kt:176`, `ImageAnnotationDialog.kt:83` (+ Save-AlertDialog Z.253)
  - `ui/components/ImagePickerDialog.kt:76`, `UpdateDialog.kt`, `UpdateProgressDialog.kt`, `DqSettingsComponents.kt:113` (zentrale Dropdown-Komponente)
  - `ui/screens/projectdetail/`: `FullscreenImageDialog.kt:43`, `VideoPlaybackDialog.kt:99`, `PdfPreviewDialog.kt:86`, `UsbExportDialog.kt:62`, `ProjectDetailScreen.kt` (6 AlertDialogs)
  - `ui/screens/projects/`: `MapPickerDialog.kt:165`, `ProjectFormScreen.kt` (DatePicker + 4 ExposedDropdowns)
  - `ui/screens/network/NetworkScreen.kt:333` (Wifi-Passwort), `ui/screens/settings/SettingsScreen.kt` (Sprach-Neustart + OsdDropdown), `UpdateSection.kt` (Channel-Dropdown), `ui/screens/offlinemaps/OfflineMapsScreen.kt` (4 AlertDialogs)
  - `InspectionScreen.kt`: Licht-/Sonde-Popups, Beenden- + Verarbeitungs-Dialog
- **Vollständigkeit verifiziert:** grep aller `Dialog/AlertDialog/Popup/DropdownMenu/ExposedDropdownMenu/DatePickerDialog` ⇒ jedes Fenster trägt genau einen Helfer-Aufruf (37 Fenster, 36 Aufrufe + 1 Definition).
- **Status:** ✅ Code/Build · ⏳ On-Device-Abnahme (Dialog+Tastatur) offen – Gerät offline.

## W2 — Auto-Ausblenden abschaltbar, Default AUS (Finding 2)
- `ui/screens/settings/SettingsViewModel.kt` — `controlsAutoHide` (State), `KEY_CONTROLS_AUTO_HIDE = "controls_auto_hide"` (Default `false`), init-Read, `updateControlsAutoHide()`, `saveAll()`.
- `ui/screens/settings/SettingsScreen.kt:~129` — neue Toggle-Zeile (`settings_autohide_title/desc`, Icon `expand_less`).
- `ui/screens/inspection/InspectionScreen.kt` — reaktiver Pref-Read direkt aus `settingsStore` (robust gegen VM-Scoping, analog `damages_newest_first`); beide Auto-Hide-Effekte (`showControls`, `showBottomBar`) auf `controlsAutoHide` gegated; neuer Effekt hält das Bedienband bei AUS dauerhaft sichtbar; `onTap` schaltet bei AUS nur das rechte Panel.
- `ui/localization/LocalizationManager.kt` — de+en `settings_autohide_title`, `settings_autohide_desc`.
- **Default AUS:** Bedienband dauerhaft sichtbar; AN = bisheriges Cinema-Auto-Hide.
- **Status:** ✅ Code/Build · ⏳ On-Device-Abnahme offen.

## W3 — Meterzähler stabilisieren (Finding 6)
- `network/internal/OneFrameCodec.kt` — `subFrameXorOk()` + Aufruf in `drainRxFrames`: je Subframe XOR-Trailer prüfen, defekte Subframes verwerfen (Layout/Indizes unverändert, Trailer bleibt im payload). Am realen C18-Frame für Gruppen 21/22/23/24 verifiziert.
- `network/internal/LinearMeterCalculator.kt` — Plausibilitätsfilter (Sprung > 300 mm/Tick bzw. negativ verwerfen, Recovery nach 4 konsistenten Ausreißern) + Median-über-3; `@Synchronized`; `reset()`.
- `network/internal/OneInternalHardwareService.kt` — `resetMeterAbsolute()` ruft `meter.reset()`.
- **Tests:** `LinearMeterCalculatorTest.kt` (neu: Rampe/Spike/negativ/Jitter/Recovery/Reset), `OneFrameCodecTest.kt` (+XOR-Drop + Gegenprobe) — grün.
- **Review-Verfeinerung (W8):** `MAX_STEP_MM` 300→1000 mm (reale Frame-Rate ~51 ms/Tick ⇒ deckt ~19 m/s ab, verschluckt keine schnelle Kabelbewegung; gekipptes High-Byte ≥ 65536 mm bleibt sicher gefangen) + Median-Fenster bei Recovery leeren (Sprung schlägt sofort durch). +2 Tests.
- **Optional/Folge (nicht umgesetzt, dokumentiert):** echte Linearisierungstabelle `LinearDataPoints.java` portieren – nur am realen Kabel kalibrierbar.
- **Status:** ✅ Code/Build/Test · ⏳ Gegentest am Kabel offen – Gerät offline.

## W4 — Sub-Menü-Reiter größer + farbige Icons (Finding 5)
- `ui/screens/projectdetail/ProjectDetailScreen.kt` — `TabWithBadge` mit farbigem Icon je Reiter, größerem Text (`titleSmall`), Reiterhöhe ≥ 72 dp, Amber-Akzent für aktiven Reiter; 4 Aufrufe mit iconKey (`photo`/`alert`/`video`/`note`).
- `ui/components/DqComponents.kt` — `DqIcons.byKey` um `video`, `note` ergänzt.
- `ui/theme/Dimensions.kt` — `TabHeight = 72.dp`.
- `res/drawable/ic_dq_video.xml`, `ic_dq_note.xml` — neue Tabler-Outline-Drawables.
- **Status:** ✅ Code/Build · ⏳ Screenshot offen.

## W5 — Hauptnavigation verschlanken (Finding 3, Default umgesetzt)
- `ui/navigation/NavGraph.kt` — `bottomNavItems` = **Home / Inspektion / Einstellungen** (Projekte raus aus Leiste **und** Rail – gemeinsame Liste). Route `projects` + alle anderen Aufrufer (`ReportsScreen`, `InspectionScreen`) unverändert ⇒ keine Sackgasse.
- `ui/screens/home/HomeScreen.kt` — „Projekte"-Überschrift antippbar → `navigate("projects")` (mit Chevron-Affordanz), damit die volle Projektliste über Home erreichbar bleibt.
- **Status:** ✅ Code/Build · ⏳ Screenshot offen.

## W6 — Notiz mit Schaden zusammenlegen (Finding 4, Default umgesetzt)
- `ui/screens/inspection/InspectionScreen.kt` — Notiz-**Button** aus dem Inspektions-Bedienpanel entfernt. `showNoteDialog`/`editingNote` bleiben (Notizliste-Doppeltipp + `NoteDialog`-Block weiter aktiv).
- **Unverändert (kein Datenverlust):** `NoteDialog`, `NoteEntity/Dao/Repository`, `NotesTab` + Notiz/Audionotiz im Projekt-Detail.
- **Status:** ✅ Code/Build · ⏳ Screenshot offen.

## W7 — Doku & Hinweise (Findings 7, 9, 8)
- `generate_manual_docx.js` (+ `ONE_APP_Bedienungsanleitung.docx` neu generiert, Inhalt verifiziert) und `generate_manual.js`:
  - **7.3 USB-Export** + infoBox „USB-Stick einrichten" (einmalige Freigabe nötig, sonst keine Speicherung — Finding 7).
  - **8.1 Sprache**: irreführendes „wird sofort angewendet" entfernt + infoBox „Neustart erforderlich" (Finding 9).
- **Kein Code nötig:** USB-Berechtigungshinweis (`UsbExportDialog`) und Sprach-Neustart-Dialog (`SettingsScreen`) sind bereits implementiert.
- **Finding 8:** Sprachen für BETA bewusst de+en (CEO-Beschluss 2026-06-07).
- **Status:** ✅ docx aktualisiert · ⚠️ PDF konnte hier nicht neu gerendert werden (fehlendes Font-Asset `res/font/barlow_black.ttf`); Quelle ist aktualisiert, PDF nach Wiederherstellen der Schrift per `node generate_manual.js` neu erzeugen.

---

## Abnahme-Tabelle

| # | Kriterium | Status | Beleg |
|---|-----------|--------|-------|
| 1 | Kein grauer Balken über Bedienicons (inkl. Dialog+Tastatur) | ⏳ On-Device offen (Gerät offline); Code vollständig (alle Fenster abgedeckt, Build grün) | `tools/_louis/w1_build.log`, grep-Abdeckung 37/37 Fenster |
| 2 | Icons bleiben sichtbar (Default); Auto-Hide nur per Option | ⏳ On-Device offen; Code/Logik vollständig | `tools/_louis/w2_build.log` |
| 3 | Meterzähler läuft ruhig, kein Springen | ⏳ Kabel-Gegentest offen; Filter+XOR + Unit-Tests grün | `LinearMeterCalculatorTest`, `OneFrameCodecTest` |
| 4 | Reiter größer + farbige Icons, handschuhtauglich | ⏳ Screenshot offen; Code/Build grün | `tools/_louis/w4w5w6_build.log` |
| 5 | Navigation verschlankt, alle Bereiche erreichbar | ⏳ Screenshot offen; Code/Build grün (Projekte via Home) | NavGraph/HomeScreen-Diff |
| 6 | Notiz/Schaden wie entschieden, kein Datenverlust | ✅ Code (Button entfernt, Daten/Dialog/Tab unverändert) · ⏳ Screenshot | InspectionScreen-Diff |
| 7 | Anleitung: USB-Einrichtung + Sprach-Neustart dokumentiert | ✅ docx neu generiert + verifiziert | `ONE_APP_Bedienungsanleitung.docx` |
| 8 | `gradlew test` grün | ✅ BUILD SUCCESSFUL, alle Tests grün | `tools/_louis/final_build.log` |

---

## Adversariale Review (W8)
4 unabhängige Review-Agenten (je Welle/Wellengruppe) + Verifizierungs-Pass über den Branch-Diff (`f86cb6e..HEAD`): **0 bestätigte kritische/größere Befunde**. Ein **minor**-Hinweis (W3 Δ-Schwelle vs. reale Frame-Rate ~51 ms) wurde aufgegriffen und behoben (s. W3 „Review-Verfeinerung"). W1-Helfer-Platzierung, W2-Auto-Hide-Logik, W5-Navigationserreichbarkeit und W6-Datenerhalt wurden bestätigt korrekt.

## Offene Punkte
1. **On-Device-Abnahme + Vorher/Nachher-Screenshots** (W1–W6) – blockiert durch fehlende USB-Verbindung des Geräts `233b4bd2865177ed`. Sobald verbunden: `installDebug`, dann je Welle Screenshots nach `tools/_louis/`. Besonders W1 (jeder Dialog **inkl. Soft-Tastatur**) und W3 (Kabel mehrfach aus-/einziehen).
2. **PDF-Handbuch neu rendern** – `generate_manual.js` ist aktualisiert; Font-Asset `res/font/barlow_black.ttf` fehlt im Repo, daher PDF unverändert. Nach Wiederherstellen: `node generate_manual.js`.
3. **W3 Folge (optional):** Linearisierungstabelle (`LinearDataPoints.java`) für korrekte Absolutwerte – nur am Kabel kalibrierbar.

## Liefergegenstände
- Branch `feature/louis-feedback`, 7 Wellen-Commits (W1–W7) + 1 Review-Verfeinerung (`b619653`), gepusht.
- Build-/Test-Logs unter `tools/_louis/` (`*_build.log`).
- Diese `RESULT_LOUIS_FEEDBACK.md`.

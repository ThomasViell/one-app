# DrainQ.ONE — SA-Design app-weit umsetzen (Vorgabe + Rollout)

**Stand:** 2026-06-04 · **Quelle:** DrainQ Design-Richtlinie v1.1 (Amber/Dark-First) · **Ziel:** Designidee der 4 Mockups 1:1 auf ALLE Screens/Views.
Bauen + Geräte-Test + Commit laufen in Claude Code auf einem Feature-Branch. Diese Datei ist die verbindliche Bau-Vorgabe.
Mockups im selben Ordner: `drainq-one_01_home_SA-design.svg` … `_04_einstellungen_SA-design.svg`.

---

## 0. Entscheidungen (bestätigt 2026-06-04)

1. **Font: Inter** ersetzt Barlow (Richtlinie v1.1, OFL). Barlow wird entfernt.
2. **OSD komplett:** sowohl die Live-On-Screen-Chips ALS AUCH die ins Video **eingebrannte** OSD werden neu gestaltet. → Recording/Geräte-Test nach Welle 1 zwingend.
3. **Dark + Light:** beide Themes gleichwertig aufbauen, zur Laufzeit umschaltbar (Einstellungen → Erscheinungsbild). Dark ist Standard.

---

## 1. Farb-Tokens → `theme/Color.kt` (Dark **und** Light)

| Rolle | Token | Dark | Light |
|---|---|---|---|
| Primär/Marke | `Amber` | `#FF9900` | `#FF9900` |
| Primär Hover | `AmberHover` | `#FFB340` | `#E68A00` |
| Text auf Primär | `OnAmber` | `#1D1D1B` | `#1D1D1B` |
| Fenster-BG | `BgWindow` | `#1C1C1E` | `#FAFAFA` |
| Card/Panel | `BgPanel` | `#2C2C2E` | `#FFFFFF` |
| Sidebar/Statusbar | `BgSidebar` | `#242426` | `#F4F4F5` |
| Erhöht (Input, Toggle-off) | `BgElevated` | `#3A3A3C` | `#E4E4E7` |
| Border | `BorderSubtle` | `#3A3A3C` | `#E4E4E7` |
| Text primär | `TextPrimary` | `#F2F2F7` | `#18181B` |
| Text sekundär | `TextSecondary` | `#98989D` | `#71717A` |
| Text tertiär | `TextTertiary` | `#636366` | `#A1A1AA` |
| Erfolg | `Success` | `#30D158` | `#34C759` |
| Warnung | `Warning` | `#FFD60A` | `#FFD60A` |
| Fehler/Not-Stop | `Error` | `#FF453A` | `#FF3B30` |
| Info | `Info` | `#0A84FF` | `#0A84FF` |
| Video-BG | `VideoBg` | `#000000` | `#000000` |
| OSD-Overlay-BG | `OsdBg` | `#000` @70% | `#000` @55% |

**Themenunabhängig (gleich in Dark/Light):** Code-Familienfarben — Leitung `#0A84FF`, Schacht `#5E5CE6`, Anschluss `#BF5AF2`, Strecke `#64D2FF`, Betrieb `#30D158`. `DamageClass0..4` (ZK-Semantik) behalten.

### Material3-Mapping (`Theme.kt`)
Zwei Schemata: `darkColorScheme` + `lightColorScheme`, gleiche Rollen:
```
primary=Amber · onPrimary=OnAmber(#1D1D1B, dunkel auf Amber!) · primaryContainer=Amber@16%
secondary=Info · background=BgWindow · onBackground=TextPrimary
surface=BgPanel · onSurface=TextPrimary · surfaceVariant=BgSidebar · onSurfaceVariant=TextSecondary
error=Error · outline=BorderSubtle
```
`DrainQTheme(darkTheme: Boolean)` wählt das Schema; System-/Navigationsleiste + `isAppearanceLightStatusBars` je Theme dynamisch setzen. Theme-Quelle: `themeMode`-Pref (System/Dunkel/Hell) via DataStore, gesetzt in Einstellungen.

---

## 2. Typografie → `theme/Type.kt` (Inter, touch-vergrößert)

`InterFontFamily` (Gewichte 400/500/600 — **kein** Bold/Black). Barlow-FontFamily entfernen. Material3-Styles auf ONE-Touch-Maße (sp):

| Style | Größe | Gewicht | Verwendung |
|---|---|---|---|
| `displaySmall` | 40 | 600 | Hero-KPI (Home-Statwerte) |
| `headlineMedium` | 27 | 600 | Screen-Titel im Header |
| `titleLarge` | 22 | 600 | Card-Überschrift, Detail-Titel |
| `titleMedium` | 20 | 600 | Button-Label, Zeilen-Titel, Softbuttons |
| `bodyLarge` | 18 | 400 | Standard-Text, Listentitel |
| `bodyMedium` | 16 | 400 | Sekundär-Text, Meta |
| `labelLarge` | 15 | 500 | Nav-Label, Pills, Chips |

**Mindestgröße 16 sp** im Fließtext (Outdoor/Handschuh).

---

## 3. Maße → `theme/Dimensions.kt` + `theme/Shape.kt`

| Token | Wert | |
|---|---|---|
| `ButtonHeight` | 56 dp | Standard-CTA |
| `ButtonHeightLarge` | 72 dp | zentrale CTA / Schnellaufnahme |
| `IconButton` | 56 dp | Touch-Icon-Button (min 48) |
| `SoftButtonHeight` | 112 dp | Inspektions-Leiste |
| `NavRailWidth` | 120 dp | linke Navigation |
| `NavItemHeight` | 84 dp | Nav-Eintrag |
| `HeaderHeight` | 64 dp | App-Header |
| Icons | 24/28/32/36 dp | inline/std/toolbar/large |
| `CardPadding` | 16 dp | |
| Spacing | 4/8/12/16/20/24 dp | |
| `TouchMin` | 48 dp | absolutes Minimum |

**Shapes:** small 8 · medium 12 (Inputs) · large 16 (Cards/Dialoge/Buttons) · Pill 999.

---

## 4. Komponenten (wiederverwendbare Composables)

- **DqIcon(key,size,tint)** — Tabler-Outline-Vektor-Drawable über Key (Richtlinie §4.3), Strich 2 px.
- **DqButton** — `Primary` (BG Amber, Text/Icon `OnAmber`), `Secondary` (Border Amber, Text Amber), `Ghost`, `Danger` (`Error`). Höhe 56, Radius 16.
- **DqCard** — BG `BgPanel`, Border 1 dp `BorderSubtle`, Radius 16, Padding 16.
- **DqPill/DqStatusChip** — Höhe 32, Radius 999, BG=Statusfarbe@16 %, Text=Statusfarbe, optional Dot.
- **DqToggle** — 64×36, Track Amber(an)/`BgElevated`(aus), Knob 28.
- **DqNavRail** — Breite 120, BG `BgSidebar`, aktiv = Amber-Pille + Amber Icon/Label.
- **DqDropdownRow/DqSettingRow** — Zeilenhöhe 56.
- **DqHeader** — Höhe 64, Titel `headlineMedium`, rechts Verbindungs-Chip.
- **DqThemeToggle** — Segment Dunkel/Hell in Einstellungen, schreibt `themeMode`.

States: Hover/Pressed +8 %/+12 % Alpha · Focus 2 dp Amber-Outline. Alle Composables müssen in Dark **und** Light korrekt aussehen (nur Tokens, keine festen Farben).

---

## 5. Anwendung je Screen/View (alle)

| View | Anpassung |
|---|---|
| `navigation/NavGraph` | Adaptive **Navigation Rail** (DqNavRail), Items Start/Inspektion/Projekte/Einstellungen, aktiv Amber. |
| `splash/SplashScreen` | BG `BgWindow`, Logo-Slot, Amber-Akzent (Dark+Light). |
| `home/HomeScreen` | Mockup 01: Amber-CTA „Schnellaufnahme", Secondary „Neues Projekt", 3 Stat-DqCards (KPI 40 sp), „Letzte Projekte" mit Status-Pills. |
| `inspection/InspectionScreen` | Mockup 02 (Cinema): Vollbild-Video, Live-OSD, Status-Chips + REC, **Softbutton-Leiste** (112 dp): Foto, Schaden, Aufnahme (Amber, mittig), Sonde, Licht −/+, Meter 0. Back, Hardtasten-Spiegelung. |
| `inspection/DamageDialog`, `NoteDialog`, `ImageAnnotationDialog` | DqCard-Dialoge, Inputs 56, DqButton, Code-Familienfarben; KeyboardHideButton-Regel. |
| `components/InspectionOsd`, `OsdOverlay` (**Live**) | Token-Chips (Licht/Sonde/Meter/REC) auf `OsdBg`. |
| **OSD-Einbrennung** (Recorder/Burn-in-Pfad) | **NEU gestalten:** translucenter Balken `OsdBg`, Text `TextPrimary`/weiß, Station/Meter in Amber, Inter-Schrift; Feldsemantik (Projekt, Station, Datum) unverändert. Burn-in-Farbpalette neu definieren. **Recording-Test am Gerät zwingend** (Lesbarkeit + Bericht/PDF-Overlay weiter korrekt). |
| `projects/ProjectsScreen` | Mockup 03 Master: Such-Row, Projekt-DqCards mit Status-Pill, **Extended-FAB Amber**. |
| `projects/ProjectFormScreen`, `MapPickerDialog` | DqCards/Inputs 56, Amber-Confirm, KeyboardHideButton-Regel. |
| `projectdetail/ProjectDetailScreen` | Mockup 03 Detail: Titel + Status-Pill, Tabs Fotos/Schäden/Videos/Notizen (Amber-Underline), Thumbnail-Grid, „Inspektion fortsetzen"/„PDF-Bericht", „Galerie"-Benennung. |
| `projectdetail/FullscreenImageDialog`, `VideoPlaybackDialog`, `PdfPreviewDialog` | Schwarzes BG, Amber-Controls, große Touch-Buttons. |
| `reports/ReportsScreen` | DqCards, Large-CTA „Bericht generieren" (72 dp). |
| `settings/SettingsScreen` | Mockup 04: DqCards, DqToggle (Kiosk/Hardware-OSD/Auto-Update), DqDropdownRow (Sprache/Meterquelle), **DqThemeToggle Dunkel/Hell**. |
| `settings/UpdateSection`, `components/UpdateDialog`, `UpdateProgressDialog` | Amber „Nach Updates suchen", Fortschritt Amber, Release-Notes in DqCard. |
| `connection/ConnectionScreen` | Status-Chips/Dots, Reconnect als DqButton. |
| `offlinemaps/OfflineMapsScreen` | DqCards/Listen, Download-Progress Amber. |
| `components/Video*Player` | nur Container/Overlay-Chrome auf Tokens; Decoder unverändert. |

---

## 6. Assets & Vorarbeit

- **Inter** Regular/Medium/SemiBold (`.ttf`) → `res/font/` (+ OFL-Lizenz). **Barlow entfernen** (Font + Referenzen).
- **Tabler-Outline-Icons** der Pflicht-Keys (§4.3) als Vektor-Drawables + `DqIcon`-Mapping.
- **Inter auch für die OSD-Einbrennung** verfügbar machen (Font-Datei für den Burn-in-Renderer/FFmpeg-Pfad).
- **Neue String-Keys** lokalisiert (de/en min.): `quick_capture`, `gallery`, `appearance`, `appearance_dark`, `appearance_light`, `light_minus/plus` — keine Hardcodes.
- **Keine** neuen Permissions/Netz-Deps außer Font/Icons.

---

## 7. KRITIS-Check

- **OK** — überwiegend UI/Theme, keine neue Angriffsfläche, kein Netz/Auth/Permission berührt.
- **RELEVANT (Supply Chain, NIS2 #4):** Inter + Tabler neue Assets → SBOM/Abhängigkeitsliste + OFL-Lizenztext beilegen; Barlow-Entfernung dokumentieren.
- **WARNUNG (Rückwärtskompatibilität #3/#6):** OSD-**Einbrennung** wird verändert → betrifft aufgenommenes Beweismaterial/Bericht. Pflicht: Recording-Test am Gerät (Lesbarkeit, korrekte Felder, PDF-Overlay), bevor Welle 1 committet wird. Burn-in-Feldsemantik nicht ändern.
- **OK (Secure SDLC #5):** keine Hardcode-Farben/Strings; neue Texte über LocalizationManager. Light/Dark rein Token-getrieben.

---

## 8. Rollout in Claude Code (Feature-Branch, Wellen)

Branch `feature/sa-design-rollout`. **Fundament zuerst (seriell), dann Screen-Wellen.** Jede Welle: bauen (`gradlew assembleDebug`) → am Gerät `233b4bd2865177ed` prüfen (Dark **und** Light) → gezielt committen (kein `git add -A`).

**Welle 0 — Fundament (allein, blockiert alles):** Color.kt (Dark+Light), Theme.kt (beide Schemata + `themeMode`-Pref + dynamische System-Bars), Type.kt (Inter, Barlow raus), Dimensions/Shape (Touch), Assets (Inter, Tabler), Komponenten DqIcon/DqButton/DqCard/DqPill/DqToggle/DqNavRail/DqHeader/DqThemeToggle. Referenz-View **Settings** (inkl. Theme-Umschalter) umstellen + am Gerät gegen Mockup 04 in Dark+Light prüfen.

**Welle 1 — Kern (allein, Hardware-/Recording-nah):** InspectionScreen + Live-OSD + **OSD-Einbrennung neu** + Softbutton-Leiste + Damage/Note/ImageAnnotation-Dialoge. Geräte-Test inkl. **Aufnahme + Burn-in-Lesbarkeit** + Hardtasten.

**Welle 2 — Navigation + Eingang:** NavGraph (Rail), Home, Projects, ProjectForm, MapPicker.
**Welle 3 — Detail/Galerie:** ProjectDetail + Fullscreen/VideoPlayback/PdfPreview, Reports.
**Welle 4 — Rest:** Settings-Feinschliff, Update*, Connection, OfflineMaps, Splash.

Welle 2–4 nach Welle 0 in getrennten git-worktrees parallel möglich; Welle 1 seriell. Am Ende gesammelt mergen → PR gegen master.

---

## 9. Claude-Code-Prompt — Welle 0 (zuerst)

```
Branch feature/sa-design-rollout vom aktuellen master anlegen.
Setze das DrainQ-SA-Design-Fundament um (Vorgabe: docs/design/drainq-one_SA-Design_Umsetzung_2026-06-04.md, Abschnitte 1–4,6,7):
- theme/Color.kt: Tokens für Dark UND Light (Tabellen Abschnitt 1). Code-Familienfarben + DamageClass behalten.
- theme/Theme.kt: darkColorScheme + lightColorScheme (onPrimary dunkel!), DrainQTheme(darkTheme), themeMode-Pref via DataStore (System/Dunkel/Hell), System-/Navigationsleiste je Theme dynamisch.
- theme/Type.kt: InterFontFamily (400/500/600), Touch-Größen (Abschnitt 2). Barlow-FontFamily + ttf entfernen.
- theme/Dimensions.kt + Shape.kt: Touch-Maße + Radien (Abschnitt 3).
- Assets: Inter Regular/Medium/SemiBold nach res/font (+ OFL-Lizenz), Tabler-Outline-Vektor-Drawables für die Pflicht-Icon-Keys.
- Composables: DqIcon, DqButton, DqCard, DqPill/DqStatusChip, DqToggle, DqNavRail, DqHeader, DqThemeToggle.
Regeln: keine Hardcode-Farben/Strings (Tokens + LocalizationManager), alles in Dark+Light korrekt, KeyboardHideButton-Regel app-weit, Touch min 48 dp. Inter + Tabler in der Abhängigkeitsliste/SBOM vermerken.
Referenz-View Settings auf die neuen Komponenten + Theme-Umschalter umstellen (Mockup docs/design/drainq-one_04_einstellungen_SA-design.svg).
Bauen: gradlew assembleDebug (BUILD SUCCESSFUL Pflicht). Gezielt committen (kein git add -A).
Melde: geänderte Dateien, Build-Ergebnis, was am Gerät in Dark UND Light gegen Mockup 04 zu prüfen ist.
```

Prompts für Welle 1–4 leite ich nach erfolgreichem Welle-0-Build + Geräte-Check ab (Welle 1 inkl. OSD-Einbrennung + Recording-Test).

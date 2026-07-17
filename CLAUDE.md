# DrainQ ONE - Kanalinspektion Tablet-App

## Projektübersicht

Android-Tablet-App als **Slave-Monitor** für das DrainQ Kanalinspektionssystem. Die App zeigt den Live-Videostream der Inspektionskamera an und ermöglicht die Schadensdokumentation (Kategorie/Preset, Uhrzeitposition, Freitext) mit PDF-Report-Erzeugung. **Keine DIN-EN-13508-2-Kodierung / kein XML-Export** (CEO-Entscheid W1-E, 2026-06-07).

### Kernkonzept
- **Slave-Monitor**: Passive Anzeige des Videostreams (keine eigene Aufnahme)
- **Read-Only Hardware-Status**: Anzeige von Kameralicht, Sonde, Meterzähler (keine Steuerung)
- **Schadenserfassung**: Preset + Uhrzeitposition + Freitext (ohne DIN-EN-13508-2-Kodierung)
- **PDF-Reporting**: Professionelle Haltungsberichte direkt auf dem Tablet

---

## Design-Referenz

### DrainQ Design System (identisch mit DrainQ.Windows)
- **Dark Theme**: Dunkler Hintergrund (#0A0A0F)
- **Primary**: DrainQ Teal (#0D7377)
- **Secondary**: DrainQ Deep Blue (#0F3460)
- **Accent Light**: #14BDAC
- **Cards**: #1C1C28
- **Font**: Barlow (Corporate Font)
- **Shapes**: Rounded Corners (nicht CutCorner)
- **Navigation**: Bottom Navigation / Navigation Rail (adaptive)
- **Icons**: Material Design 3

---

## Technologie-Stack

```
Kotlin 1.9+
Jetpack Compose (Material Design 3)
Android SDK 26+ (Android 8.0 Oreo)
Target SDK: 34 (Android 14)
Room Database (SQLite)
MVVM + Clean Architecture
Koin Dependency Injection
libVLC für RTSP Streaming
ExoPlayer/Media3 als Fallback
Eclipse Paho MQTT Client
iText7 für PDF-Generierung
FFmpegKit für Video-Overlay
```

---

## Build-Konfiguration

```
applicationId: com.uip.drainq.one
namespace: com.uip.oneapp (Kotlin package — unverändert)
versionName: 0.2.0
rootProject.name: DrainQ.ONE
```

---

## Farbpalette (DrainQ Design System)

```kotlin
// Primary
val DrainQTeal      = Color(0xFF0D7377)
val DrainQTealDark  = Color(0xFF095457)
val DrainQTealLight = Color(0xFF14BDAC)

// Secondary
val DrainQDeepBlue  = Color(0xFF0F3460)

// Backgrounds
val DarkBackground      = Color(0xFF0A0A0F)
val DarkSurface         = Color(0xFF111118)
val DarkSurfaceVariant  = Color(0xFF1C1C28)

// Status
val StatusGreen  = Color(0xFF4CAF50)
val StatusOrange = Color(0xFFFF9800)
val StatusRed    = Color(0xFFF44336)
val StatusBlue   = Color(0xFF2196F3)
```

---

## Projektstruktur

```
app/src/main/java/com/uip/oneapp/
├── OneApp.kt                           # Application class
├── MainActivity.kt                     # Entry Point
├── data/
│   ├── local/ (Room Database, DAOs, Entities)
│   └── repository/ (Repositories)
├── di/AppModule.kt                     # Koin DI
├── export/ (PDF, XML Export)
├── network/ (Hardware-Anbindung, RTSP, MQTT)
├── ui/
│   ├── theme/ (Color, Theme, Type, Shape)
│   ├── components/ (VideoPlayer, VlcVideoPlayer)
│   ├── localization/ (LocalizationManager — 20+ Sprachen)
│   ├── navigation/ (NavGraph, adaptive Rail/BottomBar)
│   └── screens/ (home, inspection, projects, settings, ...)
└── utils/
```

---

## Tablet-Optimierung

- Landscape-Modus als Standard
- Adaptive Layout: Navigation Rail (>= Medium) / Bottom Bar (Compact)
- Touch-optimierte Buttons (min. 48dp)
- Handschuh-freundliche UI-Elemente

---

**Projekt:** UIP Team - DrainQ ONE
**Version:** 0.3.0
**Rebranding von:** ONE.APP v1.5.4

## Hilfe-System (W-H5, CEO-Entscheid 17.07.2026)

Neuer Screen oder Dialog → Eintrag in `tools/manual/scenes.json` + Baustein in `assets/help/help_de.json` + `help_en.json` + alle `help.*`-Keys in `assets/i18n/de.json` + `en.json`, sonst bricht `HelpCoverageTest` den Build.
UI-Änderung an bestehendem Screen → `.\tools\manual\verify.ps1 -Update` ausführen und geänderte Goldens committen.
Neue Hilfe-Texte ausschließlich über `HELP_UPDATE_PROMPT.md` im Repo-Root (Belegpflicht E6, Opus-Audit je Seite).
Release: `publish-one-release.ps1` läuft automatisch Coverage-Gate + Golden-Diff + Render + PDF (übersprungen nur mit `-SkipDocs` im Notfall).
Wochenjob: `tools/manual/weekly-manual-sync.ps1` synchronisiert Portal-Drift automatisch; bei fehlenden Texten nur Meldung (Exit 2), kein Auto-Build.

## Phase 7: libVLC Ausbau + Cleanup (v0.3.0)

Diese Version entfernt libVLC und konsolidiert den Player-Stack:
- **Player:** ExoPlayer/Media3 (RTSP-TCP, low-latency) ist jetzt der einzige Video-Player
- **OSD:** Immer aktiv via Canvas-Overlay (FfmpegVideoPlayer) — kein Feature-Flag mehr
- **Recording:** Immer via FfmpegRtspRecorder — kein Feature-Flag mehr
- **Hardware-OSD:** Weiterhin über `useHardwareOsd`-Flag schaltbar
- **APK-Größe:** Release von 230 MB → 144 MB (−86 MB durch libvlc-all Entfernung)
- **Gelöscht:** `VlcVideoPlayer.kt`, `libvlc-all:3.6.5` Dependency
- **Feature-Flags entfernt:** `useFfmpegOsdPlayer`, `useFfmpegRecording`

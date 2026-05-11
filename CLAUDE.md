# DrainQ ONE - Kanalinspektion Tablet-App

## Projektübersicht

Android-Tablet-App als **Slave-Monitor** für das DrainQ Kanalinspektionssystem. Die App zeigt den Live-Videostream der Inspektionskamera an und ermöglicht die vollständige Schadensdokumentation nach DIN EN 13508-2 mit PDF-Report-Erzeugung.

### Kernkonzept
- **Slave-Monitor**: Passive Anzeige des Videostreams (keine eigene Aufnahme)
- **Read-Only Hardware-Status**: Anzeige von Kameralicht, Sonde, Meterzähler (keine Steuerung)
- **Vollständige Schadenserfassung**: DIN EN 13508-2 konforme Dokumentation
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
versionName: 0.1.0
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
**Version:** 0.1.0
**Rebranding von:** ONE.APP v1.5.4

# DrainQ.ONE — Handover-Doku für Cowork-Folgesessions

Stand: 2026-05-12. Hat den kompletten Projekt-Kontext um in einer neuen Chat-Session ohne Vorgeschichte direkt weiterarbeiten zu können.

## Projekt in einem Absatz

DrainQ.ONE ist eine Android-Tablet-App (Kotlin/Jetpack Compose) die als **Slave-Monitor** zum NSP3CT/BWELL-Inspektionssystem (Rockchip-Hardware "ONE") läuft. Sie zeigt den Live-RTSP-Stream der Kanalkamera, ermöglicht Schadensdokumentation nach DIN EN 13508-2, generiert PDF-Berichte und steuert das Hardware-OSD remote via JSON über TCP. Verbindung zur ONE: WLAN-Hotspot der ONE (SSID `ONE_01`, ONE-IP `192.168.35.138`), Tablet ist Client `192.168.35.195`. Repo: `C:\Projekte\drainq.one`, GitHub: `ThomasViell/one-app`.

## Hardware-Setup

| Gerät | Rolle | Serial | IP |
|---|---|---|---|
| Samsung Tablet SM-X610 | Tablet-App | `R52Y303GEZH` | `192.168.35.195` |
| BWELL ONE (Rockchip RK3588, Android 12) | Controller | `233b4bd2865177ed` | `192.168.35.138` |
| USB-V4L2-Kamera (an ONE) | Quelle | — | — |
| ONE RTSP-Server | Stream-Endpoint | — | `rtsp://192.168.35.138:8554/1234` |
| ONE Command-Port (DeviceService) | JSON-Steuerung | — | `tcp://192.168.35.138:12345` |
| ONE App-Package | `com.bominwell.minipush` | — | — |

ADB ist auf der ONE rooted (`adb root` läuft durch). APK kann via `pm path com.bominwell.minipush` + `adb pull` gezogen werden — bereits dekompiliert unter `debug-logs/minipush-decompiled/` (nicht in Git, lokal vorhanden).

## Aktueller Code-Stand

- Master-Branch: `master` (default), letzter Commit `18521c8` (Phase 7 + alle nachgelagerten Fixes gemergt)
- Aktiver Feature-Branch: `feature/osd-phase-8` — libVLC zurückholen für niedrige Latenz. Status: läuft gerade in separater Claude-Code-Session. **Vor weiteren Features abwarten ob das durchläuft.**
- Plan-Doc für Phase 8: `PHASE_8_LIBVLC_REVIVAL.md`

## Was funktioniert (alles in `master` bzw. nach Phase 8 dann auch)

- Live-RTSP-Stream mit korrektem Aspect-Ratio (Letterbox via Modifier.aspectRatio)
- Schadens-Erfassung nach DIN EN 13508-2 mit Software-Burn-in
- Aufnahme als fragmented MP4 mit OSD-Overlay (FFmpegKit, libx264 ultrafast zerolatency)
- Hardware-OSD Live-Toggle (BWELL-Schema `{isShowOSD, modeON_OFF, ...}` via DeviceService:12345)
- Adresssuche mit Forward-Geocoding (Nominatim) und Map-Picker mit dynamischem Tile-Loading
- Offline-Maps via MapsForge
- Localization in 35 Sprachen
- Lichtsteuerung, Sonde, Meterzähler-Reset über JSON-Commands
- Dark-Theme (DrainQ Design System), Bottom-Nav / Navigation Rail adaptiv

## Was offen ist / mögliche nächste Features

Diese Liste ist Vorschlag, kein Backlog — der User priorisiert.

- **Hardware-OSD-Auto-Resync** beim Hardware-Reconnect (aktuell muss man toggeln wenn die Kamera rebootet)
- **CLOSE_WAIT-Aufräumen** im DeviceService — Server-seitiger BWELL-Bug, nur über APK-Patch fixbar; oder mit `shutdownOutput()` auf Tablet-Seite minimieren (ist schon drin)
- **Mehr Sprachen vervollständigen** — neue Keys (`search_address`, `pick_on_map`, `hardware_osd`, etc.) sind derzeit nur in DE. Fallback auf DE klappt, aber andere Sprachen würden's gerne native haben
- **Map-Picker Multi-Zoom-Level** — aktuell festes Tile-Zoom 16, Pinch-In/Out skaliert nur Pixel; echtes Zoom-Level-Switching würde Schärfe bringen
- **Datenbank-Export** — bisher nur PDF/XML; ggf. ISYBAU oder DWA-M150-Export
- **Settings-Migration** — `useHardwareOsd`-Toggle in Settings wird derzeit nur als Pref gespeichert, sollte mit dem neuen Live-Toggle in InspectionScreen sync sein
- **Performance auf älteren Tablets** — aktuell auf SM-X610 (Snapdragon ~8 Gen 1) getestet, schwächere Geräte könnten beim FFmpeg-Recording stocken
- **Auto-Reconnect-Backoff** — bei abrupter Trennung versucht die App sofort wieder; exponential backoff wäre robuster
- **CCTV-SDK-Direktanbindung** — derzeit eigenes JSON-Protokoll an DeviceService; das BWELL `CctvSdk-release.aar` würde z.B. PTZ-Steuerung, Zoom, Fokus etc. liefern. Größerer Umbau

## Wichtige Code-Pfade

```
app/src/main/java/com/uip/oneapp/
├── OneApp.kt                            Application class
├── MainActivity.kt                      Entry Point
├── data/                                Room DB (Project, Damage, Note, Pipe, Inspection)
├── di/AppModule.kt                      Koin-DI
├── export/                              PDF, XML, OsdRenderer (Burn-in)
├── network/
│   ├── HardwareService.kt               Interface
│   ├── OneHardwareService.kt            BWELL/MiniPush JSON-Protokoll (TCP 12345)
│   ├── OneHardwareModels.kt             SdkVideoOverlay (echtes BWELL-Schema), SdkSendData
│   ├── TwoHardwareService.kt            Alternativer Provider (ONE TWO)
│   ├── FfmpegRtspRecorder.kt            MP4-Aufnahme mit OSD-Burn-in
│   ├── NominatimService.kt              Forward/Reverse-Geocoding
│   └── OsmStaticMapService.kt           Online-OSM-Tiles + Offline-Fallback
├── maps/                                MapsForge-Renderer + Offline-Catalog
├── ui/
│   ├── components/
│   │   ├── FfmpegVideoPlayer.kt         ExoPlayer + OsdOverlay (wird durch Phase 8 ersetzt)
│   │   ├── VlcLowLatencyPlayer.kt       (Phase 8 — libVLC)
│   │   └── OsdOverlay.kt                Compose-Canvas-Overlay
│   ├── localization/LocalizationManager.kt
│   ├── navigation/NavGraph.kt
│   └── screens/
│       ├── inspection/InspectionScreen.kt   Live-View, Hardware-Status, Schadens-Erfassung
│       ├── projects/ProjectFormScreen.kt    Adresssuche + Map-Picker
│       ├── projects/MapPickerDialog.kt      Fullscreen-Karte mit Tap-to-Mark
│       └── settings/SettingsScreen.kt
└── utils/
```

## Build & Install

```powershell
cd C:\Projekte\drainq.one
$env:JAVA_HOME = "C:\Android\jdk17"
$env:ANDROID_SERIAL = "R52Y303GEZH"
.\gradlew installDebug
```

JAVA_HOME muss auf JDK 17 zeigen (nicht auf die JADX-Bundled-JRE — die kann kein `java.management`). Gradle 8.7, Compose-Compiler 1.5.10, Kotlin 1.9, AGP 8.4.0, target SDK 34, min SDK 26.

## Debug-Workflow

Tools in `tools/` (committed):

| Skript | Zweck |
|---|---|
| `tablet-watcher.ps1` | Hintergrund-Watcher. Schreibt Trigger-Datei, holt Tablet-Screenshot, legt als `debug-logs/claude-view/screen.jpg` ab |
| `tablet-snapshot.ps1` | Einmaliger Tablet-Snapshot + Logcat-Tail |
| `tablet-debug.ps1` | Dauer-Session mit scrcpy + Logcat-Streams |
| `one-snapshot.ps1` | Einmaliger Screenshot der ONE-Hardware |

In neuer Chat-Session: User startet einen der Watcher, sagt z.B. „los, schau aufs Tablet", Claude schreibt die Trigger-Datei (`C:\Projekte\drainq.one\.claude-snapshot-trigger`), liest dann `debug-logs/claude-view/screen.jpg`.

**Wichtig:** PowerShell `>` Umleitung produziert UTF-16 — für binäre Streams (Screencap, Logcat) immer `cmd /c "... > file"` verwenden. Logcat-Files mit `Out-File -Encoding UTF8` schreiben.

## BWELL/MiniPush-Wissen (aus Reverse-Engineering)

- **Hardware-OSD-Schema** (POST an `192.168.35.138:12345`):
  ```json
  {
    "videoOverlay": {
      "isShowOSD": true,    // Live-Trigger (BitmapOsdUtil.setShowHeadOsd / setNonRecordOsd)
      "modeON_OFF": 0,      // Pref (0=ON, 1=OFF)
      "colorFont": 0, "colorHig": 0, "sizeFont": 0,
      "pos1": 0, "pos2": 1, "pos3": 2,
      "osdHeadStrArr": [], "osdNormalStrArr": []
    },
    "sendCommand": [-6, -81, ...]  // Crawler-Steuer-Bytes
  }
  ```
- Pref-Datei auf der ONE: `/data/data/com.bominwell.minipush/shared_prefs/BominwellRobot.xml` (Key `SharePreferenceUtilsKEY_Overlay_modeON_off_int`, Mapping 0=ON 1=OFF)
- Pref direkt zu editieren reicht NICHT — App liest aus In-Memory-Cache. Nur JSON via DeviceService triggert Live-Wirkung.
- Original-App-Setup-Screen für OSD findet man auf der ONE physisch unter Setup-Menu → "Video Overlay"

## Convention/Style

- **User-Präferenzen:** kurze Antworten, keine Einleitungen, keine Emojis, Reihenfolge nach Wichtigkeit (kein „vorher solltest Du" / „Du kannst aber auch")
- **Sprache:** Deutsch
- **Code-Kommentare:** prägnant, erklären *warum* nicht *was*. Englisch.
- **Commit-Messages:** Deutsch erlaubt, Konvention `feat()`, `fix()`, `tools:` Präfix; Bullet-Liste was geändert wurde
- **Branch-Naming:** `feature/osd-phase-N` für inkrementelle Architekturphasen

## Memory in diesem Cowork-Profil

Existierende User-Memory unter `memory/MEMORY.md`:
- `autorun_phasenplan_pattern.md` — Skill für unbeaufsichtigte mehrphasige Claude-Läufe (Templates + OSD-Live-Burn-In als Originalbeispiel)

## Letzte signifikante Commits

```
18521c8 tools: ADB-Snapshot- und Logcat-Watcher fuer Tablet/ONE
8da3b98 feat(hardware): Live-Toggle Hardware-OSD + Aspect-Ratio + Latenz-Fix
015112e feat(hardware-osd): Echtes BWELL-Protokoll fuer Live-Toggle
5abe777 feat(project-form): Adresssuche + interaktiver Map-Picker
099f3d7 feat(phase7): libVLC ausbauen, Feature-Flags entfernen, v0.3.0
```

## Wenn neue Chat-Session: erstes Vorgehen

1. Diese Datei lesen
2. `git log --oneline -20` für aktuellen Stand
3. `git status` und `git branch --show-current` für Working-State
4. Wenn Tablet-Snapshot nötig: `tools/tablet-watcher.ps1` starten lassen
5. Wenn ONE-Inspection nötig: `adb -s 233b4bd2865177ed shell ...`, vorher ggf. `adb root`

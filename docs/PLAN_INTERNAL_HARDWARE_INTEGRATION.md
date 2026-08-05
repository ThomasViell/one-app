# Plan: Integration der internen ONE-Hardware-Anbindung in DrainQ.ONE

**Stand:** 2026-05-19
**Status:** Plan, noch nicht implementiert
**Vorlage:** Smoke-Test-App unter `C:\Projekte\one-smoketest\` (auf ONE-Hardware verifiziert)

---

## 1. Zielbild

Heute läuft DrainQ.ONE als **Slave-Monitor auf einem zweiten Tablet** (Samsung SM-X610) und kommuniziert per WLAN mit der ONE-Hardware (`192.168.35.138`):
- Videostream: RTSP über `:8554/1234`, gerendert mit ExoPlayer/Media3
- Steuerung: JSON-Pakete über TCP `:12345` zum `DeviceService` der Bominwell-App

Künftig soll DrainQ.ONE **direkt auf dem ONE-Tablet** laufen und die Hardware lokal ansprechen:
- Videostream: V4L2 direkt über `/dev/video0` (MACROSILICON MS2109 Capture-Chip, `uvcvideo`-Kernel-Treiber)
- Steuerung: Serielle Schnittstelle direkt über `/dev/ttyS5` @ 9600 baud

Die Bominwell-App `com.bominwell.minipush` wird damit obsolet und kann auf den ONE-Tablets deinstalliert werden. Das zweite Tablet entfällt im Pilot-Setup.

**Validiert durch den Smoke-Test:** Frame-Format, Sonde-Steuerung, Lichtsteuerung, Meterzähler und Live-Video funktionieren über die Direkt-Anbindung produktiv auf der ONE-Hardware (Serial-Pfad: 8 Bytes pro Frame mit XOR-Checksum, MJPEG-Stream bei 1280×720).

---

## 2. Was wir wissen — Smoke-Test-Ergebnisse

Aus `C:\Projekte\one-smoketest\` ist erfolgreich verifiziert:

| Hardware-Aspekt | Pfad | Status | Code-Quelle |
|---|---|---|---|
| Sonde an/aus | Serial Byte +2 in Base-Frame | ✅ | `OneFrameCodec.baseCommand(power=...)` |
| Sonde-Frequenz (512 Hz / 640 Hz / 33 kHz) | Serial Byte +3 in Base-Frame | ✅ | `SondeMode`-Enum, frequency-Byte |
| Lichtintensität 0–200 | Serial Byte +4 in Base-Frame | ✅ | `setLight(int 0..255)`, Hardware sättigt bei 200 |
| Meterzähler | Empfangs-Group `22` (32-bit BE in mm) | ✅ | `foldFrames` → `distanceMeters` |
| Spannungs-Telemetrie | Empfangs-Group `23` (32-bit BE in mV) | ✅ | `foldFrames` → `voltage` |
| Firmware-Version | Empfangs-Group `24` (3-Byte semver) | ✅ | `foldFrames` → `firmwareVersion` |
| Status-Echo (Power/Light/Freq) | Empfangs-Group `21` | ✅ | `foldFrames` → Status-Felder |
| Meterzähler-Reset | Client-side Offset, `distanceOffsetMeters` | ✅ | `OneController.resetMeter()` |
| Live-Video MJPEG 1280×720 | V4L2 `/dev/video0`, JNI-Bridge | ✅ | `V4L2Camera.kt` + `v4l2bridge.c` |

Wichtige technische Voraussetzung: Die App benötigt **Read/Write-Zugriff auf `/dev/ttyS5` und `/dev/video0`** ohne sich auf die `MANAGE_USB`-Permission verlassen zu können (Privileg ist `signature|privileged` und nur für System-Apps mit Plattform-Cert verfügbar). Das löst der Smoke-Test heute per `adb shell chmod 666` — für die produktive Auslieferung brauchen wir eine dauerhafte Lösung (siehe Abschnitt 7).

---

## 3. Aktuelle DrainQ.ONE-Architektur

```
                     ┌──────────────────────────────┐
                     │  InspectionScreen            │
                     │  - liest hardwareService.    │
                     │    lastRtspUrl               │
                     │  - rendert FfmpegVideoPlayer │
                     └─────────────┬────────────────┘
                                   │
                                   ▼
        ┌──────────────────────────────────────────────┐
        │  HardwareService (Interface)                 │
        │  - probeEndpoints, startPolling, ...         │
        │  - sendLightPower, sendFrequency, ...        │
        │  - var lastRtspUrl: String                   │
        └──────┬──────────────────────────┬────────────┘
               │                          │
   when(DeviceType.ONE)        when(DeviceType.TWO)
               │                          │
               ▼                          ▼
   ┌──────────────────────┐     ┌──────────────────────┐
   │ OneHardwareService   │     │ TwoHardwareService   │
   │  TCP :12345 (JSON)   │     │  Hikvision RTSP+CGI  │
   │  UDP :8555 Discovery │     │                      │
   │  Frame-Codec mit     │     │                      │
   │  Magic FA AF...      │     │                      │
   └──────────────────────┘     └──────────────────────┘
```

Auswahl per `DeviceType`-Enum in `AppModule.kt`, persistiert in `settings_data`-DataStore.

Video-Player: `FfmpegVideoPlayer(rtspUrl: String)` mit ExoPlayer/Media3 (`RtspMediaSource`). Pfad ist immer URL-basiert; es gibt aktuell keinen Bitmap-Stream-Pfad.

---

## 4. Zielarchitektur

```
                     ┌──────────────────────────────┐
                     │  InspectionScreen            │
                     │  - liest hardwareService.    │
                     │    videoSource               │
                     │  - rendert VideoView(source) │
                     └─────────────┬────────────────┘
                                   │
                                   ▼
        ┌──────────────────────────────────────────────┐
        │  HardwareService (Interface)                 │
        │  - probeEndpoints, startPolling, ...         │
        │  - sendLightPower, sendFrequency, ...        │
        │  - val videoSource: StateFlow<VideoSource>   │
        └──────┬─────────────────┬────────────┬───────┘
               │                 │            │
       ONE_REMOTE         ONE_LOCAL          TWO
               │                 │            │
               ▼                 ▼            ▼
   ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐
   │ OneHardware-    │  │ OneInternal-    │  │ TwoHardware- │
   │ Service         │  │ HardwareService │  │ Service      │
   │ TCP :12345      │  │ Serial /ttyS5   │  │ Hikvision    │
   │ → VideoSource.  │  │ V4L2 /video0    │  │ RTSP         │
   │   Rtsp(url)     │  │ → VideoSource.  │  │              │
   │                 │  │   LocalBitmap   │  │              │
   └─────────────────┘  └─────────────────┘  └──────────────┘
```

**Neuer Sealed-Type:**

```kotlin
sealed class VideoSource {
    object None : VideoSource()
    data class Rtsp(val url: String) : VideoSource()
    data class LocalBitmap(val flow: StateFlow<Bitmap?>) : VideoSource()
}
```

**Neuer Composable** `VideoView(source: VideoSource, …)` dispatcht intern:
- `Rtsp(url)` → `FfmpegVideoPlayer(rtspUrl = url, …)` (unverändert)
- `LocalBitmap(flow)` → `Image(bitmap = flow.collectAsState().value?.asImageBitmap(), …)`
- `None` → `VideoPlayerPlaceholder`

---

## 5. Konkrete Änderungen pro File

### 5.1 Hardware-Layer (neu / geändert)

| Datei | Aktion | Inhalt |
|---|---|---|
| `network/HardwareService.kt` | **erweitern** | `val videoSource: StateFlow<VideoSource>` hinzufügen; `var lastRtspUrl` als Convenience auf videoSource mappen (Backward-Compat) |
| `network/VideoSource.kt` | **neu** | Sealed Class wie oben |
| `network/DeviceType.kt` | **erweitern** | Enum-Wert `ONE_LOCAL` hinzufügen |
| `network/OneHardwareService.kt` | **kleine Anpassung** | `videoSource: MutableStateFlow<VideoSource>` Property, bei URL-Updates `Rtsp(url)` published |
| `network/TwoHardwareService.kt` | **kleine Anpassung** | wie oben |
| `network/internal/OneInternalHardwareService.kt` | **neu** | Komplette neue Implementation. Übernimmt Logik aus `OneController.kt` + `V4L2Camera.kt` der Smoke-Test-App. Implementiert HardwareService-Interface. |
| `network/internal/OneFrameCodec.kt` | **neu** | 1:1-Kopie aus Smoke-Test |
| `network/internal/V4L2Camera.kt` | **neu** | 1:1-Kopie aus Smoke-Test, Package angepasst |
| `network/internal/LinearMeterCalculator.kt` | **neu** | Aus Smoke-Test; Stützstellen aus `LinearDataPoints.java` (BWELL-RE) später ergänzen |

### 5.2 Native-Code (neu)

| Datei | Aktion | Inhalt |
|---|---|---|
| `app/src/main/cpp/CMakeLists.txt` | **neu** | wie in Smoke-Test |
| `app/src/main/cpp/v4l2bridge.c` | **neu** | 1:1-Kopie aus Smoke-Test, JNI-Package angepasst (`com_uip_oneapp_network_internal_V4L2Camera_…`) |
| `app/build.gradle.kts` | **erweitern** | `externalNativeBuild { cmake { … } }` + `ndk { abiFilters = ["arm64-v8a"] }` |

### 5.3 UI-Schicht (geändert)

| Datei | Aktion | Inhalt |
|---|---|---|
| `ui/components/VideoView.kt` | **neu** | Wrapper-Composable, dispatcht zwischen Rtsp und LocalBitmap |
| `ui/components/LocalBitmapVideoPlayer.kt` | **neu** | Compose-Image-Renderer für `StateFlow<Bitmap?>`, mit OSD-Overlay-Slot (wie im Smoke-Test) |
| `ui/screens/inspection/InspectionScreen.kt` | **anpassen** | `rtspUrl`-Logik durch `videoSource`-Beobachtung ersetzen, an drei Stellen (Main-View, Full-Screen-Dialog, Recorder-Pfad) |
| `ui/components/FfmpegVideoPlayer.kt` | **unverändert** | bleibt für RTSP-Pfad |
| `ui/screens/settings/SettingsScreen.kt` | **erweitern** | `DeviceType.ONE_LOCAL`-Option im Geräte-Dropdown |

### 5.4 DI / App-Lifecycle (geändert)

| Datei | Aktion | Inhalt |
|---|---|---|
| `di/AppModule.kt` | **erweitern** | `when (deviceType)` um `ONE_LOCAL → OneInternalHardwareService()`-Branch |
| `OneApp.kt` | **kleine Anpassung** | Optional: Auto-Detect beim ersten Start. Wenn `/dev/video0` und `/dev/ttyS5` existieren UND `device_type`-Preference noch leer ist → automatisch `ONE_LOCAL` setzen |

### 5.5 Manifest und Permissions

| Datei | Aktion | Inhalt |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | **prüfen** | Aktuell sind keine speziellen USB-Permissions nötig (FileInputStream-Pfad), aber `INTERNET` bleibt für RTSP-Modus erhalten. Keine Änderung erwartet. |

---

## 6. Migrationsstrategie

Drei Optionen, Empfehlung **C (Hybrid mit Auto-Detect)**:

**A — Hard-Switch:** `ONE_REMOTE` (alte WLAN-Variante) komplett raus, `ONE_LOCAL` ersetzt es. Samsung-Tablet wird ausgemustert. Risiko: kein Rollback-Pfad bei Hardware-Pannen.

**B — Settings-only:** Beide Modes bleiben, User wählt manuell in Settings. Pflegeaufwand: zwei Pfade dauerhaft warten. Migrations-User-Experience suboptimal.

**C — Auto-Detect mit Override (empfohlen):** Beim ersten App-Start prüft `OneApp.onCreate()` ob `/dev/video0 && /dev/ttyS5` existieren. Wenn ja → Default ist `ONE_LOCAL`. Sonst → Default ist `ONE_REMOTE` (alte WLAN-Variante). User kann in Settings überschreiben. Migration ist 0-Click: ONE-Tablets aktualisieren, App erkennt den lokalen Hardware-Zugriff. Samsung-Tablets bleiben unverändert.

Beide Pfade bleiben damit produktiv. Nach erfolgreicher Pilotphase kann `ONE_REMOTE` ggf. deprecaiert werden.

---

## 7. Permission-Strategie für `/dev/ttyS5` und `/dev/video0`

Der einzige Punkt, der im Smoke-Test einen manuellen ADB-Schritt brauchte. Drei realistische Lösungen für die Produktion:

### 7.1 Plattform-Cert von Bominwell anfordern + System-App-Installation

Sauberste Lösung. App bekommt `MANAGE_USB`, kann USB-Devices ohne Dialog öffnen, läuft direkt aus `/system/priv-app/`. Setzt politische Klärung mit Bominwell voraus (Cert herausgeben oder Build von Bominwell signieren lassen).

### 7.2 Boot-Init-Skript

Rooted Hardware. Wir liefern eine `init.onesmoketest.rc` aus, die beim Tablet-Boot `chmod 666` auf die zwei Device-Files macht. Einmal pro Tablet einzurichten (z. B. über ein Magisk-Modul oder beim Image-Build). DrainQ.ONE selbst läuft ohne System-Privilegien.

### 7.3 Root-Helper über su-Befehl

Beim App-Start ruft DrainQ.ONE via `Runtime.exec("su -c chmod 666 /dev/...")` den nötigen chmod auf. Setzt voraus, dass das Tablet ein installiertes `su`-Binary hat (Magisk-Style). Bei diesen Industrie-Tablets häufig der Fall. Vorteil: kein Tablet-Setup-Schritt, alles in der App.

**Empfehlung:** **7.3 als Pilot-Lösung** (sofort einsetzbar), **7.1 als langfristiger Pfad** (sobald Cert-Klärung erfolgt).

**Entscheidung 2026-05-19 (Thomas):** Migrationsstrategie A (Hard-Switch, WLAN-Pfad komplett raus). Permission-Strategie: Pilot mit 7.3, parallel Bominwell-Verhandlung für 7.1 starten. Phase P1 startet sofort.

---

## 8. Aufwand und Phasen

| Phase | Komponente | Aufwand |
|---|---|---|
| P1 | `VideoSource`-Sealed-Class + Interface-Erweiterung | 0,5 PT |
| P2 | NDK + CMake-Setup im drainq.one-Repo | 0,5 PT |
| P3 | `OneInternalHardwareService` (Code aus Smoke-Test portieren) | 1,0 PT |
| P4 | `VideoView`-Wrapper + `LocalBitmapVideoPlayer` | 1,0 PT |
| P5 | `InspectionScreen` an `videoSource` umstellen (drei Aufrufstellen) | 1,0 PT |
| P6 | `DeviceType.ONE_LOCAL` + `AppModule.kt`-DI-Branch | 0,5 PT |
| P7 | Auto-Detect im `OneApp.onCreate` | 0,5 PT |
| P8 | Permission-Strategie umsetzen (Variante 7.3) + integration test | 1,0 PT |
| P9 | Smoke-Test gegen produktiv-Build auf ONE-Hardware | 1,0 PT |
| **Summe** | | **~7 Personentage = 1–1,5 Wochen** |

Wesentlich kleiner als die ursprünglich genannten 2–4 Monate, weil die heutigen RE-Erkenntnisse + Smoke-Test-Code direkt portierbar sind.

---

## 9. Risiken und offene Punkte

| Risiko | Wahrscheinlichkeit | Auswirkung | Gegenmaßnahme |
|---|---|---|---|
| ExoPlayer-spezifischer Code in `InspectionScreen` reagiert nicht sauber auf Bitmap-Stream | mittel | Refactor nötig | UI-Tests pro Pfad, ggf. zwei getrennte InspectionScreen-Varianten |
| Bitmap-Decode wird auf RK3588 zum Bottleneck (>30 fps) | gering | Frame-Drops sichtbar | Hardware-MJPEG-Decoder via MediaCodec evaluieren, oder Resolution auf 1280×720 begrenzen |
| Phase-8-Branch (libVLC-Revival) kollidiert mit Player-Wrapper | mittel | Merge-Konflikt | Phase 8 erst abschließen lassen, dann hier rebasen |
| `LinearDataPoints` (Meterzähler-Linearisierung) noch nicht portiert | gering | Meterzähler-Genauigkeit | aus BWELL-RE in einer Folge-PR ergänzen — für Smoke-Test reicht Identitäts-Stub |
| Permission-Strategie 7.3 funktioniert nicht auf allen ONE-Tablet-Generationen | mittel | Tablets nicht produktiv | Plattform-Cert (7.1) als Fallback parat halten |
| `OneHardwareService`-API-Erweiterung bricht `TwoHardwareService` | gering | Build-Fehler | mit Default-Implementierung im Interface arbeiten |

---

## 10. Nächste Schritte

1. **Architektur-Entscheidung sign-off:** Auto-Detect-Variante (Abschnitt 6, Variante C) bestätigen.
2. **Permission-Strategie sign-off:** Pilot mit Variante 7.3 (su-Helper) — ja/nein.
3. **Phase-8-Status klären** und mit dem aktuellen Phase-8-Stand synchronisieren (libVLC-Branch).
4. **Feature-Branch im drainq.one-Repo:** `feature/one-local-direct`.
5. **Phase P1 starten:** `VideoSource`-Sealed-Class + Interface-Erweiterung — kleinster Schritt, bricht nichts und ist sofort testbar.

---

## 11. Verweis auf Quellen

- Smoke-Test-Repository: `C:\Projekte\one-smoketest\` (vollständig validiert)
- Reverse-Engineering der Bominwell-APK: `C:\Projekte\one-revers\analyse\01_architektur_phase1.md` und `02_serial_protokoll_minipush.md`
- DrainQ.ONE-Projektstand: `C:\Projekte\drainq.one\PROJECT_STATUS.md`
- Aktueller HardwareService-Interface: `app/src/main/java/com/uip/oneapp/network/HardwareService.kt`

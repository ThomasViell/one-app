# ONE.APP - NSP3CT Slave Monitor

## Projektübersicht

Android-Tablet-App als **Slave-Monitor** für das NSP3CT Kanalinspektionssystem. Die App zeigt den Live-Videostream der Inspektionskamera an und ermöglicht die vollständige Schadensdokumentation nach DIN EN 13508-2 mit PDF-Report-Erzeugung.

### Kernkonzept
- **Slave-Monitor**: Passive Anzeige des Videostreams (keine eigene Aufnahme)
- **Read-Only Hardware-Status**: Anzeige von Kameralicht, Sonde, Meterzähler (keine Steuerung)
- **Vollständige Schadenserfassung**: DIN EN 13508-2 konforme Dokumentation
- **PDF-Reporting**: Professionelle Haltungsberichte direkt auf dem Tablet

---

## Design-Referenz

### ONE App / WinCan SchachtApp Style
Das UI-Design basiert auf der **WinCan SchachtApp** mit folgenden Merkmalen:
- **Dark Theme**: Dunkler Hintergrund (#1A1A2E)
- **Akzentfarbe**: Rot/Orange für wichtige Elemente (WinCan-Rot: #E63946)
- **Projektlisten**: Cards mit Status-Indikatoren und Progress-Bars
- **Navigation**: Bottom Navigation oder Side Drawer
- **Icons**: Moderne, flache Icons im Material Design 3 Stil

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
ExoPlayer/Media3 für RTSP Streaming
Eclipse Paho MQTT Client
iText7 für PDF-Generierung
```

---

## Projektstruktur

```
app/src/main/java/com/uip/oneapp/
├── OneApp.kt                           # Application class
├── MainActivity.kt                     # Entry Point
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt              # Room Database
│   │   ├── dao/
│   │   │   ├── ProjectDao.kt
│   │   │   ├── PipeDao.kt
│   │   │   ├── InspectionDao.kt
│   │   │   └── DamageDao.kt
│   │   └── entity/
│   │       ├── ProjectEntity.kt
│   │       ├── PipeEntity.kt
│   │       ├── InspectionEntity.kt
│   │       └── DamageEntity.kt
│   ├── remote/
│   │   └── Nsp3ctMqttClient.kt
│   └── repository/
│       ├── ProjectRepository.kt
│       └── InspectionRepository.kt
│
├── domain/
│   ├── model/
│   │   ├── Project.kt
│   │   ├── Pipe.kt
│   │   ├── Damage.kt
│   │   └── DamageCatalog.kt
│   └── usecase/
│       ├── GetProjectsUseCase.kt
│       ├── SaveDamageUseCase.kt
│       └── GenerateReportUseCase.kt
│
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── components/
│   │   ├── VideoPlayer.kt
│   │   ├── DamageCodeSelector.kt
│   │   ├── MeterDisplay.kt
│   │   └── StatusIndicator.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   └── screens/
│       ├── home/
│       ├── projects/
│       ├── inspection/
│       ├── pipes/
│       ├── reports/
│       └── settings/
│
└── di/
    └── AppModule.kt                    # Koin DI Module
```

---

## Datenbank-Schema (Room)

### Haupt-Entities

**ProjectEntity** - Projekte/Aufträge
- id, name, description, clientName, contractorName, projectNumber, status, timestamps

**PipeEntity** - Haltungen (Kanalabschnitte)
- id, projectId, designation, startNode, endNode, streetName, material, profile, nominalWidth, length

**InspectionEntity** - Inspektionen
- id, pipeId, inspectionDate, inspectorName, weatherCondition, inspectionDirection, videoFileName

**DamageEntity** - Schäden nach DIN EN 13508-2
- id, inspectionId, position, mainCode, characterization1/2, quantification1/2, clockPosition, photoFileName

---

## App-Screens

### 1. Home/Dashboard
- Verbindungsstatus NSP3CT
- Aktuelle Projekte
- Schnellzugriff letzte Inspektion

### 2. Projekte
- Projektliste mit Status
- Projekt erstellen/bearbeiten
- Haltungsliste pro Projekt

### 3. Inspektion (Hauptarbeitsbereich)
```
┌─────────────────────────────────────────────────────────────────┐
│ [←] Haltung: KS001-KS002  │  DN 300  │  PVC  │  ⚙️             │
├─────────────────────────────────────────────────────────────────┤
│                                        │  Hardware Status      │
│         LIVE VIDEO                     │  💡 Licht: AN (80%)   │
│         (RTSP Stream)                  │  📡 Sonde: AUS        │
│                                        │  📏 Meter: 12.45 m    │
│         [Text Overlay:]                │                       │
│         12.45m | 09:23:15              │  Letzte Schäden:      │
│                                        │  • 10.2m BAB-A        │
├─────────────────────────────────────────────────────────────────┤
│  [📷 Foto] [⚠️ Schaden] [📝 Notiz]  │  Position: [12.45] m   │
└─────────────────────────────────────────────────────────────────┘
```

### 4. Schadenserfassung Dialog
- Hauptkode Auswahl (BAA, BAB, BBA, etc.)
- Charakterisierungen 1 & 2
- Quantifizierungen
- Uhrzeigerstellung (01-12)
- Foto-Anhang
- Bemerkungen

### 5. Berichte
- PDF-Report Generierung
- Vorschau
- Export/Teilen

### 6. Einstellungen
- NSP3CT Verbindung (IP, Port)
- Firmenstammdaten
- Schadenskatalog

---

## NSP3CT Kommunikation (Vorbereitet)

### Schnittstelle (noch nicht festgelegt)
```kotlin
interface Nsp3ctConnection {
    val connectionState: StateFlow<ConnectionState>
    val lightStatus: StateFlow<LightStatus>      // Read-Only
    val probeStatus: StateFlow<ProbeStatus>      // Read-Only
    val meterReading: StateFlow<Float>           // Read-Only
    val rtspStreamUrl: StateFlow<String?>
    
    suspend fun connect(config: Nsp3ctConfig): Result<Unit>
    suspend fun disconnect()
}
```

### Mock-Daten für Entwicklung
```kotlin
object MockNsp3ctData {
    val meterReading = MutableStateFlow(12.45f)
    val lightStatus = MutableStateFlow(LightStatus(isOn = true, brightness = 80))
    val probeStatus = MutableStateFlow(ProbeStatus(isActive = false))
    const val TEST_RTSP_URL = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4"
}
```

---

## Dark Theme Farbpalette

```kotlin
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val DarkSurfaceVariant = Color(0xFF1F2B47)
val DarkPrimary = Color(0xFFE63946)        // WinCan Rot
val DarkSecondary = Color(0xFF4CC9F0)      // Akzent Blau
val DarkOnBackground = Color(0xFFE8E8E8)
val DarkOnSurface = Color(0xFFBDBDBD)

val StatusGreen = Color(0xFF4CAF50)
val StatusYellow = Color(0xFFFFEB3B)
val StatusRed = Color(0xFFE63946)
```

---

## Entwicklungs-Prioritäten

1. **Phase 1**: Grundstruktur
   - App-Skeleton mit Navigation
   - Dark Theme
   - Room Database Schema

2. **Phase 2**: Datenverwaltung
   - Projekt CRUD
   - Haltungs CRUD
   - Schadenserfassung

3. **Phase 3**: Inspektion UI
   - Video-Player (mit Test-Stream)
   - Hardware-Status Anzeige
   - Schadenscode-Dialog

4. **Phase 4**: Reporting
   - PDF-Generierung
   - Export-Funktionen

5. **Phase 5**: Hardware-Integration
   - NSP3CT Anbindung
   - Echtes RTSP-Streaming

---

## Tablet-Optimierung

- Landscape-Modus als Standard
- Mindestens 10" Display Support
- Touch-optimierte Buttons (min. 48dp)
- Handschuh-freundliche UI-Elemente

---

## Referenz-Dokumentation

- DIN EN 13508-2:2011 - Zustandserfassung Entwässerungssysteme
- DWA-M 149-2 - Zustandsklassifizierung
- WinCan Enterprise Guide
- bluemetric INSPECTOR Funktionsbeschreibung

---

**Projekt:** UIP Team - ONE.APP Slave Monitor  
**Version:** 1.0.0  
**Erstellt:** Februar 2026

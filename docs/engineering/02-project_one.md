# Disziplin: Entwurf

## 1. Projektinformationen

| Feld              | Wert                                                                 |
| ----------------- | --------------------------------------------------------------------- |
| Name              | DrainQ.ONE                                                             |
| System            | one                                                                    |
| Version           | 0.5.x-beta (Analysestand lt. 01-analysis: 0.5.14/514, 2026-07-13). [AI-draft] Der Gradle-Fallback in `app/build.gradle.kts` (ohne CI-ENV) liefert `versionCode=401`/`versionName="0.4.1"` — die tatsächlich ausgelieferte Version wird über `APP_VERSION_CODE`/`APP_VERSION_NAME` im Build überschrieben und ist aus dem Repo-Stand allein nicht abschließend verifizierbar. |
| Autor             | Thomas Viell (CEO) — Entwurf AI (Sonnet-Bauer), rückwärts rekonstruiert aus Code |
| Datum             | 2026-07-14                                                             |
| Dokumentvorlage   | [[02-project_template]]                                                |
| Namenskonvention  | [[naming-convention_one]]                                              |
| Projektkatalog    | `C:\Projekte\drainq.one\`                                              |
| Entwurfskatalog   | `docs\engineering\` (Abweichung von der Regelwerks-Struktur, siehe [[naming-convention_one]]) |
| Freigegeben am    | — (offen)                                                              |

**Verknüpfte Dokumente:**

| Feld    | Wert                          |
| ------- | ----------------------------- |
| Analyse | [[01-analysis_one]]           |

---

> [!info] Zusammenarbeit mit AI und Methodik
> Dieses Dokument ist eine **Rückwärts-Rekonstruktion**: Es beschreibt den technischen Ist-Zustand,
> wie er sich aus dem Code (`app/src/main/java/com/uip/oneapp/...`, `app/build.gradle.kts`,
> `AndroidManifest.xml`, `app/src/test/...`) und aus §9 der Analyse [[01-analysis_one]] ableiten
> lässt. Es wurden **keine neuen Architekturentscheidungen getroffen** — jede Aussage ist entweder
> im Code belegt oder ausdrücklich mit `[AI-draft]` als unverifizierten Vorschlag markiert (z. B.
> IDE-Wahl, nicht aus dem Repo ableitbare Prozessentscheidungen). Jede Entscheidungszeile ist vom
> Menschen (CEO) zu verifizieren.

---

## 2. Entwicklungsumgebung (Ist-Zustand)

Die App ist ein natives Android-Projekt mit Gradle-Kotlin-DSL-Build, Jetpack Compose als alleiniger
UI-Technologie und Koin als Dependency-Injection-Rahmen. Ein Teil der Kamera-/Meterzähler-Anbindung
läuft nativ über eine eigene NDK-Bridge (`v4l2bridge.c`) direkt auf `/dev/video0`, weil die App auf
derselben RK3588-Hardware läuft, die auch die Kamera bedient (kein separates Steuergerät).

| Bereich                          | Entscheidung (Ist-Zustand, Code-belegt) | Begründung |
| --------------------------------- | ---------------------------------------- | ---------- |
| IDE                               | [AI-draft] Android Studio (naheliegend für Gradle-KTS/KSP-Android-Projekt; im Code nicht belegbar) | Standardwerkzeug für Android/Compose-Projekte dieser Art |
| Sprache und Version               | Kotlin, JVM-Target 17 (`kotlinOptions.jvmTarget="17"`, `compileOptions` VERSION_17). Die konkrete Kotlin-Plugin-Version steht vermutlich im Root-`build.gradle.kts` (nicht gelesen). [AI-draft: Kotlin-Versionsnummer] | Aktuelle Kotlin/Android-Toolchain |
| Framework / SDK und Version       | Jetpack Compose (compose-bom 2024.09.03, Compiler-Extension 1.5.14, Material3); Android SDK: `compileSdk=35`, `minSdk=26`, `targetSdk=34` | Belegt in `app/build.gradle.kts` |
| Compiler / Runtime                | JDK 17 (`sourceCompatibility`/`targetCompatibility` VERSION_17); Android NDK via CMake 3.22.1 (`externalNativeBuild`, `src/main/cpp/CMakeLists.txt` + `v4l2bridge.c`) für den direkten `/dev/video0`-Zugriff im DIRECT-Modus | Belegt in `app/build.gradle.kts` |
| Build-System                      | Gradle mit Kotlin-DSL (`build.gradle.kts`), Plugins: `com.android.application`, `org.jetbrains.kotlin.android`, `com.google.devtools.ksp` (Room-Codegen), `kotlin.plugin.serialization` | Belegt in `app/build.gradle.kts` |
| Zielplattform(en)                 | Android 8.0+ (API 26) auf Rockchip RK3588 (embedded Kiosk-Gerät „ONE"), ABI-Filter `arm64-v8a`/`armeabi-v7a` | CON-01/REN-08 der Analyse; `ndk.abiFilters` im Gradle-Skript |
| Externe Bibliotheken              | Room 2.6.1 · Koin 3.5.3 (`koin-android`, `koin-androidx-compose`) · Media3/ExoPlayer 1.5.1 (`media3-exoplayer`, `media3-exoplayer-rtsp`, `media3-ui`) · FFmpegKit `ffmpeg-kit-full-gpl:2.1.0` · iText7 `itext7-core:7.2.5` · OkHttp 4.12.0 · Gson 2.10.1 · ZXing (`core:3.5.3`, `zxing-android-embedded:4.3.0`) · Coil 2.5.0 (+ `coil-svg`) · DataStore Preferences 1.0.0 · `androidx.security:security-crypto:1.1.0-alpha06` · WorkManager 2.9.0 · Mapsforge 0.21.0 (`mapsforge-map-android`, `mapsforge-themes`) · kotlinx-coroutines 1.7.3 · kotlinx-serialization-json 1.6.2 · Play-Services-Location 21.1.0 · Paho-MQTT (mqttv3 1.2.5, android.service 1.1.1) — deklariert, ungenutzt [AI-draft] | Vollständig aus `dependencies { ... }` in `app/build.gradle.kts` |
| Unit-Test-Framework                | JUnit4 4.13.2 + Robolectric 4.11.1 (`androidx.test:core-ktx`) für JVM-Unit-Tests ohne Gerät; OkHttp `mockwebserver:4.12.0` für HTTP-Mocking (Update/Wetter/Nominatim); `kotlinx-coroutines-test` für Coroutine-Tests. Instrumentierungstests (`androidTest`): `androidx.test.ext:junit`, Espresso `3.5.1`, `androidx.room:room-testing:2.6.1` | Belegt in `dependencies{}` und Verzeichnis `app/src/test/...` |
| Hardware für Integrationstests     | [AI-draft] Nicht als Katalog im Repo dokumentiert. Aus Code-Kommentaren („Louis"-Feedback, „On-Device-Befund 0.4.1, RK3588") geht hervor, dass reale ONE-Feldgeräte für manuelle Verifikation genutzt werden — kein automatisiertes Integrationstest-Setup gefunden. | Nur indirekt aus Kommentaren erschließbar |
| Hardware für Hardware-Verifikation | [AI-draft] nicht dokumentiert/nicht im Repo ersichtlich | — |

---

## 3. Architektur

**Muster:** MVVM mit klarer Trennung Datenschicht (Room) / Logikschicht (Repositories, Hardware-,
Recorder-, Export- und Update-Services) / Präsentationsschicht (Compose-Screens + ViewModels).
Es gibt keine XML-Layouts — die gesamte UI ist Jetpack Compose. Koin ersetzt Hilt/Dagger als
DI-Rahmen (`di/AppModule.kt`, ein einziges globales Modul `appModule`). Die App unterscheidet
zwei Laufzeit-Betriebsarten, die dieselbe `HardwareService`-Schnittstelle implementieren:
**DIRECT** (App läuft direkt auf der ONE-Hardware, Zugriff über NDK-Bridge/seriell) und **WiFi/Remote**
(App läuft als Tablet-Client, Zugriff über HTTP/Socket-Polling) — die Auswahl trifft
`HardwareModeDetector` zur Laufzeit (siehe `di/AppModule.kt`).

**Schlüsselklassen des Systems:**

- `OneApp` (Application-Klasse) — startet Koin, entscheidet je nach `HardwareMode`, ob
  `OneRemoteServer`/`OneVideoServer` (DIRECT) oder `OneAutoConnector` (WiFi) hochgefahren werden.
- `MainActivity` — Compose-Host, Kiosk-/LockTask-Steuerung, Immersive-Mode, Hardbutton-Dispatch
  über `HardwareKeyBus`.
- `NavGraph` — Compose-Navigation, adaptive Rail-/BottomBar-Umschaltung.
- ViewModels je Screen: `ConnectionViewModel`, `NetworkViewModel`, `PairingViewModel`,
  `SettingsViewModel`, `ProjectFormViewModel`, `ProjectsViewModel`, `ProjectDetailViewModel`,
  `OfflineMapsViewModel`.
- Repositories: `ProjectRepository`, `DamageRepository`, `NoteRepository`, `UpdateEventRepository`,
  `DamagePresetRepository`, `WeatherPresetRepository`.
- `HardwareService` (Interface) mit den Implementierungen `OneInternalHardwareService` (DIRECT,
  V4L2 + seriell `/dev/ttyS5`) und `OneHardwareService` (WiFi/Remote, HTTP-Polling).

| Bereich               | Entscheidung | Begründung |
| --------------------- | ------------ | ---------- |
| Muster                | MVVM + schichtenweise „Clean“-Trennung (kein strenges Clean-Architecture-Layering mit Use-Case-Klassen; Repositories werden direkt von ViewModels verwendet) | Aus DI-Verdrahtung (`AppModule.kt`) und Screen/ViewModel-Paarungen ersichtlich |
| UI-Technologie        | Jetpack Compose (Material Design 3), keine XML-Layouts | `ui/screens/*`, `ui/components/*`, `ui/theme/*` |
| Datenschicht          | Room-Entities + DAOs: `ProjectEntity`/`DamageEntity`/`NoteEntity`/`UpdateEventEntity`, `AppDatabase`, `data/local/dao/*` | `data/local/*` |
| Logikschicht          | `data/repository/*` (CRUD, Business-Regeln wie Schnellaufnahme-Bucket, Löschkaskade), `network/*` (Hardware/Recorder/Video-Serving/WLAN), `export/*` (PDF/ZIP/USB), `update/*`, `maps/*` | jeweilige Paketstruktur |
| Präsentationsschicht  | `ui/screens/*` (Compose-Screens + ViewModels je Screen-Ordner), `ui/components/*` (wiederverwendbare Compose-Bausteine, u. a. Videoplayer/OSD/Dialoge), `ui/navigation/NavGraph`, `ui/theme/*` (Dark/Light, Amber-Akzent, DrainQ-Branding) | `ui/*` |

---

## 4. Datenmodell

### 4.1 Entitäten

**ENT-01 ProjectEntity** (Room-Tabelle `projects`)

| Attribut | Beschreibung | Typ |
| -------- | ------------ | --- |
| id | Primärschlüssel, autogeneriert | `Long` |
| projectNumber | Vom System generierte Projektnummer (`ddMMyy_HHmm_seq` bzw. Schnellaufnahme-Bucket `<Label>_ddMMyy`) | `String` |
| auftraggeber | Auftraggeber | `String` |
| standortAdresse | Standortadresse | `String` |
| inspektionsdatum | Inspektionsdatum (als Text, kein `LocalDate`) | `String` |
| inspektor | Name des Inspekteurs | `String` |
| wetter | Wetter-Preset/-Text | `String` |
| leitungstyp | Leitungstyp | `String` |
| material | Material | `String` |
| durchmesser | Durchmesser (Text, DN-Wert) | `String` |
| inspektionslaenge | Inspektionslänge in Metern (Text) | `String` |
| startpunkt / endpunkt | Start-/Endknoten der Haltung | `String` |
| kameratyp | Kamerakopftyp (C10/C18), leer bei UNKNOWN | `String` |
| formVisuell / formVideo / formFoto | Inspektionsmethode-Flags | `Boolean` |
| videoQuality | „SD“ oder „HD“, nach Anlage nicht änderbar | `String` |
| videoOverlay | Projektdaten ins Video einblenden | `Boolean` |
| createdAt | Erstellungszeitpunkt (Epoch-ms) | `Long` |
| status | z. B. `OPEN`, `QUICK` | `String` |
| latitude / longitude | GPS-Koordinaten | `Double?` |
| mapImagePath | Pfad zum gespeicherten Kartenbild | `String?` |

Relation: 1:N zu `ENT-02 DamageEntity` und `ENT-03 NoteEntity` (Fremdschlüssel `projectId`,
`ON DELETE CASCADE`).

**ENT-02 DamageEntity** (Room-Tabelle `damages`)

| Attribut | Beschreibung | Typ |
| -------- | ------------ | --- |
| id | Primärschlüssel, autogeneriert | `Long` |
| projectId | FK → `ProjectEntity.id`, CASCADE | `Long` |
| position / positionEnd | Station (Meter), optionales Streckenende | `Float` / `Float?` |
| damageType | Preset-Bezeichnung (kein DIN-Code) | `String` |
| description | Freitext | `String` |
| photoPath / annotatedPhotoPath | Pfade zu Original-/annotiertem Foto | `String` |
| videoTimestamp | Zeitstempel im Video (ms) | `Long?` |
| createdAt / updatedAt | Zeitstempel | `Long` |

Bewusst schlank seit Migration 8→9: Kein DIN-EN-13508-2-Code (`mainCode`, `characterization*`,
`quantification*`, `clockPosition*` etc. wurden entfernt) — entspricht CON-04 der Analyse.

**ENT-03 NoteEntity** (Room-Tabelle `notes`)

| Attribut | Beschreibung | Typ |
| -------- | ------------ | --- |
| id | Primärschlüssel, autogeneriert | `Long` |
| projectId | FK → `ProjectEntity.id`, CASCADE | `Long` |
| position | Station (Meter) | `Float` |
| text | Freitext | `String` |
| audioPath | Pfad zur Audio-Notiz (falls aufgenommen) | `String` |
| createdAt | Zeitstempel | `Long` |

**ENT-04 UpdateEventEntity** (Room-Tabelle `update_events`, kein Fremdschlüssel — eigenständiges
Audit-Log des Update-Vorgangs)

| Attribut | Beschreibung | Typ |
| -------- | ------------ | --- |
| id | Primärschlüssel, autogeneriert | `Long` |
| timestamp | Zeitpunkt des Ereignisses | `Long` |
| eventType | Wert von `UpdateEventType` als String | `String` |
| fromVersion / toVersion | Ausgangs-/Zielversion | `String?` |
| source | Manifest-/Portal-URL (ohne Tokens) | `String?` |
| errorMessage | Fehlertext bei Misserfolg, sonst `null` | `String?` |

### 4.2 Persistenz

| Bereich            | Entscheidung |
| ------------------ | ------------ |
| Engine             | Room 2.6.1 über SQLite (`AppDatabase`, DB-Datei `oneapp_database`) |
| Zugriffsschicht    | DAO-Interfaces (`ProjectDao`, `DamageDao`, `NoteDao`, `UpdateEventDao`) + Repositories (`data/repository/*`) darüber |
| Migrationsstrategie | Explizite `Migration`-Objekte 3→4, 4→5, 5→6, 6→7, 7→8, 8→9 in `AppDatabase.kt`. **Kein** `fallbackToDestructiveMigration` — eine fehlende Migration führt zu einem sichtbaren Absturz statt stillem Datenverlust (CEO-Vorgabe, Kommentar „M4“ im Code). `exportSchema=true` seit Version 9 erzwingt Schema-Export nach `app/schemas/` und damit Migrationstests bei jeder Schemaänderung. Migration 8→9 (CEO-Beschluss 2026-06-07) entfernt die komplette DIN-13508-2-Hierarchie (Tabellen `pipes`/`inspections`, DIN-Spalten in `damages`) per SQLite-Tabellen-Neubau (`damages_new` → Rename), Bestandsdaten bleiben über `INSERT…SELECT` mit `COALESCE`-Fallback auf `legacyDamageType` erhalten. Aktuelle Schemaversion im Code: **9**. |

### 4.3 Hilfstypen

**`UpdateEventType`** — Enum der Update-Lebenszyklus-Ereignisse (`data/local/entity/UpdateEventEntity.kt`)

| Wert | Beschreibung |
| ---- | ------------ |
| `CHECK` | Manifest-Abfrage beim Portal gestartet |
| `DOWNLOAD_START` | APK-Download gestartet |
| `DOWNLOAD_OK` | Download + Hash-Verifikation erfolgreich |
| `DOWNLOAD_FAIL` | Download oder Hash-Verifikation fehlgeschlagen |
| `INSTALL_INITIATED` | PackageInstaller-Session gestartet |
| `INSTALL_DONE` | Installation erfolgreich abgeschlossen |

---

## 5. Module

| ID     | Modul | Klasse(n) | Verantwortung | Verknüpfte Anforderungen |
| ------ | ----- | --------- | ------------- | ------------------------ |
| MOD-01 | Recorder-Stack | `Recorder`, `RecorderConfig`, `RecorderFactory`, `FfmpegRtspRecorder`, `FallbackRecorder`, `HardwareBitmapRecorder`, `LocalBitmapRecorder`, `RecorderJournalMuxer`, `RecorderRemux`, `H264StreamJournal` | HD-Videoaufnahme über HW-Encoder mit Pause/Fortsetzen, unsichtbarer Software-Rückfall, Journal-basierte Crash-Wiederherstellung (Prozess-Kill → abspielbare Datei nach Neustart), Foto-Standbild-Erfassung | REF-06, REF-07 · REN-01, REN-02, REN-04 |
| MOD-02 | OSD-/Meter-Stack | `OsdRenderer`, `OsdSettings`, `OsdOverlay`, `InspectionOsd`, `MeterSample`/`MeterSampleV3`, `MeterTrackReader`/`Writer` (+V3), `LinearMeterCalculator` | Station/Datum-Einblendung (live + eingebrannt), Meterwert-Filterung/-Plausibilisierung, Meter-Spur-Aufzeichnung synchron zum Video | REF-05, REF-09, REF-16, REF-17 · REN-03, REN-14 |
| MOD-03 | Video-Server / RTSP / V4L2 | `network/video/H264Encoder`, `NalUtils`, `OneVideoServer`, `RtpTimestamp`, `RtspVideoServer`, `SocketTuning`, `network/internal/V4L2Camera`, `CameraFrameBus`, `OneFrameCodec`, `OneInternalHardwareService`, `VideoSource`, `RtspStreamTester`, `CameraEncoderArbiter` | Live-Kamerabild aus `/dev/video0` lesen (native NDK-Bridge), als RTSP/H.264 servieren, Frame-Fan-out zwischen lokaler Anzeige und Encoder, Ein-Encoder-Exklusivität auf dem einzigen HW-AVC-Codec | REF-04, REF-06 · REN-01, REN-02 |
| MOD-04 | Hardware-/Geräteverbindung | `OneHardwareService`, `OneHardwareModels`, `HardwareModeDetector`, `HardwareService` (Interface), `NetworkDiscoveryService`, `OneAutoConnector`, `KnownOneStore`, `ConnectivityMonitor`, `WifiController` | Verbindung zur ONE (DIRECT: seriell `/dev/ttyS5`; Remote: WiFi/HTTP-Polling), automatische Modus-Erkennung, Discovery/Pairing/Auto-Reconnect | REF-03, REF-09 · REN-07 |
| MOD-05 | Sonde-/Licht-/Hardbutton-Steuerung | `SondeFrequency`, `HardwareKeyBus` | Ortungssonden-Frequenzzyklus, Lichtstufen, Zuordnung physischer Gerätetasten (F1–F8) zu App-Aktionen | REF-14, REF-15 |
| MOD-06 | Projekt-/Schadens-/Notizverwaltung | `ProjectRepository`, `DamageRepository`, `NoteRepository`, `DamagePresetRepository`, `ProjectDao`, `DamageDao`, `NoteDao` | CRUD für Projekte/Schäden/Notizen, Schnellaufnahme-Tages-Bucket, vollständige (DSGVO-taugliche) Projektlöschung inkl. aller Dateien | REF-01, REF-02, REF-08, REF-13 |
| MOD-07 | Export (PDF/ZIP/USB) | `ProjectExportService`, `UsbExportService`, `ReportLogo` | PDF-Haltungsbericht (iText7, eingebetteter Inter-Font, Leitungsverlauf-Grafik), ZIP-Bündel, USB-Export mit fsync-Schreibverifikation nach `/DrainQ/<Projektnr>/` | REF-10, REF-18, REF-19 |
| MOD-08 | Update-Client | `HttpUpdateService`, `UpdateConfig`, `UpdateInstaller`, `UpdateInstallReceiver`, `UpdateWorker`, `UpdateModels`, `UpdateEventRepository` | Manifest-Abruf vom DrainQ-Portal, SHA-256-Verifikation der APK, Installation über `PackageInstaller`, Audit-Protokollierung | REF-12 · REN-10, REN-15 |
| MOD-09 | Kiosk / Bootstrap / Geräteverwaltung | `bootstrap/AndroidDevicePolicyGateway`, `DeviceFilePermissionBootstrap`, `DeviceOwnerLocationProvisioner`, `OneDeviceAdminReceiver`, `PackageReplacedReceiver`, `MainActivity` (Kiosk-Teil) | Device-Owner-Provisionierung, LockTask-Kiosk, Immersive-Mode/Gesten-Taskbar-Unterdrückung, Autostart nach Update/Boot | REF-11 · REN-05 |
| MOD-10 | Netzwerk / WLAN / Hotspot / Dual-Mode | `AccessPointController`, `AndroidSoftApStarter`, `AndroidLohsStarter`, `SoftAp`, `WifiQr`, `OneRemoteServer`, `OneRemoteProtocol` | In-App-WLAN, Tablet-Hotspot (privilegierter SoftAP mit LOHS-Fallback), Dual-Mode-Fernanzeige-Protokoll, QR-Kopplung | REF-22, REF-28 · REN-13 |
| MOD-11 | Karten / Standort / Wetter | `OfflineMapCatalog`, `OfflineMapDownloadWorker`, `OfflineMapManager`, `OfflineMapRenderer`, `NominatimService`, `OsmStaticMapService`, `WeatherApiService`, `LocationService`, `WeatherPresetRepository` | Offline-Kartenkacheln (Mapsforge), Geocoding (Nominatim), statische Kartenbilder (OSM), Wetter-Presets, GPS-Standort | REF-23, REF-24 |
| MOD-12 | Sicherer Speicher / Cloud-Stub | `AndroidEncryptedStorage`, `KnownOneStore` (Nutzer), `CloudAccountStore` | Verschlüsselte Ablage von WLAN-Zugangsdaten bekannter ONEs (Keystore); Cloud-Konto-Anbindung als bewusster Platzhalter | REN-09 · REF-27 |
| MOD-13 | Screens / Präsentation | siehe §6 (`ui/screens/*`, zugehörige ViewModels) | Compose-UI je Anwendungsfall, bindet MOD-01 bis MOD-12 an den Nutzer | REF-01…REF-30 (siehe §6) |

**Auflösung MOD-10 (Phantomklasse):** `FallbackHotspotStarter` existiert nicht als eigene Quelldatei im Repo. Das reale Zwei-Schichten-Modell des Hotspot-Fallbacks besteht aus `AndroidSoftApStarter` (privilegierter SoftAP-Pfad) und `AndroidLohsStarter` (LOHS-Fallback bei fehlender Privilegierung); Testbeleg dafür ist `FallbackHotspotStarterTest` (siehe [[04-testing_one]] TU-08), die den Klassennamen `FallbackHotspotStarter` nur als Testklassen-/Konzeptname trägt, nicht als produktive Klasse.

**Konsistenzcheck (Priorität A):** Alle REF-01…REF-12 (funktional, Priorität A laut Analyse) sind
mindestens einem MOD zugeordnet: REF-01/02 → MOD-06; REF-03 → MOD-04; REF-04 → MOD-03; REF-05 →
MOD-02; REF-06/REF-07 → MOD-01 (MOD-03 als Zulieferer); REF-08 → MOD-06; REF-09 → MOD-02/MOD-04;
REF-10 → MOD-07; REF-11 → MOD-09; REF-12 → MOD-08. Ebenso REN-01…REN-08, REN-15 (nichtfunktional,
Priorität A) → MOD-01/02/03/04/09.

### 5.1 Interne Schnittstellen zwischen Modulen

| Sender (MOD-xx) | Ereignis / Signal | Empfänger (MOD-xx) | Handler / Slot |
| --------------- | ------------------ | -------------------- | -------------- |
| MOD-03 (`CameraFrameBus`) | Kameraframe (V4L2-Fan-out) | MOD-01 (`HardwareBitmapRecorder`), MOD-13 (Live-Vorschau in `InspectionScreen`) | Observable/Flow-Konsum je Abonnent, genau ein `/dev/video0`-Open |
| MOD-04 (`OneAutoConnector`, `HardwareModeDetector`) | Verbindungsstatus (`isConnected`, Probe-Ergebnis) | MOD-13 (`ConnectionViewModel`, `NetworkViewModel`) | StateFlow-Beobachtung, UI-Statusanzeige/Reconnect-Trigger |
| MOD-05 (`HardwareKeyBus`) | Hardbutton-Ereignis (Licht/Sonde/Aufnahme, F1–F8) | MOD-13 (`InspectionScreen`/`InspectionControls`) | Callback löst dieselbe Aktion wie der positionsgleiche Softbutton aus |
| MOD-02 (`MeterTrackWriter`) | Meter-Sample (zeitgenau) | MOD-01 (`RecorderJournalMuxer`, Sidecar `*.meter.jsonl`) | Schreibt Meter-Spur parallel zur Videoaufnahme |
| MOD-08 (`UpdateWorker`/`HttpUpdateService`) | Update-Lifecycle-Ereignis (`UpdateEventType`) | MOD-06/MOD-08 (`UpdateEventRepository` → Room) | Persistiert Audit-Eintrag, `pruneOldEvents()` rotiert |
| MOD-10 (`OneRemoteServer`) | Video-/Steuer-Frames (DIRECT-Modus) | Zweitgerät (ACT-11, außerhalb des Systems) | `OneRemoteProtocol` über TCP im Tablet-Hotspot |
| MOD-12 (`KnownOneStore`) | Passphrase bekannter ONE (verschlüsselt) | MOD-04 (`OneAutoConnector`) | Auto-Reconnect-Zustandsmaschine liest/schreibt über `SecretKeyValueStore`-Interface |

---

## 6. Benutzeroberfläche

### 6.1 Ansichten

| ID     | Ansicht | Zentrale UI-Elemente | Verknüpfte UC / Anforderungen |
| ------ | ------- | --------------------- | ------------------------------ |
| SCR-01 | `SplashScreen` | Startbildschirm mit DrainQ-Bildmarke | UC-14 |
| SCR-02 | `HomeScreen` (+ `StorageInfo`) | Kachel-Navigation, Speicher-Füllstandsbalken (intern/USB) | REF-20 · UC-04 |
| SCR-03 | `ConnectionScreen` (+ `ConnectionViewModel`) | Verbindungsstatus zur ONE, Reconnect | REF-03 · UC-05 |
| SCR-04 | `ProjectsScreen` (+ `ProjectsViewModel`) | Projektliste „Auftraggeber — Standort — Projektnr.“ | REF-02 · UC-04 |
| SCR-05 | `ProjectFormScreen` (+ `ProjectFormViewModel`, `MapPickerDialog`, `CameraTypePrefill`, `InspectionDateGuard`) | Projekt anlegen/bearbeiten, Kartenauswahl, automatische Kameratyp-/Datumsplausibilisierung | REF-01, REF-24, REF-30 · UC-04 |
| SCR-06 | `ProjectDetailScreen` (+ `ProjectDetailViewModel`, `PdfPreviewDialog`, `FullscreenImageDialog`, `UsbExportDialog`, `VideoPlaybackDialog`) | Projektdetails, PDF-Vorschau, Vollbild-Fotoansicht, USB-Export-Dialog, Videowiedergabe mit Meter-Overlay | REF-10, REF-16, REF-17, REF-18, REF-19 · UC-03, UC-07, UC-08 |
| SCR-07 | `InspectionScreen` (+ `DamageDialog`, `NoteDialog`, `ImageAnnotationDialog`, `InspectionControls`, `MeterInput`) | Live-Video, Aufnahme-Steuerung, Schadens-/Notizerfassung, Foto-Annotation, Meterzähler-Anzeige/Nullung | REF-04, REF-05, REF-06, REF-07, REF-08, REF-09, REF-13, REF-14, REF-15, REF-21 · UC-01, UC-02, UC-06, UC-10, UC-11 |
| SCR-08 | `ReportsScreen` | Berichtsübersicht | REF-10 · UC-03 |
| SCR-09 | `SettingsScreen` (+ `SettingsViewModel`, `UpdateSection`) | Einstellungen, Helligkeit, Sprache, Theme, Update-Anzeige/-Installation | REF-12, REF-25, REF-26, REF-29 · UC-09, UC-17 |
| SCR-10 | `NetworkScreen` (+ `NetworkViewModel`) | Online-Status, In-App-WLAN, Hotspot/Tethering | REF-22 · UC-12 |
| SCR-11 | `CloudLoginScreen` | DrainQ-Konto-Login (Stub, siehe §8) | REF-27 · UC-15 |
| SCR-12 | `OfflineMapsScreen` (+ `OfflineMapsViewModel`) | Kartenkacheln herunterladen/verwalten — **ausgeblendet seit 06.09.2026, CEO-Entscheid, Schalter `FeatureFlags.offlineMapsScreen`** (Welle bedienbild Z-4) | REF-23 · UC-13 |
| SCR-13 | `PairingScreen` (+ `PairingViewModel`) | Dual-Mode: Tablet-Hotspot an/aus, WIFI-QR-Kopplung | REF-28 · UC-16 |

### 6.2 Navigation

`NavGraph.kt` implementiert eine Compose-`NavHost`-Navigation mit adaptiver Leisten-Umschaltung:
ab „Medium“-Fensterklasse eine **Navigation Rail** (`NavGraphRail`), sonst eine **Bottom
Navigation Bar** (`NavGraphBottomBar`). Die Hauptleiste zeigt nur Home / Inspektion / Einstellungen
(„Projekte“ ist über die Home-Kachel erreichbar, bleibt aber als eigene Route bestehen). Während
der Inspektion (`route.startsWith("inspection")`) wird die Rail ausgeblendet („immersiv“), damit
Video und die Hardbutton-Leiste die volle Breite nutzen. Start-Route ist `Screen.Home`. Detailrouten
mit Parametern: `inspection/{projectId}`, `project_detail/{projectId}`, `project_form/{projectId}`.
Modale/Sekundärrouten ohne eigenen Leisteneintrag: `offline_maps`, `network`, `pairing`,
`cloud_login`, `project_form`.

---

## 7. Integrationen

| System | Protokoll / API | Bibliothek | Verknüpfte Anforderungen |
| ------ | ---------------- | ---------- | ------------------------- |
| ONE-Hardware (Kamera/Meterzähler/Sonde/Licht) | DIRECT: seriell `/dev/ttyS5` + natives V4L2 `/dev/video0`; Remote: HTTP/Socket-Polling über WLAN (Ziel-IP Default `192.168.43.1`) | Eigene NDK-Bridge (`v4l2bridge.c`, CMake) + `OneHardwareService`/`OneInternalHardwareService` | REF-03, REF-09, REF-14, REF-15 · REN-07 |
| Live-Video-Streaming | RTSP/H.264 (eigener `RtspVideoServer`, Port 8554) | `androidx.media3:media3-exoplayer(-rtsp)` 1.5.1 als Client, eigener Server-Stack | REF-04 · REN-01 |
| Video-Overlay/-Encoding | Lokale Prozessverarbeitung (Muxing/Burn-in) | FFmpegKit (`ffmpeg-kit-full-gpl:2.1.0`) | REF-05, REF-06 |
| DrainQ-Update-Portal | HTTPS (`https://license.drainq.com/api/software/one/releases.<channel>.json` + APK-Download) | OkHttp 4.12.0 + Gson 2.10.1 | REF-12 · REN-10 |
| Kartendienste (OSM / Nominatim / Wetter) | HTTPS REST (Geocoding via Nominatim, statische Kartenbilder via OSM, Wetter-API); Offline-Rendering aus `.map`-Kacheln | OkHttp (REST); Mapsforge 0.21.0 (Offline-Rendering) | REF-23, REF-24 |
| USB-Speicher | Lokales Dateisystem (`StorageManager`/`StorageVolume`, `MANAGE_EXTERNAL_STORAGE`) | Android SDK (kein Fremd-Lib) | REF-18 |
| Zweitgerät (Dual-Mode-Fernanzeige) | Eigenes TCP-Binärprotokoll über WLAN-Hotspot (SoftAP mit LOHS-Fallback), Kopplung per WIFI-QR | Eigenimplementiert (`OneRemoteServer`/`OneRemoteProtocol`); ZXing 3.5.3 (Encode) / `zxing-android-embedded` 4.3.0 (Scan) | REF-28 |
| MQTT | Paho-MQTT ist in `build.gradle.kts` deklariert (`org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` + `org.eclipse.paho.android.service:1.1.1`), wird aber im Quellcode und im `AndroidManifest.xml` nicht genutzt → tote/verwaiste Dependency; CEO-Entscheid: entfernen. [AI-draft] | `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`, `org.eclipse.paho.android.service:1.1.1` | — |

---

## 8. Sicherheit und Compliance

> Einordnung laut Analyse (CON-03): **KRITIS/NIS2/ISO 27001 sind für die ONE nicht einschlägig** —
> die ONE ist ein mobiles Feld-Erfassungsgerät, kein Teil einer kritischen Infrastruktursteuerung.
> Der einzige zutreffende Compliance-Rahmen ist die **DSGVO** (lokale Personen-/Auftraggeberdaten).
> Diese Zeile ist daher bewusst „nicht zutreffend“ für KRITIS/NIS2/ISO 27001 und wird unten nicht
> gesondert geführt.

| Bereich                                     | Entscheidung (Ist-Zustand) | Begründung / REN-Bezug |
| -------------------------------------------- | ---------------------------- | ------------------------ |
| Authentifizierung / Autorisierung           | Kein Nutzer-Login für den Kernprozess (Single-Operator-Kiosk-Gerät, kein Mandantenmodell). Die DrainQ-Cloud-Anbindung (`CloudAccountStore`) ist ein **bewusster Stub**: `isLoggedIn()` liefert immer `false`, `saveSession()`/`clearSession()` sind leer implementiert (TODO-Kommentar im Code verweist auf künftigen OAuth-Flow mit `EncryptedSharedPreferences`). | REF-27 (Priorität C, Reifegrad „Stub“ lt. Analyse); kein Compliance-Zwang, solange kein Cloud-Auth-Flow existiert |
| Schutz gespeicherter Daten                  | WLAN-Zugangsdaten bekannter ONEs liegen in `AndroidEncryptedStorage` (`EncryptedSharedPreferences`, `MasterKey` AES256-GCM im Android Keystore, Schlüssel-/Wertverschlüsselung AES256_SIV/AES256_GCM) — belegt in `network/AndroidEncryptedStorage.kt`, genutzt von `KnownOneStore`. Projekt-/Schadens-/Auftraggeberdaten liegen dagegen **unverschlüsselt** in Room/SQLite auf dem Gerätespeicher (kein SQLCipher o. Ä. im Code gefunden). | REN-09 erfüllt für Zugangsdaten. Verschlüsselung der Projektdaten-at-rest ist **nicht umgesetzt** — [AI-draft] vom CEO zu entscheiden, ob dies für Feldgeräte gefordert wird (Restrisiko ist durch Kiosk-/Sideload-Betrieb ohne Play-Store gemindert, aber physischer Geräteverlust bleibt ein Risiko) |
| Schutz übertragener Daten                   | Update-, Wetter-, Nominatim- und OSM-Verkehr läuft im Code ausschließlich über `https`-Endpunkte (`UPDATE_PROXY_URL="https://license.drainq.com/..."`, `NominatimService`/`WeatherApiService`/`OsmStaticMapService`). **Altlast:** `AndroidManifest.xml` setzt `android:usesCleartextTraffic="true"` ohne begleitende `network_security_config` — das erlaubt technisch Klartext-HTTP für beliebige Domains, obwohl kein Code-Pfad dies aktuell nutzt. | REN-13 (Priorität C lt. Analyse, niedriges Restrisiko); offene Empfehlung: Cleartext-Flag entfernen bzw. durch Domain-Allowlist ersetzen |
| Protokollierung / Audit-Log                  | `UpdateEventEntity`/`UpdateEventRepository` protokolliert den kompletten Update-Lebenszyklus (`CHECK`/`DOWNLOAD_START`/`DOWNLOAD_OK`/`DOWNLOAD_FAIL`/`INSTALL_INITIATED`/`INSTALL_DONE`) inkl. Fehlermeldungen, mit `pruneOldEvents()` als Log-Rotation. Ein generelles Ereignis-/Zugriffs-Logging für Nutzeraktionen existiert **nicht**. | Update-Audit erfüllt REN-15 (Datenerhalt-Nachweis). Generelles Logging ist REN-12 (Priorität D, „optional“) — bewusste Nicht-Umsetzung, da kein KRITIS-Zwang besteht (CON-03) |
| Update- / Patch-Weg                         | Feste Bezugsquelle `https://license.drainq.com/api/software/one/` (aus `BuildConfig`, per lokalem Pref `proxyUrl` **ungeprüft überschreibbar** — keine Domain-Allowlist). Integritätsprüfung der APK: **SHA-256-Hash-Vergleich** gegen den Manifest-Wert (`HttpUpdateService.sha256Hex`) — **keine kryptographische Signaturprüfung** des Manifests. Installation über `PackageInstaller`-Session mit Nutzerbestätigung (`REQUEST_INSTALL_PACKAGES`), kein Silent-Install. | REN-10 (Priorität B): Hash-Prüfung ist umgesetzt, Signaturprüfung + Domain-Allowlist für `proxyUrl` sind **offen** (dokumentierte Sicherheits-Hygiene-Lücke ohne Compliance-Zwang, siehe Analyse) |
| Umgang mit personenbezogenen Daten (DSGVO)  | Auftraggeber-/Standort-/Projektdaten bleiben lokal in Room; keine automatische Cloud-Synchronisation (Cloud-Login ist Stub, s. o.). `ProjectRepository.deleteProjectCompletely()` löscht DB-Zeilen (Room-CASCADE) **und** alle zugehörigen Dateien (Fotos/Videos/Audio/Berichte/Exporte) unwiderruflich — unterstützt Löschungsanspruch/Datenminimierung. GPS-Standortdaten nur nach expliziter OS-Berechtigung (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`) und aktiver Nutzeraktion (Karte/Adresssuche). | REN-11 im Ist-Code umgesetzt; ein über die OS-Berechtigungsabfrage hinausgehender expliziter Einwilligungsdialog ist **nicht belegt** [AI-draft] |

---

## 9. Testen

### 9.1 Testumgebung

| Szenario | Werkzeug | Anmerkungen |
| -------- | -------- | ----------- |
| JVM-Unit-Tests ohne Android-Gerät | JUnit4 4.13.2 + Robolectric 4.11.1 | Großteil von `app/src/test/...` läuft ohne Emulator/Gerät |
| HTTP-Mocking (Update/Wetter/Nominatim) | OkHttp `mockwebserver` 4.12.0 | u. a. in `UpdateServiceTest`, `UpdateE2ETest` |
| Coroutine-Tests | `kotlinx-coroutines-test` 1.7.3 | für Suspend-Funktionen in Repositories/Services |
| Room-Migrationstests / DB-Erstellung | Robolectric + `androidx.room:room-testing` | `AppDatabaseCreateTest` prüft, dass die Migrationskette 3→9 eine valide DB erzeugt |
| Instrumentierungstests (echtes Gerät/Emulator) | Espresso 3.5.1 + `androidx.test.ext:junit` | `androidTest`-Verzeichnis vorhanden, Inhalt nicht im Detail erkundet [AI-draft] |
| Manuelle Feldverifikation | [AI-draft] reale ONE-Hardware (RK3588) | Aus Code-Kommentaren erschließbar („On-Device-Befund 0.4.1“, Feedback-Referenzen „Louis“), kein automatisiertes Setup dokumentiert |

### 9.2 Strategie der Unit-Tests

Abgeleitet 1:1 aus dem Verzeichnis `app/src/test/java/com/uip/oneapp/...` (Klasse → zugehörige
Testklasse):

| Klasse | Testfälle (aus Testklassen-Namen abgeleitet) |
| ------ | --------------------------------------------- |
| `LinearMeterCalculator` | `LinearMeterCalculatorTest` — Meterwert-Filterung/Plausibilisierung |
| `SondeFrequency` | `SondeFrequencyTest` — Frequenzzyklus-Logik der Ortungssonde |
| `CameraFrameBus` | `CameraFrameBusTest` — Frame-Fan-out an mehrere Konsumenten |
| `OneFrameCodec` | `OneFrameCodecTest` — Kodierung/Dekodierung interner Frame-Nachrichten |
| `HttpUpdateService`/`UpdateConfig` | `UpdateServiceTest`, `UpdateE2ETest` — Manifest-Check, Hash-Verifikation, Fehlerpfade, End-to-End-Update |
| `AppDatabase` | `AppDatabaseCreateTest` — Migrationskette 3→9 erzeugt gültige DB |
| `OsdRenderer` | `OsdRendererTest` — OSD-Text-Rendering |
| `RecorderJournalMuxer` / `RecorderRemux` | `RecorderJournalMuxerTest`, `RecorderRemuxTest` — Crash-Recovery-Remux (REN-04) |
| `H264Encoder` / `NalUtils` / `RtpTimestamp` / `SocketTuning` | jeweils gleichnamige `*Test` — Format-/Timing-Korrektheit des RTSP/H.264-Stacks |
| `H264StreamJournal` | `H264JournalCodecTest` — Journal-Kodierung |
| `HardwareBitmapRecorder` / `LocalBitmapRecorder` | `HardwareBitmapRecorderStateTest`, `LocalBitmapRecorderStateTest` — Zustandsautomat Aufnahme |
| `HardwareModeDetector` | `HardwareModeDetectorTest` — DIRECT-vs-WiFi-Erkennung |
| `AccessPointController` / `FallbackHotspotStarter` / `SoftAp` | `AccessPointControllerTest`, `AccessPointSpecTest`, `FallbackHotspotStarterTest`, `SoftApSpecTest` — SoftAP/LOHS-Fallback-Logik |
| `CameraEncoderArbiter` | `CameraEncoderArbiterTest` — Ein-Encoder-Exklusivität |
| `KnownOneStore` | `KnownOneStoreTest` — Auto-Reconnect-Speicher |
| `OneAutoConnector` | `OneAutoConnectorTest` — Reconnect-Zustandsautomat |
| `OneRemoteProtocol` / `OneRemoteServer` | `OneRemoteProtocolTest`, `OneRemoteServerTest` — Dual-Mode-Protokoll |
| `MeterTrackWriter` (+V3) | `MeterTrackWriterTest`, `MeterTrackWriterV3Test` — Meter-Spur-Schreibung |
| „LookupMeter“ (Leseweg) | `LookupMeterTest`, `LookupMeterV3Test` — Meter-Spur-Auswertung bei Wiedergabe |
| `RecoveredMarkerTest` | Erkennung/Markierung wiederhergestellter Aufnahmen nach Crash |
| `WifiQr` | `WifiQrTest` — WIFI-QR-String-Format |
| `DeviceOwnerLocationProvisioner` | `DeviceOwnerLocationProvisionerTest` — Device-Owner-Provisionierungslogik |
| `OsdOverlay` (UI) | `OsdOverlayTest` |
| `StorageInfo` (Home) | `StorageInfoTest` |
| `InspectionControls`, `MeterInput` | `InspectionControlsTest`, `MeterInputTest` |
| `CameraTypePrefill`, `InspectionDateGuard` | `CameraTypePrefillTest`, `InspectionDateGuardTest` |
| `CapturePersistenceTest` | Persistenz-Verhalten von Foto-/Videoaufnahmen (Repository-Ebene) |

---

## 10. Zusammenfassung für AI

**Strukturelle Konsistenzprüfung (durchgeführt vor Befüllung dieses Abschnitts):**
- Alle funktionalen/nichtfunktionalen Anforderungen mit Priorität A aus der Analyse (REF-01…REF-12;
  REN-01…REN-08, REN-15) sind einem MOD-xx zugeordnet — siehe Konsistenzhinweis am Ende von §5.
- Alle MVP-Anwendungsfälle (UC-01, UC-02, UC-03, UC-06, UC-09, UC-14) sind durch mindestens eine
  Ansicht abgedeckt: UC-01/UC-02/UC-06/UC-10/UC-11 → SCR-07; UC-03 → SCR-06/SCR-08; UC-09 → SCR-09;
  UC-14 (Kiosk) → MOD-09 (Systemverhalten, kein eigener Screen nötig, da kein Nutzer-Dialog).
- Jede Zeile in §8 ist entschieden (Entscheidung mit Code-Beleg oder begründetes „offen“/„Stub“);
  KRITIS/NIS2/ISO 27001 sind laut Analyse (CON-03) nicht einschlägig und daher bewusst nicht als
  eigene Zeile geführt.

**Technologie-Stack:** Kotlin (JVM-Target 17) · Jetpack Compose (Material3, compose-bom
2024.09.03) · Android SDK 26–34 (compileSdk 35) auf Rockchip RK3588 · Gradle-Kotlin-DSL + KSP ·
Room 2.6.1 · Koin 3.5.3 · Media3/ExoPlayer 1.5.1 (RTSP) · FFmpegKit 2.1.0 · iText7 7.2.5 · OkHttp
4.12.0 · Mapsforge 0.21.0 · `security-crypto` 1.1.0-alpha06 · natives NDK-Modul (`v4l2bridge.c`,
CMake 3.22.1) · JUnit4/Robolectric für Unit-Tests.

**Zu generierende/erhaltende Schlüsselklassen:** `AppDatabase` + Entities/DAOs (ENT-01…ENT-04) ·
Repositories (`ProjectRepository`, `DamageRepository`, `NoteRepository`, `UpdateEventRepository`,
`DamagePresetRepository`, `WeatherPresetRepository`) · `HardwareService`-Abstraktion mit
`OneInternalHardwareService`/`OneHardwareService` · Recorder-Stack (`RecorderFactory`,
`FfmpegRtspRecorder`, `RecorderJournalMuxer`) · OSD-/Meter-Stack (`OsdRenderer`,
`LinearMeterCalculator`, `MeterTrackWriter`) · Video-Server-Stack (`OneVideoServer`,
`RtspVideoServer`, `CameraFrameBus`) · Export-Services (`ProjectExportService`, `UsbExportService`)
· Update-Client (`HttpUpdateService`, `UpdateConfig`, `UpdateInstaller`) · Kiosk/Bootstrap
(`MainActivity`, `OneDeviceAdminReceiver`) · Screens/ViewModels je SCR-xx · `AppModule.kt` (Koin).

**Empfohlene Implementierungsreihenfolge (bei Neuaufbau aus diesem Entwurf):**
1. Datenmodell (ENT-01…ENT-04, `AppDatabase` inkl. Migrationskette)
2. Repositories (MOD-06)
3. Hardware-/Video-/Recorder-Kern (MOD-03 → MOD-01 → MOD-02 → MOD-04 → MOD-05, in dieser
   Abhängigkeitsreihenfolge, da MOD-01 den Frame-Bus aus MOD-03 konsumiert)
4. Export (MOD-07) und Update-Client (MOD-08)
5. Kiosk/Bootstrap (MOD-09)
6. Screens/ViewModels (MOD-13), gebunden über `AppModule.kt`
7. Integrationen Karten/Netzwerk/Dual-Mode (MOD-10, MOD-11, MOD-12)

**Abhängigkeiten zwischen Modulen:** MOD-01 (Recorder) hängt von MOD-03 (`CameraFrameBus`) und
MOD-02 (OSD/Meter-Einbrennung) ab · MOD-02 hängt von MOD-04 (liefert den rohen Meterwert) ab ·
MOD-04 hängt von MOD-12 (verschlüsselte Zugangsdaten für Auto-Reconnect) ab · MOD-07 (Export) hängt
von MOD-06 (Daten) und MOD-01 (Aufnahmedateien) ab · MOD-08 (Update) hängt von MOD-06
(`UpdateEventRepository`) ab · MOD-09 (Kiosk) ist eine Querschnittsschicht, die von `MainActivity`
unabhängig von den übrigen Modulen aktiviert wird · MOD-13 (Screens) hängt von praktisch allen
übrigen Modulen ab (Präsentationsschicht) · MOD-10 (Dual-Mode) hängt von MOD-03/MOD-04 ab (spiegelt
deren Zustand über WLAN).

---

## Glossar der Entwurfsbegriffe

**Entität** — Domänenobjekt, abgebildet als Room-`@Entity`-Datenklasse.

**Modul** — logisch abgegrenzter Teil des Systems mit kohärentem Funktionsumfang; hier meist ein
Kotlin-Package oder eine zusammengehörige Klassengruppe innerhalb eines Packages.

**Ansicht** — ein Compose-Screen aus `ui/screens/*`, dem Benutzer über die Navigation präsentiert.

**Schicht** — Abstraktionsebene der Architektur: Präsentation (Compose-Screens/ViewModels),
Logik (Repositories/Services), Daten (Room).

**Interne Schnittstelle** — Kommunikationsmechanismus zwischen Modulen (Flow/StateFlow-Beobachtung,
Callback, Datei-Sidecar, TCP-Protokoll); definiert in §5.1.

---

*Nach Freigabe des Dokuments: `02-audit_one.md` anlegen.*

# Disziplin: Implementierung

## 1. Projektinformationen

| Feld                    | Wert                                                                 |
| ----------------------- | ----------------------------------------------------------------------- |
| Name                    | DrainQ.ONE                                                             |
| System                  | one                                                                    |
| Version                 | 0.5.x-beta. Repo-Root enthält bereits eine veröffentlichte Beta-APK `DrainQ-ONE_0.5.15-beta_515.apk` — eine Version **neuer** als der in 01-analysis/02-project referenzierte Stand 0.5.14/514 (2026-07-13). [AI-draft] Der Gradle-Fallback in `app\build.gradle.kts` (ohne CI-Umgebungsvariablen) liefert `versionCode=401`/`versionName="0.4.1"`; die tatsächlich ausgelieferte Version wird über `APP_VERSION_CODE`/`APP_VERSION_NAME` beim Build überschrieben und ist aus dem Repo-Stand allein nicht abschließend verifizierbar. |
| Autor                   | Thomas Viell (CEO) — Implementierung AI (Sonnet-Bauer), rückwärts rekonstruiert aus Code |
| Datum                   | 2026-07-14                                                             |
| Dokumentvorlage         | [[03-implementation_template]]                                        |
| Namenskonvention        | [[naming-convention_one]]                                              |
| Projektkatalog          | `C:\Projekte\drainq.one\`                                              |
| Implementierungskatalog | `docs\engineering\` (Abweichung von der Regelwerks-Struktur, siehe [[naming-convention_one]] — Disziplindokumente liegen gebündelt neben dem bereits bestehenden Code, statt eigener Wurzelordner je Disziplin) |
| Freigegeben am          | — (offen)                                                              |

**Verknüpfte Dokumente:**

| Feld    | Wert                          |
| ------- | ----------------------------- |
| Analyse | [[01-analysis_one]]           |
| Entwurf | [[02-project_one]]            |

---

> [!info] Zusammenarbeit mit AI und Methodik
> Dieses Dokument ist wie der Entwurf eine **Rückwärts-Rekonstruktion**: Es beschreibt die
> tatsächliche Realisierung der in [[02-project_one]] §5 benannten Module, wie sie sich aus dem
> Code (`app\src\main\java\com\uip\oneapp\...`, `app\build.gradle.kts`, `app\src\test\...`)
> ableiten lässt. Es wurden **keine neuen Realisierungsentscheidungen getroffen** — jede Zeile ist
> entweder im Code belegt oder ausdrücklich mit `[AI-draft]` als unverifizierten Befund markiert.
> **Wichtiger Befund dieser Rekonstruktion:** `CLAUDE.md` ist an mehreren Stellen veraltet
> (nennt libVLC und „Clean Architecture“, beides im aktuellen Code nicht mehr/so nicht vorhanden)
> — siehe §3 und §6. Umgekehrt ist die MQTT-Aussage aus [[02-project_one]] §7 zu korrigieren: Die
> Paho-MQTT-Abhängigkeit **ist** im aktuellen `app\build.gradle.kts` vorhanden (siehe §3) — nur die
> Nutzung im Quellcode konnte nicht belegt werden. Jede Entscheidungs-/Befundzeile ist vom
> Menschen (CEO) zu verifizieren.

## 2. Code-Lokalisierung

Die App ist ein einzelnes Android-Gradle-Modul (`:app`) unter der Repository-Wurzel. Es gibt kein
separates Backend/Frontend-Splitting — Code, native Bridge (NDK) und Ressourcen liegen alle unter
`app\`. Ein gesonderter KI-Build-Katalog (`ai_build\`) existiert nicht und ist nicht angelegt: Der
Sonnet-/Opus-Bauer arbeitet in diesem Projekt direkt im selben Arbeitsbaum wie ein menschlicher
Entwickler (Claude Code läuft lokal auf demselben Repo-Checkout, es gibt kein getrenntes
CI-Sandbox-Setup mit eigenem Ausgabepfad) — die im Regelwerk vorgesehene Trennung „AI-Build vs.
Mensch-Build“ hätte hier keinen Verifikationsnutzen, da beide denselben Gradle-Build und dieselbe
`app\build\`-Ausgabe verwenden. Diese Entscheidung ist projektspezifisch und in
[[naming-convention_one]] vorweggenommen.

| Feld                     | Wert                                                              |
| ------------------------ | ------------------------------------------------------------------ |
| Code-Katalog             | `app\`                                                             |
| Quellcode-Katalog        | `app\src\main\` (Kotlin unter `java\com\uip\oneapp\...`, native Bridge unter `cpp\`) |
| Build-Katalog            | `app\build\` (Gradle-Standardausgabe; belegt: `app\build\outputs\apk\debug\app-debug.apk` und `app\build\outputs\apk\release\app-release.apk` liegen bereits vor) |
| Build-Katalog AI         | entfällt — siehe Begründung oben; kein separates `ai_build\` |
| Ausführungskatalog       | kein Desktop-„run\“-Ordner: Artefakt wird auf dem Zielgerät installiert (Paket `com.uip.drainq.one`, Aktivität `.MainActivity`) — per `adb install`/`installDebug` im Entwicklungsbetrieb oder per `PackageInstaller`-Session über den In-App-Update-Client (MOD-08) im Feldbetrieb |
| Einstiegspunkt           | `app\src\main\java\com\uip\oneapp\MainActivity.kt` (Compose-Host, Kiosk-Steuerung); Application-Klasse `app\src\main\java\com\uip\oneapp\OneApp.kt` (Koin-Start, Hardware-Server-Bootstrap je nach `HardwareMode`) |
| Name des Artefakts       | `app-debug.apk` / `app-release.apk` (Gradle-Standardnamen, `assembleDebug`/`assembleRelease`); zusätzlich im Repo-Root abgelegte, versionierte Auslieferungs-APKs nach dem Muster `DrainQ-ONE_<version>-beta_<versionCode>.apk` (z. B. `DrainQ-ONE_0.5.15-beta_515.apk`), erzeugt/benannt durch `tools\publish-one-release.ps1` für die Portal-Distribution (siehe MOD-08) |
| Projektdatei             | `app\build.gradle.kts` (Modul, Android-/Dependency-Konfiguration) + `build.gradle.kts` (Root, Plugin-Versionen) + `settings.gradle.kts` (`rootProject.name = "DrainQ.ONE"`, `include(":app")`) |
| Repository / Branch      | GitHub `ThomasViell/one-app`; aktiver Branch **`feature/dual-mode`** (Stand `PROJECT_STATUS.md`, 2026-07-13: letzter Commit `46a4742`, noch nicht nach `master` gemerged — Merge-Gate laut Status-Datei: USB-Export-Test mit Louis) |

---

## 3. Realisierungsumgebung (faktisch)

Faktisch aus `build.gradle.kts` (Root + Modul) und `settings.gradle.kts` gelesen. Abweichungen von
`CLAUDE.md` sind unten explizit vermerkt, da `CLAUDE.md` laut Aufgabenstellung teilweise veraltet
ist und nicht ungeprüft übernommen werden darf.

| Komponente | Wert |
| ---------- | ---- |
| IDE | [AI-draft] Android Studio (naheliegend für ein Gradle-KTS/KSP-Android-Projekt; im Repo nicht belegbar) |
| Sprache | Kotlin, Gradle-Plugin `org.jetbrains.kotlin.android` **1.9.24** (Root `build.gradle.kts`); JVM-Target **17** (`kotlinOptions.jvmTarget="17"`, `compileOptions` VERSION_17) |
| Android Gradle Plugin | `com.android.application` **8.4.0** |
| KSP (Room-Codegen) | `com.google.devtools.ksp` **1.9.24-1.0.20**; Room-Schema-Export nach `$projectDir/schemas` (`app\schemas\`, existiert im Repo — Voraussetzung für `exportSchema=true` + Migrationstests) |
| Kotlin-Serialization-Plugin | `org.jetbrains.kotlin.plugin.serialization` **1.9.24** |
| Android SDK | `compileSdk=35`, `minSdk=26`, `targetSdk=34` — deckt REN-08 (RK3588/Android 8+) |
| Native Build (NDK) | CMake **3.22.1** (`externalNativeBuild`, `app\src\main\cpp\CMakeLists.txt` + `v4l2bridge.c`, beide Dateien im Repo bestätigt), `ANDROID_STL=c++_shared`, ABI-Filter `arm64-v8a`/`armeabi-v7a` |
| UI-Framework | Jetpack Compose — `compose-bom` **2024.09.03**, Compose-Compiler-Extension **1.5.14**, Material3 |
| Datenbank | Room **2.6.1** (`room-runtime`, `room-ktx`, `room-compiler` via KSP) |
| Dependency Injection | Koin **3.5.3** (`koin-android`, `koin-androidx-compose`) |
| Video/Streaming | Media3/ExoPlayer **1.5.1** (`media3-exoplayer`, `media3-exoplayer-rtsp`, `media3-ui`) als RTSP-Client; FFmpegKit **2.1.0** (`com.antonkarpenko:ffmpeg-kit-full-gpl`, Community-Fork, **nicht** das offizielle, 2024 eingestellte `com.arthenica`-Artefakt) für Overlay-Burn-in |
| PDF | iText7 `itext7-core` **7.2.5** |
| HTTP/JSON | OkHttp **4.12.0**, Gson **2.10.1** |
| QR | ZXing `core` **3.5.3** + `zxing-android-embedded` **4.3.0** |
| Bildladen | Coil **2.5.0** + `coil-svg` (mit Ausschluss von `com.caverock:androidsvg-aar` wegen Duplicate-Class-Konflikt mit Mapsforge) |
| Datenhaltung Prefs | DataStore Preferences **1.0.0**; `androidx.security:security-crypto` **1.1.0-alpha06** (EncryptedSharedPreferences/Keystore) |
| Hintergrundarbeit | WorkManager **2.9.0** (+ `runtime-livedata` für WorkInfo-Beobachtung in Compose) |
| Karten | Mapsforge **0.21.0** (`mapsforge-map-android`, `mapsforge-themes`) |
| Coroutines/Serialisierung | kotlinx-coroutines **1.7.3** (+ `-play-services` für Location), kotlinx-serialization-json **1.6.2** |
| Standort | `com.google.android.gms:play-services-location` **21.1.0** |
| **MQTT [AI-draft, Befund]** | `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` + `org.eclipse.paho.android.service:1.1.1` sind im aktuell gelesenen `app\build.gradle.kts` **vorhanden** (Kommentarzeile `// MQTT` direkt darüber) — dies widerspricht der Aussage in [[02-project_one]] §7, die Abhängigkeit fehle vollständig. Im durchsuchten Quellbaum (`app\src\main\java\com\uip\oneapp\...`) trägt jedoch keine Datei „Mqtt“ im Namen; eine Nutzung ist damit nicht belegt. **Zu klären (CEO/Entwurf-Korrektur):** tote Abhängigkeit entfernen oder tatsächliche Verwendung nachweisen. |
| Unit-Test-Framework | JUnit4 **4.13.2**, Robolectric **4.11.1**, `androidx.test:core-ktx` **1.5.0**, OkHttp `mockwebserver` **4.12.0**, `kotlinx-coroutines-test` **1.7.3** |
| Instrumentierungstests | `androidx.test.ext:junit` **1.1.5**, Espresso **3.5.1**, `androidx.room:room-testing` **2.6.1** |
| Signierung | `signingConfigs.release` liest `KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` aus Umgebungsvariablen; ohne gesetztes `KEYSTORE_PATH` fällt der Release-Build auf die Debug-Signierung zurück (`signingConfigs.getByName("debug")`). Eine Keystore-Datei `oneapp-release.keystore` liegt im Repo-Root — [AI-draft] ob diese versioniert werden sollte, ist eine Sicherheitsfrage außerhalb des Auftrags dieses Dokuments, aber als Befund festgehalten. |
| Update-Distribution (Build-Konfiguration) | `buildConfigField UPDATE_PROXY_URL = "https://license.drainq.com/api/software/one/"`, `UPDATE_CHANNEL = "beta"`; `versionCode`-Konvention `MAJOR*10000+MINOR*100+PATCH` (Fallback `401`/`"0.4.1"` ohne CI-Env, siehe §1) |
| **Abweichung zu `CLAUDE.md`** | `CLAUDE.md` nennt „libVLC für RTSP Streaming“ als Stack-Bestandteil — im Code widerlegt: Es existiert keine `VlcVideoPlayer.kt` mehr (laut `CLAUDE.md`s eigenem Abschnitt „Phase 7“ entfernt) und keine `libvlc`-Dependency in `build.gradle.kts`. `CLAUDE.md` nennt außerdem „MVVM + Clean Architecture“ — im Code ist die Trennung MVVM ohne eigene Use-Case-Schicht (Repositories werden direkt von ViewModels verwendet), wie bereits in [[02-project_one]] §3 festgestellt. |

---

## 4. Realisierung

### 4.1 Module

Realisierung der Module aus §5 des Dokuments [[02-project_one]]. Alle Pfade sind relativ zu
`app\src\main\java\com\uip\oneapp\`, sofern nicht anders angegeben. Jedes MOD-xx aus dem Entwurf
hat unten einen Eintrag; alle Klassennamen wurden gegen die tatsächliche Verzeichnisstruktur
geprüft.

| MOD-xx | Klasse / Datei | Beschreibung der Realisierung |
| ------ | -------------- | ----------------------------- |
| MOD-01 | `network\Recorder.kt`, `RecorderConfig.kt`, `RecorderFactory.kt`, `FfmpegRtspRecorder.kt`, `FallbackRecorder.kt`, `HardwareBitmapRecorder.kt`, `LocalBitmapRecorder.kt`, `RecorderJournalMuxer.kt`, `RecorderRemux.kt`, `H264StreamJournal.kt` | Vollständig realisiert, alle im Paket `network\` (nicht in einem eigenen `recorder\`-Unterpaket). `RecorderFactory` wählt zur Laufzeit zwischen `HardwareBitmapRecorder` (HW-Encoder-Pfad) und `LocalBitmapRecorder`/`FallbackRecorder` (Software-Rückfall) — laut `PROJECT_STATUS.md` (2026-07-13) wurde ein früherer manueller Aufnahmeweg-Schalter ersatzlos entfernt, der Rückfall ist jetzt immer unsichtbar automatisch. `RecorderJournalMuxer`/`H264StreamJournal`/`RecorderRemux` realisieren die Crash-Recovery (REN-04): Ein Journal wird parallel zur MP4 geschrieben und nach einem Prozess-Kill beim nächsten Start remuxt. |
| MOD-02 | `export\OsdRenderer.kt`, `export\OsdSettings.kt`, `ui\components\OsdOverlay.kt`, `ui\components\InspectionOsd.kt`, `network\MeterSample.kt`, `network\MeterSampleV3.kt`, `network\MeterTrackReader.kt`/`MeterTrackReaderV3.kt`, `network\MeterTrackWriter.kt`/`MeterTrackWriterV3.kt`, `network\internal\LinearMeterCalculator.kt` | Realisiert. Es existieren **zwei Generationen** von Meter-Sample/-Track-Klassen (ohne „V3“-Suffix und mit „V3“-Suffix) parallel im selben Paket — laut Testverzeichnis (`LookupMeterTest`/`LookupMeterV3Test`, `MeterTrackWriterTest`/`MeterTrackWriterV3Test`) sind beide Generationen weiterhin unit-getestet. [AI-draft] Ob die V3-Variante die Vorgängerversion vollständig ablöst oder beide parallel produktiv sind (z. B. für unterschiedliche Firmware-/Protokollstände der ONE), ist aus der reinen Dateiliste nicht abschließend zu klären. `OsdRenderer`/`OsdSettings` liegen bewusst im Paket `export\`, nicht in einem eigenen OSD-Paket. |
| MOD-03 | `network\video\H264Encoder.kt`, `NalUtils.kt`, `OneVideoServer.kt`, `RtpTimestamp.kt`, `RtspVideoServer.kt`, `SocketTuning.kt`; `network\internal\V4L2Camera.kt`, `CameraFrameBus.kt`, `OneFrameCodec.kt`, `OneInternalHardwareService.kt`; `network\VideoSource.kt`, `RtspStreamTester.kt`, `CameraEncoderArbiter.kt` | Vollständig realisiert. `V4L2Camera.kt` ist die Kotlin-Seite der nativen NDK-Bridge (`app\src\main\cpp\v4l2bridge.c`) für den direkten `/dev/video0`-Zugriff im DIRECT-Modus. `CameraFrameBus` verteilt genau einen geöffneten `/dev/video0`-Stream an mehrere Abonnenten (Live-Vorschau + Recorder), `CameraEncoderArbiter` erzwingt die Ein-Encoder-Exklusivität auf dem einzigen HW-AVC-Codec des RK3588. |
| MOD-04 | `network\OneHardwareService.kt`, `OneHardwareModels.kt`, `HardwareModeDetector.kt`, `HardwareService.kt`, `NetworkDiscoveryService.kt`, `OneAutoConnector.kt`, `KnownOneStore.kt`, `ConnectivityMonitor.kt`, `WifiController.kt` | Vollständig realisiert. `HardwareService` ist das gemeinsame Interface für `OneInternalHardwareService` (DIRECT, MOD-03/seriell) und `OneHardwareService` (Remote/WiFi-Polling); `HardwareModeDetector` entscheidet zur Laufzeit, welche Implementierung Koin (`di\AppModule.kt`) verdrahtet. |
| MOD-05 | `network\internal\SondeFrequency.kt`; `ui\hardware\HardwareKeyBus.kt` | Realisiert. `HardwareKeyBus` ist ein eigenständiges Paket `ui\hardware\` (nicht `network\`) — bewusste Trennung zwischen Hardware-Übertragung (MOD-04) und UI-seitigem Tastatur-/Hardbutton-Dispatch. |
| MOD-06 | `data\repository\ProjectRepository.kt`, `DamageRepository.kt`, `NoteRepository.kt`, `DamagePresetRepository.kt`; `data\local\dao\ProjectDao.kt`, `DamageDao.kt`, `NoteDao.kt` | Vollständig realisiert. `ProjectRepository.deleteProjectCompletely()` löscht sowohl die Room-Zeilen (CASCADE) als auch alle zugehörigen Dateien — Beleg für REN-11/DSGVO-Löschanspruch (siehe §4.4). |
| MOD-07 | `export\ProjectExportService.kt`, `export\UsbExportService.kt`, `export\ReportLogo.kt` | Vollständig realisiert. PDF-Erzeugung über iText7 (§3), USB-Export über `StorageManager`/`MANAGE_EXTERNAL_STORAGE` (kein Fremd-Bibliothek nötig, Android-Bordmittel). |
| MOD-08 | `update\HttpUpdateService.kt`, `UpdateConfig.kt`, `UpdateInstaller.kt`, `UpdateInstallReceiver.kt`, `UpdateWorker.kt`, `UpdateModels.kt`; `data\repository\UpdateEventRepository.kt` | Vollständig realisiert. **Ergänzender Befund** gegenüber [[02-project_one]]: Es existiert zusätzlich `update\UpdateService.kt` — ein schlankes Interface (`checkForUpdate()`, `downloadAndInstall()`), das vermutlich von `HttpUpdateService` implementiert wird (nicht einzeln verifiziert, aber aus Namensgleichheit der Methoden naheliegend). Dieses Interface war im Entwurf nicht als eigene Klasse aufgeführt — [AI-draft] Nachtrag, kein Widerspruch. |
| MOD-09 | `bootstrap\AndroidDevicePolicyGateway.kt`, `DeviceFilePermissionBootstrap.kt`, `DeviceOwnerLocationProvisioner.kt`, `OneDeviceAdminReceiver.kt`, `PackageReplacedReceiver.kt`; `MainActivity.kt` (Kiosk-Teil) | Vollständig realisiert. `AndroidManifest.xml` bestätigt den Device-Admin-Receiver (`BIND_DEVICE_ADMIN`, `@xml/device_admin`), den dualen `MAIN`/`HOME`-Intent-Filter (Autostart-Anspruch) sowie `PackageReplacedReceiver` auf `MY_PACKAGE_REPLACED` (Auto-Neustart nach Self-Update, verhindert das im Code dokumentierte Hängenbleiben auf dem System-Sperrbildschirm). |
| MOD-10 | `network\AccessPointController.kt`, `AndroidSoftApStarter.kt`, `AndroidLohsStarter.kt`, `SoftAp.kt`, `WifiQr.kt`, `OneRemoteServer.kt`, `OneRemoteProtocol.kt` | Realisiert, mit einem Befund: [[02-project_one]] nennt zusätzlich eine Klasse `FallbackHotspotStarter` (auch als eigene Testdatei `FallbackHotspotStarterTest.kt` vorhanden). Im Quellbaum existiert **keine** gleichnamige Datei — Codekommentare in `SoftAp.kt`/`AccessPointController.kt` beschreiben stattdessen ein Zwei-Schichten-Modell aus bevorzugtem, privilegiertem `AndroidSoftApStarter` und öffentlichem Rückfall `AndroidLohsStarter` (`WifiManager.startLocalOnlyHotspot`), wobei Letzterer in Kommentaren selbst als „der Rückfall-Pfad des `FallbackHotspotStarter`“ referenziert wird. [AI-draft] Ohne Volltextsuche über den kompletten Dateiinhalt nicht abschließend klärbar, ob `FallbackHotspotStarter` eine (evtl. lokale/verschachtelte) Klasse innerhalb einer der beiden Dateien ist oder eine dritte, nicht gefundene Datei existiert. |
| MOD-11 | `maps\OfflineMapCatalog.kt`, `OfflineMapDownloadWorker.kt`, `OfflineMapManager.kt`, `OfflineMapRenderer.kt`; `network\NominatimService.kt`, `OsmStaticMapService.kt`, `WeatherApiService.kt`, `LocationService.kt`; `data\repository\WeatherPresetRepository.kt` | Vollständig realisiert. `OfflineMapDownloadWorker` läuft als WorkManager-Foreground-Service (`FOREGROUND_SERVICE_DATA_SYNC`-Permission im Manifest bestätigt). |
| MOD-12 | `network\AndroidEncryptedStorage.kt`, `KnownOneStore.kt`; `cloud\CloudAccountStore.kt` | Vollständig realisiert. `AndroidEncryptedStorage` implementiert `SecretKeyValueStore` mit `EncryptedSharedPreferences` + `MasterKey` (AES256-GCM, Android Keystore) und degradiert bei defektem Keystore-Eintrag geordnet auf No-op statt abzustürzen (Code-Kommentar). Details/Sicherheitsbewertung siehe §4.4. |
| MOD-13 | `ui\screens\*` (13 Screen-Ordner) + zugehörige `*ViewModel.kt`; `ui\navigation\NavGraph.kt`; `ui\theme\*`; `ui\components\*` | Vollständig realisiert — Verzeichnisstruktur deckt sich 1:1 mit SCR-01…SCR-13 aus [[02-project_one]] §6.1 (z. B. `screens\inspection\InspectionScreen.kt` + `InspectionControls.kt` + `MeterInput.kt` + `DamageDialog.kt` + `NoteDialog.kt` + `ImageAnnotationDialog.kt` für SCR-07). Zusätzlich vorhanden, im Entwurf nicht als eigene Ansicht geführt: `ui\localization\LocalizationManager.kt` (Mehrsprachigkeit, REF-26) und `ui\utils\AdaptiveUtils.kt` (Fensterklassen-Erkennung für die Rail-/BottomBar-Umschaltung aus §6.2). |

### 4.2 Interne Schnittstellen

Realisierung der Schnittstellen aus §5.1 des Dokuments [[02-project_one]], ergänzt um die
tatsächlichen Dateien.

| MOD-xx (Sender) | Ereignis / Signal | MOD-xx (Empfänger) | Handler / Slot | Datei |
| --------------- | ----------------- | ------------------- | --------------- | ----- |
| MOD-03 | Kameraframe (V4L2-Fan-out) | MOD-01, MOD-13 | Flow-/Observable-Konsum je Abonnent, genau ein `/dev/video0`-Open | `network\internal\CameraFrameBus.kt` |
| MOD-04 | Verbindungsstatus (`isConnected`, Probe-Ergebnis) | MOD-13 (`ConnectionViewModel`, `NetworkViewModel`) | StateFlow-Beobachtung | `network\OneAutoConnector.kt`, `network\HardwareModeDetector.kt` → `ui\screens\connection\ConnectionViewModel.kt`, `ui\screens\network\NetworkViewModel.kt` |
| MOD-05 | Hardbutton-Ereignis (Licht/Sonde/Aufnahme, F1–F8) | MOD-13 (`InspectionScreen`/`InspectionControls`) | Callback löst dieselbe Aktion wie der positionsgleiche Softbutton aus | `ui\hardware\HardwareKeyBus.kt` → `ui\screens\inspection\InspectionScreen.kt`, `InspectionControls.kt` |
| MOD-02 | Meter-Sample (zeitgenau) | MOD-01 (Sidecar `*.meter.jsonl`) | Schreibt Meter-Spur parallel zur Videoaufnahme | `network\MeterTrackWriter.kt`/`MeterTrackWriterV3.kt` → `network\RecorderJournalMuxer.kt` |
| MOD-08 | Update-Lifecycle-Ereignis (`UpdateEventType`) | MOD-06 (`UpdateEventRepository` → Room) | Persistiert Audit-Eintrag, `pruneOldEvents()` rotiert | `update\HttpUpdateService.kt`, `update\UpdateWorker.kt` → `data\repository\UpdateEventRepository.kt` |
| MOD-10 | Video-/Steuer-Frames (DIRECT-Modus) | Zweitgerät (ACT-11, außerhalb des Systems) | `OneRemoteProtocol` über TCP im Tablet-Hotspot | `network\OneRemoteServer.kt`, `network\OneRemoteProtocol.kt` |
| MOD-12 | Passphrase bekannter ONE (verschlüsselt) | MOD-04 (`OneAutoConnector`) | Auto-Reconnect-Zustandsmaschine liest/schreibt über `SecretKeyValueStore`-Interface | `network\AndroidEncryptedStorage.kt` → `network\KnownOneStore.kt` → `network\OneAutoConnector.kt` |

### 4.3 Externe Integrationen

Realisierung der Integrationen aus §7 des Dokuments [[02-project_one]].

| Externes System | Bibliothek | Klasse / Datei |
| --------------- | ---------- | -------------- |
| ONE-Hardware (Kamera/Meterzähler/Sonde/Licht) | Eigene NDK-Bridge (CMake/C) + kein Fremd-SDK | `app\src\main\cpp\v4l2bridge.c` + `network\internal\V4L2Camera.kt` (DIRECT, `/dev/video0`); `network\OneHardwareService.kt`/`network\internal\OneInternalHardwareService.kt` (seriell `/dev/ttyS5` bzw. HTTP/Socket-Polling Remote) |
| Live-Video-Streaming (RTSP/H.264) | `androidx.media3:media3-exoplayer(-rtsp)` 1.5.1 als Client, eigener Server-Stack | `network\video\RtspVideoServer.kt`, `OneVideoServer.kt`, `H264Encoder.kt` |
| Video-Overlay/-Encoding (Burn-in) | FFmpegKit `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` | `network\FfmpegRtspRecorder.kt`, `export\OsdRenderer.kt` |
| DrainQ-Update-Portal | OkHttp 4.12.0 + Gson 2.10.1, HTTPS `https://license.drainq.com/api/software/one/releases.<channel>.json` | `update\HttpUpdateService.kt`, `update\UpdateConfig.kt` |
| Kartendienste (OSM / Nominatim / Wetter) | OkHttp (REST); Mapsforge 0.21.0 (Offline-Rendering) | `network\NominatimService.kt`, `network\OsmStaticMapService.kt`, `network\WeatherApiService.kt`, `maps\OfflineMapRenderer.kt` |
| USB-Speicher | Android-Bordmittel (`StorageManager`/`StorageVolume`, `MANAGE_EXTERNAL_STORAGE`) | `export\UsbExportService.kt` |
| Zweitgerät (Dual-Mode-Fernanzeige) | Eigenes TCP-Protokoll; ZXing 3.5.3 (Encode) / `zxing-android-embedded` 4.3.0 (Scan) | `network\OneRemoteServer.kt`, `network\OneRemoteProtocol.kt`, `network\WifiQr.kt` |
| **MQTT** | `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` + `org.eclipse.paho.android.service:1.1.1` — Abhängigkeit im Build vorhanden (siehe §3), **keine Verwendung im Quellcode gefunden**. [AI-draft] Vermutlich toter Altbestand aus einer früheren Architekturphase (`CLAUDE.md` führt MQTT weiterhin als Stack-Bestandteil); zu entfernen oder Verwendung nachzuweisen. | — |

### 4.4 Sicherheitsmaßnahmen

Realisierung der Entscheidungen aus §8 des Dokuments [[02-project_one]]. Bewertung: KRITIS/NIS2/
ISO 27001 sind laut Analyse (CON-03) für die ONE nicht einschlägig; einziger verbindlicher Rahmen
ist die DSGVO. Diese Zeile wird daher unten nicht separat als „KRITIS-Compliance“ geführt, sondern
faktisch je Umsetzungsbereich dargestellt.

| Bereich (aus Entwurf §8) | Umsetzung | Klasse / Datei / Konfiguration |
| ------------------------ | --------- | ------------------------------- |
| Authentifizierung / Autorisierung | Kein Nutzer-Login im Kernprozess (Single-Operator-Kiosk). DrainQ-Cloud-Login ist bewusster Stub: `isLoggedIn()` liefert konstant `false`, `saveSession()`/`clearSession()` sind leere Implementierungen. **Umgesetzt = Stub laut Plan; kein OAuth-Flow vorhanden.** | `cloud\CloudAccountStore.kt`, UI-Seite `ui\screens\network\CloudLoginScreen.kt` |
| Schutz gespeicherter Daten (Zugangsdaten) | **Umgesetzt.** WLAN-Zugangsdaten bekannter ONEs liegen in `EncryptedSharedPreferences` mit `MasterKey` (AES256-GCM) im Android Keystore; bei defektem Keystore-Eintrag geordneter Reset statt Crash. | `network\AndroidEncryptedStorage.kt` (implementiert `SecretKeyValueStore`), genutzt von `network\KnownOneStore.kt` |
| Schutz gespeicherter Daten (Projektdaten) | **Nicht umgesetzt.** Projekt-/Schadens-/Auftraggeberdaten liegen unverschlüsselt in Room/SQLite (`oneapp_database`). Keine SQLCipher- oder vergleichbare DB-Verschlüsselungs-Dependency im Build gefunden. | `data\local\AppDatabase.kt`; **offen** — [AI-draft] CEO-Entscheidung ausstehend, ob für Feldgeräte gefordert |
| Schutz übertragener Daten | **Teilweise umgesetzt.** Update-/Wetter-/Nominatim-/OSM-Verkehr läuft ausschließlich über `https`-Endpunkte im Code (`UPDATE_PROXY_URL`, `NominatimService`, `WeatherApiService`, `OsmStaticMapService`). **Altlast bestätigt:** `AndroidManifest.xml` setzt `android:usesCleartextTraffic="true"` ohne begleitende `network_security_config` — technisch erlaubt das Klartext-HTTP für beliebige Domains, obwohl aktuell kein Code-Pfad dies nutzt. | `AndroidManifest.xml` (Attribut `usesCleartextTraffic`), `network\NominatimService.kt`, `network\WeatherApiService.kt`, `network\OsmStaticMapService.kt`, `update\UpdateConfig.kt` |
| Protokollierung / Audit-Log | **Umgesetzt für Updates, generell nicht umgesetzt.** `UpdateEventEntity`/`UpdateEventRepository` protokollieren den vollständigen Update-Lebenszyklus (`CHECK`/`DOWNLOAD_START`/`DOWNLOAD_OK`/`DOWNLOAD_FAIL`/`INSTALL_INITIATED`/`INSTALL_DONE`) inkl. Fehlermeldung, mit `pruneOldEvents()`-Rotation. Ein generelles Nutzeraktions-Logging existiert nicht (bewusste Nicht-Umsetzung, kein Compliance-Zwang laut CON-03). | `data\local\entity\UpdateEventEntity.kt`, `data\repository\UpdateEventRepository.kt` |
| Update-/Patch-Weg | **Teilweise umgesetzt.** Feste Bezugsquelle `https://license.drainq.com/api/software/one/` aus `BuildConfig` (siehe §3); lokal per Pref `proxyUrl` **ungeprüft überschreibbar** (keine Domain-Allowlist im Code gefunden). Integrität: SHA-256-Hash-Vergleich der APK gegen den Manifest-Wert. **Keine kryptographische Signaturprüfung** des Manifests. Installation über `PackageInstaller`-Session mit Nutzerbestätigung (`REQUEST_INSTALL_PACKAGES`), kein Silent-Install. | `update\HttpUpdateService.kt` (Hash-Verifikation), `update\UpdateInstaller.kt` + `update\UpdateInstallReceiver.kt` (`PackageInstaller`), `AndroidManifest.xml` (`REQUEST_INSTALL_PACKAGES`) |
| Umgang mit personenbezogenen Daten (DSGVO) | **Umgesetzt.** Auftraggeber-/Standort-/Projektdaten bleiben lokal in Room, keine automatische Cloud-Synchronisation (Cloud-Login ist Stub, s. o.). `deleteProjectCompletely()` löscht DB-Zeilen (CASCADE) **und** alle zugehörigen Dateien (Fotos/Videos/Audio/Berichte/Exporte) unwiderruflich. GPS-Standort nur nach OS-Berechtigung (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`, im Manifest bestätigt) und aktiver Nutzeraktion. Ein über die OS-Berechtigungsabfrage hinausgehender expliziter Einwilligungsdialog ist im UI-Code nicht belegt. | `data\repository\ProjectRepository.kt` (`deleteProjectCompletely()`), `AndroidManifest.xml` (Location-Permissions); [AI-draft] Einwilligungsdialog nicht verifiziert |
| Bezugsquellen-Kontrolle (Signierung, Repo-Ebene) | [AI-draft, Befund über den Entwurfsrahmen hinaus] `oneapp-release.keystore` liegt unversioniert(?) im Repository-Root; `signingConfigs.release` fällt ohne `KEYSTORE_PATH`-Env auf die Debug-Signierung zurück. Nicht Teil von [[02-project_one]] §8, aber als Realisierungsbefund relevant für eine künftige Sicherheitsbewertung. | `app\build.gradle.kts` (`signingConfigs`), Repo-Root `oneapp-release.keystore` |

---

## 5. Testen

### 5.1 Realisierung der Unit-Tests

Realisierung der Tests aus §9.2 des Dokuments [[02-project_one]]. Alle Testdateien liegen unter
`app\src\test\java\com\uip\oneapp\...` und laufen als reine JVM-Tests (JUnit4/Robolectric, siehe
§3) ohne Emulator. Die inhaltliche Auswertung der Testfälle (bestanden/fehlgeschlagen, Abdeckung)
ist Gegenstand von [[04-testing_one]]; diese Tabelle bildet nur die **Realisierung** (Klasse →
Testdatei) ab.

| Klasse | Testdatei | Testfälle |
| ------ | --------- | --------- |
| `network\internal\LinearMeterCalculator.kt` | `network\internal\LinearMeterCalculatorTest.kt` | Details in [[04-testing_one]] |
| `network\internal\SondeFrequency.kt` | `network\internal\SondeFrequencyTest.kt` | Details in [[04-testing_one]] |
| `network\internal\CameraFrameBus.kt` | `network\internal\CameraFrameBusTest.kt` | Details in [[04-testing_one]] |
| `network\internal\OneFrameCodec.kt` | `network\OneFrameCodecTest.kt` | Details in [[04-testing_one]] |
| `update\HttpUpdateService.kt`/`UpdateConfig.kt` | `update\UpdateServiceTest.kt`, `update\UpdateE2ETest.kt` | Details in [[04-testing_one]] |
| `data\local\AppDatabase.kt` | `data\local\AppDatabaseCreateTest.kt` | Details in [[04-testing_one]] |
| `export\OsdRenderer.kt` | `export\OsdRendererTest.kt` | Details in [[04-testing_one]] |
| `network\RecorderJournalMuxer.kt`/`RecorderRemux.kt` | `network\RecorderJournalMuxerTest.kt`, `network\RecorderRemuxTest.kt`, `network\RecoveredMarkerTest.kt` | Details in [[04-testing_one]] |
| `network\video\H264Encoder.kt`/`NalUtils.kt`/`RtpTimestamp.kt`/`SocketTuning.kt` | `network\video\H264EncoderFormatTest.kt`, `NalUtilsTest.kt`, `RtpTimestamperTest.kt`, `SocketTuningTest.kt` | Details in [[04-testing_one]] |
| `network\H264StreamJournal.kt` | `network\H264JournalCodecTest.kt` | Details in [[04-testing_one]] |
| `network\HardwareBitmapRecorder.kt`/`LocalBitmapRecorder.kt` | `network\HardwareBitmapRecorderStateTest.kt`, `network\LocalBitmapRecorderStateTest.kt` | Details in [[04-testing_one]] |
| `network\HardwareModeDetector.kt` | `network\HardwareModeDetectorTest.kt` | Details in [[04-testing_one]] |
| `network\AccessPointController.kt`/`AndroidLohsStarter.kt`/`SoftAp.kt` | `network\AccessPointControllerTest.kt`, `AccessPointSpecTest.kt`, `FallbackHotspotStarterTest.kt`, `SoftApSpecTest.kt` | Details in [[04-testing_one]] — Zuordnung `FallbackHotspotStarterTest.kt` → Quellklasse siehe Befund in §4.1/MOD-10 |
| `network\CameraEncoderArbiter.kt` | `network\CameraEncoderArbiterTest.kt` | Details in [[04-testing_one]] |
| `network\KnownOneStore.kt` | `network\KnownOneStoreTest.kt` | Details in [[04-testing_one]] |
| `network\OneAutoConnector.kt` | `network\OneAutoConnectorTest.kt` | Details in [[04-testing_one]] |
| `network\OneRemoteProtocol.kt`/`OneRemoteServer.kt` | `network\OneRemoteProtocolTest.kt`, `network\OneRemoteServerTest.kt` | Details in [[04-testing_one]] |
| `network\MeterTrackWriter.kt`/`MeterTrackWriterV3.kt` | `network\MeterTrackWriterTest.kt`, `MeterTrackWriterV3Test.kt` | Details in [[04-testing_one]] |
| `network\MeterTrackReader.kt`/`MeterTrackReaderV3.kt` (Leseweg) | `network\LookupMeterTest.kt`, `LookupMeterV3Test.kt` | Details in [[04-testing_one]] |
| `network\FfmpegRtspRecorder.kt` | `network\FfmpegRtspRecorderTest.kt` | Details in [[04-testing_one]] |
| `network\WifiQr.kt` | `network\WifiQrTest.kt` | Details in [[04-testing_one]] |
| `bootstrap\DeviceOwnerLocationProvisioner.kt` | `bootstrap\DeviceOwnerLocationProvisionerTest.kt` | Details in [[04-testing_one]] |
| `ui\components\OsdOverlay.kt` | `ui\components\OsdOverlayTest.kt` | Details in [[04-testing_one]] |
| `ui\screens\home\StorageInfo.kt` | `ui\screens\home\StorageInfoTest.kt` | Details in [[04-testing_one]] |
| `ui\screens\inspection\InspectionControls.kt`/`MeterInput.kt` | `ui\screens\inspection\InspectionControlsTest.kt`, `MeterInputTest.kt` | Details in [[04-testing_one]] |
| `ui\screens\projects\CameraTypePrefill.kt`/`InspectionDateGuard.kt` | `ui\screens\projects\CameraTypePrefillTest.kt`, `InspectionDateGuardTest.kt` | Details in [[04-testing_one]] |
| Persistenz-Verhalten Foto-/Videoaufnahme (Repository-Ebene) | `data\repository\CapturePersistenceTest.kt` | Details in [[04-testing_one]] |
| Recorder-Format-/OSD-Entscheidung (übergreifend) | `network\RecorderFormatAndOsdDecisionTest.kt` | [AI-draft] In [[02-project_one]] §9.2 nicht als eigene Zeile geführt — Nachtrag, keine dedizierte Quellklasse identifiziert (vermutlich Integrationstest über `RecorderFactory`) |

---

## 6. Zusammenfassung

**Realisierte Module:** Alle 13 Module (MOD-01…MOD-13) aus [[02-project_one]] §5 sind im Code
vollständig auffindbar und den dort benannten Klassen zuordenbar (siehe §4.1). Kein MOD-xx aus dem
Entwurf ist ohne Code-Entsprechung. Zwei Klassen aus dem Entwurf (`FallbackHotspotStarter` in
MOD-10, konkrete Implementierungsklasse hinter `UpdateService`-Interface in MOD-08) konnten nicht
1:1 auf eine gleichnamige Datei abgebildet werden — siehe Befunde unten.

**Abweichungen vom Entwurf:**
1. **MQTT-Aussage zu korrigieren:** [[02-project_one]] §7 behauptet, die Paho-MQTT-Abhängigkeit
   fehle vollständig im Build. Der direkte Blick in `app\build.gradle.kts` (§3 dieses Dokuments)
   zeigt das Gegenteil: Die Abhängigkeit ist vorhanden. Nur die **Verwendung** im Quellcode konnte
   nicht belegt werden (keine Datei mit „Mqtt“ im Namen). Empfehlung: Entwurf §7 im Zuge des
   nächsten Audits korrigieren und CEO-Entscheidung einholen (Abhängigkeit entfernen oder
   Verwendungsnachweis erbringen).
2. **`FallbackHotspotStarter` (MOD-10):** Im Entwurf als eigene Klasse geführt, im Quellbaum nicht
   als eigene Datei gefunden — nur eine Testdatei (`FallbackHotspotStarterTest.kt`) und Code-
   Kommentare, die auf ein Zusammenspiel von `AndroidSoftApStarter` (privilegiert) und
   `AndroidLohsStarter` (öffentlicher Rückfall) hindeuten. [AI-draft] Nicht abschließend geklärt,
   ob es sich um eine verschachtelte/lokale Klasse handelt.
3. **`UpdateService`-Interface (MOD-08):** Im Code vorhanden (`update\UpdateService.kt`), im
   Entwurf nicht separat aufgeführt. Reine Ergänzung, kein Widerspruch — vermutlich die
   Abstraktion hinter `HttpUpdateService`.
4. **Zwei Meter-Generationen (MOD-02):** `MeterSample`/`MeterTrackReader`/`-Writer` existieren
   sowohl ohne als auch mit „V3“-Suffix, beide weiterhin unit-getestet. Der Entwurf benennt beide,
   ohne den Anlass der Parallelführung zu erläutern — [AI-draft] Klärung, ob V2 (bzw. Vorgänger)
   noch produktiv gebraucht wird oder entfernt werden kann, steht aus.
5. **`CLAUDE.md` ist veraltet** (siehe Auftragshinweis): nennt libVLC als RTSP-Player (im Code seit
   „Phase 7“ entfernt, siehe §3) und „Clean Architecture“ (tatsächlich MVVM ohne Use-Case-Schicht).
   Diese Feststellung bestätigt bereits [[02-project_one]] und wird hier durch direkten
   Dependency-Vergleich erhärtet.
6. **Versionsstand:** Der Repository-Root enthält bereits eine veröffentlichte Beta
   `DrainQ-ONE_0.5.15-beta_515.apk` — einen Schritt neuer als der in [[01-analysis_one]]/
   [[02-project_one]] referenzierte Analysestand 0.5.14/514. Reine Standfeststellung, keine
   inhaltliche Abweichung der Architektur.
7. **Keine strukturellen MOD-Lücken:** Alle MOD-xx haben Code; kein Code-Bereich wurde gefunden,
   der ohne zugehöriges MOD-xx aus dem Entwurf existiert (die zusätzlich gefundenen Dateien —
   `LocalizationManager.kt`, `AdaptiveUtils.kt`, `UpdateService.kt` — sind Detailergänzungen
   innerhalb bestehender Module, keine neuen Module).

**Stand der Unit-Tests:** Realisierung vollständig nachgewiesen — jede in [[02-project_one]] §9.2
benannte Testklasse existiert unter `app\src\test\java\com\uip\oneapp\...` (siehe §5.1), plus zwei
im Entwurf nicht geführte Ergänzungen (`RecorderFormatAndOsdDecisionTest.kt`,
`FfmpegRtspRecorderTest.kt`). Tatsächliche Lauf-Ergebnisse (bestanden/fehlgeschlagen, Anzahl) sind
nicht Gegenstand dieses Dokuments — Details in [[04-testing_one]].

---

## Glossar der Implementierungsbegriffe

**Modul** — logisch abgegrenzter Systemteil aus dem Entwurf (MOD-xx); abgebildet als Klasse, Datei oder zusammengehörige Dateigruppe.

**Interne Schnittstelle** — Kommunikationsmechanismus zwischen Modulen (Signale/Slots, Events, Messages o. Ä.); definiert in §5.1 des Entwurfs.

**Externe Integration** — Verbindung zu einem System außerhalb der Anwendung; definiert in §7 des Entwurfs.

**Realisierungsumgebung** — Gesamtheit der Werkzeuge, Bibliotheken und Konfigurationen, die zum Bauen und Ausführen des Systems erforderlich sind.

**Artefakt** — ausführbares Ergebnis des Builds: Executable, Paket, Archiv oder Container-Image.

---

*Nach Freigabe des Dokuments: `03-audit_one.md` anlegen.*

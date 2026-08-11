> ÜBERHOLT am 2026-08-05 durch OFFENE_PUNKTE.md. Nur noch als Nachweis aufbewahrt.

# Offene TODOs & Fix-Bilanz — 2026-07-01 (autonomer CC-Lauf, Branch `feature/dual-mode`)

**Auftrag:** Code analysieren, alles ohne Hardware/Gerät und ohne Produktentscheidung Behebbare fixen,
Build+Tests grün halten, offene Punkte dokumentieren. Basis: 2 Explore-Reviews (network-Paket + Rest-App),
TODO/FIXME-Sweep, Compiler-Warnungs-Sweep.

---

## (a) Was behoben wurde (19 Commits, thematisch)

### Vorarbeit der Vorsession committet
| Commit | Inhalt |
|---|---|
| `a50a226` | **OneHardwareService writeMutex** — alle Socket-Writes serialisiert (Ursache Backlog #3 „Licht-Slider unsauber im Remote-Modus"); Licht/Frequenz übernehmen Sollwert sofort lokal (letzter Wert gewinnt). |
| `10d9b53` | **Backlog #1 erledigt:** Debug-Log-Panel aus dem Verbindungs-Screen entfernt (LogCard raus, internes Log bleibt). |

### Nebenläufigkeit / Ressourcen-Lecks (network)
| Commit | Inhalt |
|---|---|
| `b75eac5` | **OneRemoteServer:** stop() schließt jetzt alle Client-Sockets (blockierte `input.read()`-Handler liefen ewig weiter); Stale-Loop nach stop()+start() behoben (Schleifen lasen die mutable `scope`-Property → sahen den NEUEN Scope); acceptLoop-finally schloss nach Restart das Socket der neuen Instanz. |
| `5cee1d7` | **Hotspot-Starter (W3a):** SoftAP DISABLED-während-Hochlauf entschärfte den 6-s-Fallback-Timer nicht → Phantom-„Aktiv"/QR ohne AP; SoftApCallback-Registrierung wird bei jedem Terminal-Zustand abgemeldet (leakte pro Fehlversuch); LOHS „no-credentials" ließ einen laufenden Hotspot ohne Handle zurück → Reservation wird geschlossen. |
| `179b91c` | **OneHardwareService:** alle copy-dann-set auf `_hardwareState`/`_logMessages` → atomares `update{}` (TCP-Reader vs. UI-Thread verlor Updates); `removeLast()`-Crash (s. u.). |
| `80c70ef` | **Interner HW-Pfad:** `stopPolling()` schloss den seriellen fd, während `nativeReadSerial` noch bis ~100 ms blockieren kann (close-while-read + fd-Reuse) → close erst nach Job-join; rxLoop Stale-Scope-Fix; `@Volatile` auf serialFd/connected/scope/rxJob. **V4L2Camera:** start() direkt nach stop() öffnete `/dev/video0` (exklusiv), bevor der alte Job geschlossen hatte → Kamera blieb tot; jetzt join vor nativeOpen + nativeClose in finally. |
| `c610afa` | **Recorder:** `FFmpegKit.cancel()` ohne Id brach ALLE Sessions ab (auch Export-Encodes) → gezielter Cancel per sessionId; FfmpegRtspRecorder-State-Wedge (RECORDING nach schnell scheiternder Session für immer hängend) → State vor executeAsync; LocalBitmapRecorder FIFO-Open-Fehler ließ FFmpeg ewig warten → Session-Abbruch + Cleanup. |
| `bd9a48a` | **RtspVideoServer** Socket-Leak bei stop-vor-bind + fehlendes `@Volatile`; **WifiController.scan()** Doppel-Resume-Crash (atomarer Guard); **NetworkDiscoveryService** `_isScanning` hing bei Exception/Cancel auf true. |

### Crashes / Funktionsfehler (UI & App)
| Commit | Inhalt |
|---|---|
| `179b91c`/`d28361c` | **`removeLast()`-Crash:** compileSdk 35 + Kotlin 1.9 bindet an `java.util.List.removeLast` (erst API 35) → `NoSuchMethodError` auf der Android-12-ONE, sobald ein Log 50 Einträge überschreitet. Beide Stellen (OneHardwareService, ConnectionViewModel) ersetzt. |
| `d28361c` | **ConnectionViewModel:** jeder refresh/scan startete weitere NIE endende StateFlow-Collectors im viewModelScope (Leak + N-fach-Updates) → Collectors genau einmal in init. |
| `313727e` | **Video-Player:** bei rtspUrl-Wechsel wurde der neue ExoPlayer nie an die TextureView/PlayerView gebunden (schwarzes Bild) und `onPlayerReady` feuerte nie für die neue Instanz (Pause/Resume steuerte einen released Player). FfmpegVideoPlayer + VideoPlayer. |
| `35fd3f5` | **Foto/Schaden aus Video-Playback funktionierte nie:** PlayerView rendert default in eine SurfaceView, captureFrame() sucht eine TextureView → immer null. Neues Layout `player_view_texture.xml` (`surface_type=texture_view`). |
| `89695b3` | DamageDialog speicherte still leeren Schadenstyp (Presets laden asynchron); MediaPlayer-Crashes bei defekter Audio-Datei (NoteDialog, ProjectDetailScreen); weatherError-Snackbar feuerte nur einmal; setCompanyLogo lief ungeschützt auf Main; UpdateEventRepository `dao!!`-Crashes. |
| `01a44e6` | su-chmod-Bootstrap: unbegrenztes `waitFor()` in Application.onCreate → 3-s-Timeout + destroyForcibly (ANR-Schutz). |

### Speicher / Jank
| Commit | Inhalt |
|---|---|
| `d6dd7c1` | PdfPreviewDialog recycelt Seiten-Bitmaps (~90 MB bei 20 Seiten); MapPickerDialog Tile-Cache auf 96 Tiles begrenzt (wuchs unbegrenzt); ImageAnnotationDialog dekodierte Multi-MB-JPEGs synchron IN der Composition und rief onDismiss() mitten im Compose → produceState auf IO. |

### Dead Code / Projektregeln / Warnungen
| Commit | Inhalt |
|---|---|
| `313727e`/`5f82e84` | **VideoOverlayProcessor.kt gelöscht** (0 Aufrufer) + InspectionScreen-Reste (overlayEntries sekündlich befüllt aber nie gelesen, unerreichbarer „Video wird verarbeitet"-Dialog, lastRecordedFilePath, SmallActionButton). |
| `ede523f` | **L10n-Nachzug:** OfflineMapsScreen/-ViewModel war komplett fest deutsch → auf `S()`-Keys umgestellt; ProjectDetail-Toasts, InspectionScreen „+N weitere"/„Projekt", ProjectForm-GPS-Hinweis/OK, PDF-Render-Fehler; neue Keys de+en; hartkodiertes Grün → `ui/theme AnnotationGreen`. |
| `3081836` | **S() Sprachabo robust:** der kollektierte Sprach-State wurde nie GELESEN → Compose-Subscription auf den Sprachwechsel nicht garantiert; jetzt `getString(key, lang)`. Tote Variablen (HomeScreen, RtspVideoServer-Parameter); Deprecations: AutoMirrored-Icons, HorizontalDivider, progress-Lambda-Overloads. |
| `dabb147` | docs: Status/Backlog/Plan-Fortschreibung der Vorsessions committet. |
| Spike | `app/src/main/java/com/uip/oneapp/spike/` (Wegwerf-Code W3c, unreferenziert, kompilierte aber ins APK) → nach `tools/_spike/src/` verschoben (untracked, Dateien erhalten). |

---

## (b) Offene TODOs

### Im Code lösbar (Rest)
| Prio | Stelle | Punkt |
|---|---|---|
| M | `di/AppModule.kt:77` | `runBlocking { settingsStore.data.first() }` blockiert den Main-Thread bei der ersten DI-Auflösung (App-Start). Fix = async Transport-Auswahl (Architektur-Umbau der HardwareService-Erzeugung), nicht per Quick-Fix. |
| M | `ui/screens/inspection/InspectionScreen.kt` (~430–490, doPhoto/doDamage) | Bitmap-Grab + OSD-Render + JPEG-Compress + Datei-Write synchron auf dem Main-Thread → sichtbarer Jank bei Foto/Schaden während laufender Aufnahme. Umbau: Grab auf Main, Compress/Write auf Dispatchers.IO — mit Bedacht (Reihenfolge Foto→Dialog). |
| M | `ui/screens/settings/UpdateSection.kt:253–292` | „Installing"-Stage unerreichbar (Dialog wird davor geschlossen); Download läuft in `rememberCoroutineScope` → stirbt beim Verlassen des Screens; Byte-Fortschritt immer 0/0. Fix = Download in ViewModel/WorkManager + echtes Progress-Wiring (deckt sich mit P1-Rest „Update-Byte-Fortschritt"). |
| N | `LocalizationManager.kt` | Tote Keys nach UI-Entfernungen: `log`, `no_activity_yet` (LogCard weg), `video_processing_title/message` (Dialog weg) — in 35 Sprachblöcken; beim nächsten L10n-Lauf mit ausräumen. Neue Keys (`offline_maps_*` u. a.) existieren nur de+en → in die 33 Restsprachen nachziehen (Portal-Strecke). |
| N | `ui/screens/projects/MapPickerDialog.kt:149ff` | `offlineFallback`-Bitmap wird nie recycelt; temp `map_picker_*.jpg` bleibt im cacheDir liegen. |
| N | Deprecations (Rest) | `menuAnchor()` (6 Stellen → MenuAnchorType-Overload), `SOFT_INPUT_ADJUST_RESIZE` (DamageDialog/NoteDialog), `LocalLifecycleOwner` (Move nach lifecycle-compose), `statusBarColor/navigationBarColor` (Theme.kt:146f), `DefaultMediaCodecAdapterFactory`-Ctor (VideoPlayer.kt:76). `WifiConfiguration` (WifiController) ist für API<30 nötig → bleibt. |
| N | `ui/components/InspectionOsd.kt:33f` | Parameter `sondeMode`/`lightLevel` ungenutzt — laut Doc-Kommentar bewusste Backwards-Compat; beim nächsten InspectionScreen-Refactor entfernen. |
| N | `.gitattributes` fehlt weiterhin | W0-Punkt „`* text=auto eol=lf` setzen" (beendet das CRLF-Phantom dauerhaft) — bewusst NICHT im autonomen Lauf gemacht: normalisiert ~190 Dateien = riesiger Diff, gehört in einen eigenen, abgestimmten Commit. |

### Braucht Gerät / Hardware (nur vermerkt, nicht angefasst)
| Prio | Stelle | Punkt |
|---|---|---|
| H | `network/AccessPointController.kt:105` | **TODO(device, Welle 5):** Hotspot-Abnahme auf der ONE — SoftAP OHNE Standort-Prompt, gebrandete SSID, QR-Scan → Tablet joint → Video/Telemetrie/Steuerung. Inkl. Klärung priv-app-Allowlist vs. Plattform-Signatur (`docs/SOFTAP_WERKS_PRIVILEG.md`). |
| H | `network/video/RtspVideoServer.kt:26f` | TODO(device) Tablet↔ONE-Video-Roundtrip über WLAN (W5); TODO(harden) mehrere Clients, RTCP, Auth. RTSP :8554 vs. :554 am Gerät klären. |
| H | `network/video/OneVideoServer.kt:26/28`, `H264Encoder.kt:26/36` | TODO(perf) Zero-Copy-Pfad (V4L2→HW-Decoder→Surface→HW-Encoder); on-device verifizieren, ob der RK3588-Encoder Sub-Sekunden-GOP honoriert; On-Device-Abnahme lokale Anzeige + Tablet parallel. |
| H | `network/OneRemoteProtocol.kt:221` | Remote-Akku-Telemetrie (Tablet zeigt ONE-Akku) = TODO(device), Welle 5. |
| H | `network/internal/CameraFrameBus.kt:46` | TODO(W3d) Voll-Arbitrierung lokale UI ↔ Tablet auf EINEM Bus („Encoder läuft weiter bei Screen aus"). |
| H | Backlog #3 / `a50a226` | Licht-Slider-Fix (writeMutex) ist im Code — Wirksamkeit im Remote-Betrieb am Gerät verifizieren. |
| H | PROJECT_STATUS „morgen zuerst" | Kamerakopf C10/C18-Erkennung (OEM-App gegenprüfen; seriell kommt kein Group 23/24). |
| H | Louis-Merge-Gate | On-Device-Abnahme W1 (Dialoge inkl. Soft-Tastatur) + W3 (Kabel mehrfach ziehen) auf `feature/louis-feedback` vor Merge→master. |
| M | `OneRemoteServer.kt` (Kommentar Empfangs-Schleife) | Härtung SDK-Header-Framing statt Brace-Matching — erst nach echtem Byte-Strom-Mitschnitt am Gerät (W5). |

### Braucht Entscheidung (Produkt/CEO)
| Prio | Punkt |
|---|---|
| M | **SD/HD-Toggle:** raus (HD fix) oder SD echt = 720×576? (PROJECT_STATUS #2, seit 04.06. offen) |
| M | **STA+AP-Exklusivität:** Hotspot an ⇒ kein WLAN-Internet auf der ONE (Cloud/Update). UX-/Produkt-Entscheid (PLAN_DUAL_MODE Risiken). |
| M | **W3b OPEN DECISION** (`OneRemoteServer.kt` Kopf): Client-Keepalive setzt beim ersten Verbinden den lokalen Licht-/Frequenz-Stand auf den Tablet-Stand (typ. Licht=0). Echte Master/Slave-Arbitrierung = W3d. |
| M | **BETA-Sprachauswahl:** auf de+en kürzen oder alle 35 lassen? (CEO-Frage offen seit 07.06.) |
| N | Settings je Modus: „Bildschirmhelligkeit im Tablet-Modus ausblenden?" (borderline, Thomas bestätigen — Backlog-Detail zu #2). |
| N | `cloud/CloudAccountStore.kt:26/34` — TODO(cloud) OAuth/Token-Ablage: DrainQ-Cloud-Login „später" (beschlossen), Umfang offen. |

### Extern (BWELL / Louis)
| Prio | Punkt |
|---|---|
| H | **BWELL-Provisionierung Werks-Image:** priv-app-Allowlist (NETWORK_SETTINGS/TETHER_PRIVILEGED) ODER Plattform-Signatur für den SoftAP-Pfad (`docs/SOFTAP_WERKS_PRIVILEG.md`); langfristig Variante 7.1 (App plattformsigniert nach /system/priv-app) → ersetzt den su-chmod-Bootstrap. |
| M | **Louis-Feldfeedback:** Reply-Entwurf liegt in Outlook; nächste Feldtest-Runde nach Beta-Release (RELEASE_LOUIS_BETA_RUNBOOK.md). |
| N | PDF-Handbuch: `generate_manual.js` lädt `barlow_*.ttf`, vorhanden ist nur `inter_*` → Skript auf Inter umstellen oder Barlow außerhalb `res/font` laden; bis dahin docx-Generator nutzen. |

---

## (c) Build-/Test-Status + Commits dieses Laufs

**Build:** `.\gradlew assembleDebug test` **GRÜN** (letzter Lauf nach `3081836`, 2026-07-01). Nach jedem Batch gebaut+getestet; ein Zwischenfehler (fehlender `HorizontalDivider`-Import) sofort behoben.

**Commits (chronologisch):**
```
a50a226 fix(dual-mode): Socket-Writes serialisiert (writeMutex) — Licht-Slider im Remote-Modus
10d9b53 cleanup(dual-mode): Debug-Log-Panel aus dem Verbindungs-Screen entfernt (Backlog #1)
b75eac5 fix(dual-mode): OneRemoteServer — Client-Sockets bei stop() schließen, Stale-Loops nach Restart
5cee1d7 fix(dual-mode): Hotspot-Starter — Phantom-Aktiv-Timer + Reservation-/Callback-Leaks (W3a)
179b91c fix(dual-mode): OneHardwareService — atomare StateFlow-Updates + removeLast-Crash
d28361c fix: ConnectionViewModel — Endlos-Collector-Leak + removeLast-Crash
80c70ef fix(dual-mode): interner HW-Pfad — fd-Close-Race, V4L2-Restart-Race, fehlende @Volatile
c610afa fix: Recorder — gezielter Session-Cancel statt FFmpegKit.cancel() global + State-Wedge
bd9a48a fix: kleinere Netz-Races — RTSP-Socket-Leak, Scan-Doppel-Resume, isScanning-Haenger
313727e fix: Video-Player — neuer ExoPlayer wird bei URL-Wechsel nie an die View gebunden
89695b3 fix: UI-Kleinfehler — leerer Schadenstyp, MediaPlayer-Crashes, Einmal-Snackbar, Logo-IO
5f82e84 cleanup: tote Overlay-Nachverarbeitung aus InspectionScreen entfernt
ede523f l10n: hardcodierte UI-Strings auf S()-Keys umgestellt + Annotations-Gruen ins Theme
01a44e6 fix: su-chmod-Bootstrap mit 3-s-Timeout statt unbegrenztem waitFor()
35fd3f5 fix: Foto/Schaden aus Video-Playback funktionierte nie — PlayerView auf TextureView
d6dd7c1 fix: Speicher/Jank — PDF-Bitmaps recyclen, Tile-Cache begrenzen, Decode raus aus der Composition
3081836 cleanup: Compiler-Warnungen — S()-Sprachabo robust, tote Variablen, Deprecations
dabb147 docs: Status/Backlog/Plan-Fortschreibung (Architektur-B-Befund, Louis-W0-W8, W3-Unterteilung)
```
(+ dieser Bericht als eigener docs-Commit.)

**Nicht angefasst (bewusst):** CRLF-Churn über ~190 Dateien (kein `git add -A`), `.claude/settings.local.json`,
alle TODO(device)/Welle-5-Punkte, Kamerakopf-Erkennung, SD/HD-Toggle, BWELL-Provisionierung, Louis-Feldfeedback.
Kein Merge nach master, kein Tag, kein Force-Push. Branch wurde nicht gepusht (Push nicht Teil des Auftrags).

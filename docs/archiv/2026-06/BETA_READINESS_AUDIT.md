# BETA-Readiness-Audit — drainq.one (ONE-Schiebekamera)

**Stand:** 2026-06-06 · **Branch (Basis):** `feature/network-settings` · **Quick-Win-Branch:** `feature/beta-hardening`
**Methode:** Schichtweises Voll-Audit (15 Schichten, 81 Befunde) + adversariale Verifikation jedes „nicht-verdrahtet/läuft-ins-Leere"-Befunds am echten Code (18 Kernbefunde gegengeprüft, 0 widerlegt). Jede Aussage ist mit Datei:Zeile + Zitat belegt.
**Build:** `assembleDebug` grün vor und nach den Quick-Wins (BUILD SUCCESSFUL, inkl. nativem v4l2bridge).

> KRITIS/NIS2 spielen hier bewusst keine Rolle (Handwerker-Sanitär). Sicherheits-Hygiene nur als normaler Qualitätsbefund am Rande, kein BETA-Gate.

---

## 1. Executive Summary

**BETA-fähig: bedingtes JA.** Der **Kern-Workflow** *Live-Bild → erfassen (Foto/Schaden/Notiz/Video) → speichern → wiederfinden → PDF/ZIP exportieren & teilen* ist code-seitig **funktional vollständig und solide verdrahtet**. Die drei gravierendsten Feldtest-Showstopper sind **behoben**:

- **#6/#8 Schnellaufnahme:** gelöst — `effectiveProjectId = projectId ?: getOrCreateQuickProjectId()` legt einen Tages-Bucket an; die alten stillen `if(projectId==null) return`-Sackgassen existieren nicht mehr (`InspectionScreen.kt:122`, `ProjectRepository.kt:35-45`).
- **#7 Zurück aus Inspektion:** gelöst — immer sichtbarer Zurück-Button + POWER-Langdruck-Beenden-Dialog (`InspectionScreen.kt:581-604`, `877-892`).
- **#4 Tastatur fährt nicht ein:** im Kern-Dialog (NoteDialog) bereits korrekt; DamageDialog + Settings + Projektsuche in diesem Lauf nachgezogen (Quick-Wins).
- **#1/#3 Hardtasten/Softbutton-Leiste + FAB:** UI-seitig vollständig verdrahtet (`HardwareKeyBus` → `runHwButton`, feste Leiste, Extended-FAB).

**Was BETA noch im Weg steht (keine Kern-Workflow-Blocker, aber „sieht funktionsfähig aus, ist es nicht"):**

| # | Hoch-Punkt | Kern |
|---|---|---|
| B1 | **XML-Export behauptet „DIN EN 13508-2 kompatibel", ist aber ein proprietäres Flach-Schema** ohne ISYBAU-Struktur; verwirft alle strukturierten Schadensfelder. Keine Fachsoftware kann es importieren. | falsches Versprechen |
| B2 | **Self-Update funktioniert nicht** (Release-URL → 404 + kein Receiver für den Installer-Status). „Nach Updates suchen" läuft still ins Leere, Installation hängt nach Download. | stiller Fehlschlag |
| B3 | **Kiosk-Schalter sperrt nicht wirklich** — nur System-Bars werden versteckt, kein LockTask. Wischgeste verlässt die App weiterhin (Feldtest #5), solange die ONE nicht als Device-Owner provisioniert ist. | trügt |
| B4 | **Tote „Wirkung"-Schalter:** SD/HD-Toggle und Hardware-OSD-Toggle werden gespeichert, aber von keinem Recorder/Konsumenten gelesen. | nicht verdrahtet |

**Zahlen:** 81 Befunde — **1 (Auditor-)Showstopper → auf Hoch korrigiert** (Self-Update blockiert *nicht* den Kern-Workflow), **4 Hoch**, **21 Mittel**, **55 Niedrig**. Davon **21 nur am Gerät endgültig verifizierbar** (HW-Tasten, Sonde, V4L2-Recording-Datei, Kamerakopf). **6 Quick-Win-Commits** auf `feature/beta-hardening` bereinigt ~8 triviale Lücken (IME, Export-Naming, Speicher-Leiche, tote Annotation, irreführende Marker); Branch ist **nicht** gemergt.

**Empfehlung:** BETA freigeben **nach** Abarbeitung von B1–B4 (alle ohne Architektur-Umbau lösbar — B1 ggf. nur durch ehrliches Re-Labeling) **und** der On-Device-Verifikationsliste (Abschnitt 6). Der eigentliche Inspektions-/Dokumentations-/PDF-Loop ist tragfähig.

---

## 2. BETA-Blocker — priorisiert

Kategorie: `nv` = nicht verdrahtet · `lL` = läuft ins Leere · `fehlt` · Schweregrad: SS/Hoch/Mittel · QW = Quick-Win-fixbar · Aufwand S/M/L. Niedrig-Befunde stehen in Abschnitt 3/4.

| Nr | Befund | Ort (Datei:Zeile) | Kat | Schwere | QW | Aufw. | Status |
|----|--------|-------------------|-----|---------|----|----|--------|
| 1 | XML-Export ist kein ISYBAU/DIN-Format; verwirft strukturierte 13508-2-Felder, „DIN-kompatibel" ist Falschversprechen | `export/model/XmlModels.kt:86-105`, `export/XmlExportService.kt:93,115-117` | lL | **Hoch** | nein | L | offen |
| 2 | Self-Update: Release-Manifest-URL liefert 404 (Repo hat 0 Releases) — Check findet nie ein Update | `app/build.gradle.kts:31`, `update/UpdateConfig.kt:25` | lL | **Hoch** | nein | M | offen |
| 3 | Self-Update: kein BroadcastReceiver für `ACTION_INSTALL_STATUS` → Installation hängt nach Download (System-Dialog wird nie ausgelöst) | `update/UpdateInstaller.kt:32-41` | nv | **Hoch** (Auditor: SS) | nein | M | offen / on-device |
| 4 | Kiosk hält nicht — nur System-Bars versteckt, kein `startLockTask`; Home/Recents weiter wirksam (#5) | `MainActivity.kt:113-123` | lL | **Hoch** | nein | M | offen / on-device |
| 5 | 404/NotConfigured wird in der UI als „App ist aktuell" kaschiert (verdeckt B2) | `ui/screens/settings/UpdateSection.kt:224-225` | lL | Mittel | ja* | S | offen* |
| 6 | SD/HD-Toggle komplett tot — kein Recorder liest `videoQuality` | `data/local/entity/ProjectEntity.kt:29`, `network/FfmpegRtspRecorder.kt:124-146`, `network/LocalBitmapRecorder.kt:44-91` | lL | Mittel | nein | M | Entscheidung |
| 7 | Hardware-OSD-Toggle (`use_hardware_osd`) wird gespeichert, aber nie gelesen — toter Schalter | `ui/screens/settings/SettingsViewModel.kt:111,228-231` | lL | Mittel | nein | M | Entscheidung |
| 8 | Lokal-Aufnahme (ONE) brennt kein OSD ins MP4 — „Mit/Ohne Overlay" ohne Wirkung | `network/LocalBitmapRecorder.kt:76-81` | lL | Mittel | nein | M | offen |
| 9 | `fallbackToDestructiveMigration()` aktiv — stiller Datenverlust bei jeder unbehandelten Versionslücke | `data/local/AppDatabase.kt:215` | lL | Mittel | nein | S | Entscheidung |
| 10 | DIN-Hierarchie tot: `PipeRepository`/`InspectionRepository`/-DAOs registriert, aber nie konsumiert; `inspectionId` immer null | `di/AppModule.kt:89-90`, `InspectionScreen.kt:418` | nv | Mittel | nein | L | Entscheidung |
| 11 | XML-DIN-Code wird per String-Split aus Legacy-`damageType` erraten statt aus `mainCode` | `export/XmlExportService.kt:115-117` | lL | Mittel | nein | S | offen |
| 12 | PDF+XML-Export: XML wird erzeugt, aber im PDF-Pfad nie ausgeliefert/geteilt (verwaiste Datei) | `ui/screens/projectdetail/ProjectDetailViewModel.kt:94-100` | lL | Mittel | nein | M | offen |
| 13 | Frequenz-Mapping TX vs. RX invertiert — Sonde-OSD zeigt nach dem Setzen die falsche Frequenz | `network/internal/OneInternalHardwareService.kt:379-385`, `InspectionScreen.kt:774` | lL | Mittel | nein | S | offen / on-device |
| 14 | `btn1..btn6` aus `GROUP_STATUS` in `foldFrames` verworfen — serielle Hardtasten nicht ausgewertet (Pfad hängt allein an KeyCode-Annahme) | `network/internal/OneInternalHardwareService.kt:325-331` | lL | Mittel | nein | M | on-device |
| 15 | ConnectionScreen registriert, aber von nirgends erreichbar (toter 970-Z.-Screen, RTSP-Legacy) | `ui/navigation/NavGraph.kt:68,216` | lL | Hoch (Nav) | nein | M | Entscheidung |
| 16 | HW-Init/Teardown nicht an Activity-Lifecycle gekoppelt (kein stopPolling bei Background) | `InspectionScreen.kt:349-358`, `MainActivity.kt` | lL | Mittel | nein | M | on-device |
| 17 | Keine HOME-Launcher-Kategorie — Gerät bootet nach Reboot nicht in die App | `AndroidManifest.xml:46-49` | fehlt | Mittel | nein | S | Entscheidung |
| 18 | ReportsScreen registriert, aber unerreichbar + CTA führt nur auf Projektliste (Pseudo-Aktion) | `ui/navigation/NavGraph.kt:227`, `ui/screens/reports/ReportsScreen.kt:45-50` | lL | Mittel | nein | S | Entscheidung |
| 19 | ProjectDetail-Löschdialog (Datenverlust!) komplett hartkodiert deutsch — in fremder Sprache unverständlich | `ui/screens/projectdetail/ProjectDetailScreen.kt:630-663` | lL | Mittel | ja* | S | offen* |
| 20 | DamageDialog: Tastatur schloss nicht zuverlässig (nur clearFocus statt hartem Hide) + kein ADJUST_RESIZE | `ui/screens/inspection/DamageDialog.kt:147,280,323,79-89` | lL | Mittel | **ja** | S | **✅ behoben** `7c0aa2a` |

\* B5/B19: Quick-Win-fähig, aber erfordert je einen neuen i18n-Key in der 35-Sprachen-Tabelle (translations_raw) → bewusst der L10N-Pipeline überlassen statt die generierte Tabelle hand-zu-editieren (Abschnitt 5, „nicht angefasst").

---

## 3. Befunde im Detail (gruppiert nach Schicht)

### 3.1 Export — Hoch (Falschversprechen) + Mittel

**[Hoch] XML ist kein DIN/ISYBAU-Austauschformat** — `export/model/XmlModels.kt:86-105`
`XmlObservation` trägt nur `id/Position/Code/Description/PhotoReference/Timestamp`. Der Mapper setzt `code = extractDamageCode(damage.damageType)` (`XmlExportService.kt:93`) und zerlegt damit das **Legacy-Freitextfeld** per `split(" - "," – ","-")` (`:115-117`) — obwohl `DamageEntity` alle strukturierten 13508-2-Felder hält (`mainCode, characterization1/2, quantification1/2, clockPositionStart/End, damageClass, positionEnd`; `DamageEntity.kt:25-49`), die **komplett verloren gehen**. Die UI verspricht „DIN EN 13508-2 kompatibel" (`export_include_xml_hint`, angezeigt `ProjectDetailScreen.kt:259`) und das XML deklariert `<Standard>DIN EN 13508-2:2011</Standard>` (`XmlExportService.kt:70`). Foto-Schäden (`damageType="Foto"`, `InspectionScreen.kt:418`) erzeugen `<Code>Foto</Code>` — kein gültiger Code.
*Auswirkung:* Kein WinCan/IKAS/KaPro/ISYBAU-Import möglich; Kunden, die ein normkonformes Austauschformat erwarten, bekommen ein nicht-importierbares Eigenformat.
*Lösung:* Entweder (a) Compliance-Behauptung abschwächen („DrainQ ONE Exportformat (proprietär)") — Quick-Win-nah, oder (b) echtes ISYBAU/13508-2 implementieren, das `mainCode` etc. ausgibt. Für BETA mindestens (a).

**[Mittel] DIN-Code aus Legacy-Text erraten** — `export/XmlExportService.kt:115-117`. Fix: `damage.mainCode` bevorzugen, `extractDamageCode` nur als Fallback. (Display-Helper `InspectionScreen.kt:1632-1636` macht es bereits korrekt — nur der Export weicht ab.)

**[Mittel] XML im PDF-Pfad verwaist** — `ProjectDetailViewModel.kt:94-100`. Bei „PDF + XML einschließen" (Default `exportIncludeXml=true`, `ProjectDetailScreen.kt:110`) wird `generateXmlExport(...)` aufgerufen, der Rückgabewert aber verworfen; geteilt/gespeichert wird nur die PDF (`ProjectDetailScreen.kt:183-190`). Die XML liegt verwaist unter `exports/xml/`. Fix: im PDF+XML-Fall auf ZIP umschalten oder `ACTION_SEND_MULTIPLE`.

*Sauber (kein Befund):* Die gesamte Export-Kette UI→VM→Service→Datei→Teilen ist verdrahtet; **PDF-Inhalt ist echt** (iText7, mehrseitig, Logo, Schadensfotos, Rohr-Längsprofil); **ZIP bündelt alle Artefakte** (Fotos/Videos/Audio/PDF/XML/Karte); Share via FileProvider (`AndroidManifest.xml:52-60`).

### 3.2 Update — Hoch (Self-Update tot)

**[Hoch] Manifest-URL 404** — `app/build.gradle.kts:31` (`UPDATE_PROXY_URL = .../ThomasViell/one-app/releases/latest/download/`). Effektive URL `…/releases.stable.json` → **live verifiziert HTTP 404** (Repo existiert, hat 0 Releases). `checkForUpdate()` ist voll verdrahtet (`UpdateSection.kt:207` → `HttpUpdateService` → `UpdateConfig.kt:25`), findet aber nie ein Update. Fix: GitHub-Release als `latest` mit Asset `releases.stable.json` + referenzierter APK (`versionCode > 3`) publizieren oder URL korrigieren.

**[Hoch, Auditor-„Showstopper" → Hoch] Kein Installer-Status-Receiver** — `update/UpdateInstaller.kt:32-41`. `session.commit(pendingIntent.intentSender)` liefert `STATUS_PENDING_USER_ACTION` (enthält den System-Bestätigungsdialog) **nur** an den `ACTION_INSTALL_STATUS`-Broadcast. **Repo-weit kein Receiver dafür** (Manifest hat keinen `<receiver>`; dynamisch nur Akku/WLAN — selbst verifiziert). Folge: nach Download passiert sichtbar nichts. `REQUEST_INSTALL_PACKAGES` ist im Manifest vorhanden (`:22`), die Installation ist also nur User-bestätigt möglich — der nie ausgelöste Dialog ist zwingend. *Re-Grading:* Self-Update blockiert **nicht** den Inspektions-Kern-Workflow → Hoch statt Showstopper. Fix: Receiver registrieren, der `STATUS_PENDING_USER_ACTION` per `EXTRA_INTENT` mit `FLAG_ACTIVITY_NEW_TASK` startet.

**[Mittel] 404 als „aktuell" kaschiert** — `UpdateSection.kt:224-225`: `NotConfigured → CheckState.NoUpdate` rendert den grünen „App ist aktuell"-Text. Der reale 404-Fall wird so als Erfolg getarnt (verdeckt die Diagnose von B2). Fix: `NotConfigured` eigenständig als Hinweis/Fehler anzeigen. (Quick-Win-fähig, aber neuer i18n-Key nötig — siehe Abschnitt 5.)

### 3.3 Kiosk / Lifecycle — Hoch + Mittel

**[Hoch] Kiosk sperrt nicht** — `MainActivity.kt:113-123`. `applySystemBars()` versteckt nur die System-Bars (`hide(systemBars)` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). **Kein `startLockTask`** im gesamten Code. Wischgeste/Home-Taste verlässt die App weiterhin (Feldtest #5). Der `kiosk_mode`-Toggle ist real verdrahtet (`SettingsViewModel.kt:112` → `MainActivity.kt:46-51`), trügt aber über echte Sperre. Fix: bei vorhandenem Device-Owner (`isDeviceOwner()` existiert in `WifiController.kt:63`) in `onResume`/`onWindowFocusChanged` `startLockTask()` koppeln; sonst dokumentiertes manuelles Screen-Pinning. **On-device:** prüfen, ob die ONE Device-Owner ist.

**[Mittel] HW-Lifecycle nicht gekoppelt** — `InspectionScreen.kt:349-358`. `startPolling()` läuft nur aus einem `LaunchedEffect(Unit)` der Composition; MainActivity hat keine `onResume/onPause/onStop`. Kein `stopPolling`/Re-Init bei Background → genau die in #5 vermutete Hardware-„Hänger"-Folge nach versehentlichem Verlassen. Fix: `DefaultLifecycleObserver` oder `DisposableEffect` mit `stopPolling()`. **On-device** verifizieren, ob das Live-Bild nach App-Wechsel zurückkommt.

**[Mittel] Keine HOME-Kategorie** — `AndroidManifest.xml:46-49`: nur `LAUNCHER`. Nach Reboot landet das Feldgerät auf dem Android-Launcher statt in DrainQ.ONE. Trade-off: HOME-Filter wirkt auf jedem Gerät (für TWO/Tablet unerwünscht) — sauberer per Device-Owner-Provisionierung. Entscheidung dokumentieren.

*Sauber:* `kiosk_mode`-Pref durchgängig verdrahtet; `screenOrientation=sensorLandscape` + `adjustResize` korrekt; `DeviceFilePermissionBootstrap.grantIfNeeded()` läuft idempotent vor Koin-Init (`OneApp.kt:24`).

### 3.4 Hardware / Seriell — Mittel + On-Device

**[Mittel] Frequenz-Anzeige invertiert** — `OneInternalHardwareService.kt:379-385`. TX-Tabelle (`OneFrameCodec.kt:35`: `1=512Hz,2=640Hz,3=33kHz`) und RX-`freqName` (`1=33kHz,2=640Hz,3=512Hz`) sind für Code 1/3 **exakt gegenläufig**. UI sendet „512 Hz"→1 (`InspectionScreen.kt:774`), HW echoed 1 zurück, `freqName(1)="33 kHz"` → OSD/Burn-in zeigt die falsche Frequenz. Fix: eine gemeinsame Code→Name-Tabelle für TX+RX; **on-device** die physikalisch wahre Zuordnung mit Empfänger bestimmen.

**[Mittel/On-Device] Serielle Hardtasten verworfen** — `OneInternalHardwareService.kt:325-331`. `foldFrames` liest aus `GROUP_STATUS` nur `payload[1]` (Licht) und `payload[2]` (Freq); `btn1..btn6` (`payload[3..8]`) werden nie gelesen. Der gesamte Hardtasten-Workflow hängt an der **unbestätigten Annahme**, dass die ONE-Tasten als Android-`KeyEvent` F1–F8 (KeyCode 131–138) ankommen (`MainActivity.kt:80-87`). **On-device entscheidend** (Abschnitt 6). Falls sie nur seriell kommen: btn-Bytes auf Flanken auswerten und in `HardwareKeyBus.emit(...)` brücken.

*Sauber/verifiziert:* Die `HardwareKeyBus`-Kette **innerhalb der Inspektion** ist vollständig — `MainActivity.onKeyDown` → `emit` → `InspectionScreen.kt:478-480 collect` → `runHwButton` (`:453-475`, echte Service-Calls/Navigationen); Softbutton und positionsgleiche Hardtaste teilen denselben Handler. Stream-Reassembly (`drainRxFrames`) robust + getestet (7 Tests). Kamerakopf C10/C18 verdrahtet (`GROUP_CAMERA` payload[4] → debounced `cameraId`), Diagnose-Logging an `BuildConfig.DEBUG` gekoppelt (release-sauber). `v4l2bridge.c` termios/V4L2 lehrbuchkorrekt. `OneHardwareService.kt` ist bewusst leer (Legacy), `TwoHardwareService` nur bei `device_type==TWO`.

*Niedrig:* Hardtasten-Events nur im InspectionScreen gesammelt (auf Home/Settings verloren — `HardwareKeyBus` ohne replay); Sonde-Power-Status (`payload[0]`) nie ausgelesen (kein RX-Rückkanal ob Sonde an); RX-Read-Failure setzt Verbindung nicht zurück (stiller Dauer-Fehlversuch bei FD-Bruch); `LinearMeterCalculator` ist Identitäts-Stub (keine Kabel-Dehnungs-Linearisierung).

### 3.5 Video / Recording — Mittel

**[Mittel] SD/HD-Toggle tot** — `ProjectEntity.kt:29` (`videoQuality="HD"`). Auswahl in `ProjectFormScreen.kt:794-821` gespeichert, als Badge angezeigt (`ProjectDetailScreen.kt:486-487`), aber **kein Recorder liest sie**: `FfmpegRtspRecorder` (`:124-146`) kodiert ohne `-s/scale`, `LocalBitmapRecorder` (`:44-91`) komprimiert in Originalgröße; `V4L2Camera.kt:32` ist fest 720. Repo-Grep über `videoQuality` zeigt nur Entity/DB/Form/Anzeige. **Entscheidung:** Toggle entfernen (Köpfe HD-only) **oder** Downscale-Filter durchreichen.

**[Mittel] Lokal-MP4 ohne OSD-Burn-in** — `LocalBitmapRecorder.kt:76-81`. Auf der ONE (LocalBitmap) komprimiert der aktive Recorder die rohe V4L2-Bitmap ohne `OsdRenderer`; „Mit Overlay"/„Ohne Overlay" rufen beide identisch `localRecorder.start(...)` (`InspectionScreen.kt:1483/1523`) — die Wahl ist wirkungslos, das gespeicherte Video enthält nie Distanz/Datum/Schaden-Flash (live sichtbar via Compose-Overlay). `docs/design/welle-1-prompt.md:15` fordert genau das. Fix: `OsdRenderer` vor `compress()` anwenden oder Overlay-Wahl im Lokal-Modus ausblenden. (Foto/Schaden-Screenshots brennen OSD **korrekt** ein: `InspectionScreen.kt:414/439`.)

*Sauber:* Recording-Start/Stop-Lebenszyklus, `DisposableEffect`-Cleanup, Verzeichnis-Rescan zum Wiederfinden, Video-Playback inkl. Foto/Schaden aus dem Video. `VideoOverlayProcessor` ist `@Deprecated` (toter Legacy, kein Befund).

### 3.6 Daten / Room — Mittel

**[Mittel] `fallbackToDestructiveMigration()`** — `AppDatabase.kt:215`. Produktiv (Koin-Singleton `AppModule.kt:79`). Kette 3→8 ist lückenlos, daher **kein akuter Bug** — aber der unkonditionierte Aufruf maskiert künftige fehlende Migrationen: jede unauflösbare Versionsdifferenz (z.B. 8→9 ohne Migration, oder Pre-v3-Altbestand) löscht die DB **kommentarlos** (alle Projekte/Schäden/Notizen weg). Fix für Release: entfernen oder `fallbackToDestructiveMigrationFrom(1,2)` — dann crasht eine fehlende Migration sichtbar im Test statt im Feld. **Entscheidung zur Datenverlust-Policy** → daher nicht als Quick-Win angefasst.

**[Mittel] DIN-Hierarchie tot** — `InspectionDao.kt:7-35`, `AppModule.kt:89-90`. `PipeRepository`/`InspectionRepository`/`PipeDao`/`InspectionDao` sind registriert, haben aber **null Konsumenten** außerhalb `data/`+`di/` (adversarial verifiziert). Schäden hängen direkt am Projekt (`InspectionScreen.kt:418` ohne `inspectionId`), die Tabellen `pipes`/`inspections` bleiben leer. **Entscheidung:** flaches Projekt-Modell für BETA bewusst belassen (dann tote DAOs/Repos als Schuld markieren) **oder** Hierarchie verdrahten.

*Sauber:* FK + CASCADE für den genutzten Pfad korrekt; Medien überleben Prozess-Tod (app-privater External-Storage, absolute Pfade in DB); `deleteProjectCompletely()` räumt DB + Dateien; alle 6 DAOs + Repos DI-registriert.

### 3.7 Navigation — Hoch + Mittel

**[Hoch] ConnectionScreen tot** — `NavGraph.kt:68,216`. Route `connection` registriert, aber **nicht** in `bottomNavItems` und **kein** `navigate("connection")` im gesamten Baum (adversarial verifiziert; `RESULT_SETTINGS_CLEANUP.md:35` belegt, dass der einzige frühere Einstieg bewusst gelöscht wurde). Der einzige Ort mit RTSP-Scan/`probeHardware`/`cycleLightPower`/`cycleFrequency`/Meter-Reset (`ConnectionScreen.kt:100-148`) ist unerreichbar — deckt sich mit Feldtest #2 (Sonde/Frequenz „versteckt"). Auf der ONE ist der ganze RTSP-Pfad Legacy (`ConnectionViewModel` baut `rtsp://local:8554/1234`, `:66-69`). **Entscheidung:** entfernen (ONE braucht keinen RTSP-Screen) oder bewusst als Diagnose hinter Settings verlinken. Severity Hoch (nicht SS), da der Kern-Workflow über `VideoSource.LocalBitmap` läuft, nicht über diesen Screen.

**[Mittel] ReportsScreen tot** — `NavGraph.kt:227`, `ReportsScreen.kt:31-51`. Registriert, unerreichbar, zeigt immer den Leer-Zustand; CTA „Bericht erstellen" navigiert nur auf die Projektliste. Reporting läuft real über ProjectDetail. Entfernen oder einhängen + mit ViewModel füllen.

*Sauber:* Alle 4 Bottom-Nav-Tabs + alle Detail-Screens haben Rückweg; `network/offline_maps/cloud_login/project_form/project_detail/inspection` werden real angesprungen; #7 Inspektion-Zurück behoben.

### 3.8 Dialoge / IME (#4) — Mittel (behoben) + On-Device

**[Mittel, ✅ behoben]** DamageDialog hartes Hide + ADJUST_RESIZE — siehe Abschnitt 5, Commit `7c0aa2a`.
*Sauber:* NoteDialog ist IME-Referenz (hartes `imm.hideSoftInputFromWindow` + `keyboardController.hide()` + `clearFocus`, ADJUST_RESIZE, KeyboardHideButton, `appHintLocales`); MapPicker/ImageAnnotation haben keine Textfelder; Speicher-Kette beider Dialoge → DAO verifiziert.

### 3.9 Lokalisierung — Mittel + Niedrig

**[Mittel] Löschdialog hartkodiert deutsch** — `ProjectDetailScreen.kt:630-663`. Der **unwiderrufliche** Projekt-Löschdialog (Titel/Body/Bestätigen) ist fest deutsch; nur „Abbrechen" nutzt `S("cancel")`. In fremder Sprache versteht der Nutzer die Datenverlust-Warnung nicht. Fix: 3 neue Keys (`delete_project_title/body/confirm`) via translations_raw. (Nicht hand-editiert — L10N-Pipeline.)
*Sauber:* Kern-Capture-Workflow (Inspection/Damage/Note) durchgängig `S()`-lokalisiert; Fallback-Kette Sprache→de→Key; 35 Sprachen. *Niedrig:* OfflineMapsScreen komplett deutsch (Sekundärfeature), ProjectDetail-Toasts + GPS-Snackbar + ConnectionScreen-Labels hartkodiert.

---

## 4. „Nicht verdrahtet / tote Affordanzen" — Vollständige Liste

Sichtbare oder scheinbar-funktionale Elemente **ohne echte Wirkung** (jeweils belegt):

**Settings:**
- SD/HD-Toggle (`ProjectFormScreen.kt:794`) — kein Recorder liest `videoQuality`. *(Blocker 6)*
- Hardware-OSD-Toggle (`SettingsScreen.kt:363`, `SettingsViewModel.kt:111`) — `use_hardware_osd` nie gelesen; echte HW-OSD-Logik nutzt anderen Key `hardware_osd_visible`. *(Blocker 7)*
- `device_type`/Broker-IP/RTSP-URL/TWO-Kamera-Plumbing (`SettingsViewModel.kt:143-186`) — `update*`-Funktionen nie aufgerufen, keine UI; verwaiste i18n-Keys `device_type`/`_subtitle`.
- Toolbar-Speichern-Button (`SettingsScreen.kt:81-94`) — redundant neben durchgängigem Auto-Save (suggeriert ungespeicherten Zustand).
- Erscheinungsbild-Toggle (`DqSettingsComponents.kt:147`) — nur Dunkel/Hell, `ThemeMode.SYSTEM` nach erstem Tipp nicht mehr wählbar.

**Navigation:**
- ConnectionScreen (`NavGraph.kt:216`) — unerreichbar. *(Blocker 15)*
- ReportsScreen (`NavGraph.kt:227`) — unerreichbar + Pseudo-CTA. *(Blocker 18)*
- `NetworkDiscoveryService` + `RtspStreamTester` (`AppModule.kt:49-50`) — nur vom toten ConnectionScreen genutzt.

**Cloud:**
- CloudLogin-Button (`CloudLoginScreen.kt:117-122`) — `enabled=false` + leeres onClick. **Ehrlich als „bald verfügbar" gekennzeichnet** (kein irreführender No-Op) → kein Befund, nur Vollständigkeit.
- `CloudAccountStore` (`AppModule.kt:114`) — Koin-Singleton ohne Konsument (bewusster Vorrat).

**Daten:**
- `PipeRepository`/`InspectionRepository` + DAOs — registriert, nie konsumiert. *(Blocker 10)*
- `DamageDao.countByProjectId/getById/getByInspectionId/getDamageClassCounts` (`:21-36`) — tote Query-Methoden.
- `DamageEntity.inspectionId` — wird beim Erfassen nie gesetzt (immer null).

**Hardware:**
- Serielle `btn1..btn6` (`GROUP_STATUS`) — verworfen. *(Blocker 14, on-device)*
- Sonde-Power-Status (`payload[0]`) — nie ausgelesen.

**Video/Export:**
- „Mit/Ohne Overlay" im Lokal-Modus (`InspectionScreen.kt:1483/1523`) — identisch, ohne Wirkung. *(Blocker 8)*
- XML „DIN-kompatibel" + PDF-Pfad-XML verwaist. *(Blocker 1, 12)*

**Inspektion (Niedrig):**
- Foto ohne Frame: legt 0-Byte-`.jpg` + „Foto"-DB-Eintrag an, zeigt aber Erfolgs-Blitz (`InspectionScreen.kt:410-421`) — stille Fehl-Quittung (on-device, ob `localFrame` zum Auslösen befüllt ist).
- Notiz-Sortierung nicht persistiert (`:1221`); Hardware-OSD-Toggle lädt am falschen Pref-Flow (funktioniert nur per Seiteneffekt, `:206-210`).

---

## 5. Bereits gefixte Quick-Wins (`feature/beta-hardening`)

Branch von `feature/network-settings` abgezweigt, **nicht gemergt**, gezielt gestaged, `assembleDebug` grün.

| Commit | Fix | Begründung |
|--------|-----|-----------|
| `7c0aa2a` | `fix(ime)`: DamageDialog schließt Tastatur hart + ADJUST_RESIZE (#4) | Free-Tap & ImeAction.Done nutzten nur `clearFocus()` (laut Projektregel auf ONE unzuverlässig); jetzt hartes `hideKeyboard()` + Dialog-Fenster-`SOFT_INPUT_ADJUST_RESIZE` wie NoteDialog. `dlgView` korrekt im Dialog geholt. |
| `f73d82a` | `fix(ime)`: Settings-Firmenfelder + Projektsuche schließen Tastatur (#4) | Firmenname/-adresse ohne `imeAction`; Suche hatte leeres `onSearch={}`. Jetzt `ImeAction.Done`/`Search` + `rememberKeyboardHider()` (projektweite harte Hide-Regel). |
| `f59a25d` | `fix(maps)`: Offline-Download löscht `.part` bei Abbruch | `isStopped`-Zweig ließ teils geladene Mehr-GB-Datei als Speicher-Leiche zurück (anders als der catch-Block); jetzt `part.delete()` analog. |
| `186464d` | `fix(export)`: ZIP-Eintragsnamen mit id-Fallback | „Bericht_.pdf"/„Daten_.xml" bei leerer Projektnummer (Schnellaufnahme); jetzt `ifEmpty { id }` konsistent zur ZIP-Datei selbst. |
| `359a05a` | `fix(projectdetail)`: Annotation aus Bearbeiten-Dialog erreichbar | `onOpenAnnotation`-Callback fehlte beim Schaden-Bearbeiten → Doppeltipp lief ins Leere; jetzt verdrahtet wie im Vollbild-/Inspektionspfad. |
| `586bf63` | `chore`: irreführende Marker entfernt | InspectionScreen-TODO behauptete fälschlich, Lokal-Aufnahme sei disabled (ist verdrahtet); `OneFrameCodec.parseRxFrames` (toter Code, 0 Aufrufer, inkompatibles Layout) entfernt. |

**Bewusst NICHT angefasst** (Branch unverändert gelassen, da Produktentscheidung / L10N-Pipeline / Architektur):
- B5 (Update-404-Anzeige) & B19 (Löschdialog-L10N) — Quick-Win-fähig, brauchen aber je einen neuen Key in der 35-Sprachen-Tabelle (translations_raw + Generator) → gehört in die L10N-Pipeline, nicht in eine Hand-Edit der generierten `LocalizationManager.kt`.
- B6/B7/B9/B17 (SD/HD- & HW-OSD-Toggle, Destructive-Migration, HOME-Kategorie) — Produkt-/Policy-Entscheidung („entfernen oder anbinden").
- Alles Hoch/Architektur (XML-ISYBAU, Installer-Receiver, Kiosk-LockTask, DIN-Hierarchie, tote Screens) — siehe Abschnitt 7.

*Scratch-Tools aus dem Audit-Lauf (`tools/_audit_*.ps1`, `tools/_audit_detail.txt`) sind ungetrackt und können gelöscht werden — kein Repo-Bestandteil.*

---

## 6. On-device zu verifizieren

Nur am Gerät (Serial `233b4bd2865177ed`) abschließend klärbar. Build/Install: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; C:\Projekte\drainq.one\gradlew.bat installDebug`

| # | Prüfung | Schritt / Logcat | Erwartung |
|---|---------|------------------|-----------|
| V1 | **Hardtasten F1–F8** (Blocker 14) | In Inspektion jede physische Taste drücken. Temporär `Log.d` in `MainActivity.onKeyDown` (keyCode) setzen, `adb logcat -s HwKey` | Licht zykelt / Sonde-Popup / Foto-Blitz etc. Reagiert nichts → Tasten kommen **nicht** als KeyCode 131–138 → seriellen btn-Byte-Pfad bauen. |
| V2 | **Sonde an/aus + Frequenz** (#2) | Sonde-Popup, Frequenz wählen. `adb logcat -s OneInternalHW` → `TX p=1 l=.. f=..` | TX-Frame wird gesendet; Empfänger ortet. **Achtung Blocker 13:** angezeigte Frequenz ist evtl. invertiert — wahre Code→Frequenz mit Empfänger bestimmen. |
| V3 | **V4L2-Live-Bild + chmod** | App starten. `logcat -s OneInternalHW` → `probeEndpoints: video=true serial=true`; `adb shell` → `su -c 'ls -l /dev/ttyS5 /dev/video0'` (rw für alle?), `su -c whoami`→root | Live-Bild erscheint. Schlägt `su`/chmod fehl → kein Bild ohne sichtbare Ursache (Befund DeviceFilePermissionBootstrap). |
| V4 | **Kamerakopf C10/C18** | C10- und C18-Kopf umstecken; Chip oben beobachten. Debug-Build: `logcat -s OneInternalHW` → `RX grp=23 … payload[4]` (C10=01, C18=02) | Chip wechselt C10↔C18. Bleibt leer → Köpfe senden GROUP 23 nicht (Stand 06-04 war so; seither via Reassembly gelöst — gegenprüfen). |
| V5 | **Lokal-Video-Aufnahme** (Blocker 8) | Aufnahme starten/stoppen; ProjectDetail → Videos-Tab; App neu starten, erneut prüfen | MP4 abspielbar & nach Neustart vorhanden. **Erwartet ohne OSD-Burn-in** (Blocker 8) — bestätigen. |
| V6 | **Kiosk-Sperre** (Blocker 4) | `kiosk_mode` AN; Home-Geste/-Taste, Recents | Verlässt die App weiterhin → bestätigt fehlendes LockTask. Device-Owner-Status prüfen: `adb shell dumpsys device_policy` / `dpm`. |
| V7 | **HW-Re-Init nach Background** (Blocker 16) | App in Hintergrund (anderer App-Wechsel), zurück | Live-Bild kommt zuverlässig zurück? Falls nicht → Lifecycle-Kopplung nötig. |
| V8 | **Self-Update** (Blocker 2/3) | „Nach Updates suchen" (nach Release-Publish) | Findet Update, Download → **System-Installdialog erscheint** (heute nicht — Receiver fehlt). |

---

## 7. Empfohlene Reihenfolge bis BETA

**Welle A — Ehrlichkeit & sichtbare Fehlschläge (S–M, vor BETA-Stempel):**
1. **B1 XML-Compliance** entschärfen: Label „DIN EN 13508-2 kompatibel" → „proprietäres DrainQ-Format" **oder** echtes ISYBAU (Entscheidung). Minimal-Fix (Re-Label) ist S.
2. **B5 + B19** L10N-Keys via translations_raw (Update-404-Hinweis sichtbar machen; Löschdialog übersetzen) — generieren, nicht hand-editieren.
3. **B6/B7 tote Toggles** entscheiden: SD/HD + Hardware-OSD entweder entfernen (HD-only) oder anbinden. (Quick-Win, sobald Entscheidung steht.)

**Welle B — Geräte-Gate (M, on-device, parallel):**
4. **V1–V4** abarbeiten: Hardtasten-Mapping (Blocker 14), Frequenz-Mapping (Blocker 13), V4L2/chmod (V3), Kamerakopf (V4). Ergebnis entscheidet, ob B14 ein echter Fix wird.
5. **B4 Kiosk-LockTask** + Device-Owner-Provisionierung (Golden-Image) — Feldtauglichkeit #5.
6. **B16 HW-Lifecycle** an `onResume/onPause` koppeln.

**Welle C — Update-Auslieferung (M):**
7. **B2** GitHub-Release mit `releases.stable.json` + APK publizieren.
8. **B3** Installer-Status-Receiver ergänzen. Erst dann ist Self-Update abschließbar.

**Welle D — Aufräumen (S, nice-to-have):**
9. **B15/B18** tote Screens (Connection/Reports) entfernen oder einhängen.
10. **B8** OSD-Burn-in im LocalBitmapRecorder; **B9** Destructive-Migration-Policy; **B12** PDF+XML-Auslieferung.

**Branch-Konsolidierung:** `feature/beta-hardening` → review → in `feature/network-settings` mergen → `master` → Tag `v0.4.0`.

---

## 8. Test-Lücken (nur BETA-kritische Pfade)

Aktuell 6 Testdateien, Schwerpunkt Serial-Codec (`OneFrameCodecTest`, 7 Tests) + Update (`UpdateServiceTest`, `UpdateE2ETest`). **Ungetestet ist der gesamte Erfassungs-/Persistenz-Kern.** Minimal-Absicherung vorschlagen (keine Vollabdeckung):

1. **Schnellaufnahme-Bucket** (`ProjectRepository.getOrCreateQuickProjectId`) — Unit/Room-In-Memory-Test: zweimal aufrufen → genau **ein** „Schnellaufnahme_ddMMyy"-Projekt; verhindert Regression des #6/#8-Fixes (der Kern der ALPHA→BETA-Reparatur).
2. **Capture-Persistenz** — `DamageRepository.saveDamage` + `getDamagesForProject`: Schaden mit `photoPath` speichern, wiederfinden; sichert „speichern → wiederfinden".
3. **Export-Vollständigkeit** — `ProjectExportService.generateZipWithXml`: ZIP enthält PDF + alle Fotos/Videos/Audio des Projekts (Verzeichniskonventionen `project_$id`); fängt Regression der Bündelung ab.
4. **Frequenz-Mapping-Konsistenz** (nach Klärung V2) — ein Test, der TX-Code↔`freqName` gegen **eine** Quelle der Wahrheit prüft; verhindert erneute Inversion (Blocker 13).
5. **DB-Migration** — `MIGRATION_3_4..7_8` als migrierende Room-Tests (Room `MigrationTestHelper`); macht den `fallbackToDestructiveMigration`-Risikopunkt (Blocker 9) beherrschbar und deckt Schema-Divergenzen auf.

---

*Erstellt durch schichtweises Voll-Audit + adversariale Code-Verifikation. Alle Befunde sind mit Datei:Zeile belegt; die zur Verifikation gegengeprüften Kernbefunde (18) wurden zu 100 % am Code bestätigt.*

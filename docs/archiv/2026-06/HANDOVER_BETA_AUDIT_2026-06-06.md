# HANDOVER — BETA-Readiness-Audit drainq.one

**Datum:** 2026-06-06 · **Für:** cowork / nächste Bearbeitung · **App:** DrainQ.ONE (ONE-Schiebekamera, läuft direkt auf ONE-Hardware RK3588/Android)
**Audit-Branch:** `feature/beta-hardening` (von `feature/network-settings`, **kein Merge**) · **Build:** `assembleDebug` grün
**Vollbefund:** `BETA_READINESS_AUDIT.md` (Repo-Root) — dieses Dokument ist die handover-fähige Zusammenfassung mit Prioritäten.

---

## 0. TL;DR

- **BETA-Verdikt: bedingtes JA.** Der Kern-Workflow *Live-Bild → erfassen → speichern → wiederfinden → PDF/ZIP teilen* ist code-seitig vollständig und tragfähig. Die ALPHA-Feldtest-Showstopper (#5 Kiosk teilweise, #6/#8 Schnellaufnahme, #4 Tastatur, #7 Zurück, #1/#3 Hardtasten/FAB) sind **gelöst**.
- **Methode:** 15-Schichten-Voll-Audit, **81 Befunde**, jede Aussage mit Datei:Zeile belegt. 18 Kernbefunde adversarial gegen den Code verifiziert → **18/18 bestätigt, 0 widerlegt**.
- **Verteilung:** 1 (Auditor-)Showstopper → auf **Hoch** korrigiert, **4 Hoch**, **21 Mittel**, **55 Niedrig**. **21 Punkte nur am Gerät** abschließend verifizierbar.
- **Erledigt:** 6 Quick-Win-Code-Commits + 1 Docs-Commit (siehe §3).
- **Vor BETA zwingend (P0):** XML-„DIN"-Falschversprechen, Self-Update (tot), Kiosk-Sperre (greift nicht), tote SD/HD- & HW-OSD-Toggle, Frequenz-Anzeige invertiert, On-Device-Verifikation der HW-Kette.

---

## 1. Was in diesem Lauf geliefert wurde

1. **Vollständiges Audit** aller sichtbaren Bedien-Affordanzen, Kette UI → Handler → ViewModel/Repo → Service → Hardware/DB/Datei verfolgt; jede Stelle markiert, die ohne echte Wirkung endet.
2. **Adversariale Verifikation** jedes schweren „nicht-verdrahtet/läuft-ins-Leere"-Befunds gegen den realen Code (kein Ableiten aus Namen).
3. **Quick-Win-Fixes** auf eigenem Branch `feature/beta-hardening` (gezielt gestaged, je 1 Commit, **kein** `git add -A`, **kein** Merge), `assembleDebug` grün.
4. **Deliverables:** `BETA_READINESS_AUDIT.md` (Vollbefund, 8 Abschnitte) + dieses Handover.
5. **Saubere Trennung:** „im Code definitiv offen" vs. „nur am Gerät verifizierbar".

---

## 2. BETA-Verdikt & die 4 Hoch-Blocker

Der Inspektions-/Dokumentations-/PDF-Loop funktioniert (code-seitig). Was BETA noch im Weg steht — alles ohne Architektur-Umbau lösbar:

| ID | Hoch-Blocker | Ort | Kern |
|----|--------------|-----|------|
| **B1** | XML-Export behauptet „DIN EN 13508-2 kompatibel", ist aber ein selbst erfundenes Flach-Schema; verwirft alle strukturierten Schadensfelder (`mainCode`, `characterization`, `quantification`, `clockPosition`, `damageClass`). Keine Fachsoftware (WinCan/IKAS/ISYBAU) kann es importieren. | `export/model/XmlModels.kt:86-105`, `export/XmlExportService.kt:93,115-117` | Falschversprechen |
| **B2** | Self-Update-Release-URL → **HTTP 404** (Repo `ThomasViell/one-app` hat 0 Releases). „Nach Updates suchen" findet nie etwas und kaschiert es als „App ist aktuell". | `app/build.gradle.kts:31`, `UpdateConfig.kt:25`, `UpdateSection.kt:224-225` | Stiller Fehlschlag |
| **B3** | Kein BroadcastReceiver für `ACTION_INSTALL_STATUS` → der System-Installdialog wird nie ausgelöst, Installation **hängt nach Download**. (Vom Auditor als Showstopper markiert, von uns auf Hoch korrigiert — blockiert nicht den Inspektions-Kern.) | `update/UpdateInstaller.kt:32-41` | Nicht verdrahtet |
| **B4** | Kiosk-Schalter versteckt nur die System-Bars (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), **kein `startLockTask`**. Wischgeste/Home verlässt die App weiterhin (Feldtest #5). | `MainActivity.kt:113-123` | Trügt |

---

## 3. Erledigte Quick-Fixes (`feature/beta-hardening`)

| Commit | Fix | Begründung |
|--------|-----|-----------|
| `7c0aa2a` | **DamageDialog: Tastatur hart schließen + ADJUST_RESIZE** (#4) | Free-Tap & ImeAction.Done nutzten nur `clearFocus()` (laut Projektregel auf ONE unzuverlässig). Jetzt hartes `hideKeyboard()` + Dialog-Fenster-`SOFT_INPUT_ADJUST_RESIZE` wie NoteDialog; `dlgView` korrekt im Dialog geholt. |
| `f73d82a` | **Settings-Firmenfelder + Projektsuche: IME schließt Tastatur** (#4) | Firmenname/-adresse ohne `imeAction`; Suche hatte leeres `onSearch={}`. Jetzt `ImeAction.Done`/`Search` + `rememberKeyboardHider()`. |
| `f59a25d` | **Offline-Download: `.part` bei Abbruch löschen** | `isStopped`-Zweig ließ teils geladene Mehr-GB-Datei als Speicher-Leiche zurück (anders als der catch-Block). |
| `186464d` | **Export-ZIP: id-Fallback bei leerer Projektnummer** | „Bericht_.pdf"/„Daten_.xml" bei Schnellaufnahme-Projekten; jetzt `ifEmpty { id }` konsistent zur ZIP-Datei. |
| `359a05a` | **ProjectDetail: Annotation aus Bearbeiten-Dialog erreichbar** | `onOpenAnnotation`-Callback fehlte → Doppeltipp lief ins Leere; jetzt verdrahtet wie im Vollbild-/Inspektionspfad. |
| `586bf63` | **Cleanup irreführender Marker** | InspectionScreen-TODO behauptete fälschlich, Lokal-Aufnahme sei disabled (ist verdrahtet); toter `OneFrameCodec.parseRxFrames` (0 Aufrufer, inkompatibles Layout) entfernt. |
| `234a383` | **Docs:** `BETA_READINESS_AUDIT.md` | Vollbefund. |

**Bewusst NICHT angefasst** (Branch unverändert gelassen): alles Hoch/Architektur (B1–B4, DIN-Hierarchie, tote Screens), Produkt-/Policy-Entscheidungen (SD/HD- & HW-OSD-Toggle, Destructive-Migration, HOME-Kategorie) und alle Fixes, die einen neuen i18n-Key in der 35-Sprachen-Tabelle erfordern (gehört in die translations_raw-Pipeline, nicht in eine Hand-Edit der generierten `LocalizationManager.kt`).

---

## 4. Alle Mittel-Befunde (21)

| Nr | Befund | Ort | Kategorie | Status |
|----|--------|-----|-----------|--------|
| M1 | SD/HD-Toggle tot — kein Recorder liest `videoQuality` | `ProjectEntity.kt:29`, `FfmpegRtspRecorder.kt:124-146`, `LocalBitmapRecorder.kt:44-91` | nicht verdrahtet | Entscheidung |
| M2 | Hardware-OSD-Toggle `use_hardware_osd` gespeichert, nie gelesen | `SettingsViewModel.kt:111,228-231` | läuft ins Leere | Entscheidung |
| M3 | Lokal-Aufnahme (ONE) brennt kein OSD ins MP4 — „Mit/Ohne Overlay" ohne Wirkung | `LocalBitmapRecorder.kt:76-81`, `InspectionScreen.kt:1483/1523` | läuft ins Leere | offen |
| M4 | `fallbackToDestructiveMigration()` aktiv — stiller Datenverlust bei unbehandelter Versionslücke | `AppDatabase.kt:215` | läuft ins Leere | Entscheidung (Policy) |
| M5 | DIN-Hierarchie tot: `PipeRepository`/`InspectionRepository`/-DAOs registriert, nie konsumiert; `inspectionId` immer null | `AppModule.kt:89-90`, `InspectionScreen.kt:418` | nicht verdrahtet | Entscheidung |
| M6 | XML-DIN-Code per String-Split aus Legacy-`damageType` statt `mainCode` | `XmlExportService.kt:115-117` | läuft ins Leere | offen |
| M7 | PDF+XML-Export: XML erzeugt, aber im PDF-Pfad nie ausgeliefert (verwaiste Datei) | `ProjectDetailViewModel.kt:94-100` | läuft ins Leere | offen |
| M8 | Frequenz-Mapping TX vs. RX invertiert → Sonde-OSD zeigt falsche Frequenz | `OneInternalHardwareService.kt:379-385`, `InspectionScreen.kt:774` | läuft ins Leere | offen / on-device |
| M9 | `btn1..btn6` aus `GROUP_STATUS` in `foldFrames` verworfen — serielle Hardtasten nicht ausgewertet | `OneInternalHardwareService.kt:325-331` | läuft ins Leere | on-device |
| M10 | ConnectionScreen registriert, aber von nirgends erreichbar (toter 970-Z.-RTSP-Screen) | `NavGraph.kt:68,216` | läuft ins Leere | Entscheidung |
| M11 | ReportsScreen registriert, unerreichbar; CTA „Bericht erstellen" → nur Projektliste | `NavGraph.kt:227`, `ReportsScreen.kt:45-50` | läuft ins Leere | Entscheidung |
| M12 | ProjectDetail-Löschdialog (Datenverlust!) komplett hartkodiert deutsch | `ProjectDetailScreen.kt:630-663` | läuft ins Leere (L10N) | offen (i18n-Keys) |
| M13 | HW-Init/Teardown nicht an Activity-Lifecycle gekoppelt (kein stopPolling bei Background) | `InspectionScreen.kt:349-358`, `MainActivity.kt` | läuft ins Leere | on-device |
| M14 | Keine HOME-Launcher-Kategorie — Gerät bootet nach Reboot nicht in die App | `AndroidManifest.xml:46-49` | fehlt | Entscheidung |
| M15 | 404/NotConfigured als „App ist aktuell" kaschiert (verdeckt B2) | `UpdateSection.kt:224-225` | läuft ins Leere | offen (i18n-Key) |
| M16 | DamageDialog: Tastatur schloss nicht zuverlässig + kein ADJUST_RESIZE | `DamageDialog.kt:147,280,323,79-89` | läuft ins Leere | **✅ `7c0aa2a`** |
| M17 | DamageDialog: kein `SOFT_INPUT_ADJUST_RESIZE` am Dialog-Fenster | `DamageDialog.kt:79-89` | läuft ins Leere | **✅ `7c0aa2a`** |
| M18 | Offline-Karte erscheint erst nach manuellem Refresh (kein WorkInfo-Observer) | `OfflineMapsViewModel.kt:81-86` | politur | offen |
| M19 | ConnectionViewModel baut sinnlose `rtsp://local:8554/1234`-URL (ONE-Legacy) | `ConnectionViewModel.kt:66-69,91-99` | läuft ins Leere | mit M10 |
| M20 | PipeRepository registriert, nie konsumiert | `AppModule.kt:89` | läuft ins Leere | mit M5 |
| M21 | InspectionRepository registriert, nie konsumiert | `AppModule.kt:90` | läuft ins Leere | mit M5 |

---

## 5. Alle Niedrig-Befunde (55, gruppiert) — „mit allem was gefunden wurde"

**Cloud/Netzwerk:** CloudLogin-Button `enabled=false`+leeres onClick (ehrlich „bald verfügbar", harmlos, `CloudLoginScreen.kt:117`) · CloudAccountStore reiner Stub (`CloudAccountStore.kt:20-37`) · CloudAccountStore in Koin nie injiziert (`AppModule.kt:114`) · `connectPhase` bleibt CONNECTED (`NetworkViewModel.kt:91`) · REQUEST-Pfad-WLAN nur prozessgebunden (`WifiController.kt:196`, on-device) · NetworkDiscoveryService deprecated WifiManager-API (`:58-66`).

**Daten/Room:** tote DAO-Methoden `DamageDao` (`:21-36`) · Video-MP4-Pfad nicht in DB (Wiederfinden per Verzeichnis-Scan, `InspectionScreen.kt:1465`, harmlos) · Index nur in Migration, nicht in Entity (`UpdateEventEntity.kt:15`) · `DamageEntity.inspectionId` nie gesetzt (`InspectionScreen.kt:418`).

**DI/Navigation:** ReportsScreen unerreichbar (`NavGraph.kt:227`) · NetworkDiscoveryService+RtspStreamTester nur von totem ConnectionScreen genutzt (`AppModule.kt:49-50`) · kein `BackHandler` app-weit, System-Back vs. Pfeil inkonsistent (`InspectionScreen.kt:581`, on-device) · ReportsScreen-CTA Pseudo-Aktion (`ReportsScreen.kt:45`).

**Export:** XML `<Position>` ohne Einheit/`positionEnd` (`XmlExportService.kt:130`).

**Hardware:** HardwareKeyBus nur im InspectionScreen gesammelt — Events auf Home/Settings verloren (`InspectionScreen.kt:477`) · Sonde-Power-Status `payload[0]` nie ausgewertet (kein RX-Rückkanal, `:324-331`, on-device) · RX-Read-Failure setzt Verbindung nicht zurück (stiller Dauer-Fehlversuch bei FD-Bruch, `:295`, on-device) · `LinearMeterCalculator` Identitäts-Stub, keine Kabel-Dehnungs-Linearisierung (`:12-14`).

**Inspektion/Dialoge:** Foto ohne Frame legt 0-Byte-`.jpg`+DB-Eintrag an, zeigt aber Erfolgs-Blitz (`InspectionScreen.kt:410`, on-device) · Notiz-Sortierung nicht persistiert (`:1221`) · Hardware-OSD-Toggle lädt am falschen Pref-Flow (funktioniert nur per Seiteneffekt, `:206`) · ImageAnnotationDialog Speichern im UI-Thread (`:255`, on-device) · ImageAnnotationDialog null-Bitmap → stilles Schließen (`:52-57`).

**Kiosk/Start:** DeviceFilePermissionBootstrap su-chmod scheitert still (`:59-61`, on-device) · Splash kein Auto-Dismiss (`SplashScreen.kt:129`) · kein zentraler Runtime-Permission-Bootstrap (RECORD_AUDIO erst im NoteDialog, `MainActivity.kt:41`, on-device).

**Lokalisierung (Sekundärfeatures):** OfflineMapsScreen durchgängig deutsch (`:40,61,116…`) · ProjectForm GPS-Snackbar hardcoded (`ProjectFormScreen.kt:147`) · ProjectDetail Toasts hardcoded (`:89,98`) · S()-Fallback zeigt Roh-Key bei fehlendem Key (`LocalizationManager.kt:10704`) · A11y `contentDescription` hardcoded (OfflineMaps) · ConnectionScreen `Ports:`/`Response:` hardcoded trotz vorhandener Keys (`:419,531`).

**Projekte:** `weatherError` nie zurückgesetzt — zweiter identischer Fehler stumm (`ProjectFormViewModel.kt:50`) · Damages-Tab ohne „Schaden hinzufügen"-FAB (`ProjectDetailScreen.kt:509`).

**Settings:** `device_type`/Broker-IP/RTSP-URL/TWO-Kamera-Plumbing ohne UI (`SettingsViewModel.kt:143`) · Toolbar-Speichern-Button redundant neben Auto-Save (`SettingsScreen.kt:81`) · Erscheinungsbild-Toggle ohne `ThemeMode.SYSTEM` (`DqSettingsComponents.kt:147`).

**Update/Video:** Fortschrittsdialog Endlos-Balken, Verifying-Stage tot, Bytes hart 0 (`UpdateSection.kt:269`) · FfmpegRtspRecorder echte MP4? (on-device, `:143`) · FfmpegRtspRecorder öffnet 2. RTSP-Session parallel (on-device, `:145`) · Stop-Pfad verwirft gemeldeten MP4-Pfad (on-device, `:273`).

**Bereits per Quick-Win erledigt** (in der Niedrig-Klasse): Offline-`.part`-Cleanup (`f59a25d`), ZIP-Naming (`186464d`), ProjectDetail-Annotation (`359a05a`), ProjectsScreen-IME-Search + Settings-Firmenfelder (`f73d82a`), DamageDialog-Speichern-Hide (`7c0aa2a`), parseRxFrames + RECORD-TODO (`586bf63`).

---

## 6. Tote Affordanzen (Schnellüberblick — „sieht funktionsfähig aus, ist es nicht")

- **SD/HD-Auswahl** → kein Recorder liest sie (M1)
- **Hardware-OSD-Toggle** → nie gelesen (M2)
- **„Mit/Ohne Overlay" bei Lokal-Aufnahme** → identisch (M3)
- **„Nach Updates suchen"** → findet nie etwas / Install hängt (B2/B3)
- **Kiosk-Schalter** → sperrt nicht wirklich (B4)
- **ConnectionScreen / ReportsScreen** → unerreichbar (M10/M11)
- **XML „DIN-kompatibel"** → nicht importierbar (B1)
- **Doppeltipp-Annotation im Bearbeiten-Dialog** → war No-Op (✅ behoben `359a05a`)
- **device_type/Broker/RTSP-Settings** → Plumbing ohne UI

---

## 7. Nur am Gerät verifizierbar (On-Device-Checkliste)

Build/Install: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; C:\Projekte\drainq.one\gradlew.bat installDebug`

| # | Prüfung | Schritt / Logcat | Erwartung |
|---|---------|------------------|-----------|
| V1 | **Hardtasten F1–F8** (M9) | Jede Taste in Inspektion drücken; temporär `Log.d` in `MainActivity.onKeyDown`, `adb logcat -s HwKey` | Aktion feuert. Reagiert nichts → Tasten kommen nicht als KeyCode 131–138 → seriellen btn-Byte-Pfad bauen. |
| V2 | **Sonde + Frequenz** (#2) | Sonde-Popup; `logcat -s OneInternalHW` → `TX p=1 …` | TX gesendet, Empfänger ortet. **Achtung M8:** Anzeige evtl. invertiert. |
| V3 | **V4L2-Bild + chmod** | `logcat -s OneInternalHW` → `probeEndpoints: video=true serial=true`; `adb shell su -c whoami`→root | Live-Bild da. su/chmod scheitert → kein Bild ohne Ursache. |
| V4 | **Kamerakopf C10/C18** | Köpfe umstecken; Debug: `RX grp=23 … payload[4]` (01/02) | Chip wechselt C10↔C18. |
| V5 | **Lokal-Aufnahme** (M3) | Aufnehmen, Videos-Tab, App-Neustart | MP4 abspielbar & vorhanden — erwartet **ohne** OSD-Burn-in. |
| V6 | **Kiosk-Sperre** (B4) | `kiosk_mode` AN; Home/Recents; `adb shell dumpsys device_policy` | Verlässt App → fehlendes LockTask bestätigt. Device-Owner-Status prüfen. |
| V7 | **HW-Re-Init nach Background** (M13) | App wechseln, zurück | Live-Bild kommt zurück? |
| V8 | **Self-Update** (B2/B3) | „Nach Updates suchen" (nach Release-Publish) | System-Installdialog erscheint? (heute nein) |

---

## 8. Empfehlungen mit Prioritäten

### P0 — Vor BETA zwingend
| ID | Maßnahme | Aufwand | Wer entscheidet |
|----|----------|---------|-----------------|
| B1 | XML: „DIN EN 13508-2"-Label entfernen/abschwächen (→ „proprietäres DrainQ-Format") **oder** echtes ISYBAU bauen. Minimal = Re-Label. | S (Re-Label) / L (ISYBAU) | CEO/Produkt |
| B4 | Kiosk: `startLockTask()` bei Device-Owner koppeln (`isDeviceOwner()` existiert) + Golden-Image-Provisionierung. | M | CEO + Ops |
| V1–V4 | On-Device: Hardtasten-Mapping (M9), Frequenz-Mapping (M8), V4L2/chmod, Kamerakopf. Ergebnis entscheidet, ob M8/M9 echte Code-Fixes werden. | M | Entwicklung |
| M1/M2 | Tote Toggles SD/HD + Hardware-OSD: **entfernen** (Köpfe HD-only) oder **anbinden**. | S (entfernen) | CEO/Produkt |

### P1 — BETA-relevant, zeitnah
| ID | Maßnahme | Aufwand |
|----|----------|---------|
| B2 | GitHub-Release mit `releases.stable.json` + APK (`versionCode > 3`) publizieren. | M |
| B3 | Installer-Status-Receiver ergänzen (sonst hängt jede Installation). | M |
| M15 | 404/NotConfigured in der Update-UI sichtbar machen statt „aktuell" (neuer i18n-Key). | S |
| M12 | Projekt-Löschdialog lokalisieren (Datenverlust-Warnung in fremder Sprache!) — i18n-Keys via translations_raw. | S |
| M13 | HW-Lifecycle an `onResume/onPause` koppeln. | M |
| M3 | OSD-Burn-in im `LocalBitmapRecorder` (oder Overlay-Wahl im Lokal-Modus ausblenden). | M |
| M4 | `fallbackToDestructiveMigration` Policy klären (entfernen / `…From(1,2)`). | S |
| M5 | DIN-Hierarchie: flaches Projekt-Modell bewusst beibehalten + tote DAOs/Repos markieren **oder** verdrahten. | L |
| M6/M7 | XML aus `mainCode` statt Text-Split; PDF+XML-Auslieferung (ACTION_SEND_MULTIPLE/ZIP). | S/M |
| M10/M11 | Tote Screens Connection/Reports entfernen oder einhängen. | M/S |

### P2 — Politur nach BETA
- M18 (Offline-Karte Auto-Refresh), M14 (HOME-Kategorie/Provisionierung), Update-Fortschrittsanzeige (echte Bytes/Verifying), `weatherError`-Reset, Damages-Tab-FAB, BackHandler app-weit, Sekundär-L10N (OfflineMaps/Toasts/GPS-Snackbar), Foto-ohne-Frame-Quittung, RX-Read-Failure-Recovery.

### P3 — Nice-to-have / Schuld
- LinearMeterCalculator (Kabel-Dehnung), ThemeMode.SYSTEM-Segment, tote DAO-Methoden entfernen, deprecated WifiManager-API, Splash-Auto-Dismiss, A11y-Lokalisierung, Runtime-Permission-Prime.

---

## 9. Test-Lücken (nur BETA-kritisch)

6 Testdateien aktuell, Schwerpunkt Serial-Codec + Update. **Erfassungs-/Persistenz-Kern ungetestet.** Minimal absichern:
1. **Schnellaufnahme-Bucket** (`ProjectRepository.getOrCreateQuickProjectId`) — zweimal = ein Bucket (sichert den #6/#8-Fix).
2. **Capture-Persistenz** (`DamageRepository.saveDamage` → `getDamagesForProject`).
3. **Export-Vollständigkeit** (`generateZipWithXml` bündelt alle Fotos/Videos/Audio).
4. **Frequenz-Mapping-Konsistenz** (nach V2) — TX-Code ↔ `freqName` gegen eine Quelle.
5. **DB-Migrationen** 3→8 (Room `MigrationTestHelper`) — entschärft M4.

---

## 10. Branch-/Handover-Status

- **`feature/beta-hardening`** (von `feature/network-settings`): 6 Code-Quick-Wins + 1 Docs-Commit, **kein Merge**, `assembleDebug` grün. Lokal, nicht gepusht.
- **Nächste Schritte zur Konsolidierung:** P0/P1 abarbeiten → `feature/beta-hardening` reviewen → in `feature/network-settings` mergen → `master` → Tag `v0.4.0`.
- **Repo-Regeln:** Git nur lokal, **nie `git add -A`** (CRLF-Mount-Churn), gezielt stagen. Kein `su`/Root; HW-Serial nativ. Vollbefund: `BETA_READINESS_AUDIT.md`.
- **Offene CEO-Entscheidungen (blockieren P0):** XML-Compliance (B1), SD/HD- & HW-OSD-Toggle (M1/M2), tote Screens (M10/M11), Destructive-Migration-Policy (M4), DIN-Hierarchie (M5), HOME-Kategorie/Provisionierung (M14/B4).

---

*Erstellt aus 15-Schichten-Voll-Audit + adversarialer Code-Verifikation. Alle Befunde Datei:Zeile-belegt; die 18 gegengeprüften Kernbefunde wurden zu 100 % am Code bestätigt.*

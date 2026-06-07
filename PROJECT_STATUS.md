# drainq.one — Status

## BETA-Welle 2 (Gap-Schließung Original-App) — CODE FERTIG 2026-06-07 (im Working Tree auf `feature/beta-wave-1`, NICHT committet)
**Basis:** Feature-Gap-Analyse gegen Bominwell-Original (`FEATURE_GAP_ANALYSE_ONE.html`) + OSD-Tiefenprüfung; alle 12 Gap-Punkte + 4 OSD-Befunde einzeln per CEO-Entscheid beschlossen.
- **W1-A Tote Schalter raus:** Tag/Nacht (F7) komplett entfernt (Enum/Leiste/Mapping); beide HW-OSD-Schalter (Settings `use_hardware_osd` + In-Inspektion `hardware_osd_visible`) raus → heilt auch die Foto-OSD-Falle; `toOsdSettings` hängt nur noch an `osd_enabled`.
- **W1-B OSD:** Sonde/Neigung aus dem OSD entfernt (`osd_show_inclination` + `showInclination` raus, `buildOsdLine2` = Meter+Datum); Unicode echt gerendert — `asciiSafe`-Transliteration komplett raus (Canvas = System-Font-Fallback, RTSP-drawtext = UTF-8-Textfiles).
- **W1-C Pause/Fortsetzen:** `LocalBitmapRecorder` mit PAUSED-State (eine durchgehende MP4, Pausenzeit fehlt im Video); F3/Aufnahme-Taste = Pause/Weiter-Toggle während Aufnahme; REC-Chip → amber „PAUSE"; Timer pausiert; Guards getestet (`LocalBitmapRecorderStateTest`).
- **W1-D Helligkeit:** Slider in Einstellungen (Window-Brightness, keine Spezial-Permission; −1=System, 5–100 manuell), reaktiv in MainActivity.
- **W1-E DIN/XML KOMPLETT RAUS (CEO):** Schadenserfassung nur noch Presets+Position+Freitext. `XmlExportService`/`XmlModels`/`PipeEntity`/`InspectionEntity` gelöscht; `DamageEntity` schlank; **Room v9 + MIGRATION_8_9** (damages-Neubau, legacyDamageType→damageType-Fallback, DROP pipes/inspections); ZIP ohne XML (eine `generateZip`); Export-Dialog ohne XML-Option; simple-xml-Dependency raus. Erledigt Audit-B1 nebenbei.
- **W1-F USB-Export (FTP-Ersatz):** `UsbExportService` + `UsbExportDialog` — Komplett-Projekt ODER Einzeldatei-Auswahl auf <Stick>/DrainQ/<Projektnr>/, Fortschritt, Stick-Erkennung (StorageManager, API30+); `MANAGE_EXTERNAL_STORAGE` im Manifest (Sideload-Gerät), Dialog bietet Sprung zur Berechtigungsseite; neue Header-Aktion „USB" in ProjectDetail.
- **W1-G Mikro-Check:** `tools/check-microphone.ps1` (Feature-Flag, AudioPolicy, ALSA-Capture, tinycap-Praxistest) → Ergebnis entscheidet internes Mikro vs. BT-SCO für Audio-in-Video (Beschluss 12/O1).
- **L10N:** neue Keys nur de+en eingepflegt (record_pause/resume, brightness_*, usb_*) — restliche 33 Sprachen über Fallback de; bei nächstem L10N-Lauf nachziehen.
- **NÄCHSTE SCHRITTE:** (1) Build+Tests auf dem Host: `$env:JAVA_HOME="C:\Android\jdk17"; cd C:\Projekte\drainq.one; .\gradlew assembleDebug test` (2) gezielt committen (nie `git add -A`) (3) On-Device: Pause-MP4 durchgehend? USB-Stick-Export real? Helligkeit? Unicode-OSD? (4) Mikro-Check ausführen. **Nach BETA beschlossen:** Playback-Distanzspur+Tempo, Hotspot SSID/PW, OSD voll konfigurierbar, Geräte-Info-Seite; Roadmap: Kopf-FW-Update; nicht umsetzen: Benennungsregeln.

## BETA-Welle 1 (P0+P1) — ERLEDIGT 2026-06-06 (Branch `feature/beta-wave-1`)
**Build:** `assembleDebug` grün · **Tests:** 106 grün (vorher 95). AP1–AP11 umgesetzt (Details: `RESULT_BETA_WAVE_1.md`).
- **P0:** B1 XML-Re-Label (DrainQ-XML, kein DIN-Falschversprechen) + strukturierte Schadensfelder + PDF/XML gemeinsam; B4/M14 echter Kiosk (LockTask+Device-Owner+HOME); M1/M2/M3 SD-HD- & Hardware-OSD-Toggle echt + Lokal-OSD-Burn-in; M8 Sonde-Frequenz aus einer Quelle.
- **P1:** B2/B3/M15 Self-Update (Installer-Receiver, ehrliche 404-Anzeige, Release-Doc); M12 Löschdialog lokalisiert; M4 destruktive Migration raus + Schema-Export; M13 HW-Lifecycle; M5 tote DIN-Hierarchie raus; M10/M11/M19 ConnectionScreen+ReportsScreen erreichbar/echt.
- **Nächster Schritt:** On-Device-Verifikation **V1–V12** (`RESULT_BETA_WAVE_1.md`), GitHub-Release publizieren (`docs/RELEASE_PUBLISHING.md`), dann Review/Merge → master → Tag `v0.4.0`.
- **Offen (P1-Rest):** L10N-Generator out-of-sync (re-syncen oder stilllegen), Update-Byte-Fortschritt, 3→8-Migrationstests (androidTest), restliche Niedrig-L10N.

---

**Stand:** 2026-06-04 (Abend) · **Rolle:** ONE-Schiebekamera — läuft direkt auf der ONE-Hardware (RK3588, Android), Steuerung seriell `/dev/ttyS5`, Video V4L2 `/dev/video0`
**Stack:** Kotlin / Jetpack Compose (Room, Koin, ExoPlayer/Media3, iText7, Coil-SVG) · NDK (`app/src/main/cpp/v4l2bridge.c`) · **Pfad:** `C:\Projekte\drainq.one` (GitHub: ThomasViell/one-app)
**Aktiver Branch:** `feature/network-settings` — Superset des Tagewerks (SA-Design-Rollout + Device-Fixes + Netzwerk-Feature + Pager). **⚠ NICHT gepusht** (kein origin-Tracking) → bis zum Push ist alles nur lokal = Verlustrisiko.
**Gerät:** Serial `233b4bd2865177ed`. **Build:** `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`

## Tagewerk 2026-06-04 (alles committet, am Gerät verifiziert)
**SA-Design app-weit (Welle 0–4):** Fundament Amber/Dark+Light, Inter (Barlow raus), Tabler-Icons, Dq-Komponenten (`c2aee91`); Inspektion Cinema + OSD live/Burn-in (`030bdf4`); Navigation/Home/Projekte/Form/MapPicker (`dce66d1`); Detail/Galerie/Media-Dialoge/Reports (`e674c59`); Update/Connection/OfflineMaps/Splash (`dc260ed`). Vorgabe + Mockups in `docs/design/`.

**Device-Fixes (am Gerät iteriert):** Inspektion-Bedienpanel verschlankt + unteres Band on-demand/transparent (`b5313d2`,`6232477`,`1c283d3`); Status-Chips → ein Batterie-Chip, Quelle = **Android-System-Akku** (wie OEM-App, RE-verifiziert; serielle GROUP_CAMERA-Spannung war Fehlannahme) (`a72eec5`,`af595df`); Splash mit echtem DrainQ-Logo theme-abhängig (`da3dc6e`); Navi-Leiste DrainQ-Bildmarke statt „ONE" + „Verbunden"-Chip auf Home raus (`e1c6110`); Inspektion-Back-Button größer/transparenter (`a2b6857`); ProjectDetail-Header-Aktionen als Icon+Label, größer/weiter auseinander, Back-Pfeil 72-dp-Fläche (`d6a80d4`,`adf3ec3`). Logo-Assets (CORE entfernt) in `res/raw/logo_drainq_*`.

**Pager:** wiederverwendbare `DqPager`-Komponente, 6/Seite, „Seite X/N" + Zahlen + Pfeile, Fensterung > 7 Seiten; auf Home, Projekte und ProjectDetail-Tabs (`f6d6963`,`714804c`).

**Netzwerk-Feature (eigener Schwerpunkt):** neuer „Netzwerk & Verbindung"-Screen — Online-Status, In-App-WLAN (adaptiv nach Device-Owner: privilegiert WifiManager direkt, sonst WifiNetworkSpecifier+requestNetwork), Tethering-Sprung, DrainQ-Cloud-Login als Stub (später) (`e91aacc`,`8c73de1`,`1c869c1`). Diagnose am Gerät: Test-Gerät kein Device-Owner → REQUEST-Pfad; voller geräteweiter Connect erst auf der provisionierten ONE.

**Früher am Tag:** #14 Keyboard-folgt-Sprache + Compose-1.7 (`eb390ba`), Golden-Image-Doc + Aufräumen (`3f758cb`), Status/Handover nachgezogen (`f1ed28b`).

## Offen / nächste Schritte
1. **Kamerakopf C10/C18 — Erkennung (offen, MORGEN zuerst):** Auf dieser ONE seriell NICHT erkennbar — Kopf liefert nur Group 21 (Status, alles 0) + Group 22 (Meter), **kein Group 23 (cameraID)/24 (Version)**, auch nicht beim Connect/Umstecken. USB-Deskriptor beider Köpfe identisch (`534d:2109` MACROSILICON „USB Video", leerer Serial). cameraId-Parsing + Chip oben rechts eingebaut (Commit `b849873`, Mapping 10→C10/18→C18), bleibt aber leer. **Nächster Schritt:** Original-OEM-App `com.bominwell.minipush` auf der ONE starten und prüfen, ob SIE den Kopftyp zeigt/wechselt — wenn ja, sniffen wie; wenn nein, manuelles Dropdown bleibt. (RX-Diagnose-Log ist temporär → reverten. Tool: `tools/check-camera-head.ps1`.)
2. **SD/HD-Toggle:** nicht verdrahtet (nur Metadatum, kein Recorder liest es; Köpfe HD-only). Entscheiden: Toggle raus (HD fix) oder SD echt = 720×576-Aufnahme.
3. **Geräte-Gate aus Welle 1** (OSD-Einbrennung/Recording + PDF-Overlay) verifizieren — vor master-Merge.
4. **Branch-Konsolidierung:** `feature/network-settings` (Superset) ist die Live-Linie; Draft-PR #2 (sa-design-rollout) nachziehen/ersetzen → master → Tag `v0.4.0`.
5. **DrainQ-Cloud-Login** echt ausbauen (OAuth/Token gegen drainq.web, Keystore/Encrypted, Audit) — „später". Pager auf weitere Listen bei Bedarf. Autostart (geparkt). Reorg app-one→drainq-android.

## Hinweise
- **Git nur lokal** im Terminal; CRLF-Mount-Churn (~199 Dateien) ist kein echter Stand. Gezielt committen, nie `git add -A`.
- **Akku-Quelle** = Android-System-Akku (`ACTION_BATTERY_CHANGED`), nicht seriell. Device-Owner-Pfad für WLAN: Golden-Image (`docs/PROVISIONING_GOLDEN_IMAGE.md`).
- Kein `su`; HW-Serial nativ. bash-Reads über den Mount lügen — host-seitig/lokal prüfen.

## Letzte Änderungen
- [2026-06-04] SA-Design-Rollout (Welle 0–4) + Device-Fixes + Pager + Netzwerk-Feature, am Gerät iteriert; **gepusht** auf `feature/network-settings`. Abends: Kamerakopf-Untersuchung — C10/C18 weder per USB noch seriell unterscheidbar (kein cameraID auf der Leitung); cameraId-Chip eingebaut (`b849873`); morgen OEM-App gegenprüfen.
- [2026-06-03] Hardtasten/Softbutton-Leiste, Licht −/+, V4L2-Recording, Cinema, UI-Politur.
- [2026-06-02] Feldtest-Findings + HW-Serial-Fix nativ, Kiosk-Schalter, Schnellaufnahme.

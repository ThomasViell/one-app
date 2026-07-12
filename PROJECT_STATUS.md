# drainq.one — Status

## 2026-07-12 — M2-Fix + 0.5.7-beta/507 im Portal; Gerätetests blockiert (ONE lädt/evtl. defekt)
**Kette seit 06.07.:** Louis-Feedback 06.07. → Wellen 1–5a (`feature/dual-mode`, 0.5.5/505) → Louis-Test 10.07. (B2/B1/M2/M3/M4) → Fixes 0.5.6/506 (`a94eaae`) → Geräteabnahme 11.07. auf Thomas-ONE: **B2/M3/M4/B1 OK, M2 FEHLER** (Timing-Race + Idempotenz, Diagnose in `FIX_M2_KAMERATYP_PROMPT.md`).
- **M2-Fix umgesetzt 12.07.** (CC Sonnet, `RESULT_FIX_M2.md`): Beobachter-Effekt in `InspectionScreen.kt` (hardwareState → CameraHead, distinctUntilChanged) trägt `kameratyp` nach, sobald C10/C18 erkannt und Feld leer; `cameraTypePrefill` bleibt Override-Schutz. 382/382 Tests grün, davon 2 neue Backfill-Tests. **⚠ UNCOMMITTET** — Branch-HEAD lokal+origin weiter `a94eaae`; committen+pushen steht aus.
- **Portal:** 0.5.7-beta/507 am 12.07. published (Ein-Befehl `tools\publish-one-release.ps1`), per `releases.beta.json` verifiziert LIVE. Workflow-Regel bestätigt: Test-Installs IMMER als Portal-Update (testet Update-Pfad mit), nie adb install.
- **Blocker:** Thomas-ONE reagiert nicht (Akku leer oder defekt), lädt. Offen dadurch: Update-auf-0.5.7-Test, M2-Nachtest (Bucket „Schnellaufnahme_110726" VORHER löschen), M4 pdffonts-Messung, lange Aufnahme/Pause/Recovery, Kill-Test.
- **Merge-Gate zu** (unverändert): M2-Nachtest, M4-Fontmessung, Station mit laufendem Meterzähler (nur Louis' Rig), Louis-Meterwert-Rückfrage.
- **Tagesziel:** 0.5.7 an Louis + Antwort auf seine Tests (Statusliste, Meterwert-Frage, Testauftrag Meterzähler). To-dos: `OFFENE_TODOS_2026-07-12.md`.

## 2026-06-13 — Louis-Feedback W0–W8 umgesetzt (Branch `feature/louis-feedback`, GEPUSHT)
**Quelle:** Feldtest-Mail Louis Wigman „Software ONE Pushrod" → Analyse `FEEDBACK_Kollege_2026-06-13_Analyse.md`, Auftrag `LOUIS_FEEDBACK_WELLEN_PROMPT.md`. CC-Lauf: von `feature/beta-wave-1`@f86cb6e abgezweigt, **9 Commits, `assembleDebug test` grün, adversariale Review 0 kritische Befunde**. Vollständige Doku: `RESULT_LOUIS_FEEDBACK.md`.
- **W1** `2dfdb93` — grauer Balken (Android-System-Leiste/launcher3) weg: `HideSystemBarsInDialog()` (Original-5894-Technik) in ~37 Compose-Fenster (Dialoge/AlertDialogs/Popups/Dropdowns).
- **W2** `a011373` — Auto-Ausblenden der Bedienleiste optional, **Default AUS** (DataStore `controls_auto_hide`, Settings-Toggle; de+en).
- **W3** `cc3e674` — Meterzähler stabil: Plausibilitätsfilter + Median-3 + RX-Subframe-XOR; `MAX_STEP_MM` 300→1000 mm; neue Tests.
- **W4** `3ee5d7c` — ProjectDetail-Reiter ≥72 dp + farbige Icons (Foto/Schaden/Video/Notiz), Amber-Akzent.
- **W5** `94a5e2e` — Hauptnavigation = Home/Inspektion/Einstellungen; Projekte über Home-Kachel (keine Sackgasse).
- **W6** `d879e7c` — Notiz aus der Hauptbedienung raus; NoteDialog/NotesTab/Daten unverändert (kein Datenverlust).
- **W7** `b148b8e` — Doku: USB-Einrichtung (7.3) + Sprach-Neustart (8.1), docx neu generiert.
- **W8** `b619653`/`fa33c48` — Review-Feinschliff + `RESULT_LOUIS_FEEDBACK.md`.

**OFFEN = einziges Merge-Gate:** On-Device-Abnahme — Gerät `233b4bd2865177ed` war im CC-Lauf nicht per adb erreichbar (kein installDebug/Screenshots). Vor Merge→master→Tag nachholen: `installDebug`, dann W1 jeder Dialog inkl. Soft-Tastatur (Schaden/Notiz/WLAN) + W3 Kabel mehrfach aus-/einziehen.
**Nebenpunkte:** PDF-Handbuch — `generate_manual.js` lädt noch `barlow_*.ttf`, `res/font` hat nur `inter_*` (Barlow in `Barlow.zip` im Repo-Root) → Skript auf Inter umstellen ODER Barlow außerhalb `res/font` laden (nicht ins APK); docx ist aktuell. Louis-Reply-Entwurf liegt in Outlook. Optionale W3-Folge: echte Linearisierungstabelle (nur am Kabel kalibrierbar).

## 2026-06-07 NACHMITTAG — Welle 2 GETESTET + Portal-Anbindung LIVE (Branch `feature/beta-wave-1`, ~7 lokale Commits, ⚠ NICHT gepusht!)
**Geräte-Autotest (CC via `AUTOTEST_BETA_PROMPT.md`, Bericht `TESTREPORT_BETA_AUTOTEST.md`):** T0–T14 → 11 GRÜN, 0 Crashes. Migration v9 verifiziert (10 Projekte/6 Schäden/5 Notizen erhalten, pipes/inspections weg). Pause/Resume bewiesen (MP4 14,75 s, Pausenzeit fehlt im Video). T0 = Skript-Artefakt (Dialog-Knopf heißt „Mit Einblendung"). **1 echter Bug gefunden+gefixt+committet (`8a3bd17`):** doDamage/doPhoto ohne localFrame-Fallback beim Canvas-Player → Foto/PDF leer.
**Branding:** komplett DrainQ (DeviceType „DrainQ ONE/TWO", alle 35 Sprachen + translations_raw NSP3CT→DrainQ, app_full_name „DrainQ ONE – Sewer Inspection Software", kein „-0.00m" im OSD). ⚠ Lehre: Commit `ca9f197` enthielt eine durch Mount-Python-Rewrite korrumpierte LocalizationManager.kt (~80 Zeilen verloren) — geheilt via `git restore --source=d387710` + Edit-Tool-Neuersetzung (`3fd3ff4`). NIE mit bash/python über den Cowork-Mount in große Dateien schreiben.
**Autostart + echter Kiosk FINAL am Gerät:** `cmd package set-home-activity …/com.uip.oneapp.MainActivity` + `dpm set-device-owner com.uip.drainq.one/com.uip.oneapp.bootstrap.OneDeviceAdminReceiver` → bootet ohne Dialog direkt in die App, LockTask echt, In-App-WLAN freigeschaltet. Testplan-Block F damit faktisch abgehakt.
**Update-Brücke App↔Portal LIVE:** App holt Updates von `https://license.drainq.com/api/software/one/` (Kanal beta, versionCode-Schema 400er, Commit `d387710`); Portal-Endpoint `releases.{channel}.json` deployt + verifiziert (sauberer 404 = kein Release angelegt).
**Übersetzungen ins Portal eingespielt:** `tools/l10n-import-to-portal.ps1` (Quelle = LocalizationManager.kt-Parser; translations_raw ist VERALTET, nur 198 Keys) → **451 de + 421 en Keys** im Portal (253 neu/198 aktualisiert). Partner-Strecke (DeepL-Auto + Review + 95-%-Gate) ist scharf — Partner müssen nur noch angelegt/zugeordnet werden.
**OFFEN (Reihenfolge):** (1) ⚠ `git push` drainq.one — alle heutigen Commits nur lokal! (2) Release one/beta **0.4.1** im Portal-Admin anlegen (Debug-APK!) → Self-Update am Gerät testen. (3) Handtests: USB-Stick-Export, `tools/check-microphone.ps1` (→ entscheidet Audio intern vs. BT-SCO), Helligkeit visuell, Meter/Sonde/Licht am Objekt. (4) **CEO-Frage OFFEN: Sprachauswahl der BETA-App auf de+en kürzen oder alle 35 lassen?** (App zeigt fest eingebaute 35; Portal-Freigabe wirkt erst mit Live-Nachladen nach BETA). (5) Merge-Kette → master → Tag, Testkunden-Gerät. (6) CC-Feedback vom letzten autonomen Testlauf steckte in hängender Chat-Nachricht — nach App-Neustart erneut schicken.

## BETA-Welle 2 (Gap-Schließung Original-App) — CODE FERTIG 2026-06-07 (committet als `a248e23` + Folgecommits)
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

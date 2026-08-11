# drainq.one — Status

**Stand:** 2026-08-05 · **Branch:** `master` @ `92ae04a`, synchron mit origin · Tag `v0.9.0` auf `a1afaf7` · `feature/dual-mode` vollständig in `master` enthalten (nachgeprüft: `git merge-base --is-ancestor feature/dual-mode master` → ja)
**Rolle:** ONE-Schiebekamera — läuft direkt auf der ONE-Hardware (RK3588, Android), Steuerung seriell `/dev/ttyS5`, Video V4L2 `/dev/video0`
**Stack:** Kotlin / Jetpack Compose (Room, Koin, ExoPlayer/Media3, iText7, Coil-SVG) · NDK (`app/src/main/cpp/v4l2bridge.c`) · **Pfad:** `C:\Projekte\drainq.one` (GitHub: ThomasViell/one-app)
**Geräte:** Thomas-ONE `233b4bd2865177ed` · fabrikneue Test-ONE `cc1615f07da5e76f`
**Build:** `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug` — Beta baut OHNE Release-Keystore (`assembleDebug`), NIE `assembleRelease`
**Letzter Code-Stand:** 0.9.0/900, im Portal-Beta-Kanal freigeschaltet seit 30.07. — live geprüft (05.08.): `releases.beta.json` meldet `"version":"0.9.0","versionCode":900,"releasedAt":"2026-07-30"`
**Priorität:** ONE ruht hinter drainq_sa_cpp und drainq.web (CEO 26.07.)

---

## Offene Punkte

Die vollständige, geführte Liste offener Punkte steht in `OFFENE_PUNKTE.md` (Stand 05.08.). Sie ersetzt die früher hier geführten Punktelisten — es gibt nur noch die eine Liste im Repo.

## Vendor-Anforderung: Kamerarechte

**CEO-Entscheid 11.08.:** in `OFFENE_PUNKTE.md` von „Blockiert Auslieferung" nach „Seit Juli erledigt" verschoben — mit dem Camera2-Umbau (29.07.2026) greift die App nicht mehr direkt auf `/dev/video0` zu (`AppModule.kt:82`, `v4l2bridge.c:3`, `Camera2FrameSource.kt:49`), das Bild läuft über die reguläre `android.hardware.camera2`-Schnittstelle. Die ueventd-Regel `/dev/video*   0666   root   root` (ADR-0003) war für den früheren direkten App-Zugriff nötig. Ob sie für die Camera2-Schnittstelle überhaupt noch gebraucht wird, ist offen — belegt ist das Kamerabild bisher nur auf Geräten, die die ueventd-Zusatzschicht (overlayfs) bereits tragen. Neuer offener Punkt in `OFFENE_PUNKTE.md`: eine frisch geflashte ONE ganz ohne diese Schicht ist ungeprüft — genau die Hypothese aus ADR-0005, Abschnitt 5.

Unverändert offen, unabhängig davon: das Board ist `userdebug`, root im Feld per USB ist möglich (CRA-relevant, Kandidat für eigene ADR) — steht in `OFFENE_PUNKTE.md`.

*(Der frühere Vendor-Punkt „SoftAP-Privileg bzw. Plattform-Signatur" ist erledigt: der Plattform-Signaturschlüssel liegt vor und ist in 0.9.0 im Einsatz, der Hotspot läuft mit eigener Marke; ADR-0005 ist seit 11.08. angenommen — `OFFENE_PUNKTE.md`, Abschnitt „Seit Juli erledigt".)*

---

## 2026-07-30 bis 2026-08-05 — Zusammenführung, Werkzeug-Selbstaktualisierung, Portal-Freigabe, Doku-Archivierung

- **Zusammenführung nach `master` + Version 0.9.0** (30.07.): `feature/dual-mode` per Fast-Forward über `feature/camera2-umstieg` und `feature/werkseinrichtung` nach `master` gemerged (`f1ed28b..a1afaf7`, 949 Dateien, 225 Commits), Tag `v0.9.0` auf `a1afaf7` gesetzt. Auf beiden freigegebenen Geräten per `adb install -r` aktualisiert und GRÜN bestätigt (Kamerabild da, Kiosk automatisch im Vordergrund). — `docs/archiv/2026-07/RESULT_MERGE_0_9_0_2026-07-30.md`
- **Werkzeug-Selbstaktualisierung aus dem Portal** (30.07.): `tools/werkseinrichtung/Update-WerkzeugApp.ps1` lädt die im Lizenzportal freigegebene APK selbst, prüft Prüfsumme + Plattformsignatur und tauscht die lokale Datei aus; Negativprobe (Portal nicht erreichbar) unter echter Laufzeit geprüft. — `docs/archiv/2026-07/RESULT_WERKZEUG_AUTOUPDATE_2026-07-30.md`, Commits `c10eeff`/`c717c38`
- **Version 0.9.0 im Portal freigeschaltet**: `releases.beta.json` (live abgefragt 05.08.) meldet `"version":"0.9.0","versionCode":900,"releasedAt":"2026-07-30"` — der beim Merge-Lauf bewusst mit `-SkipPublish` zurückgehaltene Release-Datensatz ist inzwischen freigeschaltet.
- **Doku-Aufräumung mit Archiv** (05.08.): 110 abgeschlossene Root-Markdown-Dateien nach `docs/archiv/` verschoben (`54cc05d`), tote Verweise auf archivierte `HANDOVER.md` geradegezogen (`384e5c3`), Ergebnisbericht `RESULT_DOKU_ARCHIVIEREN_2026-08-05.md` (`f9e832b`), die drei Juli-TODO-Listen zu `OFFENE_PUNKTE.md` zusammengeführt (`fbdb883`).

---

## 2026-07-29 — Sicherungs-Commit: 83 unverfolgte Dateien gerettet (kein Codebau)
Beim Einlesen gemessen: 83 unverfolgte Einträge im Arbeitsbaum, darunter die komplette Engineering-Disziplin, ADR-0004, alle WH-/FIX-Prompts, sämtliche Run-Reports und die 0.5.19-Handbücher — nichts davon in Git. Dasselbe Muster hatte im Repo `drainq_sa_cpp` am 27.07. rund 47 Dateien endgültig gekostet.
- Vier Commits auf `feature/dual-mode`, gepusht `8f04301..42addb1`, ahead/behind 0/0:
  `9e906fc` docs/engineering 01–04/06 + naming-convention + ADR-0004 (11 Dateien, 1631 Zeilen) · `2f8d9ff` WH1–WH5-Prompts, FIX-Prompts, Run-Reports, publish-one-l10n.README (19 Dateien, 1508 Zeilen) · `bd9ace3` Bedienungsanleitung DE+EN 0.5.19 + `tools/manual/assets/pipe_frame.png` · `42addb1` `.gitignore`.
- Ignoriert statt committet: `tools/_autotest/` (52 MB), `tools/_oem/` (114 MB), `app/datastore/` (Laufzeit-DataStore), `_to_delete/`.
- Unangetastet: BOM-Änderung in `tools/publish-one-release.ps1`, CRLF-Phantom (~210 Dateien), kein Merge, kein Build, kein Publish.
- **Lehre (Ursache korrigiert):** Während des Laufs tauchte `.git/index.lock` immer wieder auf. Nicht „Caching zwischen PowerShell und Git-Bash", sondern der Cowork-Mount: er legt die Lock-Datei an und darf sie nicht wieder entfernen (`unable to unlink … Operation not permitted`). **Regel: während ein CC-Lauf in einem Repo arbeitet, laufen dort keine Git-Befehle über den Mount.**

## 2026-07-16/17 — Hilfe-System W-H1…W-H5 KOMPLETT, gemergt in `feature/dual-mode` (`8f04301`)
Ziel: das Hilfe-System ist die einzige Wahrheit, das Handbuch ist ein Export daraus. Sechs bindende CEO-Entscheidungen E1–E6 vom 16.07.; E6 lautet: keine Halluzinationen, jede Aussage doppelt belegt (Screenshot + Code), Opus-Gegen-Audit pro Seite, Unbelegtes fliegt raus.

**W-H1 — Screenshot-Harness am Gerät.** `debugrig/ScreenshotRigBus.kt`, `ScreenshotRigReceiver.kt` + `DemoDataSeeder.kt`, `tools/manual/scenes.json` (21 Szenen), `tools/manual/capture.ps1`. 7 Unit-Tests grün. **Am Gerät** (0.5.17/517 via Portal-Update): 42/42 Szenen (21 DE + 21 EN), Live-Kamerabild 645 KB, kein Schwarzbild. Audit-Blocker gefunden und behoben: Receiver und Seeder lagen in `src/main` statt `src/debug` — bei `isMinifyEnabled = false` wären sie im Release-Bytecode gelandet.

**W-H2 — Texte + PDF.** `assets/help/help_de.json`/`help_en.json`, je +436 L10n-Keys. Erste PDFs DE 2824 KB / EN 2685 KB, 8 Kapitel. 16 von 21 Szenen PASS — die 5 Fehlschläge waren alle Dialoge.

**W-H3 — In-App-Hilfe.** Root-Cause der 5 Fehlschläge: `ScreenshotRigBus.uiState` wurde in InspectionScreen, ProjectDetailScreen und ProjectFormScreen nicht abgehört. Neue Klassen `HelpRepository`/`HelpSheet`/`HelpButton`, **„?"-Knopf auf 12 von 12 navigierbaren Screens**, offline. `scr01_splash` ersatzlos gestrichen (kein NavGraph-Ziel) → 20 Szenen. PDFs neu: DE 5,2 MB / EN 5,0 MB. **Am Gerät** (0.5.18/518): In-App-Hilfe auf 5 Screens DE+EN mit Bildbeweis in `docs/manual/help_proof/`.
Wichtiger Nebenbefund: `LocalizationManager` liest aus hartkodierten Kotlin-Maps, NICHT aus JSON — deshalb liest `HelpRepository` `assets/i18n/<lang>.json` direkt.

**W-H4 — synthetische Screenshots ohne Gerät.** Screenshots entstehen jetzt JVM-only, 19 von 20 Szenen. Damit können Partner Sprachbilder selbst rendern (`docs/manual/PARTNER_PIPELINE.md`, `render.ps1`, `build-language.ps1`).

**W-H4b — die Sprachschleife war kaputt und hat es nicht gemeldet.** Gegenprüfung ergab: alle 19 EN-Renderings waren byte-identisch mit DE. Ursache: `LocalizationManager.init(context)` im Test startete einen IO-Coroutine, der `_currentLanguage` asynchron auf „de" zurücksetzte. Nach dem Fix 20/20 Szenenpaare unterschiedlich. **Konsequenz: hartes Sprachdifferenz-Gate in `render.ps1`** — identische Hashes brechen den Lauf ab. Genau der Fehlertyp, der ohne Gate monatelang unentdeckt bleibt.

**W-H5 — drei Doku-Gates („Build bricht, Diff fängt, Release+Cron bauen").** `HelpCoverageTest` (Build wird rot, wenn eine Szene ohne Hilfe-Baustein existiert, inkl. Negativprobe), `tools/manual/verify.ps1` (Golden-Diff), 4-stufiger Docs-Gate-Block in `tools/publish-one-release.ps1` plus Wochenjob `weekly-manual-sync.ps1`. Opus-Audit PASS.

**Tests:** 448/448 grün, dreimal in Folge, nach dem Merge erneut mit `--rerun-tasks`. Der vorher sporadisch rote `UpdateE2ETest` hatte eine echte Ursache: `ProjectFormScreen` ruft `viewModel.setFilesDir(context.filesDir)`, und `getFilesDir()` liefert im JVM-Renderer null; die NPE landete beim globalen Handler und explodierte im achten `UpdateE2ETest`. Behoben rein in der Testdatei.

**Offen aus der Welle:** die Restpunkte (synthetische PDF-Vorschau, vier ungeprüfte Dialog-Hilfeseiten, deutsches Kartenbild in der EN-Strecke, Negativprobe nur als Code-Review, Portal-Purge-404, Roborazzi/Paparazzi-Widerspruch in ADR-0004) stehen einzeln in `OFFENE_PUNKTE.md`, nicht mehr hier.

## 2026-07-16 — Kamerabild auf fabrikneuer ONE war schwarz: gelöst, aber nur bis zum nächsten Re-Flash
Gerät `cc1615f07da5e76f`, fabrikneu geflasht. Ursache ist kein SELinux-Thema (das Board ist permissive), sondern schlichte Dateirechte: `/dev/video0` kommt als `0660 media:camera` hoch, die App läuft als `untrusted_app` ohne die Gruppe `camera`, `open()` scheitert. Ein `chmod 666` hilft, überlebt aber weder Reboot noch Umstecken, weil der Node neu erzeugt wird.
- Lösung: ueventd-Regel `/dev/video*   0666   root   root` in `/vendor/etc/ueventd.rc`, Wildcard weil der MS2109 zwei Nodes anlegt. Original gesichert in `tools/_oem/ueventd.rc.orig`.
- Beweis nach Reboot: `crw-rw-rw- root root 81, 0 /dev/video0`, `V4L2Bridge: Opened /dev/video0 -> fd=101`, MJPEG 1280×720, Stream mit 4 Puffern, Live-Bild-Screenshot.
- **Grenze:** der Fix liegt in overlayfs. Reboot ja, Re-Flash nein. Für die Flotte muss die Regel ins Golden-Image der `vendor`/`super`-Partition. `userdata` allein reicht nicht.
- Physisches Ab- und Anstecken wurde nicht direkt geprüft (die App hielt den Dateizeiger); der Reboot-Beweis deckt es nur indirekt.
- Änderungsantrag **CHG-05** ist eingetragen. `docs/engineering/05-deployment_one.md` existiert noch nicht und war bewusst nicht Teil des Laufs.

## 2026-07-14 — Engineering-Disziplinen auditiert: zwei echte Code-Defekte
Die Disziplinen 01–04 und 06 sind auditiert und liegen seit dem 29.07. im Repo. Zwei Funde sind keine Formalien:
- `androidTest/…/XmlExportTest.kt` referenziert den beim DIN/XML-Ausbau gelöschten `XmlExportService` — **die Datei ist nicht kompilierbar**. Vor Freigabe löschen oder archivieren.
- Tote Paho-MQTT-Dependency.
Beide sind als CHG in `06-maintenance_one.md` zu führen. Testinventar: 43 reale Testdateien, Traceability 36/36. Go/No-go bewusst offen gelassen — Entscheider ist der CEO.

---
*Ab hier: Verlauf bis 13.07.2026 unverändert.*

## 2026-07-13 — 0.5.14/514 published; Teil B+C am Gerät GRÜN; Teil A wartet auf Louis (2. Kopf)
- Commit `46a4742` auf `feature/dual-mode`, host-verifiziert. Beta **0.5.14/514** im Portal (Ein-Befehl).
  - **A** `cameraTypeAccumulate()` — Kopfwechsel im Projekt wird angehängt (z.B. „C18, C10"). NOCH NICHT device-getestet: braucht physischen Kopfwechsel C18→C10 → heute mit Louis.
  - **B** Projektliste zeigt Auftraggeber (`projectTitle()` = Auftraggeber — Standort — Projektnr.). **GRÜN am Gerät.**
  - **C** Aufnahmeweg-Schalter ersatzlos raus, immer HW + unsichtbarer Auto-Rückfall (`FallbackRecorder.kt`, `FeatureFlags.kt` gelöscht). **GRÜN am Gerät** (Aufnahme läuft normal).
- **HEUTE auf Thomas-RIG:** A Kopfwechsel C18→C10 **GRÜN**; Meterzähler (offeriert==eingebrannt) **GRÜN**. Haspel ist identisch zu Louis' — Fix ist hardware-unabhängig (Software-floor am Encoder), also zählt dieses Grün voll. **Meterzähler-Merge-Gate erledigt.**
- **MIT Louis nur noch Abnahme (kein Test):** Bestätigung, dass seine ursprüngliche Meterwert-Beanstandung weg ist.
- **OFFEN vor Merge:** nur noch Test 3 USB-Export (mit Louis, seine Büro-Befunde). Danach Merge `feature/dual-mode` → master. Louis-Mail ist gestern raus (passt).

## 2026-07-13 ABEND — L10n-Portal-Anbindung angefangen (Testballons FR/NO); Deploy-Blocker offen
Goldenes Image bewusst VERSCHOBEN (Thomas-Entscheidung) — bis dahin App-Sprachen über Portal testen.
- **Analyse:** Portal-Sprachsync ist beidseitig gebaut. Portal (`drainq.web`): `L10nApiController` + `TranslationController` mit `GET /api/locales?app=one`, `GET /api/translations/{code}.json?scope=one` (ETag), Import, Aktivieren, DeepL, Partner-Review. App-Seite (Lazy-Download) liegt fertig im Branch `feature/l10n-portal` (Repo `C:\Projekte\drainq.one-localization`), aber NICHT in `feature/dual-mode` gemergt; `l10n.portal.url` leer.
- **Live-Portal-Befund (`license.drainq.com`):** nur DE+EN haben Inhalt (core), 20 Locales, KEIN Norwegisch. Alte ONE-Referenzen waren gemischt/veraltet (UPPER_SNAKE aus alter App). Neuer Key-Vertrag der App = lowercase snake (`res/raw/l10n_de.json`, 429 Keys).
- **Gebaut:** Skript `C:\Projekte\drainq.one-localization\publish-one-l10n.ps1` (liest res/raw, Import Scope ONE, optional DeepL, `-PurgeOne`). Import lief: 429 Keys (68 neu, 361 upd). DeepL-Trigger via generischem Endpoint = noop (ONE-Weg materialisiert Einträge erst über `L10nDeeplService`/Pending-Hintergrunddienst bzw. UI).
- **Neuer Portal-Endpoint gebaut+deployt:** `POST /api/admin/l10n/translations/purge?scope=ONE&confirm=DELETE` (ApiKey, Scope-Whitelist ONE/HMX, Confirm-Guard, Audit-Log). Commit `bcc8511` (audit-Branch) → cherry-pick auf master `8f37af8`, gepusht (`f88e6a6..8f37af8`). GitHub-Action „Deploy to Hetzner" ausgelöst.
- **BLOCKER (morgen):** Purge-Skript liefert weiter **404** — Endpoint am Live-Portal nicht erreichbar, obwohl gepusht. Ursache unklar: Deploy noch nicht durch / fehlgeschlagen / Route greift nicht. TODO morgen: GitHub-Action-Log „Deploy to Hetzner" prüfen; falls grün, Container-/Routing-Problem am Server. Erst danach: `.\publish-one-l10n.ps1 -PurgeOne -SkipDeepL`, dann DeepL FR/NO manuell in `/admin/translations`, Norwegisch (`nb`) vorher als Sprache anlegen.
- **Uncommittet:** `publish-one-l10n.ps1` (neu, drainq.one-localization) + Portal-Purge liegt schon auf master. drainq.web audit-Branch hat uncommittete PROJECT_STATUS/TODO/de.json/en.json (unabhängig).

## 2026-07-12 ABEND — ALLE Louis-Befunde gefixt + am Gerät grün (0.5.13/513); Louis-Antwort-Entwurf; Louis kommt 13.07.
(Ersetzt den Mittags-Block darunter: ONE kam zurück, M2-Fix committet, komplette Testrunde durch.)
- ONE wieder online → volle Selbst-Testrunde. Alle Louis-Befunde + Nebenpunkte GRÜN am Gerät (0.5.8–0.5.13): B1 Datums-Guard, M3 Sprachumschaltung+„Quick capture", B2 Feldeingabe, M4 Route-Pfeil inkl. **pdffonts gemessen** (Inter emb+subset, „→" im Textlayer), M1 Replay Echtzeit, M2 Kameratyp-Auto-Vorbelegung.
- **Meterwert-floor** (Offer-Pfad interpolierte → floor; „offeriert==eingebrannt") GRÜN am Gerät (3 Stellen exakt) — Merge-Gate-Kernpunkt. **Leitungsverlauf** Meter-Label-Kollision (idealY→adjY) GRÜN (3,91/3,95 m getrennt).
- **0.5.9-Aufräumung:** Sprach-Neustart-Dialog + Inspektionsmethode-Sektion (manuelle Kameratyp-/Inspektionssystem-Auswahl) ersatzlos ENTFERNT — beide device-grün.
- **Hardbutton Licht/Sonde** 3 Iterationen: Grundlogik (0.5.9) → Popup `focusable=false` (0.5.10, Fokus-Stehlen) → Sonde stale-closure = lokale MutableState `sondeTxCode` (0.5.11). Licht +10%/100→0, Sonde Off→33→640→512→Off, 3s Auto-Hide. GRÜN.
- **Speicheranzeige** (Home-Karte, intern+USB Füllstandsbalken, Farbe nach Füllstand, USB via `UsbExportService.findUsbVolumes`) + Auto-Refresh (Media-BroadcastReceiver bei USB-Wechsel) + Refresh-Icon. GRÜN (0.5.12→0.5.13).
- Alle Commits auf `feature/dual-mode` (u.a. `22342f5`, `918c4a9`), KEIN Merge, kein Tag. Betas 0.5.7→**0.5.13** im Portal (Ein-Befehl `tools\publish-one-release.ps1`).
- **Louis-Antwort:** Outlook-Entwurf angelegt (`create-reply-draft`, Thread „RE: DrainQ.ONE 0.5.5-beta", an l.wigman@uip.team, EN) — Thomas liest drüber + sendet. Text auch in `LOUIS_ANTWORT_ENTWURF_2026-07-12.md`.
- **MORGEN 13.07.: Louis kommt mit seiner ONE** → gemeinsam testen: Meterzähler mit seiner Haspel, M2-Szenario b (C18→C10, 2. Kopf), USB-Export nach seinen Büro-Befunden.
- **OFFEN:** Merge `feature/dual-mode` → master (alles device-grün, Merge-Entscheidung steht aus); die 3 Punkte mit Louis morgen; Louis-Mail senden.
- Prompts/Doku im Repo-Root: `FIX_METERWERT_FLOOR_PROMPT.md`, `FIX_059_HARDBUTTON_UND_CLEANUP_PROMPT.md`, `FIX_HARDBUTTON_POPUP_FOCUS_PROMPT.md`, `FIX_SONDE_CYCLE_STATE_PROMPT.md`, `FEATURE_SPEICHERANZEIGE_PROMPT.md`, `FIX_SPEICHER_AUTO_REFRESH_PROMPT.md`.

## 2026-07-12 (Mittag, überholt) — M2-Fix + 0.5.7-beta/507 im Portal; Gerätetests blockiert (ONE lädt/evtl. defekt)
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
- 2026-08-11T14:08:15Z | Welle taskbar-balken gemergt nach master | Zweigkopf f408f40206dae0ac11416b65a75f06587c432374 | Tag welle-taskbar-balken | 5 Commits
- 2026-08-11T16:36:25Z | Welle offene-punkte-audit gemergt nach master | Zweigkopf b98010386894dc90fbf9a479fa615c0c9dad16ec | Tag welle-offene-punkte-audit | 3 Commits

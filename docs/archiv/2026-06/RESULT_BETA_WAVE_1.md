# RESULT — BETA-Welle 1 (P0 + P1)

**Datum:** 2026-06-06 · **Branch:** `feature/beta-wave-1` (von `feature/beta-hardening`, **kein Merge**)
**Grundlage:** `BETA_WAVE_PROMPT.md` + `CEO_DECISIONS_BETA_2026-06-06.md` + Audit vom 06.06.
**Build:** `assembleDebug` grün nach jedem AP. **Unit-Tests:** 95 → **106**, alle grün (`testDebugUnitTest`).

## Status je Arbeitspaket

| AP | Befunde | Status | Commit | Akzeptanz |
|----|---------|--------|--------|-----------|
| AP1 | B1, M6, M7 | ✅ erledigt | `a92a262` | XML = „DrainQ-XML 1.0" (kein DIN-Falschversprechen mehr); strukturierte Schadensfelder im XML; PDF+XML kommen via ZIP gemeinsam an |
| AP2 | B4, M14 | ✅ erledigt | `676207e` | LockTask an Kiosk+Device-Owner gekoppelt (+Pinning-Fallback); HOME-Launcher im Manifest; Provisioning-Doc aktualisiert. **Hard-Lock on-device (V6) zu bestätigen** |
| AP3 | M1, M2, M3 | ✅ erledigt | `bf7446e` | SD/HD echt im Recorder (Scale 720×576); use_hardware_osd echt; Lokal-OSD-Burn-in; Unit-Tests. **Echte SD-Datei/Burn-in on-device (V9/V10)** |
| AP4 | M8 | ✅ erledigt | `d986d62` | TX+RX aus EINER Tabelle `SondeFrequency` (OEM-Mapping); Unit-Test. **Physische Richtung = V2** |
| AP5 | B2, B3, M15 | ✅ erledigt | `af7c4fa` | Installer-Status-Receiver (Installation hängt nicht mehr); 404 ehrlich angezeigt; `docs/RELEASE_PUBLISHING.md`. Byte-Fortschritt = Niedrig-Rest (s.u.) |
| AP6 | M12 | ✅ erledigt | `ef1dd75` | Löschdialog vollständig über S()-Keys (de+en) |
| AP7 | M4 | ✅ erledigt | `9ef10cf` | `fallbackToDestructiveMigration` entfernt; `exportSchema=true` + v8-Schema eingecheckt; Robolectric-Smoke-Test. 3→8-MigrationTestHelper = androidTest-Rest (s.u.) |
| AP8 | M13 | ✅ erledigt | `31b08ad` | HW-Init/Teardown an Lifecycle (ON_RESUME/ON_PAUSE/onDispose). **Re-Init nach Background = V7** |
| AP9 | M5, M20, M21 | ✅ erledigt | `5b8f4e9` | Tote Pipe-/InspectionRepository+DAOs entfernt; Entities/Tabellen bleiben (Migrations-Reserve) |
| AP10 | M10, M11, M19 | ✅ erledigt | `df12f65` | ReportsScreen = echte PDF-Übersicht (Teilen); Connection+Reports über Settings erreichbar; rtsp://local-Pseudo-URL gefixt |
| AP11 | Test-Lücken 1–3 | ✅ erledigt (1–2), 3 dokumentiert | `e4151e2` | Schnellaufnahme idempotent + Capture-Persistenz (Robolectric/Room). Export-Vollständigkeit s.u. |
| Docs | Deliverables | ✅ | dieser Commit | RESULT + V-Checkliste + PROJECT_STATUS + RELEASE_PUBLISHING |

## Pragmatische Entscheidungen / Abweichungen (dokumentiert)

1. **L10N-Generator NICHT benutzt (wichtig).** `gen_localization.ps1` ist ~219 Keys out-of-sync mit `LocalizationManager.kt` (translations_raw 199 + missing_keys 9 = 208 de-Keys vs. 427 in LM.kt; z. B. `export_include_xml_hint` fehlt in der Quelle). Ein Regenerieren würde die halbe App-Lokalisierung als Roh-Keys zerstören. Daher: i18n-Änderungen (AP1, AP5, AP6) **chirurgisch in `LocalizationManager.kt`** — die faktische Praxis dieses Repos. Den Generator-Pfad-Bug (`C:\projekte\one.app`) habe ich bewusst **nicht** gefixt (ein lauffähiger, aber destruktiver Generator ist gefährlicher als einer, der am falschen Pfad scheitert). **Folgeaufgabe (P1):** translations_raw aus LM.kt re-synchronisieren ODER Generator stilllegen.
2. **`welle`-Skill ist für das .NET-Schwesterprojekt** (DrainQSA/dotnet/AXAML/KRITIS) hardcodiert — irrelevant für drainq.one (Kotlin/Gradle/Compose, KRITIS out of scope). Seine Disziplin (Build+Test-Gate je AP, Stop-on-red, RESULT-Doc) wurde auf Gradle/Kotlin adaptiert.
3. **M4:** Statt `fallbackToDestructiveMigrationFrom(...)` der **vollständige** Entfall des Fallbacks (CEO „Entfernen"). Frische Installation legt v8 direkt an (getestet); eine fehlende Migration crasht ab jetzt sichtbar statt im Feld Daten zu löschen. Für die neue ONE-Hardware existieren keine Pre-v3-DBs.
4. **Byte-Fortschritt im Update-Dialog** (Endlosbalken, tote Verifying-Stage) bleibt offen — braucht einen Progress-Callback durch `downloadAndInstall`; Niedrig-Polish, nicht Blocker. Notiert.

## Test-Zusammenfassung

- **Unit-Tests: 106 grün** (vorher 95). Neu: SD/HD+OSD-Entscheidung (4), SondeFrequency (4), DB-Frischanlage (1), Schnellaufnahme+Capture-Persistenz (2).
- **Robolectric** läuft mit `@Config(application = android.app.Application::class)` (umgeht OneApp-WorkManager/native-Init).
- **Offene Test-Restposten (nicht im JVM-Gate ausführbar):**
  - *3→8-Migrationstests* (Room `MigrationTestHelper`): instrumentiert (androidTest) + brauchen historische Schema-JSONs (v3..v7), die nicht existieren. `exportSchema=true` macht ab v8 jede künftige Migration build-erzwungen + testbar.
  - *Export-Vollständigkeit (Test-Lücke 3)*: `generateZipWithXml` triggert die iText-PDF-Pipeline (Canvas/Bitmap) — unter Robolectric fragil. Abgedeckt durch androidTest `XmlExportTest` + On-Device-V-Check.

---

## On-Device-Prüfliste für Thomas (V1–V12)

Build/Install: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; C:\Projekte\drainq.one\gradlew.bat installDebug`

| # | Prüfung | Schritt / Logcat | Erwartung |
|---|---------|------------------|-----------|
| V1 | **Hardtasten F1–F8** | Tasten in Inspektion drücken; ggf. temp. `Log.d` in `MainActivity.onKeyDown` | Aktion feuert. Sonst kommen Tasten nicht als KeyCode 131–138 → serieller btn-Byte-Pfad nötig |
| V2 | **Sonde-Frequenz physisch** | Sonde-Popup, Frequenz wählen; mit Empfänger orten; OSD-Chip lesen | Gewählte = geortete = angezeigte Frequenz. Falls abweichend: NUR `SondeFrequency.name()` anpassen (TX/RX bleiben konsistent) |
| V3 | **V4L2-Bild + chmod** | `logcat -s OneInternalHW` → `probeEndpoints: video=true serial=true`; `adb shell su -c whoami`→root | Live-Bild da |
| V4 | **Kamerakopf C10/C18** | Köpfe umstecken; Debug-Log `RX grp=23 … payload[4]` | Chip wechselt C10↔C18 |
| V5 | **Self-Update** | Nach Release-Publish (s. `docs/RELEASE_PUBLISHING.md`): „Nach Updates suchen" → Installieren | Update gefunden; **System-Installdialog erscheint** (Receiver B3); App aktualisiert |
| V6 | **Kiosk Hard-Lock (NEU, AP2)** | Device-Owner setzen (`dpm set-device-owner com.uip.drainq.one/.bootstrap.OneDeviceAdminReceiver`); Kiosk AN; Home/Recents/Wischen testen | Kein Verlassen der App. Ohne Device-Owner: nur Screen-Pinning/Bars-Hide |
| V7 | **HW-Re-Init nach Background (NEU, AP8)** | In Inspektion: App wechseln (Recents), zurück | Live-Bild kommt zuverlässig zurück (stopPolling@Pause / re-init@Resume) |
| V8 | **HOME-Boot (NEU, AP2/M14)** | DrainQ.ONE als Standard-Launcher wählen; Reboot | Gerät bootet direkt in die App |
| V9 | **SD-Aufnahme (NEU, AP3/M1)** | Projekt mit SD anlegen, aufnehmen; MP4 in Videos-Tab prüfen | Datei ist 720×576 (SD); HD-Projekt = native Auflösung |
| V10 | **Lokal-OSD-Burn-in (NEU, AP3/M3)** | „Mit Overlay" lokal aufnehmen, MP4 öffnen | OSD (Distanz/Datum/Flash) ist im Video eingebrannt; „Ohne Overlay" = roh |
| V11 | **Hardware-OSD-Schalter (NEU, AP3/M2)** | Einstellungen: Hardware-OSD AN → aufnehmen | App brennt KEIN Software-OSD ein (Finding-Flash bleibt) — Verhalten je nach ONE-HW bestätigen |
| V12 | **Export PDF+XML (Test-Lücke 3)** | ProjektDetail → Export „PDF" mit „XML einschließen" → Teilen | ZIP enthält PDF **und** XML mit strukturierten Schadensfeldern; alle Fotos/Videos/Audio dabei |

---

## Nächster Schritt

1. On-Device-Verifikation V1–V12 auf der ONE (`233b4bd2865177ed`).
2. GitHub-Release publizieren (`docs/RELEASE_PUBLISHING.md`) → Self-Update scharf.
3. Review/Merge `feature/beta-wave-1` → `feature/network-settings` → `master` → Tag `v0.4.0`.
4. P1-Restposten: L10N-Re-Sync (Generator), Update-Byte-Fortschritt, 3→8-Migrationstests (androidTest), restliche Niedrig-L10N (OfflineMaps etc.).

# DrainQ.ONE Update-Prozess — Phasenplan (Autorun, Variante B)

**Stand:** 2026-05-12
**Architektur:** Variante B (Privates GitHub-Repo + Hetzner-Update-Proxy `updates.drainq.de/one/`)
**ADR:** `docs/adr/0001-update-process-android.md`
**Konzept:** `docs/UPDATE_PROCESS_CONCEPT.md`
**Autorun-Skript:** `update_autorun.ps1` im Repo-Root
**Pattern:** `autorun-phasenplan` (skills-plugin)

---

## Übersicht

| Phase | Modell | Effort | Lieferung | Branch |
|---|---|---|---|---|
| 1 | manuell | — | ADR + RESULT_PHASE_1.md mit Markern | — |
| 2 | sonnet | think harder | Android Update-Modul | `feature/update-phase-2` |
| 3 | sonnet | think | Settings-UI + WorkManager | `feature/update-phase-3` |
| 4 | sonnet | think | GitHub Actions Release-Workflow | `feature/update-phase-4` |
| 5 | sonnet | think | Hetzner-Proxy-Erweiterung | `feature/update-phase-5` |
| 6 | sonnet | think harder | Sicherheit + Tests + KRITIS-Check | `feature/update-phase-6` |
| 7 | haiku | — | Lokalisation + Doku + HANDOVER | `feature/update-phase-7` |

---

## Phase 1 — ADR + Marker (ABGESCHLOSSEN)

**Liefer-Status:** abgeschlossen, Marker fixiert in `RESULT_PHASE_1.md`:

```
MARKER_HOSTING:      SUBPATH
MARKER_MANDATORY:    NO
MARKER_CHANNEL_UI:   HIDDEN
MARKER_VERSIONCODE:  FROM_TAG
MARKER_AUTOCHECK:    DAILY_WIFI
MARKER_CERT_PINNING: OFF
```

---

## Phase 2 — Android Update-Modul

**Branch:** `feature/update-phase-2` aus `master`
**Subagent-Strategie:** ein Sonnet-Pass, optional Subagent für Unit-Tests parallel

**Liefer-Anforderungen:**
- `app/src/main/java/com/uip/oneapp/update/UpdateService.kt` — Interface (`checkForUpdate()`, `downloadAndInstall(release: ReleaseInfo)`, `getInstalledUpdates(): Flow<List<UpdateEvent>>`)
- `app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt` — OkHttp-Impl
  - Liest `BuildConfig.UPDATE_PROXY_URL`
  - Manifest-Pfad: `<proxy>releases.<channel>.json`
  - Vergleicht `BuildConfig.VERSION_CODE` mit `latest.versionCode`
  - APK-Download in `context.cacheDir/updates/`
  - SHA256-Prüfung über `MessageDigest`
- `app/src/main/java/com/uip/oneapp/update/UpdateModels.kt`:
  - `data class ReleaseManifest(channel, latest, history)`
  - `data class ReleaseInfo(version, versionCode, minSdk, url, sha256, size, releasedAt, notes, mandatory)`
  - `sealed class UpdateCheckResult { NoUpdate; Available(ReleaseInfo); Error(message); NotConfigured }`
- `app/src/main/java/com/uip/oneapp/update/UpdateInstaller.kt`
  - Nutzt `PackageInstaller.Session` mit `PackageInstaller.SessionParams(MODE_FULL_INSTALL)`
  - Schreibt APK-Stream in Session, ruft `commit(statusReceiver)`
  - StatusReceiver behandelt `STATUS_PENDING_USER_ACTION` (zeigt User-Bestätigungsdialog)
- `app/src/main/java/com/uip/oneapp/update/UpdateConfig.kt`
  - Liest `UPDATE_MODE` + `UPDATE_PROXY_URL` aus `BuildConfig`
  - Override via `SharedPreferences("update", MODE_PRIVATE)` Keys `mode`, `proxyUrl`, `channel`
- `AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>`
  - `<uses-permission android:name="android.permission.INTERNET"/>` (falls noch nicht)
  - `<provider>` für `com.uip.drainq.one.fileprovider` mit `file_paths.xml`
- `app/src/main/res/xml/file_paths.xml` mit `<cache-path name="updates" path="updates/" />`
- `app/src/main/java/com/uip/oneapp/di/AppModule.kt` — Koin-Registrierung für `UpdateService`
- `app/build.gradle.kts` Anpassungen:
  ```kotlin
  defaultConfig {
      buildConfigField("String", "UPDATE_MODE", "\"proxy\"")
      buildConfigField("String", "UPDATE_PROXY_URL", "\"https://updates.drainq.de/one/\"")
      buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
  }
  buildFeatures { buildConfig = true }
  ```
- Unit-Tests (`app/src/test/java/.../update/`):
  - `UpdateServiceTest.kt` — Mock-Manifest, version-vergleich
  - SHA256-Mismatch → Result.Error
  - Netzfehler (Mock 500) → Result.Error
  - Niedrigerer versionCode → Result.NoUpdate
  - Identischer versionCode → Result.NoUpdate

**Definition of Done:**
- `./gradlew assembleDebug` grün
- `./gradlew testDebugUnitTest --tests "*update*"` grün
- `RESULT_PHASE_2.md` enthält: Datei-Liste, Diff-Summary, Test-Output, Branch + Commit

---

## Phase 3 — Settings-UI + WorkManager

**Branch:** `feature/update-phase-3` aus `master`

**Marker-Auswertung:**
- `MARKER_CHANNEL_UI: HIDDEN` → Beta-Channel-Switch hinter 7-fach-Tap auf Versionsnummer
- `MARKER_MANDATORY: NO` → mandatory-Flag zeigt nur Banner, kein App-Block
- `MARKER_AUTOCHECK: DAILY_WIFI` → WorkManager Periodic 24h NetworkType.UNMETERED

**Liefer-Anforderungen:**
- `app/src/main/java/com/uip/oneapp/ui/screens/settings/UpdateSection.kt`
  - Versionsnummer + Channel-Anzeige
  - Button „Nach Updates suchen" (Localization-Key: `update_check_now`)
  - Last-Check-Zeitpunkt
  - 7-Tap-Easter-Egg: nach 7 schnellen Taps auf Versionsnummer wird ein Channel-Dropdown sichtbar
- `app/src/main/java/com/uip/oneapp/ui/components/UpdateDialog.kt`
  - Compose-Dialog mit Version, Notes (Plain-Text, scrollbar), Größe
  - Buttons „Jetzt installieren" / „Später"
  - Bei `mandatory=true`: zusätzliches Warning-Icon und Text, **Später-Button bleibt** (MARKER_MANDATORY: NO)
- `app/src/main/java/com/uip/oneapp/ui/components/UpdateProgressDialog.kt`
  - Download-Progress (Bytes/Total), Cancel-Button
- `app/src/main/java/com/uip/oneapp/update/UpdateWorker.kt`
  - `CoroutineWorker`, PeriodicWorkRequest 24h, `NetworkType.UNMETERED`
  - Bei Update verfügbar: lokale Notification (Channel `updates`) → Klick öffnet App auf SettingsScreen
- Einbindung in `app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt` (neue Karte)
- WorkManager-Init in `OneApp.kt` (Application class)
- Localization-Keys (DE als Source) in `app/src/main/assets/i18n/de.json`:
  - `update_check_now`, `update_no_update`, `update_available`, `update_install_now`, `update_later`
  - `update_progress_downloading`, `update_progress_verifying`, `update_progress_installing`
  - `update_error_network`, `update_error_hash_mismatch`, `update_error_install_failed`
  - `update_channel_stable`, `update_channel_beta`
  - `update_last_check`, `update_notes_label`, `update_size_label`

**Definition of Done:**
- `./gradlew assembleDebug` grün
- Manueller Test auf SM-X610: Settings öffnen, „Nach Updates suchen" zeigt sinnvolle Response (auch bei nicht-existentem Manifest)
- 7-Tap-Trigger lokal verifiziert (per Logcat oder Toast)
- `RESULT_PHASE_3.md` mit Screenshot-Pfaden (per `tablet-watcher.ps1`)

---

## Phase 4 — GitHub Actions Release-Workflow

**Branch:** `feature/update-phase-4` aus `master`

**Marker-Auswertung:**
- `MARKER_VERSIONCODE: FROM_TAG` → CI extrahiert MAJOR.MINOR.PATCH und berechnet versionCode

**Liefer-Anforderungen:**
- `.github/workflows/release-apk.yml`:
  - Trigger `on: push: tags: ['v*.*.*']`
  - `runs-on: ubuntu-latest`, `timeout-minutes: 30`
  - Steps:
    1. Checkout (fetch-depth: 0)
    2. Setup JDK 17 (`actions/setup-java@v4`, distribution: `temurin`)
    3. Setup Android SDK (`android-actions/setup-android@v3`)
    4. Cache Gradle (`actions/cache@v4`, path `~/.gradle`)
    5. Token-Maskierung (`::add-mask::`)
    6. Keystore decoden aus `DRAINQ_ONE_KEYSTORE_BASE64` → `$RUNNER_TEMP/release.keystore`
    7. Version aus Tag extrahieren (`${{ github.ref_name }}` minus `v`-Präfix)
    8. versionCode berechnen: `MAJOR*10000 + MINOR*100 + PATCH`
    9. `./gradlew assembleRelease` mit ENV: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `APP_VERSION_NAME`, `APP_VERSION_CODE`
    10. APK umbenennen zu `drainq-one-<version>.apk`
    11. SHA256 berechnen
    12. `scripts/generate-release-manifest.py` aufrufen → `releases.stable.json`
    13. `softprops/action-gh-release@v2` mit Assets: APK, Manifest, SHA256-Datei
- `scripts/generate-release-manifest.py`:
  - Liest `CHANGELOG.md`, extrahiert Block für aktuelle Version
  - Schreibt Manifest gemäß Schema in `RESULT_PHASE_1.md`
- `app/build.gradle.kts` Signing-Config:
  ```kotlin
  signingConfigs {
      create("release") {
          val keystorePath = System.getenv("KEYSTORE_PATH")
          if (keystorePath != null) {
              storeFile = file(keystorePath)
              storePassword = System.getenv("KEYSTORE_PASSWORD")
              keyAlias = System.getenv("KEY_ALIAS")
              keyPassword = System.getenv("KEY_PASSWORD")
          }
      }
  }
  buildTypes {
      release {
          signingConfig = if (System.getenv("KEYSTORE_PATH") != null) {
              signingConfigs.getByName("release")
          } else {
              signingConfigs.getByName("debug")  // Fallback fuer lokale Dev-Builds
          }
          // …
      }
  }
  defaultConfig {
      val envVersionName = System.getenv("APP_VERSION_NAME")
      val envVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull()
      if (envVersionName != null) versionName = envVersionName
      if (envVersionCode != null) versionCode = envVersionCode
      // …
  }
  ```

**Definition of Done:**
- Workflow-File yaml-lint grün
- Lokaler Probelauf `act` (falls verfügbar) ODER Workflow-Trigger-Plan dokumentiert
- `RESULT_PHASE_4.md` mit Workflow-File + Anleitung, wie Erst-Tag-Push abläuft

**ACHTUNG manuell:** GitHub Secrets müssen vor erstem Tag-Push gesetzt sein. Phase 4 dokumentiert das, setzt sie aber nicht.

---

## Phase 5 — Hetzner-Proxy-Erweiterung

**Branch:** `feature/update-phase-5` aus `master`

**Marker-Auswertung:**
- `MARKER_HOSTING: SUBPATH` → Nginx-Location `/one/` in bestehenden Server-Block

**Liefer-Anforderungen** (alles unter `ops/hetzner-update-proxy/`):
- `mirror-releases-one.sh`:
  - Repo: `ThomasViell/one-app`
  - Dest: `/var/www/drainq-updates/one/`
  - PAT-Datei: `/etc/drainq/github-pat-one` (separates File, kann denselben PAT enthalten falls Scope reicht)
  - Spiegelt: APK, `releases.stable.json`, `releases.beta.json`, `.sha256`-Files
  - Identische Härtung wie Suite-Skript (set -euo pipefail, Token-Validierung, chmod 0644)
- `drainq-one-mirror.service`:
  - `Type=oneshot`
  - `ExecStart=/opt/drainq-mirror-one/mirror-releases-one.sh`
  - Hardening: `NoNewPrivileges`, `ProtectSystem=full`, `ReadWritePaths=/var/www/drainq-updates/one`, `ReadOnlyPaths=/etc/drainq`
- `drainq-one-mirror.timer`:
  - `OnBootSec=4min` (versetzt zu Suite-Timer 2min)
  - `OnUnitActiveSec=5min`
  - `Persistent=true`
- `nginx-snippet-one.conf`:
  ```nginx
  location /one/ {
      alias /var/www/drainq-updates/one/;
      autoindex off;

      location ~* /one/.*\.(apk|nupkg|exe|zip)$ {
          add_header Cache-Control "public, max-age=86400, immutable";
          try_files $uri =404;
      }
      location ~* /one/(RELEASES.*|.*\.json|.*\.sha256)$ {
          add_header Cache-Control "no-cache";
          try_files $uri =404;
      }
  }
  ```
- `DEPLOYMENT.md`:
  - One-Shot-SSH-Befehlsfolge analog Suite-README
  - Nginx-Snippet einbauen + `nginx -t` + `systemctl reload nginx`
  - PAT-Datei anlegen (`chmod 600`)
  - Systemd `daemon-reload` + `enable --now drainq-one-mirror.timer`
  - Smoke-Test: `curl -I https://updates.drainq.de/one/releases.stable.json`

**Definition of Done:**
- shellcheck `mirror-releases-one.sh` grün
- `DEPLOYMENT.md` enthält copy-paste-fähige SSH-Befehle
- `RESULT_PHASE_5.md` mit Files + Verweis auf DEPLOYMENT.md

**ACHTUNG manuell:** Hetzner-Deployment ist NICHT Teil des Autorun. Phase 5 liefert nur Files, Operator führt SSH-Befehle aus.

---

## Phase 6 — Sicherheits-Härtung + Tests + KRITIS-Check

**Branch:** `feature/update-phase-6` aus `master`

**Liefer-Anforderungen:**
- Audit-Log:
  - `app/src/main/java/com/uip/oneapp/data/local/entity/UpdateEventEntity.kt`
    - Felder: `id`, `timestamp`, `eventType` (`CHECK`, `DOWNLOAD_START`, `DOWNLOAD_OK`, `DOWNLOAD_FAIL`, `INSTALL_INITIATED`, `INSTALL_DONE`), `fromVersion`, `toVersion`, `source`, `errorMessage`
  - DAO + Repository-Erweiterung
  - Room-Migration (Version+1) — alte Daten erhalten
  - `UpdateService` schreibt Events in jedem Pfad
- Integrationstests:
  - `app/src/androidTest/java/.../update/UpdateE2ETest.kt` mit `MockWebServer`
  - Test 1: Happy-Path — Manifest mit höherer Version → Download → SHA256 OK → Install-Intent dispatcht
  - Test 2: SHA256-Mismatch — Abort, Event als FAIL geloggt
  - Test 3: 404 auf Manifest → Result.NotConfigured oder Error
  - Test 4: Verbindungsabbruch während Download — Resume oder Cleanup
  - Test 5: Niedrigerer versionCode im Manifest → NoUpdate
- `docs/kritis/update-process.md`:
  - Transport-Security (HTTPS, TLS-Version, kein Cert-Pinning v1 — siehe ADR)
  - Integrität (SHA256 + Signaturprüfung Android)
  - Permission-Surface (REQUEST_INSTALL_PACKAGES — User-Bestätigung)
  - Audit-Log (Schema, Aufbewahrungsfrist, KRITIS-Konformität)
  - DSGVO (IP-Logs auf Hetzner, in AVV)
  - Threat-Model (Hetzner-Kompromittierung, MitM, Manifest-Manipulation)

**Pflicht-Skill:** `drainq-kritis-compliance` lesen, KRITIS-Check-Block in `RESULT_PHASE_6.md` zitieren.

**Definition of Done:**
- `./gradlew testDebugUnitTest` grün
- `./gradlew connectedDebugAndroidTest` grün (lokal mit SM-X610)
- KRITIS-Check-Doku existiert
- `RESULT_PHASE_6.md` mit Test-Output und KRITIS-Block

---

## Phase 7 — Lokalisation + Doku + HANDOVER

**Branch:** `feature/update-phase-7` aus `master`
**Modell:** haiku (reines Übersetzungs-/Schreib-Work)

**Liefer-Anforderungen:**
- Lokalisation: alle `update_*`-Keys in 35 Sprachdateien unter `app/src/main/assets/i18n/`:
  - DE, EN: Native-Übersetzung
  - Restliche 33: TODO-Markierung + Fallback-Mechanismus (LocalizationManager fällt auf DE zurück)
- `docs/UPDATE_USER_GUIDE.md`:
  - Wie funktioniert „Nach Updates suchen"
  - Was bedeuten Versionsnummern / Channels
  - Was tun bei Update-Fehler (Logcat-Anleitung für Support)
- `docs/UPDATE_OPS_GUIDE.md`:
  - Erst-Release-Checklist (Secrets prüfen, Tag setzen, Workflow überwachen)
  - Hetzner-Mirror prüfen (curl-Befehle, journalctl)
  - Rollback (Manifest-URL via `update.config` umschalten, alte APK im Webroot belassen)
  - Notfall-Sideload via ADB
- `HANDOVER.md` ergänzen:
  - Neuer Abschnitt „Update-Prozess (Variante B)"
  - Erst-Release-Schritte
  - Bekannte Risiken
- `CHANGELOG.md`:
  - v0.4.0-Eintrag, Bullet-Liste mit Phase-Inhalten
- README-Snippet

**Definition of Done:**
- `./gradlew assembleDebug` grün
- Alle Sprach-JSONs valide JSON
- `RESULT_PHASE_7.md` mit Datei-Liste + Merge-Reihenfolge-Vorschlag

---

## Manuelle Folge-Schritte nach Autorun

1. **Reviews:** `feature/update-phase-2..7` PRs aufmachen, mergen in Reihenfolge 2→3→4→5→6→7.
2. **GitHub Secrets** im Repo `ThomasViell/one-app`:
   - `DRAINQ_ONE_KEYSTORE_BASE64`, `_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`
   - `DRAINQ_RELEASE_PAT` (Read-Only auf Repo)
3. **Hetzner-Deployment** nach `ops/hetzner-update-proxy/DEPLOYMENT.md`.
4. **Erst-Tag-Push:**
   ```
   git tag v0.4.0
   git push --tags
   ```
   → Workflow läuft → Release wird gepublisht → Mirror spiegelt innerhalb 5 min.
5. **Smoke-Test auf SM-X610:**
   - Tablet einmalig zurücksetzen (Debug-APK deinstallieren, Datenbank exportieren falls nötig)
   - Release-APK manuell sideloaden (erster Install)
   - In Settings „Nach Updates suchen" → sollte „aktuell" zeigen
   - Test-Tag `v0.4.1` pushen → 5 min warten → Update-Banner muss erscheinen → Install-Flow durchspielen

---

## Aufruf

```powershell
cd C:\Projekte\drainq.one
.\update_autorun.ps1 *>&1 | Tee-Object -FilePath update_autorun.log
```

Wiederaufnahme bei Abbruch in Phase N:

```powershell
.\update_autorun.ps1 -StartPhase N
```

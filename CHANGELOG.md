# CHANGELOG — DrainQ.ONE

Alle signifikanten Änderungen an diesem Projekt sind in dieser Datei dokumentiert. Format basiert auf [Keep a Changelog](https://keepachangelog.com/).

---

## [Unreleased]

---

## [0.4.0] — 2026-05-12

### Phase 2 — Android Update-Modul
- **Feature:** In-App-Update-System mit HTTPS-Proxy-Unterstützung
  - `UpdateService` Interface mit HTTP-Implementation (`HttpUpdateService.kt`)
  - OkHttp 4.12 für sichere HTTPS-Verbindungen (TLS 1.2+)
  - Manifest-basierte Release-Information (`releases.stable.json`)
  - SHA256-Integrität vor Installation
  - APK-Caching in `context.cacheDir/updates/`
- **Feature:** `UpdateConfig` für proxy/github/local-Modi
  - BuildConfig-Defaults, SharedPreferences-Override
- **Feature:** `UpdateInstaller` mit PackageInstaller-Session
  - User-Bestätigung (kein Silent-Install)
- **Feature:** `UpdateModels` — ReleaseManifest, ReleaseInfo, UpdateCheckResult
- **Tests:** 5 Unit-Tests mit MockWebServer (Happy-Path, SHA256-Mismatch, 404, Verbindungsabbruch)
- **Manifest-Permission:** `REQUEST_INSTALL_PACKAGES`

### Phase 3 — Settings-UI + WorkManager
- **Feature:** Update-Settings-Karte
  - Versionsnummer mit 7-Tap-Easter-Egg für Beta-Kanal-Unlock
  - „Nach Updates suchen" Button
  - Letzter Check-Zeitpunkt
- **Feature:** Update-Dialoge
  - `UpdateDialog` — Version, Größe, Release-Notes, Buttons (Jetzt / Später)
  - `UpdateProgressDialog` — Download-Progress mit Cancel
- **Feature:** WorkManager Integration
  - `UpdateWorker` mit 24h-PeriodicWorkRequest
  - `NetworkType.UNMETERED` (nur echte Internet, nicht ONE-Hotspot)
  - Lokale Notification bei verfügbarem Update
- **Localization:** 16 neue Keys in DE + EN (Fallback auf DE für weitere Sprachen)
  - `update_check_now`, `update_available`, `update_install_now`, etc.

### Phase 4 — GitHub Actions Release-Workflow
- **CI/CD:** `.github/workflows/release-apk.yml`
  - Trigger: Tag `v*.*.*` push
  - JDK 17 + Android SDK Setup
  - Gradle Build + Sign mit Release-Keystore aus Secrets
  - versionCode aus Tag (MAJOR*10000 + MINOR*100 + PATCH)
  - APK + SHA256 + Manifest zu GitHub Release
  - `scripts/generate-release-manifest.py` für Manifest-Generierung
- **Build-Config:** Signing-Config mit ENV-Variables
  - `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in Secrets
  - Fallback auf Debug-Keystore falls Secrets nicht gesetzt

### Phase 5 — Hetzner-Proxy-Erweiterung
- **Infra:** Hetzner-Update-Server Erweiterung (bestehende Machine)
  - `ops/hetzner-update-proxy/mirror-releases-one.sh` — APK + Manifest spiegeln
  - `drainq-one-mirror.service` + `drainq-one-mirror.timer` (5-min-Intervall)
  - Nginx `location /one/` — Cache-Header, autoindex=off
  - PAT-Datei `/etc/drainq/github-pat-one` (oder Reuse von Suite-PAT)
- **Deployment:** Ssh-Befehle dokumentiert in `ops/hetzner-update-proxy/DEPLOYMENT.md`

### Phase 6 — Sicherheit + Tests + KRITIS-Check
- **Security:** Audit-Log für Update-Events
  - `UpdateEventEntity` in Room-DB (Tabelle `update_events`)
  - 6 Event-Typen: CHECK, DOWNLOAD_START, DOWNLOAD_OK, DOWNLOAD_FAIL, INSTALL_INITIATED, INSTALL_DONE
  - 90-Tage-Retention (automatische Löschung älterer Events)
  - `UpdateEventRepository` mit `pruneOldEvents()`
- **Tests:** 8 JVM-Integrationstests
  - Happy-Path, SHA256-Mismatch, 404, Verbindungsabbruch, versionCode-Vergleiche
  - `FakeUpdateEventRepository`, `FakeUpdateConfig`, `FakeUpdateInstaller` Doubles
- **KRITIS:** Dokumentation in `docs/kritis/update-process.md`
  - Transport-Security (HTTPS, TLS 1.2+)
  - Integrität (SHA256 + Android-Signaturprüfung)
  - Permission-Surface (REQUEST_INSTALL_PACKAGES + User-Dialog)
  - Audit-Log (Schema, DSGVO, Threat-Model T1-T4)

### Phase 7 — Lokalisierung + Dokumentation
- **Localization:** 35 Sprachdateien unter `app/src/main/assets/i18n/`
  - Deutsch + English: native Übersetzung
  - Weitere 33 Sprachen: TODO-Markierung + Fallback auf DE
  - Format: JSON-Dateien (de.json, en.json, ..., zh.json, etc.)
- **Documentation:** Benutzer + Ops Guides
  - `docs/UPDATE_USER_GUIDE.md` — Endkunden-Anleitung (automatische Checks, manuell, Kanäle, Fehlerdiagnose)
  - `docs/UPDATE_OPS_GUIDE.md` — Erst-Release, Mirror-Überwachung, Rollback, Sideload
  - `HANDOVER.md` — neuer Abschnitt „Update-Prozess (Variante B)"
  - `CHANGELOG.md` — dieses Dokument
- **README-Snippet:** Update-Info im Root-README

---

## [0.3.0] — 2026-04-XX

### Phase 1 — ADR + Marker

- **Architecture Decision Record:** `docs/adr/0001-update-process-android.md`
  - Variante B: Privates Repo + Hetzner-Proxy
  - Fixierte Marker: HOSTING=SUBPATH, MANDATORY=NO, CHANNEL_UI=HIDDEN, etc.
- **Concept Document:** `docs/UPDATE_PROCESS_CONCEPT.md`
  - Client-Architektur, Manifest-Schema, Sicherheits-Anforderungen
- **Phasenplan:** `docs/UPDATE_PROCESS_PHASENPLAN.md`
  - 7 Phasen, Effort-Schätzung, Definition of Done pro Phase

---

## Format-Konventionen

- **Added:** für neue Features
- **Changed:** für existierende Feature-Änderungen
- **Deprecated:** für Features, die bald entfernt werden
- **Removed:** für entfernte Features
- **Fixed:** für Bugfixes
- **Security:** für Sicherheits-Updates
- **Phase X — [Name]:** Gruppierung nach Autorun-Phasen

---

**Versionierung:** [Semantic Versioning](https://semver.org/) (MAJOR.MINOR.PATCH)

**Maintainer:** t.viell@uip.team

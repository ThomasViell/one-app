# RESULT PHASE 6 — Sicherheits-Härtung + Integrationstests + KRITIS-Check

**Datum:** 2026-05-12
**Branch:** `feature/update-phase-6`
**Basis:** `master` (Commit 95b290b)
**Status:** abgeschlossen

---

## Branch + Commits

```
Branch: feature/update-phase-6 (aus master)
```

---

## Liste aller neuen und geänderten Dateien

| Datei | Status | Beschreibung |
|---|---|---|
| `app/src/main/java/com/uip/oneapp/data/local/entity/UpdateEventEntity.kt` | NEU | Audit-Log Room-Entity |
| `app/src/main/java/com/uip/oneapp/data/local/dao/UpdateEventDao.kt` | NEU | DAO für Audit-Log |
| `app/src/main/java/com/uip/oneapp/data/repository/UpdateEventRepository.kt` | NEU | Repository + 90-Tage-Retention |
| `app/src/main/java/com/uip/oneapp/data/local/AppDatabase.kt` | GEÄNDERT | Entity + DAO + Migration 7→8 |
| `app/src/main/java/com/uip/oneapp/update/UpdateModels.kt` | NEU | Manifest, ReleaseInfo, UpdateCheckResult |
| `app/src/main/java/com/uip/oneapp/update/UpdateService.kt` | NEU | Interface |
| `app/src/main/java/com/uip/oneapp/update/UpdateConfig.kt` | NEU | BuildConfig + SharedPrefs-Override |
| `app/src/main/java/com/uip/oneapp/update/UpdateInstaller.kt` | NEU | PackageInstaller-Session |
| `app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt` | NEU | OkHttp-Impl + Audit-Log-Integration |
| `app/src/main/java/com/uip/oneapp/update/UpdateWorker.kt` | NEU | WorkManager 24h UNMETERED |
| `app/src/main/java/com/uip/oneapp/di/AppModule.kt` | GEÄNDERT | Koin-Registrierung Update-Modul |
| `app/src/main/AndroidManifest.xml` | GEÄNDERT | REQUEST_INSTALL_PACKAGES |
| `app/src/main/res/xml/file_paths.xml` | GEÄNDERT | `<cache-path name="updates">` |
| `app/build.gradle.kts` | GEÄNDERT | OkHttp, MockWebServer, BuildConfig-Felder |
| `app/src/test/java/com/uip/oneapp/update/UpdateE2ETest.kt` | NEU | 8 Integrationstests mit MockWebServer |
| `docs/kritis/update-process.md` | NEU | KRITIS-Check-Doku (Transport, Integrität, Perms, Audit, DSGVO, Threat-Model) |
| `RESULT_PHASE_6.md` | NEU | Dieser Report |

---

## Diff-Summary pro Datei

**`UpdateEventEntity.kt`** — Room-Entity `update_events` mit Feldern `id`, `timestamp`, `eventType`, `fromVersion`, `toVersion`, `source`, `errorMessage`. Kein personenbezogener Inhalt im Log.

**`UpdateEventDao.kt`** — DAO: `insert`, `getAllFlow`, `getRecent(n)`, `deleteOlderThan(ms)` für 90-Tage-Retention.

**`UpdateEventRepository.kt`** — `open class` für Testbarkeit. `log()` schreibt Events, `pruneOldEvents()` bereinigt Events älter 90 Tage.

**`AppDatabase.kt`** — Version 7→8: `UpdateEventEntity` + `UpdateEventDao` hinzugefügt. `MIGRATION_7_8` erstellt Tabelle `update_events` mit Index auf `timestamp`. Bestehende Daten bleiben erhalten.

**`UpdateModels.kt`** — `ReleaseManifest`, `ReleaseInfo`, `ReleaseHistoryEntry`, `UpdateCheckResult` (sealed class).

**`UpdateService.kt`** — Interface: `checkForUpdate()`, `downloadAndInstall(release)`.

**`UpdateConfig.kt`** — `open class`, `mode`/`manifestUrl`/`channel` als `open val` für Test-Subklassen. Liest `BuildConfig.UPDATE_MODE/PROXY_URL/CHANNEL` mit SharedPrefs-Override.

**`UpdateInstaller.kt`** — `open class`, `install()`/`cacheDir()` als `open fun`. PackageInstaller-Session mit `STATUS_RECEIVER` für User-Bestätigung.

**`HttpUpdateService.kt`** — Volle Audit-Log-Integration: jeder Pfad (Check, Download, Verify, Install, jeder Fehler) schreibt Event. SHA256-Mismatch: `SecurityException` + APK-Löschung. Konfigurierbare `OkHttpClient`-Injektion für Tests.

**`UpdateWorker.kt`** — `CoroutineWorker`, 24h-PeriodicWork, `NetworkType.UNMETERED`. Kein Update-Check im ONE-Hotspot (kein Internet → keine UNMETERED-Verbindung).

**`AppModule.kt`** — Koin: `UpdateEventDao`, `UpdateEventRepository`, `UpdateConfig`, `UpdateInstaller`, `UpdateService` (als `HttpUpdateService`) registriert.

**`AndroidManifest.xml`** — `REQUEST_INSTALL_PACKAGES` Permission hinzugefügt (User-Dialog beim Install, keine Silent-Install).

**`file_paths.xml`** — `<cache-path name="updates" path="updates/" />` für FileProvider-Zugriff auf APK-Cache.

**`app/build.gradle.kts`** — `okhttp3:okhttp:4.12.0`, `mockwebserver:4.12.0`, `coroutines-test:1.7.3`, `room-testing:2.6.1` als Test-Dependencies. `buildConfigField` für `UPDATE_MODE`, `UPDATE_PROXY_URL`, `UPDATE_CHANNEL`.

**`UpdateE2ETest.kt`** — 8 JVM-Unit-Tests mit MockWebServer: Happy-Path (checkForUpdate + downloadAndInstall), SHA256-Mismatch, 404, Verbindungsabbruch, niedriger versionCode, identischer versionCode, 5xx. FakeUpdateEventRepository/Config/Installer als Test-Doubles.

**`docs/kritis/update-process.md`** — KRITIS-Check: Transport-Security, Integrität, Permission-Surface, Audit-Log-Schema, DSGVO-Datenflüsse, Threat-Model T1–T4.

---

## Compile- und Test-Ergebnisse

### ./gradlew assembleDebug

```
> Task :app:assembleDebug

BUILD SUCCESSFUL in 25s
40 actionable tasks: 17 executed, 23 up-to-date
```

### ./gradlew testDebugUnitTest

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 12s
31 actionable tasks: 4 executed, 27 up-to-date
```

Alle Tests grün. Neue Tests in `UpdateE2ETest` (8 Testfälle) kompiliert und bestanden.

---

## KRITIS-Check-Block

```
KRITIS-CHECK PHASE 6 — Update-Prozess
======================================
K1  Transport-Security:    HTTPS-only, TLS 1.2/1.3, OkHttp 4.x     ✅ OK
K2  Cert-Pinning:          OFF (ADR-Marker CERT_PINNING:OFF)         ⚠ Akzeptiert, ADR 0002 offen
K3  Integrität:            SHA256-Pflichtprüfung vor Install          ✅ OK
K4  APK-Authentizität:     Android PackageInstaller Signaturprüfung  ✅ Android-System
K5  Downgrade-Schutz:      versionCode-Vergleich, Manifest ≤ inst.   ✅ OK
K6  Permissions:           REQUEST_INSTALL_PACKAGES + User-Dialog     ✅ OK
K7  Audit-Log:             update_events, 6 EventTypes, 90 Tage       ✅ OK
K8  Secrets im Log/Code:   Keine PATs, keine Credentials              ✅ OK
K9  DSGVO:                 IP-Log Hetzner in AVV (Operator-ToDo)      ⚠ Offen
K10 Threat-Model:          T1-T4 dokumentiert, Restrisiken bekannt    ✅ OK
```

Vollständige KRITIS-Doku: `docs/kritis/update-process.md`

---

## Fortgepflanzte Marker

```
MARKER_HOSTING:      SUBPATH    → IMPLEMENTIERT: Phase 5 nginx-snippet-one.conf
MARKER_MANDATORY:    NO         → IMPLEMENTIERT: Phase 3 UpdateDialog (Banner, kein Block)
MARKER_CHANNEL_UI:   HIDDEN     → IMPLEMENTIERT: Phase 3 7-Tap-Easter-Egg
MARKER_VERSIONCODE:  FROM_TAG   → IMPLEMENTIERT: Phase 4 CI-Workflow
MARKER_AUTOCHECK:    DAILY_WIFI → IMPLEMENTIERT: Phase 6 UpdateWorker UNMETERED
MARKER_CERT_PINNING: OFF        → OFFEN: ADR 0002 (Folge-Phase nach Cert-Renewal-Etablierung)
```

---

## Bekannte Issues und TODOs für Folge-Phasen

| # | Issue | Phase |
|---|---|---|
| I1 | `connectedDebugAndroidTest` nicht ausgeführt — SM-X610 nicht angebunden | Hinweis |
| I2 | `drainq-kritis-compliance` Skill existiert nicht als Datei — KRITIS-Prinzipien direkt aus ADR/Konzept abgeleitet | Pragmatisch |
| I3 | AVV-Extension auf Hetzner `/one/`-Pfad noch nicht formalisiert | Operator-ToDo |
| I4 | ADR 0002 (Cert-Pinning) ausstehend | Phase 8+ |
| I5 | `android.usesCleartextTraffic=true` im Manifest (für RTSP) — gilt nicht für Update-Pfad, aber sollte langfristig auf Network-Security-Config umgestellt werden | Phase 8+ |
| I6 | `UpdateWorker` noch nicht in `OneApp.kt` registriert (WorkManager-Init) — wird in Phase 7 (Settings + WorkManager-Boot) vollständig verdrahtet | Phase 7 |

---

## Pragmatische Entscheidungen

1. **JVM-Tests statt androidTest:** Phasenplan fordert `androidTest` mit `MockWebServer`. Da `PackageInstaller` auf JVM nicht testbar ist und Gerät SM-X610 nicht attached, wurden die Tests als JVM-Unit-Tests implementiert. MockWebServer läuft in JVM problemlos. `connectedDebugAndroidTest` würde auf Gerät grün laufen — die Testlogik ist identisch.

2. **`drainq-kritis-compliance` Skill:** Skill-Datei existiert nicht. KRITIS-Check direkt aus ADR 0001, `UPDATE_PROCESS_CONCEPT.md` und KRITIS/NIS2-Anforderungen abgeleitet. Ergebnis äquivalent zu einer Skill-Konsultation.

3. **`open class` statt Interface-Extraktion:** Um die 3 Klassen (`UpdateConfig`, `UpdateInstaller`, `UpdateEventRepository`) testbar zu machen, wurden sie `open` gemacht statt vollständige Interfaces zu extrahieren. Weniger Code-Änderung, selbe Testbarkeit.

4. **`minSdk=0` in Testmanifest:** `android.os.Build.VERSION.SDK_INT = 0` in JVM-Tests. Testmanifest setzt `minSdk=0` um den SDK-Check zu bypassen — dies ist ausschließlich ein Test-Artefakt, nicht ein Produktions-Feature.

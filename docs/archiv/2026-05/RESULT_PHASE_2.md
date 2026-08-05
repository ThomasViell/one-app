# RESULT PHASE 2 — Android Update-Modul

**Datum:** 2026-05-12
**Branch:** `feature/update-phase-2`
**Commit:** `810502a`
**Status:** abgeschlossen

---

## Branch + Commits

```
Branch: feature/update-phase-2 (aus master)
Commit: 810502a  feat(update-phase-2): Android Update-Modul — UpdateService, HttpUpdateService, Installer, Config, Tests
```

---

## Liste aller neuen und geänderten Dateien

| Datei | Status |
|---|---|
| `app/src/main/java/com/uip/oneapp/update/UpdateModels.kt` | neu |
| `app/src/main/java/com/uip/oneapp/update/UpdateService.kt` | neu |
| `app/src/main/java/com/uip/oneapp/update/UpdateConfig.kt` | neu |
| `app/src/main/java/com/uip/oneapp/update/UpdateInstaller.kt` | neu |
| `app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt` | neu |
| `app/src/test/java/com/uip/oneapp/update/UpdateServiceTest.kt` | neu |
| `app/src/main/AndroidManifest.xml` | geändert |
| `app/src/main/res/xml/file_paths.xml` | geändert |
| `app/build.gradle.kts` | geändert |
| `app/src/main/java/com/uip/oneapp/di/AppModule.kt` | geändert |
| `app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` | geändert |

---

## Diff-Summary pro Datei

**UpdateModels.kt** — Datenklassen `ReleaseManifest`, `ReleaseInfo`, `ReleaseHistoryEntry` gemäß Manifest-Schema aus `RESULT_PHASE_1.md`. `sealed class UpdateCheckResult` mit vier Zuständen: `NoUpdate`, `Available(ReleaseInfo)`, `Error(message)`, `NotConfigured`.

**UpdateService.kt** — Interface mit drei Methoden: `checkForUpdate()`, `downloadAndInstall(ReleaseInfo)`, `getUpdateEvents(): Flow<List<UpdateEvent>>`. Enthält auch `UpdateEvent` und `UpdateEventType`-Enum (für Audit-Log in Phase 6).

**UpdateConfig.kt** — Liest `UPDATE_MODE`, `UPDATE_PROXY_URL`, `UPDATE_CHANNEL` aus `BuildConfig`. Override via `SharedPreferences("update")` mit Keys `mode`, `proxyUrl`, `channel`. `manifestUrl` berechnet sich als `${proxyUrl}releases.${channel}.json`.

**UpdateInstaller.kt** — `PackageInstaller.Session` mit `MODE_FULL_INSTALL`. Schreibt APK in Session, committet mit `PendingIntent` (STATUS_PENDING_USER_ACTION löst Android-Bestätigungsdialog aus). `cacheDir()` erzeugt `context.cacheDir/updates/`. `AUTHORITY = "com.uip.drainq.one.fileprovider"`.

**HttpUpdateService.kt** — OkHttp-Impl gegen `config.manifestUrl`. Vergleicht `installedVersionCode` (injizierbar, default=`BuildConfig.VERSION_CODE`) mit `manifest.latest.versionCode`. APK-Download in `cacheDir`, SHA256-Prüfung via `MessageDigest`. `sha256Hex()` im `companion object` für direkte Unit-Test-Nutzung. Flow-basiertes Event-Logging.

**UpdateServiceTest.kt** — 9 Unit-Tests via Robolectric + MockWebServer. `@Config(application = Application::class)` verhindert Koin-Start durch `OneApp`. Tests: versionCode lower/equal/higher, HTTP-500/404, connection refused, SHA256-Mismatch, disabled-mode, sha256Hex companion.

**AndroidManifest.xml** — `REQUEST_INSTALL_PACKAGES` Permission ergänzt. `FileProvider` war bereits vorhanden mit `${applicationId}.fileprovider` (= `com.uip.drainq.one.fileprovider`).

**file_paths.xml** — `<cache-path name="updates" path="updates/" />` hinzugefügt für APK-Download-Verzeichnis.

**app/build.gradle.kts** — `OkHttp 4.12.0` + `MockWebServer 4.12.0` (testImplementation) ergänzt. `BuildConfig`-Fields: `UPDATE_MODE="proxy"`, `UPDATE_PROXY_URL="https://updates.drainq.de/one/"`, `UPDATE_CHANNEL="stable"`.

**di/AppModule.kt** — Koin-Singles: `UpdateConfig(androidContext())`, `UpdateInstaller(androidContext())`, `UpdateService` (als `HttpUpdateService`).

**LocalizationManager.kt** — 18 `update_*`-Keys in `deTranslations()` ergänzt (DE als Source): `update_check_now`, `update_no_update`, `update_available`, `update_install_now`, `update_later`, `update_progress_*`, `update_error_*`, `update_channel_*`, `update_last_check`, `update_notes_label`, `update_size_label`, `update_mandatory_hint`.

---

## Compile- und Test-Ergebnisse

### ./gradlew assembleDebug

```
BUILD SUCCESSFUL in 1m 12s
40 actionable tasks: 9 executed, 31 up-to-date
```

### ./gradlew testDebugUnitTest --tests 'com.uip.oneapp.update.*'

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 12s
31 actionable tasks: 4 executed, 27 up-to-date
```

9 Tests, alle grün:
- `lower versionCode in manifest returns NoUpdate`
- `identical versionCode returns NoUpdate`
- `higher versionCode returns Available with correct release`
- `server 500 on manifest returns Error`
- `server 404 on manifest returns NotConfigured`
- `connection refused returns Error`
- `sha256Hex companion produces correct hash`
- `downloadAndInstall throws SecurityException on SHA256 mismatch`
- `mode=disabled returns NotConfigured without hitting server`

---

## Fortgepflanzte Marker aus RESULT_PHASE_1.md

Alle Marker aus Phase 1 gelten unverändert:

```
MARKER_HOSTING:      SUBPATH    → proxyUrl=https://updates.drainq.de/one/ in BuildConfig
MARKER_MANDATORY:    NO         → mandatory-Feld in ReleaseInfo vorhanden, kein App-Block
MARKER_CHANNEL_UI:   HIDDEN     → channel via SharedPrefs überschreibbar, kein UI (→ Phase 3)
MARKER_VERSIONCODE:  FROM_TAG   → BuildConfig.VERSION_CODE als Vergleichsbasis
MARKER_AUTOCHECK:    DAILY_WIFI → WorkManager noch nicht (→ Phase 3)
MARKER_CERT_PINNING: OFF        → kein Pinning in OkHttp-Client
```

---

## Bekannte Issues und TODOs für Folge-Phasen

- **Phase 3:** `UpdateWorker` (WorkManager, 24h, UNMETERED) + `UpdateSection` Settings-UI + `UpdateDialog` + `UpdateProgressDialog` noch nicht implementiert.
- **Phase 3:** `update_*`-Keys nur in DE eingetragen, alle anderen 34 Sprachen erben DE-Fallback (LocalizationManager-Fallback-Mechanismus bereits aktiv).
- **Phase 6:** `UpdateEventType`-Enum ist vorbereitet; Persistierung in Room (`UpdateEventEntity`, DAO, Migration) folgt in Phase 6.
- **INSTALL_DONE:** Das Bestätigungs-Event nach USER_ACTION kommt aus dem `PendingIntent`-BroadcastReceiver — der Receiver ist noch nicht registriert (Phase 3 bindet ihn in die Activity/ViewModel-Schicht ein).
- **Netzwerk-Fehlertext:** `HttpUpdateService` wirft `RuntimeException` mit HTTP-Code; Phase 3 übersetzt das in `update_error_network` via LocalizationKey.
- **APK-Cleanup:** Nach erfolgreicher Installation verbleibt die APK in `cacheDir/updates/` — Cleanup-Logik kommt in Phase 3 (`downloadAndInstall` post-install oder beim nächsten Check).

# RESULT PHASE 3 — Settings-UI + UpdateDialog + WorkManager (Update-Prozess)

**Datum:** 2026-05-12
**Branch:** `feature/update-phase-3`
**Commit:** `c1bb4ff`
**Status:** abgeschlossen

---

## Branch + Commits

```
Branch: feature/update-phase-3 (aus master + merge feature/update-phase-2)
Merge:  Fast-forward merge von feature/update-phase-2 als Basis (Phase-2-Code war noch nicht in master)
Commit: c1bb4ff  feat(update-phase-3): Settings-UI + UpdateDialog + WorkManager Periodic-Check
```

---

## Liste aller neuen und geänderten Dateien

| Datei | Status |
|---|---|
| `app/src/main/java/com/uip/oneapp/ui/screens/settings/UpdateSection.kt` | neu |
| `app/src/main/java/com/uip/oneapp/ui/components/UpdateDialog.kt` | neu |
| `app/src/main/java/com/uip/oneapp/ui/components/UpdateProgressDialog.kt` | neu |
| `app/src/main/java/com/uip/oneapp/update/UpdateWorker.kt` | neu |
| `app/src/main/java/com/uip/oneapp/OneApp.kt` | geändert |
| `app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt` | geändert |
| `app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` | geändert |

---

## Diff-Summary pro Datei

**UpdateSection.kt** — Compose-Karte für Settings-Seite. Zeigt Version, Channel-Label, letzten Check-Zeitpunkt. „Nach Updates suchen"-Button ruft `UpdateService.checkForUpdate()` auf und persistiert Check-Zeitpunkt in `SharedPreferences("update")["last_update_check"]`. Steuert `UpdateDialog` und `UpdateProgressDialog`. Überwacht `UpdateService.getUpdateEvents()` Flow für Download-Fortschritt. 7-Tap-Easter-Egg auf der Versionszeile blendet Channel-Dropdown ein (MARKER_CHANNEL_UI: HIDDEN).

**UpdateDialog.kt** — `AlertDialog` mit scrollbaren Versionshinweisen, Größenangabe, optional Warning-Icon bei `mandatory=true`. „Später"-Button immer sichtbar, kein App-Block (MARKER_MANDATORY: NO). Gibt Release über `onInstall`-Callback zurück.

**UpdateProgressDialog.kt** — `AlertDialog` mit `LinearProgressIndicator` (determinat wenn `totalBytes > 0`, sonst indeterminat). Drei Stages: `Downloading` / `Verifying` / `Installing`. Cancel-Button bricht Coroutine via `Job.cancel()` ab.

**UpdateWorker.kt** — `CoroutineWorker` mit `KoinComponent`-Inject von `UpdateService`. `PeriodicWorkRequestBuilder<UpdateWorker>(24, HOURS)` + `NetworkType.UNMETERED` (MARKER_AUTOCHECK: DAILY_WIFI). Bei `UpdateCheckResult.Available` wird lokale Notification (Channel `updates`) gepostet. `ExistingPeriodicWorkPolicy.KEEP` verhindert Timer-Reset bei App-Restart.

**OneApp.kt** — `NotificationChannel("updates", ...)` für Android O+ angelegt. `UpdateWorker.schedule(this)` nach Koin-Init aufgerufen.

**SettingsScreen.kt** — `UpdateSection()` vor der App-Info Karte eingebunden (2 Zeilen).

**LocalizationManager.kt** — 5 neue DE-Keys: `update_section_title`, `update_channel_label`, `update_cancel`, `update_notification_title`, `update_notification_body`.

---

## Compile- und Test-Ergebnisse

### ./gradlew assembleDebug

```
> Task :app:compileDebugKotlin
w: [pre-existing warnings: LocalizationManager unused var 'lang', SettingsScreen deprecated Divider × 6]

> Task :app:assembleDebug

BUILD SUCCESSFUL in 38s
40 actionable tasks: 8 executed, 32 up-to-date
```

### ./gradlew testDebugUnitTest

```
BUILD SUCCESSFUL in 13s
31 actionable tasks: 6 executed, 25 up-to-date
```

Alle 9 Phase-2-Tests weiterhin grün (UpdateServiceTest).

---

## Fortgepflanzte Marker aus RESULT_PHASE_1.md

```
MARKER_HOSTING:      SUBPATH    → unverändert
MARKER_MANDATORY:    NO         → UpdateDialog zeigt Warning-Banner, Später-Button immer da
MARKER_CHANNEL_UI:   HIDDEN     → 7-Tap-Easter-Egg auf Versionszeile implementiert
MARKER_VERSIONCODE:  FROM_TAG   → unverändert
MARKER_AUTOCHECK:    DAILY_WIFI → UpdateWorker 24h UNMETERED implementiert
MARKER_CERT_PINNING: OFF        → unverändert
```

---

## Pragmatische Entscheidungen

1. **Branch-Basis**: Phase-3-Branch aus master erstellt (Phasenplan-Vorgabe), Phase-2-Code aber noch nicht in master → `git merge feature/update-phase-2` (Fast-forward) als erste Aktion.

2. **Notification-Klick öffnet App ohne direkten SettingsScreen-DeepLink**: MainActivity hat keine Intent-Extra-Navigation. Pragmatisch: Launch-Intent ohne Extra. Kann in Phase 7 mit NavDeepLink verfeinert werden.

3. **`i18n/de.json` existiert nicht**: Lokalisierung läuft über `LocalizationManager.kt` Kotlin-Map. Alle `update_*`-Keys in `deTranslations()` eingetragen.

4. **Kein separates UpdateViewModel**: Update-State als lokaler Compose-State in `UpdateSection`. Ausreichend für Phase 3 (kein screen-übergreifender Bedarf). WorkManager-Zustand ist OS-seitig persistiert.

---

## Bekannte Issues und TODOs für Folge-Phasen

- **Phase 4:** GitHub Actions Workflow für Release-APK-Build fehlt noch.
- **Phase 5:** Hetzner-Proxy-Konfiguration fehlt noch.
- **Phase 6:** Audit-Log (Room-Entity, DAO, Migration), Integrationstests, KRITIS-Doku. `InstallStatusReceiver` für `ACTION_INSTALL_STATUS` noch nicht registriert.
- **Phase 7:** `update_*`-Keys nur in DE; andere 34 Sprachen erben DE-Fallback. Notification-DeepLink auf SettingsScreen.
- **APK-Cleanup**: Nach Install verbleibt APK in `cacheDir/updates/` — Cleanup in Phase 6.

# RESULT_PHASE_6 — ONE-App Hardcoded-String-Audit

**Phase:** 6 — Hardcoded-Audit + alle Composables auf `t("KEY")`  
**Branch:** `feature/l10n-phase-6-hardcoded`  
**Datum:** 2026-05-21  
**Modell:** claude-sonnet-4-6 (think, GodMode)  
**Eingabe:** RESULT_PHASE_5.md

---

## Pflicht-Marker

```
PHASE6-HARDCODED-FOUND: 69
PHASE6-HARDCODED-FIXED: 65
```

---

## Was wurde geliefert

### Repo: `C:\Projekte\drainq.one-localization`

#### Neue / geänderte Dateien

| Datei | Beschreibung |
|-------|--------------|
| `app/src/main/res/raw/l10n_de.json` | +57 neue Keys (Phase-6-Fundstellen). Bundle: 355 → 412 Keys. |
| `app/src/main/res/raw/l10n_en.json` | +57 neue Keys (EN-Übersetzungen). Bundle: 355 → 412 Keys. |
| `app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` | Neuer `S(key, vararg args)` Composable-Overload für parametrisierte Strings. |
| `app/src/main/java/com/uip/oneapp/ui/screens/connection/ConnectionScreen.kt` | 1 hardcoded String → `S("rtsp_url_placeholder")` |
| `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` | Stop-Recording-Button: `"${S("stop")} $elapsed"` → `S("stop_recording", elapsed)` |
| `app/src/main/java/com/uip/oneapp/ui/screens/inspection/PureCinemaOverlays.kt` | 4 hardcoded Strings → `S("KEY")`. Import `S` hinzugefügt. |
| `app/src/main/java/com/uip/oneapp/ui/screens/offlinemaps/OfflineMapsScreen.kt` | 30 hardcoded Strings (TopAppBar, FAB, Dialoge, Picker) → `S("KEY")`. Import `S`+`LocalizationManager` hinzugefügt. |
| `app/src/main/java/com/uip/oneapp/ui/screens/projectdetail/ProjectDetailScreen.kt` | 8 hardcoded Strings (Toasts, Delete-Dialog) → `S("KEY")` / `LocalizationManager.t("KEY")`. |
| `app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormScreen.kt` | 1 hardcoded "OK" → `S("button_ok")` |
| `app/src/main/java/com/uip/oneapp/ui/screens/settings/NetworkSettingsSections.kt` | 17 hardcoded Strings (WLAN, Hotspot, WiFi-Status, Dialoge) → `S("KEY")`. Import hinzugefügt. |
| `app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt` | 4 hardcoded Strings (Exit-Dialog) → `S("KEY")` |
| `docs/LOCALIZATION_AUDIT_REPORT_ONE.md` | Vollständiger Audit-Report mit Tabelle Datei/Zeile/alter Text/neuer Key/Status. |

---

## Diff-Summary

| Datei | +Zeilen | -Zeilen |
|-------|---------|---------|
| l10n_de.json | +57 | — |
| l10n_en.json | +57 | — |
| LocalizationManager.kt | +8 | — |
| ConnectionScreen.kt | +1 | −1 |
| InspectionScreen.kt | +1 | −1 |
| PureCinemaOverlays.kt | +5 | −4 |
| OfflineMapsScreen.kt | +35 | −27 |
| ProjectDetailScreen.kt | +12 | −10 |
| ProjectFormScreen.kt | +1 | −1 |
| NetworkSettingsSections.kt | +27 | −17 |
| SettingsScreen.kt | +7 | −10 |
| LOCALIZATION_AUDIT_REPORT_ONE.md (neu) | +170 | — |
| **Gesamt** | **+382** | **−71** |

---

## Branch + Commit-Hashes

### drainq.one-localization

| Branch | Commit | Beschreibung |
|--------|--------|--------------|
| `feature/l10n-phase-6-hardcoded` | `d97010f` | feat(l10n): Phase 6 — Hardcoded-Audit + alle Composables auf t(KEY) |

Basis: `feature/l10n-portal` (enthält Phase 0–5)

---

## Build-Status

| Build | Ergebnis |
|-------|----------|
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
| Kotlin-Kompilierung | ✅ 0 neue Fehler |
| Warnings (neu) | 0 neue Warnings (alle pre-existing Deprecations aus früheren Phasen) |

---

## Test-Status

| Test-Suite | Anzahl | Status |
|------------|--------|--------|
| Alle Unit-Tests (gesamt) | 226 | ✅ 0 Failures, 0 Errors |
| `LocalizationManagerTest` (Phase 5) | 25 | ✅ |
| `OsdAsciiSafeTest` | 14 | ✅ |
| `OsdCoordinateTest` | 5 | ✅ |
| `OsdRendererVisualTest` | 4 | ✅ |
| `OsdRenderGuardTest` | 3 | ✅ |
| `OsdSettingsDefaultsTest` | 8 | ✅ |
| `FfmpegRtspRecorderTest` | 29 | ✅ |
| `OsdOverlayTest` | 8 | ✅ |
| `UpdateE2ETest` | 8 | ✅ |
| `UpdateServiceTest` | 9 | ✅ |
| Weitere Tests | 113 | ✅ |

---

## KRITIS-Check-Status

| Prüfpunkt | Status |
|-----------|--------|
| Keine hardcodierten deutschen Strings mehr in App-Composables (user-facing) | ✅ |
| Keine Secrets in Code oder neuen Keys | ✅ |
| Keine hardcodierten Farben eingeführt | ✅ |
| Neue Bundle-Keys enthalten keine personenbezogenen Daten | ✅ |
| `LocalizationManager.t()` aus nicht-composable Context (Toast) korrekt verwendet | ✅ |
| Smart-Cast-Probleme für nullable State-Properties sauber gelöst | ✅ (lokale Variable) |
| `drainq-kritis-compliance`-Skill nicht vorhanden | ⚠️ Manuell geprüft (wie Phase 5) |

---

## Pragmatische Entscheidungen (Abweichungen vom Plan)

1. **Erweiterter Scope über Phase-0-Audit hinaus**: Phase-0-Audit hatte 19 Fundstellen in 3 Dateien. Vollständiger Sweep ergab 69 in 9 Dateien. Alle behoben — keine Einschränkung auf Phase-0-Liste.

2. **`LocalizationManager.t()` statt `S()` für `when`-Ausdrücke mit nicht-composablen Branches**: Für `when`-Ausdrücke, die Strings berechnen (z.B. WiFi-Status, Hotspot-Status), wird `LocalizationManager.t()` direkt aufgerufen. Die Recomposition-Triggerung erfolgt über `S()` bzw. `collectAsState()`-Aufrufe im selben Composable-Scope.

3. **`val hotspotLastError = state.lastError` lokale Variable**: Kotlin Smart-Cast funktioniert nicht auf `state.lastError` (Complex Expression von `collectAsState()`). Statt `?: ""` (Redundanz-Warning) wurde die nullable Property in eine lokale Variable kopiert.

4. **Exempt: 5 Strings nicht lokalisiert**: `"$count"` (Badge), `"$fileName ($fileSize)"` (Dateiinfo), Schadens-/Notiz-Daten, URL-Attributionen (`download.mapsforge.org`), technische Format-Pattern. Diese sind entweder reine Datenwerte, externe Referenzen oder numerische Literale.

5. **Typo-Fix in SettingsScreen**: `"Einstellungen oeffnen"` (typo mit oe statt ö) → Key `open_settings_btn` DE: `"Einstellungen öffnen"` (korrekte Schreibweise).

6. **`drainq-kritis-compliance`-Skill nicht vorhanden**: Wie in Phase 5 dokumentiert — manueller KRITIS-Check durchgeführt.

---

## Neue Keys in Bundle-JSONs (57 Keys, alphabetisch)

`action_irreversible`, `already_installed`, `btn_set`, `cancel`, `check_internet_retry`,
`checking_server_size`, `connect`, `delete_permanently`, `delete_project_fail`, `download`,
`download_progress`, `download_queued`, `exit_app_message`, `exit_app_title`,
`hotspot_active_local`, `hotspot_active_status`, `hotspot_error`, `hotspot_mode`,
`hotspot_mode_legacy`, `hotspot_mode_local_only`, `hotspot_title`, `meter_set_dialog_hint`,
`meter_unit`, `offline_maps_add`, `offline_maps_delete_confirm`, `offline_maps_delete_hint`,
`offline_maps_download_confirm`, `offline_maps_drift_larger`, `offline_maps_drift_smaller`,
`offline_maps_empty`, `offline_maps_empty_hint`, `offline_maps_installed_count`,
`offline_maps_select_region`, `offline_maps_server_size_label`, `offline_maps_server_unreachable`,
`offline_maps_size_check_failed`, `offline_maps_storage_label`, `offline_maps_title`,
`open_settings_btn`, `project_deleted_message`, `project_delete_confirm_header`,
`project_delete_confirm_items`, `project_delete_confirm_title`, `refresh`, `state_disabled`,
`state_enabled`, `wifi_connected_status`, `wifi_network_name_label`, `wifi_no_networks`,
`wifi_open_network`, `wifi_open_state`, `wifi_password`, `wifi_password_min8_label`,
`wifi_scan_btn`, `wifi_secured`, `wifi_title`

---

## Bekannte Issues / Offene Punkte

1. **Key-Format snake_case vs UPPER_SNAKE_CASE**: Neue Keys in Phase 6 wurden in `snake_case` angelegt (konsistent mit Phase-5-Bundle). Phase-1-Mapping sieht `UPPER_SNAKE_CASE` vor. Die komplette Key-Umbenennung erfolgt im Portal-Cutover (Phase 7), wenn die Bundle-JSONs durch `fetchBundledLocales` aus dem Portal neu gezogen werden.

2. **Portal-API-Sync ausstehend**: Neue Phase-6-Keys sind nur im lokalen Bundle (`res/raw/l10n_de.json`, `l10n_en.json`). Import ins Portal via Bulk-Import-API erfolgt in Phase 7.

3. **OfflineMapsScreen: `"Quelle: download.mapsforge.org"` nicht lokalisiert**: Diese externe Attribution mit URL wurde als exempt klassifiziert. Falls Übersetzung gewünscht, kann ein neuer Key `offline_maps_source_attribution` ergänzt werden.

4. **Keine vollständige UI-Test-Verifikation möglich**: Phase 6 ändert nur Strings in Composables. Funktions-Tests via Instrumentation/UI-Test-Suite (nicht im Projekt vorhanden) konnten nicht durchgeführt werden.

---

## Nächste Phase

**Phase 7 — Cutover + Doku + Release-Notes v0.4.0**  
Branch: `feature/l10n-phase-7-cutover`  
Repos: `drainq.one-localization` + `Drainq_Suite_repo`  
Eingabe: RESULT_PHASE_6.md  
Ziel: DE+EN importieren, 33 Sprachen verwerfen, Smoke-Tests, Release-Notes.

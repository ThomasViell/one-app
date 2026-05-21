# DrainQ.ONE — Localization Audit Report

**Phase:** 6 — Hardcoded-String-Audit  
**Datum:** 2026-05-21  
**Branch:** `feature/l10n-phase-6-hardcoded`  
**Basis:** Phase-0-Audit (`phase0/hardcoded_audit_preview.md`) + erweiterter Sweep  
**Modell:** claude-sonnet-4-6 (GodMode)

---

## Zusammenfassung

| Kategorie | Anzahl |
|-----------|--------|
| Fundstellen gesamt (user-facing) | 66 |
| Behoben via `t(KEY)` / `S("KEY")` | 61 |
| Exempt (technisch/dynamisch) | 5 |
| Neue L10N-Keys erstellt | 57 |
| Geänderte Dateien | 11 |

---

## Fundstellen — Vollständige Tabelle

### ConnectionScreen.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 1 | `ui/screens/connection/ConnectionScreen.kt` | 575 | `placeholder` | `rtsp://192.168.1.100:554/stream` | `rtsp_url_placeholder` | ✅ Behoben |

### InspectionScreen.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 2 | `ui/screens/inspection/InspectionScreen.kt` | 719 | `Text()` | `"${S("stop")} $recordingElapsed"` | `stop_recording` | ✅ Behoben |

### PureCinemaOverlays.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 3 | `ui/screens/inspection/PureCinemaOverlays.kt` | 176 | `Text()` | `Aktuellen Wert manuell eingeben...` | `meter_set_dialog_hint` | ✅ Behoben |
| 4 | `ui/screens/inspection/PureCinemaOverlays.kt` | 181 | `label` | `Meter` | `meter_unit` | ✅ Behoben |
| 5 | `ui/screens/inspection/PureCinemaOverlays.kt` | 191 | `Text()` | `Setzen` | `btn_set` | ✅ Behoben |
| 6 | `ui/screens/inspection/PureCinemaOverlays.kt` | 194 | `Text()` | `Abbrechen` | `cancel` | ✅ Behoben |

### OfflineMapsScreen.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 7 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 38 | `Text()` | `Offline-Karten` | `offline_maps_title` | ✅ Behoben |
| 8 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 41 | `contentDescription` | `Zurück` | `back` | ✅ Behoben |
| 9 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 46 | `contentDescription` | `Aktualisieren` | `refresh` | ✅ Behoben |
| 10 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 55 | `Text()` | `Karte hinzufügen` | `offline_maps_add` | ✅ Behoben |
| 11 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 78 | `Text()` | `{n} Karte(n) installiert` | `offline_maps_installed_count` | ✅ Behoben |
| 12 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 85 | `Text()` | `Belegt: {size} MB` | `offline_maps_storage_label` | ✅ Behoben |
| 13 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 116 | `Text()` | `Noch keine Karten heruntergeladen` | `offline_maps_empty` | ✅ Behoben |
| 14 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 120 | `Text()` | `Tippe auf '+ Karte hinzufügen'...` | `offline_maps_empty_hint` | ✅ Behoben |
| 15 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 166 | `String` | `⚠ {n} MB größer als der Katalog-Hinweis` | `offline_maps_drift_larger` | ✅ Behoben |
| 16 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 167 | `String` | `Hinweis: {n} MB kleiner als erwartet` | `offline_maps_drift_smaller` | ✅ Behoben |
| 17 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 173 | `title` | `Download starten?` | `offline_maps_download_confirm` | ✅ Behoben |
| 18 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 179 | `Text()` | `Aktuelle Dateigröße auf dem Server:` | `offline_maps_server_size_label` | ✅ Behoben |
| 19 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 207 | `Text()` | `Herunterladen` | `download` | ✅ Behoben |
| 20 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 212 | `Text()` | `Abbrechen` | `cancel` | ✅ Behoben |
| 21 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 221 | `title` | `Server nicht erreichbar` | `offline_maps_server_unreachable` | ✅ Behoben |
| 22 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 228 | `Text()` | `Die aktuelle Dateigröße konnte nicht...` | `offline_maps_size_check_failed` | ✅ Behoben |
| 23 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 235 | `Text()` | `Prüfe deine Internetverbindung...` | `check_internet_retry` | ✅ Behoben |
| 24 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 241 | `Text()` | `OK` | `button_ok` | ✅ Behoben |
| 25 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 252 | `title` | `Karte löschen?` | `offline_maps_delete_confirm` | ✅ Behoben |
| 26 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 253 | `Text()` | `"{name}" wird vom Tablet entfernt.` | `offline_maps_delete_hint` | ✅ Behoben |
| 27 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 258 | `Text()` | `Löschen` | `delete` | ✅ Behoben |
| 28 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 261 | `Text()` | `Abbrechen` | `cancel` | ✅ Behoben |
| 29 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 298 | `contentDescription` | `Löschen` | `delete` | ✅ Behoben |
| 30 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 318 | `title` | `Region auswählen` | `offline_maps_select_region` | ✅ Behoben |
| 31 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 345 | `String` | `Bereits installiert` | `already_installed` | ✅ Behoben |
| 32 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 346 | `String` | `Server-Größe wird abgefragt…` | `checking_server_size` | ✅ Behoben |
| 33 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 350 | `String` | `Download läuft … {pct} %` | `download_progress` | ✅ Behoben |
| 34 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 352 | `String` | `Download in Warteschlange` | `download_queued` | ✅ Behoben |
| 35 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 385 | `contentDescription` | `Abbrechen` | `cancel` | ✅ Behoben |
| 36 | `ui/screens/offlinemaps/OfflineMapsScreen.kt` | 398 | `Text()` | `Schließen` | `close` | ✅ Behoben |

### ProjectDetailScreen.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 37 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 83 | `Toast` | `Projekt gelöscht — {n} Dateien, {kb} KB freigegeben` | `project_deleted_message` | ✅ Behoben |
| 38 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 92 | `Toast` | `Löschen fehlgeschlagen: {msg}` | `delete_project_fail` | ✅ Behoben |
| 39 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 167 | `Text()` | `$fileName ($fileSize)` | — | ⚪ Exempt (reine Datei-Daten) |
| 40 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 596 | `title` | `Projekt unwiderruflich löschen?` | `project_delete_confirm_title` | ✅ Behoben |
| 41 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 599 | `Text()` | `Projekt: {pNum}` | `project_delete_confirm_header` | ✅ Behoben |
| 42 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 601-607 | `Text()` | `Es werden gelöscht: ...` (mehrzeilig) | `project_delete_confirm_items` | ✅ Behoben |
| 43 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 611 | `Text()` | `Diese Aktion kann nicht rückgängig...` | `action_irreversible` | ✅ Behoben |
| 44 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 622 | `Text()` | `Endgültig löschen` | `delete_permanently` | ✅ Behoben |
| 45 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 638 | `Text()` | `${damageType} - ${pos} m` | — | ⚪ Exempt (Schadens-Daten) |
| 46 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 660 | `Text()` | `${pos} m - ${text}` | — | ⚪ Exempt (Notiz-Daten) |
| 47 | `ui/screens/projectdetail/ProjectDetailScreen.kt` | 735 | `Text()` | `$count` | — | ⚪ Exempt (numerischer Badge) |

### ProjectFormScreen.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 48 | `ui/screens/projects/ProjectFormScreen.kt` | 368 | `Text()` | `OK` | `button_ok` | ✅ Behoben |

### NetworkSettingsSections.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 49 | `ui/screens/settings/NetworkSettingsSections.kt` | 69 | `Text()` | `WLAN` | `wifi_title` | ✅ Behoben |
| 50 | `ui/screens/settings/NetworkSettingsSections.kt` | 73 | `String` | `Aus` | `state_disabled` | ✅ Behoben |
| 51 | `ui/screens/settings/NetworkSettingsSections.kt` | 74 | `String` | `Verbunden: {ssid} {rssi} dBm` | `wifi_connected_status` | ✅ Behoben |
| 52 | `ui/screens/settings/NetworkSettingsSections.kt` | 75 | `String` | `Eingeschaltet` | `state_enabled` | ✅ Behoben |
| 53 | `ui/screens/settings/NetworkSettingsSections.kt` | 100 | `Text()` | `Netze suchen` | `wifi_scan_btn` | ✅ Behoben |
| 54 | `ui/screens/settings/NetworkSettingsSections.kt` | 108 | `Text()` | `Keine Netze gefunden...` | `wifi_no_networks` | ✅ Behoben |
| 55 | `ui/screens/settings/NetworkSettingsSections.kt` | 144 | `label` | `Passwort` | `wifi_password` | ✅ Behoben |
| 56 | `ui/screens/settings/NetworkSettingsSections.kt` | 160 | `Text()` | `Offenes Netzwerk — kein Passwort.` | `wifi_open_network` | ✅ Behoben |
| 57 | `ui/screens/settings/NetworkSettingsSections.kt` | 168 | `Text()` | `Verbinden` | `connect` | ✅ Behoben |
| 58 | `ui/screens/settings/NetworkSettingsSections.kt` | 171 | `Text()` | `Abbrechen` | `cancel` | ✅ Behoben |
| 59 | `ui/screens/settings/NetworkSettingsSections.kt` | 207 | `String` | `Gesichert`/`Offen` | `wifi_secured`/`wifi_open_state` | ✅ Behoben |
| 60 | `ui/screens/settings/NetworkSettingsSections.kt` | 251 | `Text()` | `Hotspot` | `hotspot_title` | ✅ Behoben |
| 61 | `ui/screens/settings/NetworkSettingsSections.kt` | 255-261 | `String` | `Aktiv: {ssid}` / `Aktiv (Local-only): {ssid}` / `Fehler: {err}` / `Aus` | `hotspot_active_status` / `hotspot_active_local` / `hotspot_error` / `state_disabled` | ✅ Behoben |
| 62 | `ui/screens/settings/NetworkSettingsSections.kt` | 282 | `label` | `Netzwerkname (SSID)` | `wifi_network_name_label` | ✅ Behoben |
| 63 | `ui/screens/settings/NetworkSettingsSections.kt` | 290 | `label` | `Passwort (min. 8 Zeichen)` | `wifi_password_min8_label` | ✅ Behoben |
| 64 | `ui/screens/settings/NetworkSettingsSections.kt` | 313 | `label arg` | `Passwort` (HotspotInfoRow) | `wifi_password` | ✅ Behoben |
| 65 | `ui/screens/settings/NetworkSettingsSections.kt` | 315-317 | `label/value` | `Modus` / `Vollwertiger Hotspot` / `Local-only...` | `hotspot_mode` / `hotspot_mode_legacy` / `hotspot_mode_local_only` | ✅ Behoben |

### SettingsScreen.kt

| # | Datei | Zeile | Typ | Alter Text | Neuer Key | Status |
|---|-------|-------|-----|------------|-----------|--------|
| 66 | `ui/screens/settings/SettingsScreen.kt` | 1140 | `title` | `DrainQ.ONE verlassen?` | `exit_app_title` | ✅ Behoben |
| 67 | `ui/screens/settings/SettingsScreen.kt` | 1143-1147 | `Text()` | `Die App startet automatisch nach Boot...` | `exit_app_message` | ✅ Behoben |
| 68 | `ui/screens/settings/SettingsScreen.kt` | 1164 | `Text()` | `Einstellungen oeffnen` | `open_settings_btn` | ✅ Behoben |
| 69 | `ui/screens/settings/SettingsScreen.kt` | 1168 | `Text()` | `Abbrechen` | `cancel` | ✅ Behoben |

---

## Exempt-Strings (nicht lokalisiert)

| # | Datei | Grund |
|---|-------|-------|
| — | `ConnectionScreen.kt:802,840` | `"0"` — numerische Literale |
| — | `OfflineMapsScreen.kt:85` | `"Quelle: download.mapsforge.org (ODbL..."` — externe Attribution mit URL |
| — | `OfflineMapsScreen.kt:131` | `"%.2f..%.2f °N..."` — technisches Format-Pattern |
| — | `OfflineMapsScreen.kt:199` | `"Quelle: download.mapsforge.org"` — externe Attribution URL |
| — | `OfflineMapsScreen.kt:290` | `"%.1f MB · ${entry.country}"` — dynamischer Datenwert |
| — | `ProjectDetailScreen.kt:167` | `"$fileName ($fileSize)"` — reine Dateiname-Anzeige |
| — | `ProjectDetailScreen.kt:638,660` | Dynamische Schadens-/Notiz-Datenzeilen |
| — | `ProjectDetailScreen.kt:735` | `"$count"` — numerischer Badge |
| — | `SettingsScreen.kt:200` | `"${lang.flag}  ${lang.name}"` — dynamisch von API |

---

## Neue Keys in Bundle-JSONs

57 neue Keys hinzugefügt zu `l10n_de.json` und `l10n_en.json`:

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

## Geänderte Dateien

| Datei | +Zeilen | -Zeilen |
|-------|---------|---------|
| `l10n_de.json` | +57 | — |
| `l10n_en.json` | +57 | — |
| `LocalizationManager.kt` | +8 | — |
| `ConnectionScreen.kt` | +1 | −1 |
| `InspectionScreen.kt` | +1 | −1 |
| `PureCinemaOverlays.kt` | +5 | −4 |
| `OfflineMapsScreen.kt` | +35 | −27 |
| `ProjectDetailScreen.kt` | +12 | −10 |
| `ProjectFormScreen.kt` | +1 | −1 |
| `NetworkSettingsSections.kt` | +27 | −17 |
| `SettingsScreen.kt` | +7 | −10 |
| **Gesamt** | **+212** | **−71** |

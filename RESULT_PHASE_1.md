# RESULT_PHASE_1 — Key-Mapping snake → UPPER_SNAKE + Shared-Detection

**Phase:** 1 — Key-Mapping  
**Branch:** `feature/l10n-phase-1-mapping`  
**Datum:** 2026-05-20  
**Modell:** claude-sonnet-4-6 (think, GodMode)  
**Basis-Branch:** `feature/l10n-portal` (enthält Phase-0-Outputs)

---

## Pflicht-Marker

```
PHASE1-MAPPED: 355
PHASE1-SHARED: 78
PHASE1-CONFLICTS: 4
```

---

## Was wurde geliefert

### 1. `phase1/key_mapping.csv` — vollständiges Key-Mapping

- **355 Keys** aus `phase0/keys_de_en.json` gemappt
- Spalten: `old_key, new_key, scope, source_de, source_en, windows_key_match_or_empty`
- **78 shared-Keys** → Windows-Key übernommen als new_key
- **277 one-spezifische Keys** → snake_case → UPPER_SNAKE_CASE
- **69 unique Windows-Keys** referenziert (mehrere ONE-Keys können auf denselben Win-Key zeigen)

Beispiele shared:
- `back` → `Common.Back` (scope=shared)
- `save` → `BTN_SAVE` (scope=shared)
- `close` → `BTN_CLOSE` (scope=shared)
- `damage_type_deposit` → `LinearDmg.BDA` (scope=shared)
- `osd_show_date` → `Osd.Config.ShowDate` (scope=shared)

Beispiele one-spezifisch:
- `stream_preview` → `STREAM_PREVIEW` (scope=one)
- `recording_active` → `RECORDING_ACTIVE` (scope=one)
- `nsp3ct_connection` → `NSP3CT_CONNECTION` (scope=one)
- `wifi_connect_hint` → `WIFI_CONNECT_HINT` (scope=one)

### 2. `phase1/conflicts.md` — Konflikt-Dokumentation

4 Konflikte identifiziert:

| Typ | Key | Grund | Entscheidung |
|---|---|---|---|
| HART | `pdf_company_label` | DE-Mismatch: ONE="Firma" / WIN="FIRMA" (Großschreibung) | scope=one → `PDF_COMPANY_LABEL` |
| HART | `position_label` | DE-Mismatch: ONE="Position:" (Doppelpunkt) / WIN="Position"; EN-Mismatch: ONE="Location" | scope=one → `POSITION_LABEL` |
| WEICH | `not_reachable` | ONE EN="Not accessible" vs WIN `WELCOME_PORTAL_OFFLINE` EN unbekannt | scope=shared (vorläufig), Cutover-Check Phase 7 |
| WEICH | `scan` | ONE EN="Scanning" vs `WIFI_SCAN` Kontext-Unterschied | scope=shared (vorläufig), EN-Check Phase 5 |

### 3. `phase1/validate_mapping.ps1` — Validierungs-Skript

Prüft:
- Vollständigkeit: alle 355 Source-Keys im CSV
- Eindeutigkeit: kein old_key doppelt
- Scope-Werte: nur `shared`/`one`
- Shared-Keys: Windows-Key-Referenz vorhanden
- ONE-Keys: UPPER_SNAKE_CASE korrekt
- Konsistenz: new_key == windows_key bei shared

**Ergebnis:** `VALIDATION PASSED — 0 Fehler, 0 Warnungen`

---

## Diff-Summary

| Datei | Status | +Zeilen | -Zeilen |
|---|---|---|---|
| `phase1/key_mapping.csv` | NEU | 356 (355 Keys + Header) | — |
| `phase1/conflicts.md` | NEU | ~95 | — |
| `phase1/validate_mapping.ps1` | NEU | ~110 | — |

**Repo:** `C:\Projekte\drainq.one-localization`  
**Geänderte Dateien:** 3 neue Dateien in `phase1/`  
**Zeilen gesamt:** ~561 neu

---

## Aufteilung shared vs. one-spezifisch

| Kategorie | Anzahl | Anteil |
|---|---|---|
| scope=shared | 78 | 21,97 % |
| scope=one | 277 | 78,03 % |
| **Total** | **355** | 100 % |

### Mapping-Muster bei shared (mehrere ONE-Keys → gleicher Win-Key)

| Windows-Key | ONE-Keys |
|---|---|
| `Form.Tab.Inspection` | `inspection`, `inspection_action`, `nav_inspection` |
| `DIALOG_NEW_PROJECT` | `new_project`, `new_project_fab`, `new_project_title` |
| `BTN_DELETE` | `delete`, `delete_preset` |
| `CLIENT_PROFILE_NOTES` | `notes`, `tab_notes` |
| `EZ_AI_EDIT` | `edit`, `edit_preset` |
| `Banner.Shape.Other` | `damage_type_other`, `pipe_type_other` |
| `MENU_FILE_SETTINGS` | `nav_settings`, `settings_title` |
| `MANHOLE_FINDING_SECTION_POSITION` | `pdf_position_label` |

---

## Branch + Commit-Hashes

Branch: `feature/l10n-phase-1-mapping`  
Basis: `feature/l10n-portal` (commit `c2e20bb`)

| Commit | Hash | Beschreibung |
|---|---|---|
| Phase 1 | (nach diesem RESULT) | feat(l10n): Phase 1 — Key-Mapping 355 Keys, 78 shared, 4 Konflikte |

---

## Build-Status

Phase 1 ist rein dokumentarisch (CSV/Markdown/PS1) — kein Android-Code geändert.  
**App-Build:** nicht erforderlich in Phase 1. Nächster Build-Checkpoint: Phase 5.  
**Portal-Build:** nicht erforderlich in Phase 1. Nächster Build-Checkpoint: Phase 2.

---

## Test-Status

| Test | Status |
|---|---|
| `phase1/validate_mapping.ps1` | ✅ PASSED (0 Fehler, 0 Warnungen) |
| Vollständigkeit (355 Keys) | ✅ |
| Eindeutigkeit (kein Duplikat) | ✅ |
| Scope-Werte korrekt | ✅ |
| Shared → Windows-Key korrekt | ✅ |
| ONE → UPPER_SNAKE_CASE korrekt | ✅ |

---

## KRITIS-Check-Status

| Prüfpunkt | Status |
|---|---|
| Keine Secrets in Mapping-Dateien | ✅ nur UI-Strings |
| Keine hardcodierten Farben/Werte | ✅ reine Datendateien |
| Keine personenbezogenen Daten | ✅ |
| Audit-Trail: Konfliktentscheidungen dokumentiert | ✅ `phase1/conflicts.md` |
| Validierungs-Skript für CI-Integration vorbereitet | ✅ `phase1/validate_mapping.ps1` |

---

## Pragmatische Entscheidungen

1. **Hard Conflicts (scope=one):** `pdf_company_label` und `position_label` wurden aus den Shared-Kandidaten ausgeschlossen, weil DE-Werte nicht exakt übereinstimmen. Alternativ wäre shared mit überschriebenem Text möglich — aber das würde die Windows-Semantik verfälschen.

2. **apply_location → EZ_AI_APPLY:** Der Windows-Key `EZ_AI_APPLY` hat `EZ_AI`-Kontext (Editor-Wizard). In ONE ist `apply_location` der "Übernehmen"-Button bei GPS-Standort-Übernahme. DE-Match ist exakt → scope=shared akzeptiert. Bei semantischem Bedarf kann in Phase 2 ein ONE-spezifischer Key angelegt werden.

3. **camera_password → SETTINGS_HW_PASSWORD:** Windows-Key ist hardware-settings-spezifisch, ONE nutzt ihn für RTSP-Kamera-Passwort. Gleicher DE-Wert, gleicher funktionaler Kontext (Hardware-Credential) → scope=shared akzeptiert.

4. **scan → WIFI_SCAN:** Kontext-Unterschied dokumentiert (Soft-Conflict). EN-Mismatch ("Scanning" vs. vermutlich "Scan") wird in Phase 5 beim Bundle-Build geprüft.

5. **Unique Windows-Keys: 69 statt 78 Shared-Keys:** 9 ONE-Keys teilen sich Windows-Keys mit anderen ONE-Keys (z.B. 3 ONE-Keys → `Form.Tab.Inspection`). Das ist korrekt — gleiche Strings in verschiedenen UI-Kontexten werden vom gleichen Key bedient.

6. **Entfernung des `.keep`-Files:** `phase1/.keep` wurde als Bootstrap-Datei angelegt und wird im Commit durch die echten Dateien ersetzt.

---

## Bekannte Issues / Offene Punkte

- **EN-Werte für 29 Keys leer** (aus Phase 0 bekannt): Keys wie `address_not_found`, `apply_location`, `hardware_osd`, `pick_on_map`, `search_address`, `tap_to_set_marker`, und alle `update_*`-Keys haben leere EN-Strings. Diese werden im Portal via DeepL gefüllt (Phase 4) oder manuell in Phase 7.
- **Soft-Conflict `not_reachable`:** Windows-EN-Wert für `WELCOME_PORTAL_OFFLINE` nicht verfügbar. Cutover-Check in Phase 7 obligatorisch.
- **Soft-Conflict `scan`:** EN-Wert ("Scanning" vs. erwartetes "Scan") in Phase 5 klären.
- **OfflineMapsScreen 12 Keys:** Diese hardcodierten Strings aus Phase 0 haben noch keine Keys im Mapping (da sie auch keine OLD keys hatten). Werden als neue ONE-Keys in Phase 6 beim Hardcoded-Audit angelegt.

---

*Nächste Phase:* **Phase 2 — Portal-Backend**  
*Branch:* `feature/l10n-phase-2-backend`  
*Repo:* `C:\Projekte\Drainq\Drainq_Suite_repo`  
*Eingabe:* `phase1/key_mapping.csv` + `phase0/keys_de_en.json`

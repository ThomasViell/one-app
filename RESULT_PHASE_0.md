# RESULT_PHASE_0 — Inventur, Key-Extraktion DE+EN, ADR

**Phase:** 0 — Inventur & ADR  
**Branch:** `feature/l10n-phase-0-inventur`  
**Datum:** 2026-05-20  
**Modell:** claude-sonnet-4-6 (think harder, GodMode)

---

## Pflicht-Marker

```
PHASE0-KEYS: 355
PHASE0-SHARED-CANDIDATES: 80
PHASE0-HARDCODED: 19
```

---

## Was wurde geliefert

### 1. `phase0/keys_de_en.json` — Key-Extraktion DE + EN

- **355 Keys** aus `LocalizationManager.kt` extrahiert (DE-Map als Basis)
- DE-Map: 355 Keys vollständig
- EN-Map: 326 Keys (29 Keys ohne EN-Übersetzung, davon 22 `update_*`-Keys die in Phase 2 im Portal angelegt wurden aber noch kein EN haben)
- Format: `{ "snake_key": { "de": "...", "en": "..." } }`
- Keys mit leerem EN-Wert: vorrangig `update_*`, `address_*`, `pick_on_map`, `tap_to_set_marker`
- Sprachen aktuell im Code: **35** (de, no, en, it, nl, fr, es, pt, pl, cs, sk, sl, hr, hu, ro, bg, el, da, sv, fi, et, lv, lt, ga, mt, ar, ru, tr, sr, sq, zh, ja, ko, id, th)

### 2. `phase0/hardcoded_audit_preview.md` — Hardcoded-String-Scan

- **19 Fundstellen** in 3 Dateien
- Hauptbetroffene Datei: `OfflineMapsScreen.kt` (12 Strings — dieses Screen hat keinen L10N-Key-Set)
- Weitere: `ProjectDetailScreen.kt` (5), `InspectionScreen.kt` (1), plus 2 Toast-Strings (manuell)
- `components/**`: keine Fundstellen (VideoPlayer, OsdOverlay, etc. nutzen keine User-Strings)
- Vollständige Behebung in Phase 6

### 3. `phase0/shared_candidates.json` — Windows-Abgleich

- **80 Shared-Kandidaten** identifiziert via exaktem DE-Wert-Vergleich mit `de-DE.json` (Windows Suite)
- Beispiele:
  - `close` → `BTN_CLOSE` ("Schließen")
  - `delete` → `BTN_DELETE` ("Löschen")
  - `button_ok` → `BTN_OK` ("OK")
  - `back` → `Common.Back` ("Zurück")
  - `edit` → `EZ_AI_EDIT` ("Bearbeiten")
  - `app_version` → `ABOUT_VERSION` ("Version")
  - `damage_type_deposit` → `LinearDmg.BDA` ("Ablagerung")
- Finale Validierung + Scope-Entscheidung in Phase 1 (manche Matches sind heuristisch, z.B. `edit_preset` → `EZ_AI_EDIT`)

### 4. `docs/adr/0010-l10n-portal-as-sot.md` — ADR

4 Kern-Entscheidungen dokumentiert:

| # | Entscheidung |
|---|---|
| 1 | Key-Format: `UPPER_SNAKE_CASE` (Windows-kompatibel, gemeinsamer Pool) |
| 2 | Bundle: nur DE+EN im APK, Lazy Download für weitere Sprachen |
| 3 | Übersetzung: DeepL Auto-Fill + Partner-Review ≥ 95 % vor Aktivierung |
| 4 | Portal: eigener Tab „DrainQ.ONE" + neue Partner-Entity pro Land |

---

## Mengengerüst

| Kennzahl | Wert |
|---|---|
| Keys in LocalizationManager.kt (DE) | 355 |
| Keys mit EN-Übersetzung | 326 |
| Keys ohne EN (nur DE) | 29 |
| Sprachen aktuell im APK | 35 |
| Sprachen nach Migration (Bundle) | 2 (DE + EN) |
| Sprachen nach Migration (Portal, aktivierbar) | 33 (on-demand) |
| Shared-Kandidaten (Windows-Abgleich, heuristisch) | 80 |
| Davon sicher shared (generische Begriffe) | ~40 (Phase 1 entscheidet final) |
| ONE-spezifische Keys (Schätzung) | ~275–315 |
| Hardcoded Strings (automated) | 17 |
| Hardcoded Strings (manuell Toast) | 2 |
| **Hardcoded gesamt** | **19** |
| Betroffene Dateien (Hardcoded) | 3 |

---

## APK-Größen-Abschätzung

| Posten | Vorher | Nachher |
|---|---|---|
| LocalizationManager.kt (kompiliert) | ~10.000 Zeilen → ~800 kB dex | ~200 Zeilen → ~20 kB dex |
| Sprach-Bundle | 35 × ~30 kB ≈ 1.050 kB | 2 × ~30 kB ≈ 60 kB |
| **Ersparnis gesamt (grob)** | — | **~1,8 MB** |

---

## Pragmatische Entscheidungen

1. **29 Keys ohne EN-Übersetzung:** Die `update_*`-Keys (22) und einige Mapping-Keys haben keine EN-Übersetzung in der KT-Datei. Im JSON werden sie mit leerem EN-String hinterlegt. Phase 1 ergänzt EN-Werte via DeepL-Vorschlag oder manuell.

2. **Heuristische Shared-Kandidaten:** 80 Kandidaten wurden via exaktem DE-Wert-Match identifiziert. Einige Matches sind semantisch fragwürdig (z.B. `edit_preset` → `EZ_AI_EDIT` — gleicher DE-Text, unterschiedlicher Kontext). Phase 1 trifft die finale scope-Entscheidung.

3. **OfflineMapsScreen nicht in L10N-Keys:** Dieser Screen wurde nach dem initialen L10N-Key-Set hinzugefügt und hat 12 hardcodierte Strings ohne korrespondierende Keys. In Phase 1 werden neue Keys angelegt.

4. **Toast-Strings mit Interpolation:** 2 Toast-Strings in `ProjectDetailScreen.kt` (Zeilen 83, 92) haben `$`-Interpolation und wurden vom automatischen Scan nicht erfasst. Manuell ergänzt. Behebung in Phase 6.

5. **SettingsScreen-Snackbar:** `showSnackbar(message = savedMessage)` — `savedMessage` kommt aus ViewModel via `t()`-Call, ist nicht hardcoded.

---

## Verworfenes / Offenes

- **Verworfen:** Alle 33 nicht-DE/EN Sprachen aus `LocalizationManager.kt` (no, it, nl, fr, es, pt, pl, cs, sk, sl, hr, hu, ro, bg, el, da, sv, fi, et, lv, lt, ga, mt, ar, ru, tr, sr, sq, zh, ja, ko, id, th) — gemäß Konzept v1.1 werden nur DE + EN als Bundle behalten
- **Offen:** EN-Werte für 29 Keys ohne EN-Übersetzung (folgt Phase 1)
- **Offen:** Finale Scope-Entscheidung für 80 Shared-Kandidaten (Phase 1)
- **Offen:** Neue Keys für OfflineMapsScreen (12 Strings ohne Key, Phase 1)
- **Offen:** Autorun-Skript `autorun_l10n.ps1` (nicht Teil von Phase 0)

---

## Branch + Commit-Hashes

Branch: `feature/l10n-phase-0-inventur`

> Commit-Hashes werden nach dem finalen Commit hier eingetragen.

---

## Build- und Testergebnis

Phase 0 ist rein dokumentarisch — kein Code wurde geändert, keine Build-Verifikation erforderlich.  
Nächste Build-Verifikation: Phase 1 (Key-Mapping-Skript) oder Phase 5 (App-Refactor).

---

## KRITIS-Check-Status

| Prüfpunkt | Status |
|---|---|
| Keine Secrets in Extrakt-Dateien | ✅ `keys_de_en.json` enthält nur UI-Strings |
| Keine hardcodierten Farben/Werte in neuen Dateien | ✅ Nur Markdown/JSON, kein Code |
| ADR dokumentiert KRITIS-Anforderungen | ✅ Audit-Log, Partner-Auth, DeepL-Key in Secrets |
| Übergabe-Format sauber (RESULT für Phase 1) | ✅ |

---

*Nächste Phase:* **Phase 1 — Key-Mapping snake → UPPER_SNAKE + Shared-Detection**  
*Branch:* `feature/l10n-phase-1-mapping`  
*Eingabe:* `phase0/keys_de_en.json` + `phase0/shared_candidates.json`

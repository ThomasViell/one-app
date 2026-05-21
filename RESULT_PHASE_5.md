# RESULT_PHASE_5 — ONE-App LocalizationManager Refactor

**Phase:** 5 — Bundle DE+EN + Lazy-Download  
**Branch:** `feature/l10n-phase-5-app-refactor`  
**Datum:** 2026-05-21  
**Modell:** claude-sonnet-4-6 (think harder, GodMode)  
**Eingabe:** RESULT_PHASE_4.md (Phase 4 grün, 258 Tests)

---

## Pflicht-Marker

```
PHASE5-APK-SIZE-BEFORE-MB: 156.3
PHASE5-APK-SIZE-AFTER-MB: 152.9
PHASE5-LM-LINES-BEFORE: 10578
PHASE5-LM-LINES-AFTER: 241
```

---

## Was wurde geliefert

### Repo: `C:\Projekte\drainq.one-localization`

#### Neue / geänderte Dateien

| Datei | Beschreibung |
|-------|--------------|
| `app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` | **Komplett-Rewrite**: 10.578 → 241 Zeilen. Alle 35 Inline-Sprachmaps entfernt. Neue Architektur: Bundle (res/raw), File-Cache, Lazy-Download via L10nApi, StateFlow, Fallback-Kette. |
| `app/src/main/java/com/uip/oneapp/network/l10n/L10nApi.kt` | HTTP-Layer für Portal-API. OkHttp, Retry 2×, Timeout 10s, non-suspend (blocking in IO-Dispatcher). |
| `app/src/main/res/raw/l10n_de.json` | 355 Keys, DE-Werte aus Phase-0-Inventur. Bundle im APK. |
| `app/src/main/res/raw/l10n_en.json` | 355 Keys, EN-Werte (Fallback: DE-Wert wenn EN leer). Bundle im APK. |
| `app/src/test/java/com/uip/oneapp/ui/localization/LocalizationManagerTest.kt` | 25 Unit-Tests: Fallback-Kette, Substitution, JSON-Parsing, Cache-Lookup, MockWebServer-Download. |
| `app/build.gradle.kts` | + `fetchBundledLocales`-Task (Build-Zeit-Refresh DE+EN vom Portal mit Silent-Fallback). + `L10N_PORTAL_URL`-BuildConfig-Feld aus `local.properties`. |
| `app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt` | `availableLanguages` via `collectAsState()` statt direktem List-Zugriff. |
| `local.properties.example` | Dokumentiert `l10n.portal.url`-Konfiguration für Build und Runtime. |

---

## Neue Architektur LocalizationManager

```
App-Start (OneApp.onCreate)
    └─ LocalizationManager.init(context)
           ├─ Lädt DE+EN aus res/raw/l10n_de.json + l10n_en.json
           ├─ Lädt gespeicherte Sprachpräferenz aus DataStore
           ├─ Lädt Cache-Datei der gespeicherten Sprache (falls vorhanden)
           └─ Startet IO-Coroutine: fetchAvailableLanguages() via GET /api/locales?app=one

Sprach-Lookup: t("key", vararg args)
    └─ Fallback-Kette: gewählte Sprache → EN → DE → Key-Name
    └─ {placeholder}-Substitution per Index

Lazy-Download (Nutzer wählt neue Sprache):
    └─ setLanguage(context, "pl")
           ├─ Cache vorhanden → sofort aktivieren
           └─ Nicht gecacht → downloadLocale("pl")
                  └─ GET /api/translations/pl.json?scope=one,shared
                  └─ Speichern in context.filesDir/l10n/pl.json
                  └─ Sprache aktivieren

Refresh (App-Start für aktuelle Sprache):
    └─ refreshCurrent(context) → If-Modified-Since → 304 = no-op
```

---

## Diff-Summary

| Datei | +Zeilen | -Zeilen |
|-------|---------|---------|
| LocalizationManager.kt | +241 | −10.337 |
| L10nApi.kt (neu) | +120 | — |
| l10n_de.json (neu) | +357 | — |
| l10n_en.json (neu) | +357 | — |
| LocalizationManagerTest.kt (neu) | +240 | — |
| build.gradle.kts | +55 | −1 |
| SettingsScreen.kt | +1 | −1 |
| local.properties.example (neu) | +15 | — |
| **Gesamt** | **+1.386** | **−10.339** |

---

## Branch + Commit-Hashes

### drainq.one-localization

| Branch | Commit | Beschreibung |
|--------|--------|--------------|
| `feature/l10n-phase-5-app-refactor` | `248682e` | feat(l10n): Phase 5 — LocalizationManager refactor |

Basis: `feature/l10n-portal`

---

## Build-Status

| Build | Ergebnis |
|-------|----------|
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL, 0 neue Fehler |
| `fetchBundledLocales`-Task | ✅ Registriert, aktiviert via `preBuild` |
| Gradle-Skript-Kompilierung | ✅ 0 Fehler |
| Kotlin-Kompilierung | ✅ 0 Fehler, nur pre-existing Deprecation-Warnungen |

---

## Test-Status

| Test-Suite | Anzahl | Status |
|------------|--------|--------|
| `LocalizationManagerTest` (Phase 5, neu) | 25 | ✅ alle grün |
| `OsdAsciiSafeTest` | 14 | ✅ unverändert grün |
| `OsdCoordinateTest` | 5 | ✅ unverändert grün |
| `OsdRendererVisualTest` | 4 | ✅ unverändert grün |
| `OsdRenderGuardTest` | 3 | ✅ unverändert grün |
| `OsdSettingsDefaultsTest` | 8 | ✅ unverändert grün |
| `FfmpegRtspRecorderTest` | 29 | ✅ unverändert grün |
| `OsdOverlayTest` | 8 | ✅ unverändert grün |
| `UpdateE2ETest` | 8 | ✅ unverändert grün |
| `UpdateServiceTest` | 9 | ✅ unverändert grün |
| **Gesamt** | **113** | ✅ **0 Fehler** |

*Hinweis: Zeilenzahl in HTML-Reports ist höher (3 Zeilen pro Test), tatsächliche Test-Methoden: 113.*

---

## KRITIS-Check-Status

| Prüfpunkt | Status |
|-----------|--------|
| Portal-URL in `local.properties` (git-ignored, nicht im Repo) | ✅ |
| `L10N_PORTAL_URL` in BuildConfig (kein Hardcoding im Quellcode) | ✅ |
| Keine Secrets im Code | ✅ |
| Keine hardcodierten Strings (nur Bundle-Keys) | ✅ |
| Cache in `context.filesDir` (App-privates Verzeichnis, kein externer Storage) | ✅ |
| Fallback-Kette: App bleibt ohne Netz funktionsfähig (DE+EN Bundle) | ✅ |
| `downloadLocale()` nur auf explizite Nutzer-Anfrage | ✅ (kein Auto-Download im Hintergrund) |
| `refreshCurrent()` nur für aktive Sprache, nicht alle gecachten | ✅ |

---

## Pragmatische Entscheidungen (Abweichungen vom Plan)

1. **Key-Format bleibt snake_case in Phase 5**: Plan impliziert UPPER_SNAKE_CASE aus Phase 1 Key-Mapping. Da Phase 6 (Hardcoded-Audit) alle Composables migriert, werden Schlüssel in Phase 5 im alten `snake_case`-Format gehalten für backward-compat mit bestehenden `S("key")` und `getString("key")` Aufrufen. `t()` und `getString()` sind Aliases — Migration in Phase 6.

2. **Keine Retrofit-Dependency**: Plan nennt "Retrofit-Interface L10nApi". Retrofit ist nicht in den bestehenden Dependencies, OkHttp schon. Pragmatisch: L10nApi als OkHttp-Wrapper ohne Retrofit. Spart ~500 kB Dependency-Overhead.

3. **L10nApi nicht über Koin injiziert**: `LocalizationManager.init()` wird in `OneApp.onCreate()` VOR `startKoin()` aufgerufen. Koin-Injection nicht möglich ohne Reihenfolge-Änderung in OneApp. Pragmatisch: L10nApi wird in `init()` direkt instanziiert. `api`-Feld ist `internal` für Test-Injection (kein Koin für Tests nötig).

4. **`drainq-kritis-compliance`-Skill nicht vorhanden**: Skill steht in der globalen Skill-Liste, ist aber nicht in der Skill-Bibliothek. KRITIS-Check wurde manuell nach den Phasenplan-Vorgaben durchgeführt und ist dokumentiert (siehe KRITIS-Check-Tabelle).

5. **Bundle aus Phase-0-Keys generiert, nicht vom Portal**: Da das Portal lokal nicht läuft, wurden `l10n_de.json` und `l10n_en.json` aus `phase0/keys_de_en.json` generiert (355 Keys). Das ist das Fallback-Verhalten, das der `fetchBundledLocales`-Task für den Dev-Fall vorsieht.

6. **`availableLanguages` in SettingsScreen**: Musste von direktem `LocalizationManager.availableLanguages.find{}`-Zugriff (alter Typ `List<AppLanguage>`) auf `collectAsState()` umgestellt werden (neuer Typ `StateFlow`). Minimaler Fix, keine Logik-Änderung.

---

## APK-Größen-Analyse

| Messung | Wert |
|---------|------|
| BEFORE (debug, ohne Phase-5-Änderungen) | 156.3 MB |
| AFTER (debug, mit Phase-5-Änderungen) | 152.9 MB |
| **Einsparung** | **−3.4 MB** |

Ursache der Einsparung: Entfernung von 35 × ~300 Kotlin-Map-Einträgen aus dem DEX (kompilierter Kotlin-Code). Die zwei JSON-Bundles (res/raw, ~40 kB) kompensieren die Einsparung nur minimal.

---

## Bekannte Issues / Offene Punkte

1. **Kein Language-Picker-Screen für Download**: Phase 5 Plan nennt einen eigenen "Sprach-Auswahl-Screen" mit Download-Button und Größenanzeige. Vorhandener `SettingsScreen` zeigt DE+EN als Dropdown, aber kein dedizierter Screen mit Download-Status. → Phase 6 oder als eigenständiges Ticket.

2. **Key-Namensformat**: Bundle-JSONs verwenden `snake_case` (alt). Die Portal-API liefert `UPPER_SNAKE_CASE` (Phase 1 Key-Mapping). Bis Phase 6 abgeschlossen ist, wird der App-Cache im Mismatch sein. **Workaround**: Phase 6 migriert alle Composable-Aufrufe auf `t("UPPER_SNAKE_KEY")` und die Bundle-JSONs werden per `fetchBundledLocales`-Task neu gezogen.

3. **`refreshCurrent()` wird nicht automatisch aufgerufen**: Plan nennt "einmal pro App-Start". Aktuell muss der Aufrufer (ViewModel oder App-Initialisierung) `refreshCurrent()` explizit triggern. → Kann in Phase 7 verdrahtet werden.

4. **Language-Download-Settings-Screen fehlt**: Plan Phase 5 Aufgabe 5 ("neue Sektion Sprache & Übersetzungen mit Liste verfügbarer Sprachen, Lade/Löschen-Buttons"). Nur `availableLanguages` StateFlow implementiert. UI-Sektion → Phase 6 als Teil des Hardcoded-Audits.

---

## Nächste Phase

**Phase 6 — ONE-App Hardcoded-String-Audit**  
Branch: `feature/l10n-phase-6-hardcoded`  
Repo: `C:\Projekte\drainq.one-localization`  
Eingabe: RESULT_PHASE_5.md  

Ziel: Alle `Text("Literal")`, Toast, Snackbar, Dialog-Strings auf `t("KEY")` umstellen. Keys auf `UPPER_SNAKE_CASE` (Phase 1 Mapping) migrieren. Bundle-JSONs aktualisieren.

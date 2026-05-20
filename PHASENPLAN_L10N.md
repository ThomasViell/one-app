# Phasenplan — L10N zentral via DrainQ.Web Portal

**Version:** 1.0 · 2026-05-12
**Zugehöriges Konzept:** `docs/concepts/L10N_PORTAL_KONZEPT.md` (v1.1)
**Modus:** GodMode + Autorun (PowerShell)
**Repos:** `C:\Projekte\drainq.one` (App) und `C:\Projekte\Drainq\Drainq_Suite_repo` (DrainQ.Web Portal)

---

## 0. Globale Regeln (gelten für ALLE Phasen)

**GodMode aktiv** — der Skill `godmode` MUSS pro Phase als erstes konsultiert werden. Daraus folgt:
- Keine Rückfragen. Pragmatischste Entscheidung treffen, im RESULT dokumentieren.
- Keine Approval-Pausen. Direkter Flow lesen → planen → bauen → testen → commit.
- Tempdatei-Pattern beachten bei Shell-Befehlen > 600 Zeichen.

**Skills, die pro Phase konsultiert werden müssen:**
- `godmode` — Autonomie-Regeln
- `drainq-kritis-compliance` — KRITIS/NIS2/ISO27001-Check (immer)
- Phase-spezifische Skills (siehe je Phase)

**Verbindlich pro Phase:**
- Eigenen Branch anlegen: `feature/l10n-phase-<N>-<short>` aus `main`/`master`
- Conventional Commits
- Build muss am Phasenende grün sein (App: `./gradlew assembleDebug`, Web: `dotnet build`)
- Tests müssen grün sein
- Keine hardcoded Strings/Farben/Secrets
- Am Ende `RESULT_PHASE_<N>.md` im `C:\Projekte\drainq.one`-Root mit:
  - Was wurde geliefert
  - Pragmatische Entscheidungen
  - Verworfenes / Offenes
  - Branch + Commit-Hashes (beide Repos, falls betroffen)
  - Build- und Testergebnis
  - KRITIS-Check-Status

**Übergabe zwischen Phasen:** ausschließlich über `RESULT_PHASE_<N-1>.md`. Vor Start jeder Phase liest Claude die Vorgänger-RESULT-Datei.

**Hartes Fail-Fast im Autorun-Skript:**
- Exit-Code ≠ 0 → Abbruch
- `RESULT_PHASE_<N>.md` nicht erzeugt → Abbruch
- Build oder Tests rot am Phasenende → RESULT muss das explizit ausweisen, Autorun bricht ab

---

## Phase-Matrix

| Phase | Titel | Modell | Effort | Repo(s) | Skills (zusätzlich) |
|---|---|---|---|---|---|
| **0** | Inventur, Key-Extraktion DE+EN, ADR (MANUELL) | sonnet | think harder | drainq.one | — |
| 1 | Key-Mapping snake → UPPER_SNAKE + Shared-Detection | sonnet | think | drainq.one | — |
| 2 | Portal-Backend: Locale-Status, Scope, Partner-Entity, API | sonnet | think harder | Drainq_Suite_repo | engineering:system-design, engineering:testing-strategy |
| 3 | Portal-Frontend: Tab DrainQ.ONE + Sub-Tab „Sprachen & Partner" | sonnet | think | Drainq_Suite_repo | design:design-system, design:ux-copy |
| 4 | DeepL-Service + Glossar + Partner-Review-Workflow | sonnet | think | Drainq_Suite_repo | — |
| 5 | ONE-App: LocalizationManager neu (Bundle DE+EN, Lazy-Download) | sonnet | think harder | drainq.one | engineering:debug |
| 6 | ONE-App: Hardcoded-Audit + alle Composables auf `t("KEY")` | sonnet | think | drainq.one | — |
| 7 | Cutover: 33 Sprachen löschen, DE/EN importieren, Smoke-Tests, Doku, Release-Notes v0.4.0 | haiku | — | beide | — |

---

## Phase 0 — Inventur & ADR (MANUELL vor Autorun)

**Modell:** sonnet, Effort: think harder
**Repo:** drainq.one
**Branch:** `feature/l10n-phase-0-inventur`

**Aufgaben:**
1. `app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt` parsen.
2. NUR die DE- und EN-Maps extrahieren als `phase0/keys_de_en.json` (Format `{ "snake_key": { "de": "...", "en": "..." } }`).
3. Mengengerüst dokumentieren: Anzahl Keys, Anzahl Sprachen heute (Soll vs. Ist).
4. Hardcoded-String-Scan: alle `Text(...)`-Literale, Toast/Snackbar-Texte, Dialog-Strings in `app/src/main/java/com/uip/oneapp/ui/screens/**` und `**/components/**`. Liste mit Datei+Zeile als `phase0/hardcoded_audit_preview.md` (volle Behebung erst in Phase 6).
5. Abgleich mit Windows-Keys aus `C:\Projekte\Drainq\Drainq_Suite_repo\src\DrainQ.Core\Resources\Localization\de-DE.json`. Heuristik: gleicher oder fast-gleicher DE-Wert → Kandidat für Shared-Key.
6. ADR schreiben: `docs/adr/0010-l10n-portal-as-sot.md` mit den 4 Kern-Entscheidungen aus dem Konzept.
7. `RESULT_PHASE_0.md` schreiben.

**Definition of Done:**
- `phase0/keys_de_en.json` mit n ≥ 250 Einträgen vorhanden
- `phase0/hardcoded_audit_preview.md` mit allen Fundstellen vorhanden
- ADR `0010-l10n-portal-as-sot.md` mit Entscheidungstabelle
- `RESULT_PHASE_0.md` enthält Mengengerüst + Shared-Kandidaten-Statistik

**Marker im RESULT_PHASE_0.md (Pflicht, einzeilig):**
```
PHASE0-KEYS: <Anzahl>
PHASE0-SHARED-CANDIDATES: <Anzahl>
PHASE0-HARDCODED: <Anzahl Fundstellen>
```

---

## Phase 1 — Key-Mapping

**Modell:** sonnet, Effort: think
**Repo:** drainq.one
**Branch:** `feature/l10n-phase-1-mapping`

**Aufgaben:**
1. `phase0/keys_de_en.json` einlesen.
2. Für jeden Key: neuer UPPER_SNAKE_CASE-Key vorschlagen.
3. Shared-Detection final entscheiden (Vergleich mit Windows-de-DE.json):
   - Exakt gleicher DE-Wert ODER semantisch identisch → `scope=shared`, übernimm vorhandenen Windows-Key
   - sonst → `scope=one`, neuer Key
4. Output: `phase1/key_mapping.csv` mit Spalten `old_key, new_key, scope, source_de, source_en, windows_key_match_or_empty`
5. Konflikte (gleicher DE-Wert, aber unterschiedliche EN-Übersetzung) explizit in `phase1/conflicts.md` listen.
6. Validierung: alle alten Keys haben genau ein Mapping. Test-Skript in `phase1/validate_mapping.ps1`.

**Definition of Done:**
- `phase1/key_mapping.csv` vollständig
- Validate-Skript läuft fehlerfrei
- `RESULT_PHASE_1.md` enthält Aufteilung shared vs. one-spezifisch

---

## Phase 2 — Portal-Backend

**Modell:** sonnet, Effort: think harder
**Repo:** Drainq_Suite_repo
**Branch:** `feature/l10n-phase-2-backend`
**Zusätzliche Skills:** `engineering:system-design`, `engineering:testing-strategy`

**Aufgaben:**
1. Datenmodell erweitern:
   - `Locale`-Tabelle: Felder `code`, `displayName`, `status` (enum `core | active | pending | inactive`), `partnerId?`, `bytesSize`
   - `Partner`-Tabelle: `id`, `name`, `country`, `contactEmail`, `role` (Translator/Reviewer), Login-Account-FK
   - `Key`-Tabelle: `scopes string[]` (Postgres array oder Join-Tabelle, je nach Provider)
   - `Translation`-Tabelle: `keyId`, `localeCode`, `value`, `status` (empty/machine/reviewed/locked), `updatedAt`, `updatedBy`
2. EF-Migration anlegen, SQLite + PostgreSQL kompatibel.
3. API-Endpoints:
   - `GET /api/locales?app=one` → nur `core` + `active`, inkl. Größe
   - `GET /api/translations/{locale}.json?scope=one,shared` (bestehender Endpoint erweitern um Scope-Filter)
   - `POST /api/admin/translations/import` (Bulk-Import aus key_mapping.csv)
   - `POST /api/admin/locales/{code}/activate`
   - `POST /api/admin/partners` (CRUD)
4. ETag/`Last-Modified` korrekt setzen (304 wenn unverändert).
5. Bulk-Import-Endpoint nimmt das `key_mapping.csv` aus Phase 1 + `keys_de_en.json` aus Phase 0 entgegen.
6. Unit-Tests (xUnit) für jeden Endpoint, mindestens Happy-Path + Scope-Filter + 304-Pfad + Partner-Scope-Auth.
7. KRITIS: Admin-Schreibseite hinter Auth, Audit-Log bei jeder Änderung.

**Definition of Done:**
- `dotnet build` 0 errors, 0 warnings
- `dotnet test` grün
- Migration läuft auf beiden Providern (SQLite-Test im CI vorhanden)
- Endpoints in `docs/api/l10n-portal.md` dokumentiert
- `RESULT_PHASE_2.md` mit cURL-Beispielen + Bytes-Größe je Locale nach Import

---

## Phase 3 — Portal-Frontend

**Modell:** sonnet, Effort: think
**Repo:** Drainq_Suite_repo
**Branch:** `feature/l10n-phase-3-frontend`
**Zusätzliche Skills:** `design:design-system`, `design:ux-copy`

**Aufgaben:**
1. Neuer Tab in der Admin-UI „DrainQ.ONE" (Pfad `/admin/translations/one`).
2. Liste aller ONE-Keys mit:
   - Spalten: Key · Quelle DE · Ziel (Sprachwähler) · Status · Letzte Änderung · Shared-Indikator
   - Filter: nicht übersetzt / machine / reviewed / fehlend
   - Inline-Edit + Bulk-Aktionen (DeepL füllen, als reviewed markieren, exportieren)
3. Sub-Tab „Sprachen & Partner":
   - Tabelle: Code · Name · Status · Partner · Fortschritt % · Bytes · Aktionen (aktivieren/deaktivieren)
   - Partner-CRUD-Dialog
4. Partner-Login-Sicht (separate Rolle, Routing-Guard): sieht nur seine Sprache(n) + ONE/Shared-Keys, kann editieren + reviewen.
5. „Update-Vorschau": Modal zeigt, was die App beim nächsten Fetch je Locale erhalten würde.
6. Design Tokens und Component-Library aus bestehendem Portal wiederverwenden — KEINE neuen Farben/Schriftgrößen hardcoden.

**Definition of Done:**
- UI navigierbar, alle CRUD-Pfade funktionieren gegen Phase-2-Backend
- E2E-Test (Playwright/bUnit, je nach bestehendem Setup) für mindestens einen Happy-Path
- `RESULT_PHASE_3.md` mit Screenshots-Pfaden

---

## Phase 4 — DeepL + Partner-Review

**Modell:** sonnet, Effort: think
**Repo:** Drainq_Suite_repo
**Branch:** `feature/l10n-phase-4-deepl`

**Aufgaben:**
1. `L10nDeeplService` (DI-registriert).
2. DeepL-API-Key über Secrets/Env, NIE im Code (KRITIS).
3. Glossar-Seed: `data/seed/l10n_glossary.json` mit ~50 Fachbegriffen (Haltung, Schacht, Sohle, Sanierung, ZK, Zustandsklasse, Inspektion, Kanal, Rohrleitung, …) — Vorschlag im Code, finale Liste über Web-UI editierbar.
4. Trigger:
   - Partner für Sprache benannt → Locale-Status `pending` → DeepL füllt automatisch
   - Manueller Button im Frontend „DeepL-Pass starten" pro Locale
5. Status-Workflow Translation: `empty` → `machine` → `reviewed` → `locked`.
6. Locale-Aktivierungs-Schwelle: konfigurierbar (Default 95 % reviewed → Status `active` möglich).
7. Background-Job/Hangfire (oder vorhandener Scheduler), Rate-Limit, Cost-Tracking pro Aufruf.
8. Audit-Log: jede DeepL-Übersetzung, jede Review-Aktion mit User+Timestamp.
9. Tests: Mock-DeepL-Client, Glossar-Anwendung, Schwellen-Check.

**Definition of Done:**
- DeepL-Mock-Tests grün
- Manuell mit echtem Key (in Phase 7) verifizierbar — Tests laufen ohne API-Key
- `RESULT_PHASE_4.md` mit Beispiel-Audit-Log-Einträgen

---

## Phase 5 — ONE-App LocalizationManager Refactor

**Modell:** sonnet, Effort: think harder
**Repo:** drainq.one
**Branch:** `feature/l10n-phase-5-app-refactor`
**Zusätzliche Skills:** `engineering:debug`

**Aufgaben:**
1. Komplette Map aus `LocalizationManager.kt` raus (10k Zeilen weg).
2. Build-Step in `app/build.gradle.kts`:
   - Neuer Gradle-Task `fetchBundledLocales` zieht vor `assembleDebug`/`assembleRelease` `de.json` und `en.json` vom Portal nach `app/src/main/res/raw/l10n_de.json` und `l10n_en.json`.
   - Portal-URL konfigurierbar via `local.properties` (default Production-URL).
   - Fallback: wenn Portal nicht erreichbar, letzte gecachte Bundle-Version verwenden (Warning, kein Build-Fail im Dev).
3. Neue Klasse `LocalizationManager`:
   - `init(context)` lädt zuletzt gewählte Sprache aus DataStore
   - Bundle-Fallback aus `res/raw/l10n_<locale>.json` (nur DE+EN)
   - Cache-Verzeichnis `context.filesDir/l10n/<locale>.json` für nachgeladene Sprachen
   - `availableLanguages` ist `StateFlow<List<AppLanguage>>` und wird aus `/api/locales?app=one` befüllt (mit Bundle-Fallback {DE,EN})
   - Methode `downloadLocale(code)` → Retrofit/OkHttp GET `/api/translations/{code}.json?scope=one,shared`, in Cache speichern
   - Methode `setLanguage(code)` → wenn Cache vorhanden, sofort aktivieren; sonst download → aktivieren
   - Methode `refreshCurrent()` → `If-Modified-Since` gegen aktuelle Sprache, 304 → no-op, 200 → Cache überschreiben
   - Methode `deleteLocale(code)` → löscht Cache-File
   - `t(key: String, vararg args: Any): String` mit Fallback-Kette: gewählt → EN → DE → Key
4. Netzwerk-Layer: Retrofit-Interface `L10nApi`, Koin-DI, Timeout 10 s, Retry 2×.
5. Settings-Screen: neue Sektion „Sprache & Übersetzungen" mit Liste verfügbarer Sprachen, Lade/Löschen-Buttons, „Update jetzt".
6. Sprach-Auswahl-Screen: zeigt Bundle-Sprachen + via API geladene Sprachen, Download-Button mit Größe.
7. Unit-Tests für Fallback-Kette, Cache-Lookup, Download-Flow (Mock-Server).
8. KRITIS-Check: keine Secrets in App, Portal-URL nicht hardcoded.

**Definition of Done:**
- `./gradlew assembleDebug` grün, 0 Warnings
- `./gradlew test` grün
- APK-Size-Vergleich: vorher vs. nachher dokumentieren
- `RESULT_PHASE_5.md` mit APK-Größe + Klassen-Zeilen-Vergleich

---

## Phase 6 — Hardcoded-String-Audit

**Modell:** sonnet, Effort: think
**Repo:** drainq.one
**Branch:** `feature/l10n-phase-6-hardcoded`

**Aufgaben:**
1. `phase0/hardcoded_audit_preview.md` als Startliste.
2. Plus weiterer Sweep über `app/src/main/java/com/uip/oneapp/**` mit Grep auf `Text("...")`, `stringResource(...)` (falls Android-Resourcen verwendet), `Toast.makeText`, `Snackbar`, `AlertDialog`.
3. Jeden Fund:
   - Key im Portal anlegen (via Bulk-API aus Phase 2) ODER bestehenden Key verwenden
   - Composable auf `t("KEY")` umstellen
4. Bericht analog Windows: `docs/LOCALIZATION_AUDIT_REPORT_ONE.md` mit Tabelle Datei/Zeile/alter Text/neuer Key/Status.
5. Build + Tests grün halten.

**Definition of Done:**
- Audit-Report mit allen Fundstellen, alle „Behoben"
- Keine `Text("…")` mehr mit Literalstring in App-Composables (technische Strings wie URLs ausgenommen)
- `RESULT_PHASE_6.md` mit Endzahlen

---

## Phase 7 — Cutover + Doku + Release-Notes

**Modell:** haiku
**Repo:** beide
**Branch:** `feature/l10n-phase-7-cutover`

**Aufgaben:**
1. Migrations-Skript einmalig auf Production-Portal: DE + EN importieren (aus `keys_de_en.json` + `key_mapping.csv`).
2. Alle Locales außer `de`, `en` initial auf `inactive` setzen.
3. Smoke-Tests durchführen + dokumentieren:
   - App mit deaktiviertem Netz → DE/EN Bundle greift
   - App mit Netz, Sprache PL als `active` markiert (Testdaten) → Download + Cache + Anzeige
   - PL nochmal auswählen → kein erneuter Download
   - Key fehlt in PL → Fallback EN → DE → Key
   - Inaktive Sprachen erscheinen nicht in der Auswahl
4. `CLAUDE.md` updaten (Phase 8: zentrale L10n + Lazy-Loading).
5. `docs/RELEASE_NOTES.md` Version 0.4.0 mit Liste der Änderungen + Migrations-Hinweis für Bestandsnutzer.
6. `MEMORY.md` (Auto-Memory) ggf. updaten.
7. Final-Merge in `main` beider Repos.

**Definition of Done:**
- Alle 7 Smoke-Tests dokumentiert mit Ergebnis OK/FAIL
- Release-Notes geschrieben
- `RESULT_PHASE_7.md` mit Migration-Log

---

## Autorun-Reihenfolge

```
Phase 0 [MANUELL]
    ↓ erzeugt RESULT_PHASE_0.md
Phase 1 [Autorun]
    ↓
Phase 2 [Autorun, Repo wechselt zu Suite]
    ↓
Phase 3 [Autorun, Suite]
    ↓
Phase 4 [Autorun, Suite]
    ↓
Phase 5 [Autorun, Repo wechselt zurück zu ONE]
    ↓
Phase 6 [Autorun, ONE]
    ↓
Phase 7 [Autorun, beide]
```

Autorun-Skript: `autorun_l10n.ps1` im `drainq.one`-Root.

Aufruf: `.\autorun_l10n.ps1 *>&1 | Tee-Object -FilePath autorun_l10n.log`

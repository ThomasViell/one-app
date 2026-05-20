# DrainQ.ONE — Zentrale Lokalisierung über DrainQ.Web

**Version:** 1.1 · Entwurf · 2026-05-12
**Status:** Konzept zur Abstimmung
**Ziel:** Webportal wird Single Source of Truth für alle Übersetzungen der DrainQ.ONE Tablet-App. Strings aus dem Code raus, Pflege im Portal, Sprachpakete on-demand in die App.

---

## 1. Entscheidungen (bereits getroffen)

| Thema | Entscheidung |
|---|---|
| Key-Konvention | `UPPER_SNAKE_CASE` — identisch zu DrainQ.Windows, gemeinsamer Pool |
| Bestehende 35 Übersetzungen in ONE | **Verworfen.** Komplett kippen. Nur **DE + EN** bleiben als Basissprachen im APK |
| Weitere Sprachen | Werden erst aktiviert, wenn ein **Partner pro Land** benannt ist. Übersetzung im Portal via DeepL + Partner-Review |
| Auslieferung an App | **On-Demand-Download** über WLAN. Sprachauswahl in der App zeigt nur aktivierte Länder, Paket wird beim Auswählen geladen und gecached |
| Übersetzungsprozess | DeepL Auto-Fill → Partner reviewed/korrigiert im Portal → Freigabe → App kann Paket ziehen |
| Portal-Integration | Eigener Reiter „DrainQ.ONE" neben Desktop / Web / Kataloge, gemeinsame Keys liegen im Shared-Bucket. **Partner-Verwaltung neu**: Partner pro Land mit Rolle „Translator" |

---

## 2. Architektur (Big Picture)

```
+----------------------+          +----------------------+
|  DrainQ.Web Portal   |          |   Partner-UI         |
|  (Single Source of   |<-------->|  (pro Land, DeepL,   |
|   Truth)             |          |   Review, Freigabe)  |
+----------+-----------+          +----------------------+
           |
           | 1. GET /api/locales              -> verfügbare Sprachen
           | 2. GET /api/translations/{locale}.json?scope=one,shared
           |    (nur on-demand, mit If-Modified-Since für Refresh)
           v
+----------------------+
|   DrainQ.ONE App     |
|  - Bundle: nur DE+EN |
|  - Cache pro Sprache |
|  - Lazy Download bei |
|    Sprach-Auswahl    |
+----------------------+
```

**Scopes im Portal:**
- `desktop` — DrainQ.Windows-spezifisch
- `web` — Webportal-spezifisch
- `one` — DrainQ.ONE-spezifisch (NEU)
- `shared` — App-übergreifend (BTN_SAVE, BTN_CANCEL, LABEL_ACTIVE, …)
- `catalog` — Kanalinspektionskataloge (separat, schon vorhanden)

**Locale-Status im Portal (NEU für ONE):**
- `core` — DE, EN: immer enthalten, im APK gebundlet
- `active` — Partner benannt, Übersetzung freigegeben → von App downloadbar
- `pending` — Partner benannt, Übersetzung läuft → nicht öffentlich
- `inactive` — kein Partner → wird im API-Endpoint `GET /api/locales` nicht gelistet

ONE-App bezieht beim Fetch nur `core ∪ active` Sprachen. Default-Auswahl ist DE.

---

## 3. Was ändert sich wo?

### 3.1 DrainQ.ONE (Android-App)

**Aktuell:**
- `LocalizationManager.kt` enthält ~300 Keys × 35 Sprachen als Kotlin-Map (≈10.000 Zeilen)
- Strings teils hardcoded in Composables
- Keys: `snake_case` (z. B. `stream_preview`)

**Zukünftig:**
- Bundle in der APK enthält **nur DE und EN** (`res/raw/l10n_de.json`, `l10n_en.json`)
- Sprach-Auswahl-Bildschirm zeigt:
  - DE und EN immer verfügbar
  - Weitere Sprachen werden zur Laufzeit von `GET /api/locales` geladen — nur Sprachen mit Status `core` oder `active` erscheinen
  - Neben jeder noch nicht heruntergeladenen Sprache: Button „Laden" (zeigt Größe in kB)
- Beim Auswählen einer noch nicht vorhandenen Sprache:
  1. Download `GET /api/translations/{locale}.json?scope=one,shared`
  2. Speichern in App-internem Cache (DataStore-File pro Sprache)
  3. Sprache umschalten
- Bereits heruntergeladene Sprachen: Sprachwechsel sofort, ohne Netz
- Periodischer Refresh nur für **aktuell ausgewählte** Sprache mit `If-Modified-Since` (z. B. einmal pro App-Start)
- Manuell: „Update jetzt" / „Sprache löschen" in Settings
- Fallback-Kette bei fehlendem Key: gewählte Sprache → EN → DE → Key-Name
- Alle Composables nutzen `LocalizationManager.t("KEY")` statt Literalstrings

**APK-Größenersparnis grob:** weg von 35 Sprachen × ~30 kB JSON ≈ 1 MB Bundle → nur DE+EN ≈ 60 kB. Plus Wegfall der riesigen Kotlin-Map in `LocalizationManager.kt` (10k Zeilen kompiliert).

### 3.2 DrainQ.Web Portal

**Backend:**
- DB-Schema erweitern:
  - `Key.scopes: string[]` (mehrere möglich, z. B. `["shared"]` oder `["one","desktop"]`)
  - `Locale.status` enum (`core | active | pending | inactive`) — steuert App-Sichtbarkeit
  - `Locale.partnerId` — verknüpft Sprache mit Partner-Unternehmen pro Land
  - `Partner` Entity: Name, Land, Kontakt, Rolle (Translator/Reviewer), Login-Account
- Endpoint vorhanden: `GET /api/translations/{locale}.json` — bekommt Query-Param `?scope=one,shared`
- Neu: `GET /api/locales?app=one` → Liste aller `core`/`active` Sprachen mit Größe in Bytes (für „Laden"-Button)
- 404 für `pending`/`inactive` Sprachen
- ETag/`Last-Modified` korrekt setzen (304 wenn unverändert)
- Admin-API: CRUD für Keys, Bulk-Import, DeepL-Auto-Fill, Locale-Aktivierung, Partner-Verwaltung

**Frontend (Übersetzungs-UI):**
- Neuer Tab „DrainQ.ONE" mit:
  - Liste aller ONE-Keys (Filter: nicht übersetzt / machine / reviewed / fehlend)
  - Spalten: Key · Quelle (DE) · Ziel (gewählte Sprache) · Status · Letzte Änderung
  - Bulk-Aktionen: DeepL füllen, als reviewed markieren, exportieren
  - „Shared-Pool"-Indikator: bei Klick auf einen geteilten Key wird angezeigt, in welchen Apps er genutzt wird
- Neuer Sub-Tab „Sprachen & Partner":
  - Tabelle: Locale · Status · Partner · Fortschritt (% reviewed) · Aktivieren/Deaktivieren
  - „Sprache aktivieren" Button: setzt Status auf `active`, App-Nutzer sehen sie ab dem nächsten Locale-Fetch
- Partner-Login: eingeschränkte Sicht, sieht nur seine Sprache(n) und ONE/Shared-Keys, kann editieren + reviewen
- Globale „Update-Vorschau": welche Keys werden beim nächsten App-Refresh ausgeliefert

### 3.3 DeepL-Pipeline (im Portal)

- Backend-Service `L10nDeeplService`
- Trigger: Partner für Land benannt → Locale auf `pending` → Auto-Übersetzen läuft NUR für aktivierte Sprachen
- DeepL API füllt aus DE-Quelle pro angeforderter Zielsprache
- Status pro Sprache: `empty` → `machine` → `reviewed` → `locked`
- Glossar/Termdatenbank für Fachbegriffe (Kanalinspektion, Schacht, Haltung, …) erzwingt korrekte Übersetzung
- Rate-Limit + Kostenkontrolle (DeepL Pro) — entfällt für nicht-aktivierte Sprachen → spart Geld
- Locale geht erst auf `active`, wenn Partner-Review ≥ 95 % der Keys abgeschlossen hat (Schwelle konfigurierbar)
- Bei neuen Keys nach Aktivierung: DeepL füllt nur die `active`/`pending` Sprachen, Partner bekommt Benachrichtigung „X neue Keys zur Review"

---

## 4. Phasenplan (autorun-kompatibel)

Aufbau wie das bewährte `autorun-phasenplan`-Pattern (Phasen-Skript ruft `claude -p` mit eigenem Context pro Phase, Übergabe via `RESULT_PHASE_N.md`).

| Phase | Titel | Modell | Manuell? | Output |
|---|---|---|---|---|
| **0** | Inventur, Key-Extraktion DE+EN, ADR | Sonnet | **Ja** (vor Autorun) | `RESULT_PHASE_0.md`, `keys_de_en.json`, ADR `0010-l10n-portal-as-sot.md` |
| 1 | Key-Mapping snake → UPPER_SNAKE, Shared-Erkennung gegen Windows | Sonnet | Auto | `key_mapping.csv`, `RESULT_PHASE_1.md` |
| 2 | Portal-Backend: Scope + Locale-Status + Partner-Entity + Bulk-Import (nur DE/EN) | Sonnet | Auto | Migration, API, Tests, `RESULT_PHASE_2.md` |
| 3 | Portal-Frontend: Tab „DrainQ.ONE" + Sub-Tab „Sprachen & Partner" | Sonnet | Auto | UI-Komponenten, `RESULT_PHASE_3.md` |
| 4 | DeepL-Service + Glossar + Partner-Review-Workflow + Locale-Aktivierungs-Schwelle | Sonnet | Auto | `L10nDeeplService`, Glossar-Seed, `RESULT_PHASE_4.md` |
| 5 | ONE-App: `LocalizationManager` neu (Bundle DE+EN, Lazy-Download via `GET /api/locales`) | Sonnet (think harder) | Auto | Refactor, Build-Step, `RESULT_PHASE_5.md` |
| 6 | ONE-App: Hardcoded-Audit (analog Windows) + alle Composables auf `t("KEY")` | Sonnet | Auto | `LOCALIZATION_AUDIT_REPORT_ONE.md` |
| 7 | Cutover: 33 Sprachen löschen, DE/EN importieren, Smoke-Tests, Doku, Release-Notes | Haiku | Auto | Migrations-Log, `CLAUDE.md`-Update, Release-Notes v0.4.0 |

### Phase 0 — Manuelle Vorarbeit (vor Autorun)

Inhalt:
1. Aktuelles `LocalizationManager.kt` parsen, **nur DE + EN** als Werte extrahieren → `keys_de_en.json` (Rest der 33 Sprachen wird verworfen)
2. Abgleich mit DrainQ.Windows `de-DE.json`: welche Keys sind 1:1 wiederverwendbar?
3. Vorschlag für Key-Mapping (alte snake_case → neue UPPER_SNAKE oder bereits existierender Windows-Key)
4. ADR `0010-l10n-portal-as-sot.md` schreiben (Entscheidung dokumentieren — inkl. „kill all 33 languages")
5. `RESULT_PHASE_0.md` mit Mengengerüst (Anzahl Keys, davon shared/neu/umbenannt)

**Erst danach** läuft der Autorun ab Phase 1 unbeaufsichtigt durch.

### Phase 1 — Key-Mapping

- CSV `key_mapping.csv` mit Spalten `old_key, new_key, scope, source_text_de`
- Heuristik: gleiche oder fast-gleiche DE-Werte werden auf Windows-Keys gemappt (→ `scope=shared`)
- Rest bekommt neuen ONE_-Key (→ `scope=one`)
- Validierung: alle alten Keys haben genau ein Mapping

### Phase 2 — Portal-Backend

- DB-Migration: `Key.scopes` als Array (oder Join-Tabelle)
- API: `GET /api/translations/{locale}.json?scope=one,shared` filtert + merged
- API: `POST /api/admin/translations/import` für Bulk-Import aus Phase 1
- API: `POST /api/admin/translations/{key}/deepl-fill` triggert DeepL
- Tests: scope-Filter korrekt, ETag/304, Conflict bei Shared-Key-Änderung

### Phase 3 — Portal-Frontend

- Neuer Tab unter `/admin/translations/one`
- Liste, Filter, Inline-Edit, Bulk-Aktionen
- Status-Badges (empty/machine/reviewed/locked)
- Shared-Indikator
- „Vorschau im App-Format" (zeigt, was die App beim nächsten Fetch bekäme)

### Phase 4 — DeepL + Review

- DeepL-API-Key in Secrets (KRITIS: keine Secrets im Repo)
- Glossar mit ~50 Fachbegriffen aus Kanalinspektion seed (Haltung, Schacht, Sohle, Sanierung, ZK, …)
- Bei neuem Key: automatisch alle 34 Zielsprachen → Status `machine`
- Review-UI: Liste „neu seit letztem Login", Button „Reviewed" je Sprache
- Audit-Log: wer hat wann was freigegeben (KRITIS-Anforderung)

### Phase 5 — ONE-App Refactor

- `LocalizationManager` umbauen:
  - 10k-Zeilen-Map aus Kotlin-Quellcode raus
  - Bundle nur noch DE + EN (`res/raw/l10n_de.json`, `res/raw/l10n_en.json`) — beim Build aus Portal gezogen
  - Cache pro heruntergeladener Sprache (App-internes File-Verzeichnis)
  - `availableLanguages` wird zur Laufzeit über `GET /api/locales?app=one` befüllt
  - Sprachauswahl-UI: Liste mit „Heruntergeladen" / „Laden (XX kB)" Status je Eintrag
  - Lazy-Download bei Auswahl: `GET /api/translations/{locale}.json?scope=one,shared` → in Cache speichern → aktivieren
  - Refresh nur für aktuell ausgewählte Sprache mit `If-Modified-Since`
  - Keine Hintergrund-Downloads für nicht ausgewählte Sprachen
- Funktion `t(key: String, vararg args: Any): String` ersetzt direkte Lookups
- Build-Step: vor `assembleRelease` zieht Gradle-Task `de.json` und `en.json` aus Portal in `res/raw/`
- Fallback-Kette: gewählte Sprache (Cache) → EN (Bundle) → DE (Bundle) → Key selbst
- Settings: „Heruntergeladene Sprachen verwalten" — Liste mit Größe + Lösch-Button pro Sprache

### Phase 6 — Hardcoded-Audit

Spiegel zum Windows-Audit (`LOCALIZATION_AUDIT_REPORT.md`):
- Alle Composables scannen auf String-Literale in `Text(...)`, `Toast`, Snackbar, Dialog-Texte
- Befunde-Tabelle mit Datei, Zeile, alter Text, neuer Key
- Behebung und Verifikation
- Bericht `docs/LOCALIZATION_AUDIT_REPORT_ONE.md`

### Phase 7 — Cutover

- Migrations-Skript einmalig: **nur DE + EN** nach Portal importieren, 33 alte Sprachen verworfen
- Alle Locales außer `de`, `en` initial auf `inactive` setzen (sobald Partner kommt: Status → `pending`)
- Smoke-Tests:
  - App startet ohne Netz → DE/EN Bundle greift
  - App mit Netz, Auswahl PL (sofern als `active` gesetzt) → Download, Cache, Anzeige
  - PL nochmal auswählen → kein erneuter Download, sofortiger Wechsel
  - Key fehlt in PL → Fallback EN → DE → Key
  - Inaktive Sprachen erscheinen nicht in der Auswahl
- `CLAUDE.md` updaten (Phase 8: zentrale L10n + Lazy-Loading)
- Release-Notes v0.4.0 mit klarer Ansage: „Sprachen außer DE/EN sind bis zur Partner-Benennung deaktiviert"

---

## 5. Risiken / offene Punkte

| Risiko | Mitigation |
|---|---|
| Kunde im Land X hat Tablet, aber kein WLAN auf Baustelle, Sprache noch nicht geladen | Sprache vor erstem Einsatz im Büro-WLAN laden — Standard-Onboarding-Schritt. Solange nichts geladen: EN als Fallback |
| App auf Baustelle ohne Internet → veralteter Cache | Cache bleibt persistent, Refresh nur bei verfügbarem Netz, kein Block |
| Shared-Key-Konflikt: Windows und ONE wollen unterschiedlichen Text | Konvention: Shared-Keys sind generisch (Speichern, Abbrechen). Bei Konflikt → Key splitten in scope-spezifisch |
| DeepL-Fehler bei Fachbegriffen | Glossar pflegen, Partner-Review verpflichtend, Schwelle 95 % reviewed bevor `active` |
| Partner antwortet nicht / liefert nicht | Sprache bleibt `pending` → wird nicht ausgeliefert → kein Schaden. Partner-Wechsel im Portal möglich |
| KRITIS: Übersetzungs-Endpoint öffentlich | Kein Auth — wie schon bei Windows (Translations sind kein Schutzgut). Audit-Log auf Admin-Schreibseite, Partner-Login mit Rollen-Scope |
| Portal-Ausfall → App ohne Refresh | Cache + Bundle → App bleibt funktionsfähig, lediglich neue Strings fehlen |
| Versionierte App / neue Keys ohne Übersetzung in PL | Fallback PL → EN → DE → Key |
| Bestehende ONE-Nutzer haben jetzt nur noch DE/EN | Begleitkommunikation: Sprache muss neu im WLAN geladen werden, klare Info im Release. Da bisherige MT-Übersetzungen ohnehin teils mies waren → echter Qualitäts-Gewinn |

---

## 6. Was als Nächstes konkret zu tun ist

1. **Diesen Entwurf abnehmen oder Änderungen markieren.**
2. Phase 0 manuell starten — Inventur der aktuellen Keys und ADR.
3. `RESULT_PHASE_0.md` und `key_mapping.csv` (Erstentwurf) vorlegen.
4. Autorun-Skript `autorun_l10n_portal.ps1` aus Template ableiten, Phasen 1–7 verdrahten.
5. Autorun starten.

---

**Geschätzter Aufwand (Brutto):**
- Phase 0 manuell: ~2 h
- Phasen 1–4 Portal: ~2 Tage Autorun-Laufzeit
- Phasen 5–7 ONE: ~1,5 Tage Autorun-Laufzeit
- Partner-Review pro Land (sobald benannt): ~4–8 h einmalig pro Sprache, danach nur Deltas
- **DeepL-Kosten:** entfallen für nicht aktivierte Sprachen → bei Start nur DE↔EN, dann pro Land einzeln freischalten

**Nebeneffekt:** APK-Größe sinkt um ~1 MB, kompilierte Klasse `LocalizationManager` von ~10k auf ~200 Zeilen.

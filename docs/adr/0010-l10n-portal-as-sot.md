# ADR 0010 — DrainQ.Web Portal als Single Source of Truth für L10N

**Datum:** 2026-05-20  
**Status:** Angenommen  
**Kontext:** Phase 0 der zentralen Lokalisierungsmigration (PHASENPLAN_L10N.md)  
**Autoren:** GodMode Phase 0 (claude-sonnet-4-6)

---

## Kontext

Die DrainQ.ONE Tablet-App enthält aktuell ~300 Übersetzungs-Keys × 35 Sprachen als hart­codierte Kotlin-Map in `LocalizationManager.kt` (~10.000 Zeilen). Strings sind teilweise direkt in Composables eingebettet. Das DrainQ.Web Portal verwaltet bereits Übersetzungen für DrainQ.Windows und die Web-Oberfläche in einem zentralen Backend mit Key/Value-Store.

**Problem:** Drei isolierte Übersetzungs-Silos (Windows, Web, ONE), keine gemeinsame Pflege, keine Partner-Review-Pipeline, keine kontrollierte Sprach-Aktivierung.

---

## Entscheidungen

### Entscheidung 1 — Key-Konvention: UPPER_SNAKE_CASE

| | Details |
|---|---|
| **Entscheidung** | Alle Keys werden in `UPPER_SNAKE_CASE` geführt, identisch zur Konvention in DrainQ.Windows |
| **Alternativen** | `snake_case` (bisherig ONE), `camelCase`, `dot.notation` |
| **Begründung** | Gemeinsamer Key-Pool mit Windows ermöglicht Shared-Keys ohne Duplizierung. Heutiger ONE-Stand: `snake_case` (z. B. `stream_preview`) wird in Phase 1 auf `UPPER_SNAKE` gemappt. Konflikte bei Shared-Keys sind explizit über den `scope`-Mechanismus auflösbar. |
| **Konsequenz** | Phase 1 erzeugt vollständiges Mapping `old_snake → NEW_UPPER_SNAKE` mit Scope-Zuordnung |

---

### Entscheidung 2 — Bundle-Strategie: Nur DE+EN im APK, Lazy Download

| | Details |
|---|---|
| **Entscheidung** | Das APK enthält nur DE und EN als gebündelte JSON-Ressourcen (`res/raw/l10n_de.json`, `res/raw/l10n_en.json`). Alle weiteren Sprachen werden on-demand heruntergeladen. |
| **Alternativen** | Alle 35 Sprachen gebündelt (Status quo), alle Sprachen aus Portal (kein Bundle) |
| **Begründung** | Aktuelle 35 Sprachen × ~30 kB ≈ 1 MB APK-Overhead für nicht genutzte Sprachen. Maschinell übersetzte Strings ohne Partner-Review sind teils fehlerhaft — besser nicht ausliefern. Lazy Download spart DeepL-Kosten: nur aktivierte Sprachen werden übersetzt. |
| **Konsequenz** | Fallback-Kette: ausgewählte Sprache (Cache) → EN (Bundle) → DE (Bundle) → Key-Name. App bleibt ohne Netz funktionsfähig. Bestehende 33 Nicht-DE/EN-Sprachen werden verworfen (Begleitkommunikation in Release-Notes v0.4.0). |

---

### Entscheidung 3 — Übersetzungsprozess: DeepL Auto-Fill + verpflichtender Partner-Review

| | Details |
|---|---|
| **Entscheidung** | DeepL übersetzt automatisch aus DE-Quelle in alle aktivierten/pending Sprachen. Danach ist ein Partner-Review mit Schwelle ≥ 95 % Pflicht, bevor eine Sprache auf Status `active` gesetzt werden kann. |
| **Alternativen** | Nur manuell (zu langsam), nur DeepL ohne Review (zu unzuverlässig bei Fachbegriffen) |
| **Begründung** | Kanalinspektion-Fachbegriffe (Haltung, Schacht, Sohle, Sanierung, Zustandsklasse) werden von DeepL ohne Kontext falsch übersetzt. Ein Glossar mit ~50 Termen wird gepflegt. Partner kennen die Branche im Zielland. KRITIS-Anforderung: Audit-Log für jede Freigabe. |
| **Konsequenz** | Status-Workflow pro Translation: `empty → machine → reviewed → locked`. Locale-Status: `core | active | pending | inactive`. Nur `core ∪ active` erscheinen im App-Endpoint. DeepL-Kosten entfallen für `inactive` Sprachen. |

---

### Entscheidung 4 — Portal-Integration: Eigener Reiter „DrainQ.ONE" + Partner-Verwaltung

| | Details |
|---|---|
| **Entscheidung** | Im DrainQ.Web-Admin-Portal wird ein separater Tab `/admin/translations/one` mit Sub-Tab „Sprachen & Partner" angelegt. Partner (Übersetzer/Reviewer) werden pro Land verwaltet und haben eingeschränkten Login-Zugang. |
| **Alternativen** | ONE in bestehenden Tab integrieren (Scope-Filter), externes Tool (Weblate, Crowdin) |
| **Begründung** | ONE hat ONE-spezifische Keys (`scope=one`) UND teilt Keys mit Windows (`scope=shared`). Eigener Tab vermeidet Verwirrung bei Redakteuren. Externes Tool würde Portal-Integration duplizieren und Sync-Aufwand erzeugen. Partner-Verwaltung pro Land ist KRITIS-relevant (Audit-Log wer hat was freigegeben). |
| **Konsequenz** | Neue DB-Entities: `Partner` (Name, Land, Kontakt, Rolle), `Locale.partnerId`, `Locale.status` Enum, `Key.scopes: string[]`. Neuer API-Endpoint: `GET /api/locales?app=one` (liefert nur `core|active`). |

---

## Zusammenfassung der Entscheidungstabelle

| # | Thema | Entscheidung | Verworfen |
|---|---|---|---|
| 1 | Key-Format | `UPPER_SNAKE_CASE` (Windows-kompatibel) | `snake_case` (bisherig ONE) |
| 2 | APK-Bundle | Nur DE+EN, Lazy Download für Rest | Alle 35 Sprachen gebündelt |
| 3 | Übersetzung | DeepL Auto + Partner-Review ≥ 95 % | Nur manuell / Nur DeepL |
| 4 | Portal | Eigener Tab + Partner-Verwaltung | Externes Tool / Inline-Integration |

---

## Konsequenzen für bestehende Nutzer

- Nutzer mit einer der 33 nicht-DE/EN Sprachen verlieren ihre Spracheinstellung → Fallback auf DE
- Kommunikationspflicht im Release v0.4.0: Sprache muss nach Update-Installation neu im WLAN geladen werden
- Qualitäts-Gewinn: bisherige MT-Qualität der 33 Sprachen war teils unakzeptabel

## KRITIS/NIS2-Bewertung

- Übersetzungs-Endpoint `GET /api/translations/{locale}.json` ist öffentlich (keine Schutzdaten)
- Admin-Schreibseite hinter Auth + Audit-Log (KRITIS-Anforderung erfüllt)
- Partner-Login mit Rollen-Scope (sieht nur eigene Sprache)
- DeepL-API-Key in Secrets, nie im Code oder Repo
- Kein personenbezogenes Datum in Translation-Keys

---

*Nachfolgende ADRs: 0011 (Key-Mapping Phase 1), 0012 (Portal-DB-Migration Phase 2)*

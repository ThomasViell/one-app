# PLAN: Hilfe-System + Bedienungsanleitung DrainQ.ONE

**Datum:** 2026-07-16 · **Planer:** Fable (CEO-Konzeptfreigabe 16.07.) · **Bau:** Sonnet · **Audit:** Opus
**Status:** FREIGEGEBEN durch CEO-Entscheidungen (siehe §1) · Wellen W-H1 → W-H3

---

## 0. Leitidee

KEIN klassisches Handbuch als Einzeldokument. Wir bauen ein **Hilfe-System als Single Source of Truth**:
pro Screen genau ein strukturierter Hilfe-Baustein (Titel, Seitenzweck, Element-Erklärungen,
Screenshot-Referenz). Daraus entstehen ZWEI Ausgaben:

1. **In-App-Hilfe:** „?"-Button auf jedem Screen öffnet den Hilfe-Baustein genau dieser Seite,
   in der Gerätesprache (offline eingebettet, Portal-Refresh bei Verbindung).
2. **Bedienungsanleitung:** Generator setzt alle Bausteine + Screenshots pro Sprache zu einem
   PDF zusammen (HTML-Zwischenformat fällt mit ab).

Screenshots + Texte entstehen **vollautonom in einem Run** (ONE per USB-C/ADB, Kamera angeschlossen),
wiederholbar bei jedem neuen Feature.

## 1. CEO-Entscheidungen (16.07.2026, bindend)

| # | Entscheidung |
|---|--------------|
| E1 | Hilfe-System als SSOT, Handbuch = Export daraus |
| E2 | In-App-Hilfe eingebettet (offline) + Portal-Update bei Verbindung |
| E3 | Sprachen: DE + EN sofort; danach alle Portal-Sprachen per DeepL, Partner-Review im Portal |
| E4 | Handbuch-Format: PDF (aus HTML-Zwischenformat) |
| E5 | Screenshot-Run mit angeschlossener Kamera/Crawler → Live-Video-Screens im selben Run |
| E6 | KEINE Halluzinationen: jede Aussage doppelt belegt (Screenshot + Code/Engineering-Doku), Opus-Gegen-Audit pro Seite, Unbelegtes fliegt raus |

## 2. Ist-Zustand (Repo-Befund 16.07., Basis der Planung)

- **13 Screens** dokumentiert: SCR-01…SCR-13 in `docs/engineering/02-project_one.md` §6.1,
  Routen in §6.2 (`NavGraph.kt`). Das ist die verbindliche Seitenliste.
- **L10n-Mechanik:** `res/raw/l10n_de.json` / `l10n_en.json` (429 Keys, lowercase_snake),
  `ui/localization/LocalizationManager`. Portal-Scope ONE existiert; Import-Skript
  `C:\Projekte\drainq.one-localization\publish-one-l10n.ps1`; App-seitiges Lazy-Nachladen fertig
  im Branch `feature/l10n-portal` (Repo `drainq.one-localization`, NICHT gemergt).
- **Altlast:** `generate_manual.js` / `generate_manual_docx.js` (Repo-Root) = Handbuch v0 mit
  **hartkodierten Texten** und Barlow-Fonts (nicht mehr in `res/font`, dort nur Inter).
  → Wird durch das neue System ERSETZT; Dateien nach `docs/manual/_legacy/` verschieben.
- **Sprachumschaltung ohne Neustart** funktioniert (0.5.9) → Screenshot-Run kann DE und EN
  in einem Durchlauf abfahren.
- **Beta-Builds sind Debug-Builds** (`assembleDebug`, kein Keystore) → Debug-only-Hooks für den
  Harness sind im Beta-Kanal verfügbar.
- **Basis-Branch:** `feature/dual-mode` (aktuelle Beta-Linie 0.5.16). Arbeitsbranch:
  `feature/help-system`, abgezweigt von `feature/dual-mode`.
- KRITIS für ONE nicht einschlägig (CON-03, CEO 14.07.); Hilfe-Inhalte enthalten keine
  personenbezogenen Daten → nur übliche Hygiene.

## 3. Architektur

### 3.1 Hilfe-Content-Modell (SSOT)

Datei je Sprache: `app/src/main/res/raw/help_de.json`, `help_en.json` (gleiches Muster wie l10n_*).

```json
{
  "schema": 1,
  "screens": [
    {
      "id": "scr02_home",
      "route": "home",
      "title": "help.scr02_home.title",
      "intro": "help.scr02_home.intro",
      "elements": [
        { "id": "storage_card", "label": "help.scr02_home.storage_card.label",
          "text": "help.scr02_home.storage_card.text" }
      ],
      "screenshot": "scr02_home",
      "evidence": ["SCR-02", "REF-20", "UC-04"]
    }
  ]
}
```

- **Texte selbst liegen als normale L10n-Keys** (Prefix `help.`) in `l10n_de.json`/`l10n_en.json`
  → laufen automatisch über den bestehenden Portal-Import (Scope ONE), DeepL, Partner-Review,
  Lazy-Download. Die `help_*.json` hält nur Struktur + Key-Referenzen + Evidenz.
- `evidence` = Rückführbarkeit auf 02-project/01-analysis (Anti-Halluzinations-Anker, E6).

### 3.2 Screenshot-Harness (Welle W-H1)

App-seitig (nur `BuildConfig.DEBUG`):
- `ScreenshotRig` (neues Paket `debugrig/`): BroadcastReceiver, per adb steuerbar:
  - `…DEMO_SEED` — legt deterministisches Demo-Projekt an (feste Projektnr., 3 Schäden mit
    Fotos, 1 Notiz, festes Datum, Kameratyp C18); idempotent (vorher purge).
  - `…NAVIGATE --es route <route>` — navigiert NavGraph auf beliebige Route
    (inkl. `inspection/{id}`, `project_detail/{id}`).
  - `…SET_LOCALE --es lang de|en` — nutzt vorhandene Neustart-freie Sprachumschaltung.
  - `…UI_STATE --es state <name>` — öffnet definierte Dialog-Zustände (DamageDialog,
    UsbExportDialog, PdfPreview …) für Detail-Screenshots.
- Host-seitig: `tools/manual/capture.ps1` — Szenenkatalog `tools/manual/scenes.json`
  (Route + optional UI_STATE + Wartezeit + Dateiname), Ablauf je Sprache:
  seed → für jede Szene: navigate → settle → `adb exec-out screencap -p` →
  `docs/manual/screenshots/<lang>/<szene>.png`. Läuft gegen die per Portal-Update installierte
  Beta (Regel: NIE adb install).
- Live-Video-Szenen (SCR-07 Inspektion) setzen angeschlossene Kamera voraus (E5). Harness prüft
  vor Start: Kamerabild vorhanden (`/dev/video0`-Probe via vorhandener Diagnostik) — wenn nein: ABBRUCH
  mit klarer Meldung (kein Schwarzbild-Screenshot ins Handbuch).

**Neues Feature später = 1 Eintrag in `scenes.json` + 1 Hilfe-Baustein + Re-Run.** Das ist der
Agenten-Erweiterungspfad.

### 3.3 Text-Pipeline + Anti-Halluzinations-Gate (Welle W-H2)

Pro Screen, vollautomatisch im Run:
1. **Quellen sammeln:** Screenshot (frisch aus W-H1) + Composable-Quellcode des Screens +
   SCR/REF/UC-Abschnitte aus 01/02-Engineering-Doku.
2. **Sonnet schreibt** den Hilfe-Baustein DE (Titel, Zweck, jedes sichtbare Bedienelement).
   HARTE REGEL: nur beschreiben, was auf dem Screenshot sichtbar ODER im Code belegt ist;
   jede Element-Erklärung referenziert Code-Symbol oder Screenshot-Bereich.
3. **Opus-Audit** (nicht-bauendes Modell) je Seite: „Steht auf dem Screenshot wirklich, was der
   Text behauptet? Fehlt ein sichtbares Element? Gibt es Behauptungen ohne Beleg?" →
   Befunde zurück an Sonnet, max. 2 Iterationen, dann PASS/FAIL je Seite. FAIL-Seiten werden
   NICHT ausgeliefert, sondern als offener Punkt gelistet.
4. **EN:** Übersetzung durch Sonnet + Opus-Stichprobe (Terminologie: bestehende EN-Keys als Glossar).
5. Ergebnisprotokoll `docs/manual/RUN_REPORT_<datum>.md`: je Seite Quelle→Text→Audit-Verdict.

### 3.4 Handbuch-Generator (Welle W-H2)

- `tools/manual/generate.js` (Node, ersetzt `generate_manual.js`):
  liest `help_<lang>.json` + aufgelöste L10n-Keys + Screenshots → **HTML** (ein File,
  DrainQ-CI, Inter eingebettet) → **PDF** via headless Chromium (puppeteer-core, vorhandenes
  Chromium nutzbar) nach `docs/manual/DrainQ-ONE_Bedienungsanleitung_<lang>_<version>.pdf`.
- Kapitelfolge = Bedienlogik, nicht SCR-Nummern: Erste Schritte (Splash/Home/Verbindung) →
  Projekt anlegen → Inspektion (Kernkapitel) → Projekt/Bericht/Export → Einstellungen/Netzwerk/
  Karten/Update → Dual-Mode/Pairing → Cloud (Hinweis „in Vorbereitung", Stub REF-27).
- Deckblatt: Produktfoto (`01 - front_with_belt_on_black_rgb.png`), Version aus Build.

### 3.5 In-App-Hilfe + Portal (Welle W-H3)

- `HelpSheet` (Compose ModalBottomSheet/Dialog): rendert Hilfe-Baustein der aktuellen Route,
  Bild oben, Elemente als Liste. `HelpButton` („?", 40dp-Regel beachten) in der TopBar aller
  13 Screens; während Inspektion in der Bedienleiste (stört Video nicht).
- `HelpRepository`: eingebettete `help_*.json` + Keys aus LocalizationManager; wenn Portal-
  Nachladen (feature/l10n-portal) gemergt ist, kommen aktualisierte `help.*`-Keys automatisch
  über denselben Weg (E2) — kein Sondersystem.
- Portal: `publish-one-l10n.ps1`-Lauf nimmt die neuen `help.*`-Keys mit (Scope ONE);
  danach DeepL je Zielsprache + Partner-Review = E3-Strecke, unverändert.

## 4. Wellen

### W-H1 — Screenshot-Harness (Sonnet baut, Opus auditiert)
Umfang: `debugrig/` (Seed/Navigate/Locale/UiState, strikt BuildConfig.DEBUG), `tools/manual/capture.ps1`,
`scenes.json` mit min. 20 Szenen (13 Screens + Kern-Dialoge SCR-05/06/07), Kamera-Precheck.
**Abnahme:** Ein Kommando erzeugt kompletten Screenshot-Satz DE+EN am echten Gerät; zweiter Lauf
liefert pixel-deterministische Ergebnisse (bis auf Live-Video-Bildinhalt); Unit-Tests für Seed-Idempotenz.

### W-H2 — DER AUTONOME RUN: Texte + Audit + PDF (ein Durchlauf, ohne CEO)
Voraussetzung: W-H1 gemergt, Beta mit Rig via Portal-Update auf der ONE, Kamera dran, USB-C/ADB.
Ablauf: capture DE+EN → Text-Pipeline §3.3 → Handbuch-PDF DE+EN §3.4 → `RUN_REPORT`.
**Abnahme:** PDF DE+EN liegen vor; RUN_REPORT zeigt je Seite Audit-PASS; 0 unbelegte Aussagen
(Opus-Bestätigung); FAIL-Seiten (falls) als offene Punkte gelistet.

### W-H3 — In-App-Hilfe + Portal-Anbindung
Umfang: `HelpSheet`/`HelpButton`/`HelpRepository`, `help.*`-Keys via `publish-one-l10n.ps1` ins
Portal, DeepL-Anstoß für vorhandene Zielsprachen (E3 Stufe 2).
**Abnahme:** „?" auf jedem Screen zeigt die richtige Seite in DE und EN am Gerät; Keys im Portal
sichtbar/pflegbar; Beta published (Ein-Befehl `tools\publish-one-release.ps1`).

## 5. Risiken / Abhängigkeiten

| Risiko | Umgang |
|--------|--------|
| Kamerabild fehlt beim Run (Video-Permission-Thema fabrikneuer Geräte) | Precheck im Harness → harter Abbruch statt Schwarzbild; Run auf Thomas-ONE (Kamera verifiziert) |
| `feature/l10n-portal` noch nicht gemergt | W-H3 funktioniert auch ohne (eingebettet); Portal-Refresh wird mit dem L10n-Merge automatisch scharf |
| Merge-Entscheidung dual-mode→master offen | Arbeitsbranch hängt an feature/dual-mode; Merge-Reihenfolge unberührt |
| Portal-Purge/Deploy-Blocker (404, Stand 13.07.) | betrifft nur Portal-Neuimport, nicht W-H1/W-H2 |
| Dialog-Screenshots flaky (Timing) | settle-Wartezeiten + `dumpsys window`-Probe im Harness; Szenen einzeln wiederholbar |

## 6. Nicht-Ziele

Keine Video-Tutorials, keine Onboarding-Tour, kein Handbuch für Portal/Windows (eigene Systeme),
keine Änderung am L10n-Portal selbst (vorhandene Strecke wird nur benutzt).

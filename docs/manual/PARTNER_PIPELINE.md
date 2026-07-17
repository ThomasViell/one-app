# DrainQ ONE — Partner-Handbuch-Pipeline (W-H4)

> Status: Konzept + lokale Befehle fertig. Portal-seitige Hooks noch nicht implementiert.

## Überblick

Für jede neue Sprache im Portal werden automatisch synthetische Screenshots (via Paparazzi)
und ein fertiges PDF-Handbuch erzeugt — ohne Gerät, ohne manuelles Eingreifen.

```
Portal-Export (JSON)
       │
       ▼
build-language.ps1   ← einziger Einstiegspunkt
       │
       ├─► render.ps1  (Paparazzi, JVM)  → screenshots_synth/<lang>/*.png
       │
       └─► generate.js (Puppeteer/Chrome) → DrainQ-ONE_Bedienungsanleitung_<lang>_<ver>.pdf
```

---

## Lokale Ausführung

### Voraussetzungen

| Tool     | Version       | Hinweis                                        |
|----------|--------------|------------------------------------------------|
| JDK      | 17+          | `JAVA_HOME` zeigt auf JDK 17                   |
| Android SDK | SDK 34+   | `ANDROID_HOME` gesetzt                         |
| Node.js  | 20+          | für generate.js + puppeteer-core               |
| Chrome   | beliebig     | Pfad in `CHROME_PATH` oder Standard-Install    |

### Befehl

```powershell
# Von tools/manual/ aus:
.\build-language.ps1 -Lang fr -PortalUrl "https://portal.drainq.com/api/translations/fr.json?scope=one"
```

Intern ruft das Skript auf:

```powershell
# 1. Download (falls -PortalUrl angegeben)
Invoke-WebRequest -Uri $PortalUrl -OutFile tools/manual/translations/fr.json

# 2. Paparazzi-Render (20 Szenen)
.\render.ps1 -Langs fr -TranslationJson tools/manual/translations/fr.json

# 3. PDF-Erzeugung
node generate.js --lang fr
```

### Ergebnis

```
docs/manual/
  screenshots_synth/fr/          ← 19–20 PNGs (Paparazzi-Output)
  DrainQ-ONE_Bedienungsanleitung_fr_<ver>.pdf
```

### Bekannte Sprachen (App-Bundle, kein JSON nötig)

```powershell
.\build-language.ps1 -Lang de   # Deutsch  (19/20 synth, 1 Geräte-Fallback)
.\build-language.ps1 -Lang en   # Englisch (19/20 synth, 1 Geräte-Fallback)
```

### Offline-Test mit lokalem JSON

```powershell
.\build-language.ps1 -Lang nl -TranslationJson "C:\tmp\nl_export.json"
```

---

## CI/CD-Integration (Konzept)

### Trigger-Optionen

**Option A — Portal-Webhook → GitHub Actions**

```yaml
# .github/workflows/manual-language.yml
on:
  repository_dispatch:
    types: [portal-translation-ready]

jobs:
  build-manual:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - name: Build manual
        shell: pwsh
        env:
          PORTAL_URL: ${{ github.event.client_payload.translationUrl }}
          LANG_CODE:  ${{ github.event.client_payload.lang }}
        run: |
          cd tools/manual
          .\build-language.ps1 -Lang $env:LANG_CODE -PortalUrl $env:PORTAL_URL
      - name: Upload PDF artifact
        uses: actions/upload-artifact@v4
        with:
          name: manual-${{ github.event.client_payload.lang }}
          path: docs/manual/DrainQ-ONE_Bedienungsanleitung_*.pdf
```

Portal-seitig löst ein POST-Request den Workflow aus:

```bash
curl -X POST https://api.github.com/repos/uip-team/drainq-one/dispatches \
  -H "Authorization: token $GH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"event_type":"portal-translation-ready","client_payload":{"lang":"fr","translationUrl":"https://portal.drainq.com/api/translations/fr.json?scope=one"}}'
```

**Option B — Nightly-Batch (alle aktiven Portalsprachen)**

```yaml
on:
  schedule:
    - cron: '0 3 * * *'  # 03:00 UTC täglich

jobs:
  build-all-languages:
    strategy:
      matrix:
        lang: [de, en, fr, nl, pl, cs]   # Liste aus Portal-API
    # ...
```

---

## Portal-JSON-Format

Das Skript erwartet ein flaches JSON-Objekt mit App-String-Keys:

```json
{
  "quick_capture":          "Schnellaufnahme",
  "gallery":                "Galerie",
  "settings":               "Einstellungen",
  "inspection_live_title":  "Live-Inspektion",
  ...
}
```

Fehlende Keys fallen auf die DE-Übersetzung zurück
(implementiert in `LocalizationManager.getString`).
Ein WARN-Log im Render-Schritt listet kritische fehlende Keys.

---

## Offene Punkte (Portal-seitig, nicht Teil von W-H4)

| # | Punkt |
|---|-------|
| P1 | Portal-Export-Endpoint `/api/translations/<lang>.json?scope=one` implementieren |
| P2 | Webhook-Trigger bei neuem Übersetzungsstand einrichten |
| P3 | PDF-Ergebnis zurück ins Portal hochladen (z.B. S3 + Asset-Link) |
| P4 | GitHub Actions Workflow-Datei committen + Secrets konfigurieren |

---

## dlg_pdf_preview — bekannte Einschränkung

Szene `dlg_pdf_preview` fällt auf Geräte-Screenshot zurück (OFFEN nach 2 Fix-Iterationen):

```
[WARN] dlg_pdf_preview [de]: Screenshot-Quelle = Geräte-Ref.
[WARN] dlg_pdf_preview [en]: Screenshot-Quelle = Geräte-Ref.
```

Ursache: Paparazzi rendert den Dialog-Wrapper mit Layout-Offset; der Workaround
(`renderInline=true`) zeigt korrekten Header + Export-Knopf, aber `1/1`-Seitenzahl
unter der Fake-Seite fehlt. Auswirkung auf das Handbuch: minimal — die übrigen 19/20
Szenen sind vollständig synthetisch.

---

_Letzte Aktualisierung: W-H4 Phase 8 (2026-07-17)_

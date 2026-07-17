# DrainQ ONE — Handbuch-System (W-H1/W-H2)

## Kurzanleitung

### 1. Screenshots aufnehmen (W-H1)

Voraussetzungen: ONE per USB-C/ADB angeschlossen, Kamera angeschlossen, Beta-APK mit
ScreenshotRig installiert (via Portal-Update, KEIN `adb install`).

```powershell
# Kompletter Lauf DE + EN
tools\manual\capture.ps1

# Nur eine Sprache
tools\manual\capture.ps1 -Langs de

# Einzelne Szene wiederholen
tools\manual\capture.ps1 -Only scr07_inspection_live -Langs de
```

Screenshots landen in `docs/manual/screenshots/<lang>/<szene>.png`.

### 2. Szenen-Katalog

`tools/manual/scenes.json` — jede Szene hat:
- `name` — Dateiname des Screenshots (ohne .png)
- `route` — NavGraph-Route; `{demoId}` wird durch die echte Demo-Projekt-ID ersetzt
- `uiState` — optionaler Dialog-Zustand (null = kein Dialog)
- `settleMs` — Wartezeit nach Navigation in ms
- `notes` — menschliche Erklärung

Neue Szene = 1 Eintrag in `scenes.json` + 1 Hilfe-Baustein in `help_de.json` + Re-Run.

### 3. Handbuch generieren (W-H2)

```bash
node tools/manual/generate.js
```

Erzeugt HTML + PDF nach `docs/manual/DrainQ-ONE_Bedienungsanleitung_<lang>_<version>.pdf`.

### 4. Rig-Aktionen (ADB direkt)

```bash
# Demo-Projekt anlegen
adb shell am broadcast -a com.uip.drainq.one.rig.DEMO_SEED -p com.uip.drainq.one --receiver-foreground

# Navigieren
adb shell am broadcast -a com.uip.drainq.one.rig.NAVIGATE --es route home -p com.uip.drainq.one

# Sprache wechseln
adb shell am broadcast -a com.uip.drainq.one.rig.SET_LOCALE --es lang en -p com.uip.drainq.one

# Dialog öffnen
adb shell am broadcast -a com.uip.drainq.one.rig.UI_STATE --es state damage_dialog -p com.uip.drainq.one
```

## _legacy/

`generate_manual.js` und `generate_manual_docx.js` (v0 mit hartkodierten Texten und
Barlow-Fonts) wurden durch das neue System ersetzt. Archiv für Referenz.

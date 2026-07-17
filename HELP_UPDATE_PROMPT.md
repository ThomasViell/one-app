# HELP_UPDATE_PROMPT — Hilfe-Baustein für Screen X ergänzen

**Wiederverwendbarer Miniprompt** für das Ergänzen eines Hilfe-Bausteins zu einem neuen oder geänderten Screen. Dieser Prompt ersetzt die freie Texterfindung — jede Aussage muss belegt sein.

---

## Verwendung

Ersetze `<SCREEN_ID>` und `<ROUTE>` überall durch die konkreten Werte (z. B. `scr14_export`, `export`).

---

## Quellen (PFLICHT, vor dem Schreiben lesen)

1. **Screenshot (Synth):** `docs/manual/screenshots_synth/de/<SCREEN_ID>.png` (DE) und `en/<SCREEN_ID>.png` (EN)
2. **Code:** `app/src/main/java/com/uip/oneapp/ui/screens/<package>/<ScreenName>Screen.kt`
3. **Seitenreferenz:** Abschnitt SCR-XX in `docs/engineering/02-project_one.md` §6.1
4. **Routen-Register:** `app/src/main/java/com/uip/oneapp/ui/navigation/NavGraph.kt`

---

## Belegpflicht (E6 — KEINE Halluzinationen)

- Jedes beschriebene Element muss auf dem Screenshot **sichtbar** ODER im Code **nachweisbar** sein.
- Jede Element-Erklärung referenziert entweder einen Code-Symbol-Namen oder einen Screenshot-Bereich.
- Unbelegtes fliegt raus — lieber kürzer und korrekt als vollständig und falsch.

---

## Schritte (Sonnet baut, Opus auditiert)

### Schritt 1 — Szene registrieren (`tools/manual/scenes.json`)

Eintrag hinzufügen:
```json
{
  "name": "<SCREEN_ID>",
  "route": "<ROUTE>",
  "uiState": null,
  "settleMs": 1500,
  "notes": "Kurze Beschreibung."
}
```

### Schritt 2 — Screenshots rendern

```powershell
.\tools\manual\render.ps1 -Langs de,en
```

→ PNGs landen in `docs/manual/screenshots_synth/de/` und `en/`.

### Schritt 3 — Hilfe-Baustein schreiben (DE)

Quelle: Screenshot + Code. Format (vgl. `app/src/main/assets/help/help_de.json`):
```json
{
  "id": "<SCREEN_ID>",
  "route": "<ROUTE>",
  "title": "help.<SCREEN_ID>.title",
  "intro": "help.<SCREEN_ID>.intro",
  "screenshot": "<SCREEN_ID>",
  "evidence": ["SCR-XX"],
  "elements": [
    { "id": "element_id", "label": "help.<SCREEN_ID>.element_id.label", "text": "help.<SCREEN_ID>.element_id.text" }
  ]
}
```

Baustein in `help_de.json` → `screens`-Array einfügen.

### Schritt 4 — Keys in `de.json` schreiben

In `app/src/main/assets/i18n/de.json` alle referenzierten `help.*`-Keys als flache Einträge einfügen:
```json
"help.<SCREEN_ID>.title": "Seitentitel auf Deutsch",
"help.<SCREEN_ID>.intro": "Einleitung auf Deutsch.",
"help.<SCREEN_ID>.element_id.label": "Element-Name",
"help.<SCREEN_ID>.element_id.text": "Element-Erklärung auf Deutsch."
```

### Schritt 5 — EN: Baustein + Keys

Baustein in `help_en.json` (identische Struktur wie DE, gleiche Key-Referenzen).
Keys in `en.json` — Terminologie-Referenz: bestehende EN-Keys als Glossar.

### Schritt 6 — Opus-Audit (je Seite)

Opus-Aufruf: „Steht auf dem Screenshot `<SCREEN_ID>_en.png` wirklich, was der EN-Text behauptet? Fehlt ein sichtbares Element? Gibt es Aussagen ohne Beleg?"
→ Befunde zurück an Sonnet; max. 2 Iterationen. PASS = ausliefern, FAIL = Seite als offen listen.

### Schritt 7 — Coverage-Gate bestätigen

```bash
./gradlew :app:testDebugUnitTest --tests=com.uip.oneapp.help.HelpCoverageTest
```

→ MUSS grün sein. Rot = fehlende Keys oder Bausteine nachpflegen.

### Schritt 8 — Golden-Diff Gate

```powershell
.\tools\manual\verify.ps1
```

→ Wenn UI-Änderung auch andere Szenen betrifft: `verify.ps1 -Update` + Goldens committen.

### Schritt 9 — Commit

```
feat(help): Hilfe-Baustein <SCREEN_ID> — DE+EN, Audit PASS
```

---

## Abnahme-Checkliste

- [ ] `scenes.json` — Szene eingetragen
- [ ] `help_de.json` — Baustein vorhanden (alle Keys referenziert)
- [ ] `help_en.json` — Baustein vorhanden (alle Keys referenziert)
- [ ] `de.json` — alle `help.*`-Keys vorhanden und nicht leer
- [ ] `en.json` — alle `help.*`-Keys vorhanden und nicht leer
- [ ] Opus-Audit PASS (keine unbelegten Aussagen)
- [ ] `HelpCoverageTest` grün
- [ ] `verify.ps1` grün (oder Goldens aktualisiert)

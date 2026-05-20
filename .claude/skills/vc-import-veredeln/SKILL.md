---
name: vc-import-veredeln
description: Replaces the deterministic Genesis import entry in MASTER_BRAIN.md with a narrative, AI-enriched initial entry. Run once per project after bulk import.
---

# vc-import-veredeln — KI-Veredelung des Genesis-Eintrags

Du veredelst den deterministischen Genesis-Import-Eintrag in `MASTER_BRAIN.md` zu einem erzählerischen, lesbaren Initial-Eintrag.

## Schritt 1 — Quellen lesen

Lese folgende Dateien:

1. `MASTER_BRAIN.md` — suche den Block mit `<!-- Import-Genesis -->` (das ist der Genesis-Block)
2. Führe `git log --oneline -100` aus (letzte 100 Commits als Überblick)
3. `README.md` (falls vorhanden, erste 500 Zeichen)
4. `CLAUDE.md` oder `AGENTS.md` (falls vorhanden, Inhaltsverzeichnis und wichtigste Sections)

## Schritt 2 — Idempotenz-Check

Falls der Genesis-Block bereits das Badge `KI-veredelt (Import)` enthält: Ausgabe `"Projekt wurde bereits veredelt — keine Aktion nötig."` und stoppen.

## Schritt 3 — Erzählerischen Eintrag generieren

Schreibe einen neuen MASTER_BRAIN-Block mit diesen Sektionen:

```markdown
## YYYY-MM-DD — Genesis-Import [Projektname] <!-- Import-Genesis -->

> **Badge:** KI-veredelt (Import)

### Projektgeschichte

[2-4 Sätze über Entstehung, Alter, Entwicklung des Projekts — abgeleitet aus Git-Log und README]

### Wichtige Meilensteine

[Bulleted List der 3-5 signifikantesten Commit-Gruppen oder Phasen, erkennbar aus git log]

### Aktueller Stand

[1-2 Sätze was das Projekt heute tut, basierend auf README und jüngsten Commits]

### Offene Themen erkennbar aus Code

[Bulleted List mit 2-3 Themen die aus Commit-Messages oder README-Struktur als offen erkennbar sind. Falls nichts Konkretes: weglassen]
```

## Schritt 4 — Eintrag in MASTER_BRAIN.md ersetzen

Ersetze den kompletten Genesis-Block (vom `##`-Header bis zum nächsten `---` oder Ende der Datei) durch den neuen veredelten Block.

Behalte alle anderen Einträge in `MASTER_BRAIN.md` unverändert.

## Regeln

- Nur Fakten aus den gelesenen Quellen verwenden — keine Erfindungen
- Keine LLM-typischen Floskeln ("Als KI kann ich sagen…", "Beachte, dass…")
- Sprache: Deutsch
- Badge MUSS `KI-veredelt (Import)` lauten (exakt so, für Idempotenz-Check)
- Commit NICHT automatisch — User committet selbst

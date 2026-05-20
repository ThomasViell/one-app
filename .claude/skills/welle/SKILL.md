---
name: welle
description: Führt eine WELLE/Wave aus PLANS/ autonom aus. Inklusive Annahmen-Check, Build-Verifikation und Conventional Commit. Aufruf interaktiv mit `/welle <N>` oder mit `--auto` für headless-Modus.
---

# WELLE-Executor — {{ProjectName}}

Führt eine strukturierte WELLE aus `PLANS/` aus. Sechs Phasen der Reihe nach, keine überspringen.

## Argumente

- `/welle <N>` → interaktiv, Phase-1-STOPP zur Bestätigung
- `/welle <N> --auto` → headless, liest Annahmen aus `## Annahmen (pre-confirmed for autorun)`
- `/welle <N.S>` → nur STEP S aus WELLE N

Plan-Dateien: `PLANS/WELLE-<N>-<slug>.md`

## Phase 1 — Spec laden & Annahmen

- Plan aus `PLANS/` lesen, Ziel + Dateipfade + Akzeptanzkriterien ausgeben
- Interaktiv: Annahmen explizieren → STOPP → User bestätigt
- Autorun: `## Annahmen (pre-confirmed for autorun)` Sektion lesen, kein STOPP

## Phase 2 — Planning & Parallelisierung

- STEPs extrahieren, Abhängigkeitsgraph erstellen
- Unabhängige STEPs parallel via Sub-Agents
- Sequentielle STEPs: Schema → Repository → Service → ViewModel → View

## Phase 3 — Implementation

- Config-Dateien via Write-Tool, nie Terminal-Heredoc
- Keine Klartext-Secrets im Code
- Bei Unsicherheit → Phase 4 vor nächster Datei

## Phase 4 — Verifikation [BLOCKIEREND]

```bash
dotnet build --nologo   # 0 errors, 0 warnings
dotnet test --no-build  # alle Tests grün
```

Max. 3 Selbstkorrektur-Iterationen. Danach Stopp + Report.

## Phase 5 — Commit & Push

```
feat(welle-{N}): <summary>

- Dateien: N
- Tests: +N (gesamt N)
- Subsysteme: ...

Co-Authored-By: Claude <noreply@anthropic.com>
```

## Phase 6 — Retro

- `WAVE_PROGRESS.md` ergänzen
- `MASTER_BRAIN.md` Tageseintrag mit Badge "Autorun"
- Kurze Retro: was lief gut, wo Annahmen korrigiert, Folge-Welle

## Exit-Codes

- `0` — Erfolg
- `1` — Verifikation nach 3 Iterationen rot
- `2` — Plan nicht gefunden
- `3` — Autorun ohne Annahmen-Sektion
- `4` — Annahmen widerlegt

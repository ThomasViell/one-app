# PLANS — drainq.one

Dieser Ordner enthält die WELLE-Pläne für das Projekt.

## Namensschema

```
WELLE-<N>-<slug>.md
```

Beispiel: `WELLE-1-grundstruktur.md`

## Aufbau eines Welle-Plans

```markdown
# WELLE N — Titel

**Status:** PLANNED | IN_PROGRESS | DONE
**Geschätzte Dauer:** X Stunden

## Ziel
...

## STEPs
### STEP 1 — ...

## Annahmen (pre-confirmed for autorun)
...
```

## Ausführung

```bash
/welle <N>         # interaktiv
/welle <N> --auto  # autorun (headless)
```

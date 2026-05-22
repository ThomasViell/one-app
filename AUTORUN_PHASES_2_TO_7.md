# Autorun Phasen 2-7 — Bedienungsanleitung

## Was das macht
`autorun_phases.ps1` startet Claude Code **headless** sechs Mal hintereinander, jeweils mit eigenem Kontext (entspricht `/clear` zwischen den Phasen). Jede Phase liest den `RESULT_PHASE_N-1.md` als Eingang und schreibt `RESULT_PHASE_N.md` als Ausgang. Das Skript bricht ab, sobald ein Result-File fehlt oder Claude Code mit Fehlercode zurueckkommt.

## Voraussetzungen
- Phase 1 ist durch, `RESULT_PHASE_1.md` liegt im Repo-Root.
- `claude` CLI ist im PATH (`claude --version` testen).
- Eine Pro-/Max-Subscription oder API-Key ist aktiv.
- PowerShell-Execution-Policy erlaubt lokale Skripte: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` (einmalig).

## Wichtige Flags im Skript
- `--model sonnet|haiku` — schaltet pro Phase zwischen Modellen.
- `--dangerously-skip-permissions` — noetig fuer autonomes Arbeiten ohne Rueckfrage bei jedem Edit/Bash. **Nur in vertrauenswuerdigem Repo verwenden.**
- Effort wird ueber Text-Hint im Prompt gesteuert (`think`, `think harder`).

## Start

### Option A — Sequentiell (eine PowerShell-Session, alle Phasen nacheinander)

```powershell
cd C:\Projekte\drainq.one
.\autorun_phases.ps1 *>&1 | Tee-Object -FilePath autorun.log
```

`Tee-Object` schreibt das Log nach `autorun.log` UND auf den Bildschirm.

### Option B — Parallel (zwei Terminals, Phasen 2+3 gleichzeitig)

Wenn Du Phase 2 und 3 parallel laufen lassen willst (laut Phasenplan moeglich), brauchst Du eine angepasste Variante. Sag mir Bescheid, dann splitte ich das Skript in `autorun_main.ps1` (Phase 2,4,5,7) und `autorun_side.ps1` (Phase 3,6).

## Modell- und Effort-Plan (im Skript hinterlegt)

| Phase | Modell | Effort-Hint |
|---|---|---|
| 2 FFmpeg-Decoder | sonnet | think harder |
| 3 OSD-Renderer | sonnet | think |
| 4 Integration | sonnet | think harder |
| 5 Recording | sonnet | think |
| 6 Cleanup/Docs | haiku | — |
| 7 libVLC-Ausbau | sonnet | — |

## Was tun bei Abbruch
- `autorun.log` lesen — letzte Ausgaben zeigen wo es haengt.
- Letztes RESULT_PHASE_N.md pruefen.
- Skript ab der gescheiterten Phase neu starten (Code anpassen: `$phases`-Array vorne kuerzen).

## Nach Abschluss
Das Skript listet am Ende alle `RESULT_PHASE_*.md`. Kopier sie mir nacheinander rueber, dann mache ich Sprint-Reviews und entscheide ueber den Pilot-Rollout.

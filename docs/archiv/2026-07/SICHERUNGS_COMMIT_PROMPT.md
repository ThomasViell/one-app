# AUFTRAG: Sicherungs-Commit drainq.one — 83 unverfolgte Dateien

ROLLE: Du bist Software-Ingenieur im Repo `C:\Projekte\drainq.one`.
Aktiver Branch: `feature/dual-mode` (HEAD `8f04301`, synchron mit origin).

## Warum
83 unverfolgte Dateien liegen im Arbeitsbaum und sind in keinem Commit — darunter
die komplette Engineering-Disziplin, ADR-0004, alle WH-/FIX-Prompts und Run-Reports
sowie die 0.5.19-Handbuch-PDFs. Im sacpp-Repo sind auf genau diesem Weg am 27.07.
rund 47 Dateien endgueltig verloren gegangen. `wip/feierabend` sichert unverfolgte
Dateien NICHT.

## HARTE REGELN
1. **KEIN `git add -A`, KEIN `git add .`, KEINE repo-weiten Git-Befehle.**
   Nur die unten namentlich gelisteten Pfade stagen.
2. **Kein `git checkout`, kein `git reset`, kein `git clean`, kein Branch-Wechsel.**
3. Die ~210 als "modified" gemeldeten Dateien sind **CRLF-Phantom** — nicht anfassen.
   Einzige echte Aenderung ist ein BOM in `tools/publish-one-release.ps1` — ebenfalls
   NICHT committen.
4. Alle Git-Befehle **lokal in PowerShell** ausfuehren, nicht ueber einen Mount.
   Falls `.git\index.lock` existiert: `del .git\index.lock`.
5. Kein Build, kein Test, kein Publish, kein Merge. Nur Git.

## SCHRITT 1 — .gitignore erweitern
Haenge ans Ende von `.gitignore` an (Datei bleibt sonst unveraendert):

```
# Laufzeit-DataStore der App (kein Quellcode)
app/datastore/

# Autotest-/OEM-Screenshots und UI-Dumps (>160 MB Wegwerf-Artefakte)
tools/_autotest/
tools/_oem/

# Pseudo-Loeschordner (Cowork-Mount kann nicht loeschen)
_to_delete/
```

## SCHRITT 2 — Commit A: Engineering-Doku + ADR
Stage genau diese Pfade:

```
docs/adr/0004-synthetic-screenshots.md
docs/engineering/01-analysis_one.md
docs/engineering/01-audit_one.md
docs/engineering/02-audit_one.md
docs/engineering/02-project_one.md
docs/engineering/03-audit_one.md
docs/engineering/03-implementation_one.md
docs/engineering/04-audit_one.md
docs/engineering/04-testing_one.md
docs/engineering/06-audit_one.md
docs/engineering/naming-convention_one.md
```

Commit-Message:
```
docs(engineering): Disziplinen 01-04/06 + Naming-Convention + ADR-0004 nachtragen

Bisher unverfolgt im Arbeitsbaum - Verlustrisiko geschlossen.
```

## SCHRITT 3 — Commit B: Prompts, Run-Reports, Plaene
Stage genau diese Pfade:

```
CHG05_UND_PUSH_PROMPT.md
FEATURE_SPEICHERANZEIGE_PROMPT.md
FIX_014_KAMERATYP_LISTE_RECORDER_PROMPT.md
FIX_059_HARDBUTTON_UND_CLEANUP_PROMPT.md
FIX_HARDBUTTON_LICHT_SONDE_PROMPT.md
FIX_HARDBUTTON_POPUP_FOCUS_PROMPT.md
FIX_SONDE_CYCLE_STATE_PROMPT.md
FIX_SPEICHER_AUTO_REFRESH_PROMPT.md
FIX_VIDEO_PERMISSION_PERSIST_PROMPT.md
MERGE_HELP_RUN_PROMPT.md
PLAN_HILFESYSTEM_2026-07-16.md
RESULT_MERGE_HELP.md
WH1_HARNESS_PROMPT.md
WH3_MASTER_RUN_PROMPT.md
WH4B_LANG_FIX_PROMPT.md
WH4_SYNTH_SCREENSHOTS_PROMPT.md
WH5_DOKU_GATES_PROMPT.md
WH_MASTER_RUN_PROMPT.md
publish-one-l10n.README.md
```

Commit-Message:
```
docs(prompts): Hilfesystem-Welle WH1-WH5 + FIX-Prompts + Run-Reports nachtragen
```

## SCHRITT 4 — Commit C: Handbuecher 0.5.19 + Manual-Asset
Stage genau diese Pfade:

```
docs/manual/DrainQ-ONE_Bedienungsanleitung_de_0.5.19.pdf
docs/manual/DrainQ-ONE_Bedienungsanleitung_en_0.5.19.pdf
tools/manual/assets/pipe_frame.png
```

Hinweis: die 0.5.17-PDFs sind bereits versioniert, die 0.5.19er gehoeren also dazu.
`.gitignore` enthaelt `/*.pdf` — das greift nur im Repo-Root, `docs/manual/` ist
nicht betroffen. Falls Git die Dateien dennoch als ignoriert meldet: `git add -f`
NUR fuer diese drei Pfade.

Commit-Message:
```
docs(manual): Bedienungsanleitung DE+EN 0.5.19 + pipe_frame-Asset
```

## SCHRITT 5 — .gitignore committen
```
.gitignore
```
Commit-Message:
```
chore(git): Autotest-/OEM-Screenshots, app/datastore und _to_delete ignorieren
```

## SCHRITT 6 — Push
```
git push origin feature/dual-mode
```

## SCHRITT 7 — Verifikation und Bericht
Fuehre aus und gib die Ausgaben woertlich wieder:
```
git log --oneline -6
git status --porcelain | Select-String '^\?\?' | Measure-Object -Line
git status --porcelain | Select-String '^\?\?'
```

Berichte:
- die vier Commit-Hashes
- Anzahl verbleibender unverfolgter Eintraege (Erwartung: nur noch die vier
  ignorierten Ordner sind weg, also 0 unerwartete Eintraege)
- Push-Ergebnis (alter..neuer Hash)

**STOPP danach.** Kein Merge nach master, kein Tag, kein Release-Publish.

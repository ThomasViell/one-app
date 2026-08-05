# Ergebnis: Dokumente archivieren — Repo-Root aufräumen (Teil 1 von 2)

Branch: `docs/archiv-2026-08-05` (von `master`, nicht gemerged, nicht getaggt). Zwei Commits, je einmal gepusht nach AP-2 und AP-3.

## Zahlen (Regel 36 — am Ergebnis gezählt)

- Root-Markdown-Dateien **vorher:** 120 (115 getrackt + 5 untracked, geprüft per `git ls-files '*.md' | grep -v '/'` + `git status --porcelain`)
- Root-Markdown-Dateien **jetzt:** 10
- Dateien im Archiv (`docs/archiv/**/*.md`, ohne `README.md`): **110**
- 10 + 110 = 120 — Bilanz geschlossen, keine Datei verschwunden oder doppelt gezählt
- Tests: **449/449 grün**, 0 Failures, 0 Errors (`./gradlew testDebugUnitTest`, BUILD SUCCESSFUL in 1m 24s)
- Doku-Gate `HelpCoverageTest`: **2/2 grün** — von der Verschiebung nicht betroffen (Hilfe-System-Assets unter `assets/help/*` wurden nicht angefasst)
- Tote Verweise in den 10 verbliebenen Root-Dateien: **0** nach Korrektur (3 in `README.md`, 1 in `docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md`)

## AP-1 — Zuordnungstabelle (freigegeben vom CEO, mit drei Auflagen)

**Klasse A — bleibt im Root (8):** README.md, CLAUDE.md, PROJECT_STATUS.md, CHANGELOG.md (Vorgabe), OFFENE_TODOS_2026-07-01/09/12.md (Lauf 2, unangetastet), HELP_UPDATE_PROMPT.md (laufende Prozessvorlage, von CLAUDE.md aktiv referenziert).

**Klasse C — überholt, ins Archiv mit ÜBERHOLT-Zeile (5):** CLAUDE_CODE_XML_EXPORT.md, XML_EXPORT_IMPLEMENTATION.md (beide durch CEO-Entscheid W1-E, 2026-06-07, „kein XML-Export" ersetzt), QR_EINRICHTUNG_PROMPT.md (trug bereits eigenen ÜBERHOLT-Hinweis vom 30.07.2026), HANDOVER.md (Zweck von PROJECT_STATUS.md übernommen), HANDOVER_SESSION_2026-06-02.md (explizit durch HANDOVER_SESSION_2026-06-04.md abgelöst).

**Klasse D — bleibt liegen, CEO-Auflage 2 (2):** HANDBUCH_USB_EXPORT.md, publish-one-l10n.README.md. Beide **nicht verschoben, nicht gekennzeichnet**, gehen als offene Punkte in Lauf 2:
- HANDBUCH_USB_EXPORT.md: Inhalt (Textbaustein für die Bedienungsanleitung, Louis-Feedback #9b) ist noch nicht in `ONE_APP_Bedienungsanleitung.docx/pdf` eingeflossen.
- publish-one-l10n.README.md: Status unklar — beschreibt ein Skript im Parallel-Repo `drainq.one-localization` mit offenem Blocker; ob durch den neueren „L10N Phase 0"-Ansatz abgelöst, ist nicht geklärt.

**Klasse B — abgeschlossener Bericht/Auftrag, ins Archiv (105):** alle übrigen Dateien — `RESULT_*` (32), `RUN_REPORT_*`/`TESTREPORT_*`, datierte `HANDOVER_*`, alle `*_PROMPT.md`-Einzelaufträge mit passendem RESULT-Nachweis oder Commit-Beleg, sowie datierte Analysen/Pläne/Runbooks.

## Auflage 1 — Nachlauf „tun soll" vs. „getan hat"

Alle 105 B-Dateien inhaltlich geprüft (nicht nur Kopf/Titel). **Ergebnis: 0 Umsortierungen.** Die namentlich benannten Verdachtsfälle sowie alle weiteren Verfahrens-artigen Namen (RUNBOOK/TESTPLAN/PLAN/BACKLOG/MASTER_RUN) sind durchgängig versions-/datumsgebunden:

- `RELEASE_LOUIS_BETA_RUNBOOK.md` — „Stand: vorbereitet 2026-06-13", feste Version 402/0.4.2 → historischer Einzel-Release, kein wiederverwendbares Verfahren (der reale, laufende Weg ist `tools/publish-one-release.ps1`).
- `RELEASE_LOUIS_BETA_W1W2_RUNBOOK.md` — „Stand: vorbereitet 2026-07-07", feste Version 501/0.5.1.
- `TESTPLAN_BETA_ONDEVICE.md` — „Gerät: ONE (Serial `233b4bd2865177ed`) · Stand: Branch `feature/beta-wave-1` (`48c454f`) · Datum: 2026-06-06", Testfälle referenzieren konkrete Commits — einmalige Objektabnahme.
- (HANDBUCH_USB_EXPORT.md war nicht Teil dieser Prüfung — Auflage 2, bleibt ohnehin liegen.)

Sanity-Check per Namensmuster (`RUNBOOK|TESTPLAN|HANDBUCH|VERFAHREN|ANLEITUNG|GUIDE|CHECKLIST`) bestätigt: keine weiteren Verfahrens-Dateien in der 105er-Liste übersehen.

## AP-2 — Archiv angelegt und befüllt

Struktur nach inhaltlichem Datum (Dateiname → Dokumentkopf → `git log --diff-filter=A`, in dieser Reihenfolge):

```
docs/archiv/2026-02/  (2 Dateien)
docs/archiv/2026-05/  (12 Dateien)
docs/archiv/2026-06/  (20 Dateien)
docs/archiv/2026-07/  (76 Dateien)
docs/archiv/README.md
```

110 Dateien mit `git mv` verschoben (0 Fehlschläge). Die 5 zuvor untracked Prompt-Dateien (`KAMERA_BEFUND_PROMPT.md`, `MERGE_UND_RELEASE_PROMPT.md`, `PLATTFORMSIGNATUR_TESTLAUF_PROMPT.md`, `SICHERUNGS_COMMIT_PROMPT.md`, `WERKSEINRICHTUNG_PROMPT.md`) wurden vorab mit `git add <Dateiname>` einzeln benannt gestaged (kein `-A`), dann verschoben. Inhalte unverändert, außer den 5 C-Dateien: dort oben eine Zeile `> ÜBERHOLT am <Datum> durch <Datei>. Nur noch als Nachweis aufbewahrt.` ergänzt (bei QR_EINRICHTUNG_PROMPT.md war das bereits vorhanden, nicht dupliziert).

## AP-3 — Verweise geradegezogen

Durchsucht: `README.md`, `CLAUDE.md`, `PROJECT_STATUS.md`, `CHANGELOG.md`, alles unter `docs/`, `.github/`, `tools/`, `scripts/`, sowie Gradle-Build-Dateien, nach den 110 Basename-Treffern und nach Markdown-Link-Syntax (`](...)`).

- Keine funktionalen Verweise in `tools/`, `.github/` oder Gradle-Dateien gefunden (nur zwei Kommentarzeilen in `tools/publish-one-release.ps1` und `tools/werkseinrichtung/Update-WerkzeugApp.ps1`, die auf `AUTOUPDATE_WERKZEUG_PROMPT.md` verweisen — reine Prosa-Kommentare, kein Code liest den Pfad, keine Gate-Abhängigkeit).
- Keine Markdown-Link-Syntax (`[...](Datei.md)`) im gesamten Repo auf eine verschobene Datei gefunden — 0 technisch kaputte Links.
- **Echte Korrektur nötig (2 Dateien, 4 Stellen):** `README.md` (3×) und `docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md` (1×) verwiesen auf `HANDOVER.md` als lebendige Dokumentation — jetzt archiviert und Klasse C (überholt durch `PROJECT_STATUS.md`). Beide auf `PROJECT_STATUS.md` umgestellt.
- Alle übrigen Fundstellen (`PROJECT_STATUS.md`, `docs/adr/*`, `docs/engineering/04-testing_one.md`, `docs/UPDATE_PROCESS_PHASENPLAN.md`, `docs/WERKSEINRICHTUNG.md`, die drei `OFFENE_TODOS_*.md`) sind Prosa-Zitate in Backticks ohne Pfadangabe, innerhalb von Berichten/Plänen, die selbst historische Nachweise sind oder auf historische Nachweise verweisen (z.B. „siehe `RESULT_PHASE_6.md`" als Beleg für einen früheren Zustand). Das sind keine klickbaren Links und keine Gate-Abhängigkeiten — unverändert gelassen, wie in Auflage 1 bestätigt.

## AP-4 — Nachweis

- `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL**, 449/449 Tests grün, 0 Failures/Errors (JUnit-XML ausgezählt, nicht nur Exit-Code).
- `HelpCoverageTest`: 2/2 grün — Hilfe-System-Gate unberührt.
- Root-Verweisprüfung nach Fix: 0 Treffer für `HANDOVER.md` mehr in `README.md`/`docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md`.
- Zählung: 120 → 10 (Root) + 110 (Archiv) = 120.

## Was ich nicht entscheiden konnte

Beide D-Fälle bleiben laut Auflage 2 unverändert liegen und wandern als offene Punkte in Lauf 2:
1. **HANDBUCH_USB_EXPORT.md** — ob der Inhalt bereits in die Bedienungsanleitung übernommen wurde, konnte ich ohne Diff gegen `ONE_APP_Bedienungsanleitung.docx` nicht klären.
2. **publish-one-l10n.README.md** — Status des beschriebenen Skripts im Parallel-Repo `drainq.one-localization` unklar, ob fortgeführt oder durch „L10N Phase 0" abgelöst.

## Nicht angefasst

APKs, Build-Logs, Screenshots, Keystores, sonstige Nicht-Markdown-Altlasten im Root (eigenes Thema). Die drei `OFFENE_TODOS_*.md` (Lauf 2). `.claude/settings.local.json` und weitere vorbestehende, mit diesem Auftrag nicht zusammenhängende Änderungen im Arbeitsverzeichnis (`.claude-lauf.lock`, `_ap4_evidence/`, `_ap6_publish_log.txt`, `tools/werkseinrichtung/ARBEITSANWEISUNG_FERTIGUNG.md`) wurden nicht gestaged oder committet.

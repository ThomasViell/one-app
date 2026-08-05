# FIX+MERGE-RUN: feature/help-system → feature/dual-mode (sauber, mit Test-Root-Cause)

**Modus:** vollautonom. Repo `C:\Projekte\drainq.one`. Kein Merge nach master. Kein `git add -A`.
**Ausgangslage (verifiziert 18.07.):** HEAD=feature/help-system (c4caf20, gepusht). Der CEO-Merge
scheiterte: checkout dual-mode wurde nie wirksam. Lokaler `feature/dual-mode` (73deeea) und
`origin/feature/dual-mode` (a6d5bc1) DIVERGIEREN — vermutlich lokale Commits vom 16.07.
(Video-Permission-Persist, siehe RESULT_VIDEO_PERMISSION_PERSIST.md / CHG05_UND_PUSH_PROMPT.md).
`UpdateE2ETest > downloadAndInstall - disconnect during body logs DOWNLOAD_FAIL` ist ROT
(UncaughtExceptionsBeforeTest, UpdateE2ETest.kt:134) — bekannt seit W-H4 (dort per --tests-Filter
umgangen), KEINE Folge des Hilfe-Systems.

## Phase 1 — Lage klären
`git status` (warum scheiterte der checkout? uncommitted/untracked Kollisionen dokumentieren,
nichts wegwerfen), `git fetch --all`, dann `git log --oneline origin/feature/dual-mode..feature/dual-mode`
und Gegenrichtung: Wer ist ahead/behind? Befund ins Protokoll.

## Phase 2 — dual-mode synchronisieren
Lokale dual-mode-Commits, die origin fehlen (Video-Permission etc.): pushen. Falls origin Commits
hat, die lokal fehlen: fast-forward/mergen. Ziel: local == origin auf feature/dual-mode,
NICHTS verlieren. Bei echtem Konflikt: abbrechen + Bericht, nicht raten.

## Phase 3 — Test-Root-Cause UpdateE2ETest (VOR dem Merge, auf help-system)
Regel „Root-Cause statt Workaround": Ursache des UncaughtExceptionsBeforeTest im
Disconnect-Testfall finden (typisch: MockWebServer-Disconnect wirft auf Hintergrund-Dispatcher,
Exception entkommt dem runTest-Scope). Sauber fixen (Exception-Handler/Dispatcher-Injektion im
Test, KEINE Produktionscode-Änderung ohne Not). `testDebugUnitTest` KOMPLETT (448 Tests, ohne
Filter) muss GRÜN sein — 3× hintereinander laufen lassen (Flaky-Beweis). Nur wenn nach 2
Fix-Iterationen nicht lösbar: `@Ignore` mit Begründung + CHG-Ticket in 06-maintenance — als
dokumentierte Ausnahme, im Bericht rot markieren. Commit auf feature/help-system + push.

## Phase 4 — Merge + Verifikation
`git checkout feature/dual-mode` (muss jetzt sauber gehen) →
`git merge --no-ff feature/help-system` → Konflikte lösen (erwartet: keine/trivial) →
`.\gradlew assembleDebug :app:testDebugUnitTest` GRÜN → `git push`.
Beweis ins Protokoll: `git log --oneline -5` + Testzusammenfassung.

## Phase 5 — Bericht
`RESULT_MERGE_HELP.md`: Phase-1-Befund (warum checkout scheiterte, wer war ahead/behind),
Test-Root-Cause + Fix, Merge-Commit-SHA, finale Teststände, was gepusht wurde. STOPP —
kein Release-Publish in diesem Lauf (macht der CEO danach per Ein-Befehl, Gates laufen mit).

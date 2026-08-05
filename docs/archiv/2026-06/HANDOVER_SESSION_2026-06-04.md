# Handover drainq.one — Session 2026-06-04

Für den Neustart in einem frischen Chat. Wichtigstes zuerst. (Löst den Stand vom 02.06. ab — der ist überholt.)

## Sofort beim Start
1. Diese Datei + `PROJECT_STATUS.md` lesen.
2. **Stand prüfen, nicht raten:** `git log --oneline -10` und `git status -sb`. „Erledigt/committet?" **immer gegen `git log`/`git show`**, nie gegen den Working Tree (Grep/Read sehen auch Uncommittetes).
3. **Push offen:** `master` war zuletzt 7 Commits vor `origin/master`. Prüfen, ob gepusht.

## Umgebung / Gotchas (wichtig)
- App läuft auf der **ONE-Hardware** (RK3588), Serial `/dev/ttyS5`, Video V4L2 `/dev/video0`. Gerät-Serial `233b4bd2865177ed`.
- Build: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`.
- **Git nur lokal** (Cowork-Mount kann nicht schreiben; stale `.git\index.lock` → `del`).
- **bash-Reads über den Mount lügen** — Integrität nur mit Read-Tool / lokal.
- **Kein `su`** (`error=13`) — HW-Serial läuft nativ im App-Prozess.
- **CRLF-Churn:** über den Mount sehen ~213 Dateien „geändert" aus = reiner Zeilenende-Churn. Beim Committen NUR gezielt per Pfad adden, nie `git add -A`.

## Diese Session (2026-06-04) erledigt
- **#14 Soft-Keyboard folgt App-Sprache** (`hintLocales`) + **Compose-1.7-Bump** — `eb390ba`, 7 Dateien, `gradlew assembleDebug` BUILD SUCCESSFUL. (i18n de/en.json bewusst draußen: reiner CRLF-Churn.)
- **Lose Dateien aufgeräumt** — `3f758cb`: PROVISIONING-Doc committet, PDFs via `.gitignore /*.pdf` raus, 3 Commit-Helfer gelöscht.
- **Stand-Verifikation** gegen Repo: gesamter Briefing-Backlog ist seit 03.06. committet (siehe PROJECT_STATUS „Fertig + committet").

## Offen (Reihenfolge)
1. Push `origin master` (Backup, kein Geräte-Update — das nur über Versions-Tag).
2. **CRLF-Churn-Aufräum-Commit:** `.gitattributes` = `* text=auto eol=lf` + `git add --renormalize .`, separat von Features.
3. **btn-Index ↔ physische Taste** am Gerät per Logcat (`OneInternalHW`) bestätigen — Handler ist da.
4. **Autostart auf der ONE** (HOME-Launcher/Boot-Receiver) — geparkt.
5. **Reorg** app-one → `drainq-android`; Update-Client → `drainq-cloud`.

## Referenzen
- HW-Protokoll (dekompilierte Original-App): `C:\Projekte\one-revers`, `one-reverse-software` — Frame: Magic `FA AF 00 10 00 01` + [len][group][payload][xor]; Base-Control group 0x01: byte2=power, byte3=light(0–100), byte4=freq; RX-Gruppen 21/22/23/24; `btn1..6` in `GROUP_STATUS` payload[3..8].
- Feldtest-Analyse: `FEEDBACK_Jakob_2026-06-02_Analyse.md`. Provisionierung: `docs/PROVISIONING_GOLDEN_IMAGE.md`.

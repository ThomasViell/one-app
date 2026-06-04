# drainq.one — Status

**Stand:** 2026-06-04 · **Rolle:** ONE-Schiebekamera — läuft **direkt auf der ONE-Hardware** (RK3588, Android), Steuerung seriell `/dev/ttyS5`, Video V4L2 `/dev/video0`
**Stack:** Kotlin / Jetpack Compose (Room, Koin, ExoPlayer/Media3, iText7) · NDK (`app/src/main/cpp/v4l2bridge.c`) · **Pfad:** `C:\Projekte\drainq.one` (GitHub: ThomasViell/one-app)
**Branch:** master · **Gerät:** Serial `233b4bd2865177ed`
**Build:** `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`

## Aktueller Stand (Session 2026-06-04)
**Der komplette Briefing-/Jakob-Backlog ist umgesetzt, committet und gebaut.** `master` ist **7 Commits vor `origin/master`** (Push = reines Backup, löst kein Geräte-Update aus — das kommt nur über einen Versions-Tag). Letzter Build `gradlew assembleDebug`: **BUILD SUCCESSFUL**.

### Fertig + committet
- **#5 Kiosk/Vollbild** als Schalter (Standard AUS) · **#4 Tastatur-Dismiss** in Dialogen · **#8 Schnellaufnahme** (Tages-Bucket, Foto aus V4L2-Live-Frame, Blitz) · Home/Inspektion aufgeräumt — `29de01e`
- **Hardware-Serial-Fix (nativ, termios 9600 8N1):** Meter, Meter-Reset, Sonde laufen; Frequenz 1=33k/2=640/3=512, Licht 0–100 — `29de01e`
- **#3/#7 UI-Politur:** Extended-FAB „Neues Projekt", Zurück aus Inspektion, Action-Icons 40dp, Notiz direkt anlegen — `9c175d6`
- **#1/#3 Hardtasten F1-F8 + Softbutton-Leiste**, **Licht −/+ über Taste** (Slider-Rekompositions-Problem gelöst), Sonde-Popup, **#16 Vollbild/Cinema-Inspektion** — `607217d`; Licht am Gerät verifiziert 03.06.
- **#15 V4L2-Recording** (Lokal-Video, Echtzeit via FFmpeg-Pipe), globaler KeyboardHideButton (`KeyboardSupport.kt`), Icons app-weit 40dp — `d8340ca`
- **#14 Soft-Keyboard folgt App-Sprache** (`hintLocales`) + **Compose-1.7-Bump** — `eb390ba`, 7 Dateien, Build grün. (i18n de/en.json bewusst nicht mit: reiner CRLF-Churn ohne Inhaltsdiff.)
- **Lose Dateien aufgeräumt:** PROVISIONING-Doc committet, PDFs via `.gitignore /*.pdf` ausgeschlossen, Commit-Helfer gelöscht — `3f758cb`

## Offen
1. **Push** `origin master` (7 Commits ahead) — Backup, kein Geräte-Update.
2. **CRLF-Churn aufräumen:** Working-Tree zeigt über den Cowork-Mount ~213 Dateien „geändert" = reiner CRLF/LF-Churn. `.gitattributes` auf `* text=auto eol=lf` + `git add --renormalize .` als **eigener** Aufräum-Commit (nicht mit Features mischen).
3. **btn-Index ↔ physische Taste** einmalig am Gerät per Logcat (`OneInternalHW`) bestätigen — Handler ist da, nur das Mapping.
4. **Autostart auf der ONE** (HOME-Launcher/Boot-Receiver) — geparkt.
5. **Reorg:** app-one → `drainq-android`, Update-Client → `drainq-cloud`.

## Wichtige Hinweise
- **Git nur lokal** im echten Terminal — Cowork-Mount kann nicht schreiben (kein `unlink`); stale `.git/index.lock` → `del .git\index.lock` (kein Git-Prozess hält es).
- **bash-Reads über den Mount lügen** (NUL-Bytes, Truncation, CRLF-Churn) — Datei-Integrität nur mit Read-Tool / lokal prüfen.
- **„Committet?" immer gegen `git log`/`git show` prüfen, nicht gegen den Working Tree** — Grep/Read finden auch uncommittete Änderungen (führte bei der Verifikation kurz zur Fehlannahme, #14 sei schon drin).
- **Kein `su`** in der App (`error=13`) — privilegierte Geräte-Ops nicht über su; HW-Serial läuft nativ im App-Prozess.
- HW-Protokoll-Referenz: dekompilierte Original-App in `C:\Projekte\one-revers` / `one-reverse-software`.

## Letzte Änderungen
- [2026-06-04] #14 Keyboard-Sprache + Compose-1.7 (`eb390ba`, Build OK); lose Dateien aufgeräumt (`3f758cb`); Status-Docs nachgezogen.
- [2026-06-03] Hardtasten/Softbutton-Leiste, Licht −/+, V4L2-Recording, Cinema, UI-Politur (`d8340ca`,`607217d`,`9c175d6`).
- [2026-06-02] Feldtest-Findings + HW-Serial-Fix nativ, Kiosk-Schalter, Schnellaufnahme (`29de01e`). Details: `HANDOVER_SESSION_2026-06-04.md`, `FEEDBACK_Jakob_2026-06-02_Analyse.md`.

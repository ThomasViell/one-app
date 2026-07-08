# Übergabe — drainq.one, Louis-Feedback 06.07. (Stand 2026-07-07)

Für den neuen Tageschat. Kurz, Wichtigstes zuerst.

## Schlagzeile
Louis' Beta-Test 06.07. (9 Kommentare) verifiziert und abgearbeitet: **7 Punkte im Code erledigt** (Welle 1 + Welle 2 auf `feature/dual-mode`), **Beta 0.5.1/501 gebaut + im Portal + auf erste Test-ONE**, Louis re-testet. Offen: nur **#8 (Durchmesser/Länge)** — am Gerät nachstellen.

## Was heute lief
- **Welle 1 — Quick Wins** (Commits `d31a462`,`287f561`,`df37dfe`,`067fb5f`,`44910ed`; Bericht `RESULT_LOUIS_W1_QUICKWINS.md`; 280 Tests grün):
  Licht bis 100 % · Sonde aktive Frequenz grün · Settings-Knopf „Datum & Uhrzeit stellen" (`ACTION_DATE_SETTINGS`) · X am Info-Panel · Schadensart-Dropdown → `AlertDialog` (dialog-fest).
- **Welle 2 — Video** (Commits `614ec40`,`59c1569`,`8606909`,`821b769`; Bericht `RESULT_LOUIS_W2_VIDEO.md`):
  `RecorderRemux.kt` (`-c copy -movflags +faststart`) remuxt beim Stopp → behebt „nur Endzeit" (#5b) + abgeschnittenes Video nach Pause (#9a); live weiter fragmentiert = absturzsicher. Nachweis `scripts/verify_remux_faststart.ps1`: mvhd.duration frag=0 s → remuxt=8 s. (ffprobe maskiert den Bug, nur header-lesende Player/Android betroffen.)
- **Beta-Build 0.5.1 / versionCode 501** OHNE Keystore (`.\gradlew.bat assembleDebug` → `app-debug.apk`; Kopie `DrainQ-ONE_0.5.1-beta_501.apk`). sha256 `81a7df070c5b70a478146c5f1047321b7e4eff6b646f4d0ae766b74ffbe593eb`, size `163166690`. Ins Portal (Channel beta, license.drainq.com) geladen, auf erste Test-ONE self-updated.
- **Mail an Louis** (l.wigman@uip.team): Outlook-Entwurf „RE: Test ONE DrainQ Beta version" — von Thomas gesendet. Pro Punkt Fix + gezielte #8-Rückfrage + Testliste + USB-Kurzanleitung.
- **#9b USB dokumentiert:** `HANDBUCH_USB_EXPORT.md` (FAT32/exFAT, „Alle Dateien"-Zugriff, Ziel `DrainQ/<Projektnr>/`).

## Louis' 9 Punkte — Status
| # | Thema | Status |
|---|---|---|
| 1+6 | Schadensart nicht änderbar | ✅ W1 (AlertDialog), Geräte-Check offen |
| 2 | Sonde aktive Frequenz unsichtbar | ✅ W1 (grün) |
| 3 | Info-Panel nicht schließbar | ✅ W1 (X am Panel) |
| 4 | Licht max 90 % | ✅ W1 (100 %) |
| 5a | Foto aus Video | ✅ schon vorher gefixt (`35fd3f5`) |
| 5b | nur Endzeit, kein Timer | ✅ W2 |
| 7 | Datum 01.01.2021 / Uhr | ✅ W1 (Datum=heute; Uhr-Knopf) |
| 9a | Video nach Pause abgeschnitten | ✅ W2 |
| 9b | Report auf USB | ✅ dokumentiert (Funktion existierte) |
| **8** | **Durchmesser/Länge im Report** | **OFFEN — Code intakt, nur am Gerät reproduzierbar** |

## Offen / nächste Schritte
1. **Louis' Re-Test abwarten** — v. a. exakt, was bei **#8** scheitert (Feld nicht antippbar / Tastatur / Wert weg nach Speichern / Report-Zeile leer). Erst das entscheidet: echter Bug oder Bedien-Thema.
2. **Geräte-Abnahme W1+W2** an der ONE — Checkliste in `RELEASE_LOUIS_BETA_W1W2_RUNBOOK.md` (W1 5 Punkte, W2 Video beide Modi, #8-Repro).
3. Danach: Merge-Entscheidung `feature/dual-mode` → master/Tag (bislang bewusst kein Merge).

## Wichtige Regeln (nicht vergessen)
- **Beta baut OHNE Keystore:** `.\gradlew.bat assembleDebug` → `app-debug.apk`. NIE `assembleRelease`/KEYSTORE_-Env vorschlagen.
- Git-Zustand NIE über Sandbox-bash beurteilen (CRLF-Phantom-Diffs) — Host-Read/Grep ist Schiedsrichter.
- Branch: `feature/dual-mode`. DrainQ-Dachordner ist in dieser Session NICHT gemountet — nur `C:\Projekte\drainq.one`.

## Verlustrisiko (uncommittet)
Neue Doku-Dateien im Repo-Root sind **untracked** (von CC nicht committet): `LOUIS_06-07_WELLE1_QUICKWINS_PROMPT.md`, `LOUIS_06-07_WELLE2_VIDEO_PROMPT.md`, `HANDBUCH_USB_EXPORT.md`, `RELEASE_LOUIS_BETA_W1W2_RUNBOOK.md`, `HANDOVER_2026-07-07_LOUIS.md`. Code (W1/W2) ist committet. Empfohlene Sicherung: siehe Report unten.

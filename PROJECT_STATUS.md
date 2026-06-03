# drainq.one — Status

**Stand:** 2026-06-02 · **Rolle:** ONE-Schiebekamera — läuft **direkt auf der ONE-Hardware** (RK3588, Android), Steuerung seriell `/dev/ttyS5`, Video V4L2 `/dev/video0`
**Stack:** Kotlin / Jetpack Compose (Room, Koin, ExoPlayer/Media3, iText7) · NDK (`app/src/main/cpp/v4l2bridge.c`) · **Pfad:** `C:\Projekte\drainq.one` (GitHub: ThomasViell/one-app)
**Branch:** master · **Gerät:** Serial `233b4bd2865177ed`
**Build:** `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`

## Aktueller Stand (Session 2026-06-02)
Großer Feldtest-Durchlauf auf Basis von „Jakob Feedback 0206" + weiteren Findings. **Hardware-Steuerung repariert** (war komplett tot). Alle Code-Änderungen sind **uncommitted** und müssen lokal committed werden (Cowork-Mount kann kein Git schreiben).

### Fertig + verifiziert
- **#5 Kiosk/Vollbild** als Schalter in Einstellungen, **Standard AUS** (sperrt nicht mehr aus). `MainActivity` blendet System-Bars nur bei aktivem Kiosk aus.
- **#4 Tastatur-Dismiss** in Dialogen (Tap-außerhalb + „Fertig"-Tasten).
- **#8 Schnellaufnahme**: Inspektion ohne Projekt legt Tages-Bucket „Schnellaufnahme_ddMMyy" an; Foto/Schaden funktionieren; **Foto-Capture aus V4L2-Live-Frame** (vorher leer, da kein TextureView im Lokal-Modus); Foto-Blitz als Quittung.
- **Home:** „ONE Controller"-Karte entfernt.
- **Inspektion-Panel:** „Hardware status" + „Neu verbinden" entfernt.
- **Hardware-Kommunikation (Regression-Fix):** serielle I/O komplett nativ (termios 9600 8N1 raw, native read/write in `v4l2bridge.c`) statt rohem FileStream ohne termios + `available()`-Bug. **Meter, Meter-Reset, Sonde laufen.** Sonde-Frequenz-Mapping korrigiert (1=33kHz, 2=640Hz, 3=512Hz), Licht-Range 0–100.
- **Projektformular-Tastatur:** Wischen/Scrollen schließt Tastatur + Weiter/Fertig-Kette.

### Offen / zuletzt dran
- **#3 Licht — TEST OFFEN.** Slider ließ sich wegen ~30 Hz-Rekomposition nicht bewegen → durch **−/+ Tasten** ersetzt (10 %-Schritte). Letzter Build muss noch verifiziert werden. TX-Logging (`OneInternalHW: TX … -> hex`) aktiv zur Diagnose.

## Backlog (offene Jakob-/Folge-Punkte)
1. **#1/#3 Hardtasten + Softbutton-Leiste** — gemeinsame Aktionsliste, feste Leiste am physischen Tastenraster; `btn1..6` kommen aus RX-`GROUP_STATUS` (payload[3..8], aktuell verworfen). Byte-Index↔Tastenposition per Logcat ermitteln.
2. **#7 Zurück aus Inspektion + „Gallery"-Benennung** (ProjectDetail ist Projektverzeichnis).
3. **#3 FAB „Neues Projekt"** in Projektliste vergrößern (Extended-FAB).
4. **#14 Soft-Keyboard folgt App-Sprache** — braucht Compose-Bump 1.6.1→1.7 (für `KeyboardOptions.hintLocales`) + Keyboard-Sprachen auf der ONE.
5. **#15 Video-Recording aus V4L2/LocalBitmap (MediaCodec)** — Aufnahme-Button im Lokal-Modus noch deaktiviert (kein RTSP).
6. **#16 Cinema-Layout** — rechtes Panel bleibt dauerhaft, Nav-Rail liegt im Live-Bild, keine untere Bedienleiste.
7. **Reorg:** wird `app-one` in `drainq-android`; Update-Client auf `drainq-cloud` umstellen.

## Wichtige Hinweise
- **Git nur lokal** (Cowork-Mount kann nicht schreiben). Nach diesem Stand: alles committen.
- **Kein `su`** in der App (`error=13`). Privilegierte Geräte-Ops nicht über su.
- HW-Protokoll-Referenz: dekompilierte Original-App in `C:\Projekte\one-revers` / `one-reverse-software`.

## Letzte Änderungen
- [2026-06-02] Feldtest-Findings + Hardware-Serial-Fix (nativ), Kiosk-Schalter, Schnellaufnahme, UI-Aufräumen. Details: `HANDOVER_SESSION_2026-06-02.md`, `FEEDBACK_Jakob_2026-06-02_Analyse.md`.
- [2026-05-20] OSD-Politur; Settings-Cleanup (Migration A) gemerged

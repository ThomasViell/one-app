> ÜBERHOLT am 2026-06-04 durch HANDOVER_SESSION_2026-06-04.md (siehe dort: "Löst den Stand vom 02.06. ab"). Nur noch als Nachweis aufbewahrt.

# Handover drainq.one — Session 2026-06-02

Für den Neustart in einem frischen Chat. Kontext kompakt, Wichtigstes zuerst.

## Sofort beim Start
1. Diese Datei + `PROJECT_STATUS.md` lesen.
2. **Offener Test:** Licht. Slider wurde durch **−/+ Tasten** ersetzt (Slider ließ sich wegen ~30 Hz-Rekomposition nicht bewegen). Letzter Build muss verifiziert werden: bauen, „Licht +" tippen → ändert sich Licht am Kopf + Anzeige „Licht: X%"? Falls nicht: TX-Zeilen aus `adb -s 233b4bd2865177ed logcat -s OneInternalHW:*` schicken (Byte 3 prüfen).
3. **Alles ist uncommitted** — Cowork-Mount kann kein Git schreiben. Lokal committen (siehe „Geänderte Dateien").

## Umgebung / Gotchas (wichtig)
- App läuft auf der **ONE-Hardware** (RK3588), Serial `/dev/ttyS5`, Video V4L2 `/dev/video0`. Gerät-Serial `233b4bd2865177ed`.
- Build: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`
- **Git nur lokal** (Mount verbietet unlink; stale `.git\index.lock` → `del`).
- **bash-Reads über den Mount lügen** (NUL-Bytes, Truncation) — Datei-Integrität nur mit Read-Tool prüfen.
- **Kein `su`** in der App (`error=13`). Geräte-Ops nicht über su lösen (HW-Serial läuft jetzt nativ im App-Prozess).

## Diese Session umgesetzt (verifiziert ✅ / Test offen ⏳)
- ✅ **#5 Kiosk** = Schalter in Einstellungen, Standard AUS.
- ✅ **#4 Tastatur-Dismiss** in Dialogen.
- ✅ **#8 Schnellaufnahme**: Tages-Bucket + Foto-Capture aus V4L2-Live-Frame + Foto-Blitz.
- ✅ **Home**: „ONE Controller"-Karte raus. **Inspektion-Panel**: „Hardware status" + „Neu verbinden" raus.
- ✅ **Hardware-Serial-Fix** (Kern): native serielle I/O (termios 9600 8N1 raw) → **Meter, Meter-Reset, Sonde laufen**. Frequenz-Mapping 1=33k/2=640/3=512, Licht 0–100.
- ⏳ **Licht** −/+ Tasten — Test offen.
- ✅/⏳ **Projektformular-Tastatur**: Wisch/Scroll schließt + Weiter-Kette (kurz gegenprüfen).

## Geänderte Dateien (für lokalen Commit)
```
app/src/main/java/com/uip/oneapp/MainActivity.kt
app/src/main/java/com/uip/oneapp/ui/screens/home/HomeScreen.kt
app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt
app/src/main/java/com/uip/oneapp/ui/screens/inspection/DamageDialog.kt
app/src/main/java/com/uip/oneapp/ui/screens/inspection/NoteDialog.kt
app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormScreen.kt
app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsViewModel.kt
app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/uip/oneapp/ui/localization/LocalizationManager.kt
app/src/main/java/com/uip/oneapp/network/internal/OneInternalHardwareService.kt
app/src/main/java/com/uip/oneapp/data/local/dao/ProjectDao.kt
app/src/main/java/com/uip/oneapp/data/repository/ProjectRepository.kt
app/src/main/cpp/v4l2bridge.c
docs/waves/WELLE-1-Kiosk-Tastatur.md            (neu)
FEEDBACK_Jakob_2026-06-02_Analyse.md            (neu)
HANDOVER_SESSION_2026-06-02.md                  (neu)
PROJECT_STATUS.md
```
Vorschlag Commit: `feat: Feldtest-Findings — HW-Serial nativ (Meter/Sonde/Licht), Kiosk-Schalter, Schnellaufnahme, UI-Aufräumen, Tastatur`

## Backlog (Reihenfolge offen)
1. Licht final bestätigen, dann committen.
2. **#1/#3 Hardtasten + Softbutton-Leiste**: gemeinsame Aktionsliste, feste Leiste am Tastenraster; `btn1..6` aus RX-`GROUP_STATUS` (payload[3..8]) auswerten — werden aktuell verworfen. Byte-Index↔physische Taste per Logcat ermitteln. On-Screen-Softbuttons = Hardtasten-Belegung (Handschuh).
3. **#7** Zurück aus Inspektion + „Gallery"/Projektverzeichnis-Benennung.
4. **#3** FAB „Neues Projekt" vergrößern (Extended-FAB).
5. **#14** Soft-Keyboard folgt App-Sprache (Compose-Bump 1.6.1→1.7 für `hintLocales` + Keyboard-Sprachen auf der ONE).
6. **#15** Video-Recording aus V4L2/LocalBitmap (MediaCodec) — Aufnahme-Button im Lokal-Modus deaktiviert.
7. **#16** Cinema-Layout (rechtes Panel auto-hide, Rail im Bild, untere Bedienleiste).
8. **Autostart**: App startet auf der ONE nicht automatisch (HOME-Launcher/Boot-Receiver) — vom User geparkt.
9. **Reorg**: app-one in drainq-android; Update-Client auf drainq-cloud.

## Referenzen
- HW-Protokoll (funktionierende Original-App, dekompiliert): `C:\Projekte\one-revers`, `C:\Projekte\one-reverse-software` — `decoded/jadx/.../SerialControl/MiniPushControlHelper.java`, `SerialHelper.java`, `ControlArgs.java`, `analyse/02_serial_protokoll_minipush.md`. Frame: Magic `FA AF 00 10 00 01` + [len][group][payload][xor]; Base-Control group 0x01: byte2=power, byte3=light(0–100), byte4=freq; RX-Gruppen 21/22/23/24 (status/meter/camera/version).
- Analyse aller 8 Jakob-Punkte: `FEEDBACK_Jakob_2026-06-02_Analyse.md`.
- Welle-1-Spec: `docs/waves/WELLE-1-Kiosk-Tastatur.md`.

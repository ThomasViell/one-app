# APK auf Test-Tablet installieren

**Ziel:** v0.3.0 Release-APK aus dem aktuellen Build aufs Samsung-Tablet (VID 04E8 / PID 6864, ADB-Interface bereits erkannt) installieren, App starten, Logcat in eine Datei mitschneiden.

## Vorbedingungen
- Tablet ist per USB-C verbunden und im ADB-Modus (siehe `Get-PnpDevice` Output: SAMSUNG Android ADB Interface, Status OK)
- Auf dem Tablet ist USB-Debugging aktiviert und der RSA-Fingerprint des PC ggf. einmal bestätigt
- Repo `C:\Projekte\drainq.one`, Branch `feature/osd-phase-7` (oder master nach Merge)

## Auftrag

### Schritt 1 — adb beschaffen
Pruefe ob `adb` im PATH ist. Falls nicht:

Variante A (bevorzugt): `winget install --id Google.PlatformTools --silent --accept-source-agreements --accept-package-agreements` (kein Admin-Rechte noetig wenn winget Scope user)

Variante B: Falls winget fehlt — direkt von Google laden:
- Download: `https://dl.google.com/android/repository/platform-tools-latest-windows.zip`
- Entpacken nach `C:\Tools\platform-tools\`
- PATH dieser Session erweitern: `$env:PATH += ';C:\Tools\platform-tools'`

Variante C: Pruefe ob Android Studio bereits installiert ist (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`) und nutze diesen Pfad.

Verifizieren: `adb version` muss eine Versionsnummer zeigen.

### Schritt 2 — Geraet anbinden
- `adb devices -l` ausfuehren. Erwartung: ein Geraet mit Status `device` (nicht `unauthorized`).
- Falls `unauthorized`: User auffordern, auf dem Tablet den USB-Debugging-Dialog mit Häkchen "Diesem Computer immer vertrauen" zu bestaetigen.
- `adb shell getprop ro.product.model` und `adb shell getprop ro.build.version.release` ausgeben — fuer den Test-Bericht.

### Schritt 3 — APK lokalisieren
Pruefe in dieser Reihenfolge:
1. `app\build\outputs\apk\release\app-release.apk` (vom Phase-7-Build)
2. `DrainQ_ONE_v0.3.0.apk` (falls schon im Repo-Root)

Falls keines davon existiert: `.\gradlew assembleRelease` ausfuehren und dann Pfad 1 nehmen.

Datei-Existenz pruefen, Groesse loggen (erwartet ~144 MB).

### Schritt 4 — APK installieren
- `adb install -r -d <pfad>` ausfuehren (`-r` ersetzt existierende, `-d` erlaubt Downgrade)
- Wenn Output `INSTALL_FAILED_UPDATE_INCOMPATIBLE` oder `INSTALL_FAILED_VERSION_DOWNGRADE`: alte Version deinstallieren mit `adb uninstall com.uip.drainq.one` (User-Daten gehen verloren — vorher beim User rueckfragen!) und neu installieren
- Bei Erfolg `Success` im Output erwartet

### Schritt 5 — App starten
- `adb shell am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity`
- 3 Sekunden warten, dann pruefen ob Prozess laeuft: `adb shell pidof com.uip.drainq.one` muss eine PID zeigen

### Schritt 6 — Logcat-Snapshot
- Logcat leeren: `adb logcat -c`
- 30 Sekunden mitschneiden waehrend Startphase: `adb logcat -d -v time *:W com.uip.drainq.one:V FfmpegRtspRecorder:V OsdRenderer:V > install_logcat.txt` (nach 30 s)
- Datei `install_logcat.txt` im Repo-Root ablegen, letzte 50 Zeilen in den Endbericht packen

### Schritt 7 — Endbericht
Schreibe `INSTALL_RESULT.md` im Repo-Root mit:
- adb-Version, Tablet-Modell, Android-Version
- APK-Pfad, Groesse, MD5
- Install-Ausgabe (Success/Fehlercode)
- App-Start: PID + erste 50 Logcat-Zeilen
- Auffaelligkeiten (CRASH, FATAL, AndroidRuntime, OutOfMemory, RuntimeException)
- Empfehlung naechster Schritt: A1-Test starten oder Bug-Fix noetig

## Wichtige Verbote
- Keine `adb shell pm clear com.uip.drainq.one` ohne explizite Userfreigabe (Datenverlust)
- Keine `adb uninstall` ohne explizite Userfreigabe
- Keine Aenderungen am App-Code in diesem Lauf — nur Install + Smoke-Test

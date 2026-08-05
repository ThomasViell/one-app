# Auftrag: Android-Systemleiste bei Aufnahmestart im Kiosk endgültig beseitigen (On-Device, autonom)

**Rolle:** Du bist ein autonomer On-Device-Debugger für DrainQ.ONE. Reproduziere, diagnostiziere
ursachengenau, fixe im Code, baue/installiere aufs Gerät, verifiziere — iteriere bis grün.

## Gerät / Build
- Serial: `233b4bd2865177ed` (RK3588_s, Android 12, **userdebug → `adb root` verfügbar**).
- Build/Install: `$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"; .\gradlew installDebug`
- Repo: `C:\Projekte\one-app`-Arbeitskopie unter `C:\Projekte\drainq.one`, Branch **`feature/beta-wave-1`**.
- App-Paket: `com.uip.drainq.one` (Kotlin-Package `com.uip.oneapp`).

## Fehlerbild (exakt reproduzieren)
Autostart → App startet im Kiosk (Device-Owner + LockTask, Leiste anfangs **weg**) →
**Inspektion** öffnen → **Aufnahme starten** → Dialog → **„Ohne Einblendung"** →
**die Android-Navigationsleiste erscheint unten und bleibt dauerhaft.**
Beim App-Start ist sie weg; erst der Aufnahmestart holt sie zurück.

## Was heute schon geklärt ist (NICHT erneut ausprobieren)
- **Kiosk ist AN.** Leiste am App-Start ausgeblendet, kommt erst beim Aufnahmestart.
- **`qemu.hw.mainkeys=1` ist in `/system/build.prop` gesetzt und LIVE** (`getprop qemu.hw.mainkeys` → `1`),
  Gerät wurde neu gestartet. Trotzdem erscheint die Leiste bei der Aufnahme → die Aufnahme erzwingt
  die Leiste an der Systemeinstellung vorbei (Fenster-/Insets-Ebene). **→ Kein build.prop-Thema.**
  Prüfe am Ende, ob der App-Fix auch **ohne** diese Prop wirkt (Prop testweise rausnehmen + reboot),
  damit der Fix auf einem Standard-Image gilt und nicht von der Prop abhängt.
- **`settings put global policy_control immersive.navigation=*` wirkt auf Android 12 NICHT** (entfernt) — nicht verwenden.
- **MainActivity.kt** hat bereits:
  - `onWindowFocusChanged` → `applyKiosk()` (re-apply bei Fokusrückkehr),
  - `applySystemBars()` postet `controller.hide(systemBars)` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`,
  - einen Insets-Listener (1500 ms) — der **feuert beim Aufnahme-Übergang NICHT**,
  - **UNCOMMITTET im Arbeitsbaum:** einen Lebenszyklus-Backstop (1-s-Schleife, `repeatOnLifecycle(RESUMED)`),
    der bei Kiosk + sichtbaren System-Bars `applySystemBars()` aufruft.
    → Installiere zuerst den aktuellen Arbeitsbaum und prüfe, ob dieser Backstop die Leiste bei der
      Aufnahme innerhalb ~1 s wieder einzieht. Wenn ja: sauber machen/committen. Wenn nein/zu langsam/
      flackert: **echte Ursache finden** (siehe unten) und gezielt fixen.

## Vermutete Ursache (verifizieren, nicht raten)
Der Aufnahmestart öffnet/verändert ein Fenster, das den Immersive-Zustand der Activity zurücksetzt.
Kandidaten: Compose-`Dialog` („Aufnahme starten"), ein **Foreground-Service + Notification** für die
Aufnahme, oder ein neu erzeugter `SurfaceView`/Player (Phase-7-Canvas-Player `LocalBitmapVideoPlayer`
/ `FfmpegRtspRecorder`). **Kein MediaProjection im Code** (bereits geprüft).

## Diagnose-Schritte (Belege sichern unter `tools/_navbarfix/`)
1. `uiautomator dump` + `screencap` in drei Zuständen: (a) App-Start, (b) Aufnahme-Dialog offen,
   (c) Aufnahme läuft. Navigationsleiste je vorhanden/nicht?
2. Im Zustand „Aufnahme läuft": `adb shell dumpsys window` (bzw. `dumpsys window windows`) →
   welches Fenster hat Fokus, welche `requestedVisibleTypes`/`mSysUiVis`/Insets erzwingen die Nav-Bar?
   Vergleiche mit Zustand „App-Start".
3. `adb logcat` um den Aufnahmestart → Hinweise auf neue Fenster/Insets/Foreground-Service.
4. Ursache eindeutig benennen (Datei:Zeile).

## Fix (minimal + korrekt, Ursache adressieren)
Mögliche Richtungen — wähle die, die die Ursache trifft:
- Beim Aufnahmestart den Immersive-Zustand der Activity **aktiv neu setzen** (z. B. Callback aus
  `InspectionScreen` Aufnahme-Start → `MainActivity.applySystemBars()`), ggf. mehrfach verzögert,
  um den Fenster-Übergang zu überdauern.
- Falls ein Foreground-Service/Notification die Bar triggert: Fenster-Flags des Service/der App korrigieren.
- Den vorhandenen Backstop-Wächter beibehalten, wenn er die zuverlässige Bremse ist — aber so schnell,
  dass kein sichtbares Flackern bleibt.

## Randbedingungen
- Branch `feature/beta-wave-1`. **Gezielt committen, nie `git add -A`.**
- **Keine bash/python-Schreibzugriffe über den Cowork-Mount auf große Dateien** (CRLF/Korruptionsgefahr) —
  Code nur über Editor-Tools ändern.
- Kein `su` nötig (`adb root` vorhanden). HW-Serial nativ.
- Nichts anderes kaputt machen: bestehende Tests müssen grün bleiben (`.\gradlew test`).

## Abnahme (alle müssen grün sein, am Gerät verifiziert)
1. Aufnahme **Ohne Einblendung** starten → Leiste bleibt weg (oder verschwindet < 0,3 s, kein sichtbares Flackern).
2. Aufnahme **Mit Einblendung** + Pause/Weiter + Stop → Leiste bleibt weg.
3. Nach Aufnahme-Stop → Leiste bleibt weg.
4. Hochwischen zeigt die Leiste nur transient, sie zieht sich wieder ein.
5. Funktioniert **ohne** `qemu.hw.mainkeys=1` (Prop testweise entfernen + reboot) — Fix darf nicht davon abhängen.
6. `.\gradlew test` grün.

## Liefergegenstand
- Kurzer Bericht `RESULT_NAVBAR_FIX.md`: Ursache (Datei:Zeile), Fix, Vorher/Nachher-Belege, Abnahme-Status.
- Gezielte Commits auf `feature/beta-wave-1`, gepusht.
- Falls `qemu.hw.mainkeys=1` für den Fix **nicht** nötig ist: Empfehlung, ob die Prop im Golden-Image
  trotzdem bleibt (Nav-Bar systemweit aus) oder rausgenommen wird — kurz begründen.

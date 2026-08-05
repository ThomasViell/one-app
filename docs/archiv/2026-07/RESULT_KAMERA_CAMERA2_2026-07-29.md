# RESULT — Kamera über Standard-Camera2: Verursacher-Suche + Bildbeweis

**Datum:** 2026-07-29
**Gerät:** `233b4bd2865177ed` — `rk3588_s`
**Branch:** `feature/dual-mode` (keine Code-Änderung an der App — dieser Lauf sucht und misst)
**Bezug:** `KAMERA_BEFUND_PROMPT.md`

---

## Frage 1 — Wer stoppt `vendor.camera-provider-2-4-ext`?

**Ergebnis: Nicht die DrainQ-App. Der genaue Verursacher des Boot-Stopps konnte NICHT ermittelt werden — belegte Positiv-Widerlegung der App, aber keine positive Identifikation des tatsächlichen Auslösers.**

### 1.1 Repo-Suche (Datei:Zeile)

Volltextsuche im gesamten `drainq.one`-Quellcode nach `camera-provider`, `ctl.stop`, `ctl.start`, `SystemProperties.set`, `chmod`, `0666`, `video0`, `Runtime.exec`, `ProcessBuilder`:

- **Kein einziger Treffer** für `ctl.stop`, `ctl.start` oder `camera-provider` im App-Code (nur in Doku/Report-Dateien, die diese Strings zitieren).
- Einziger `Runtime.exec`/`ProcessBuilder`-Treffer mit Gerätebezug:
  `app/src/main/java/com/uip/oneapp/bootstrap/DeviceFilePermissionBootstrap.kt:53` —
  `Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))` mit `cmd = "chmod 666 /dev/ttyS5 /dev/video0"`.
  Das ist **ausschließlich ein `chmod`**, kein `stop`/`ctl.stop` und fasst den Provider-Dienst nicht an.
- Alle übrigen `video0`-Treffer (`V4L2Camera.kt`, `OneInternalHardwareService.kt`, `CameraFrameBus.kt`, `HardwareModeDetector.kt`, `v4l2bridge.c`, `OneApp.kt`, `AppModule.kt`) sind reine Doku-Kommentare oder Pfad-Konstanten — keiner startet/stoppt einen Systemdienst.
- **Befund: Im App-Repo existiert kein Code, der den Kameradienst stoppt oder startet.**

### 1.2 Gerätesuche nach weiteren Startskripten

- `/vendor/etc/init/init.drainq.rc.disabled` — bekannt, wird von init nicht geparst (Dateiendung `.disabled`).
- **Neu gefunden:** `/system/etc/init/init.drainq.rc` (aktiv, ohne `.disabled`) + `/system/bin/drainq-postboot.sh`, gestartet via `on property:sys.boot_completed=1 → start drainq_postboot` (oneshot-Service). Vollständiger Inhalt beider Dateien gelesen:
  - Zweck laut Kommentar im Skript: versteckt den `ScreenDecorOverlayBottom`-Balken am unteren Displayrand. Setzt `settings put secure navigation_mode 0`, `setprop persist.sys.top_app com.uip.drainq.one`, `setprop sys.status.hidebar_enable true`, killt danach `com.android.systemui` (Auto-Restart liest neue Settings).
  - **Fasst weder `/dev/video0` noch `camera-provider` an.** Als Verursacher **ausgeschlossen** — kein `video`/`camera`-String im gesamten Skript.
- Keine weiteren `.rc`-Dateien mit `drainq`- oder `camera`-Bezug in `/vendor/etc/init`, `/system/etc/init`, `/odm/etc/init` oder `/data/local` gefunden (`grep -rl` über alle drei Verzeichnisse).
- `/data/local/tmp` enthielt zwei Skripte (`probe.sh`, `probe2.sh`) mit Zeitstempeln vor Sessionbeginn — offensichtlich Diagnose-Artefakte einer früheren Untersuchung (Inhalt: reine `dumpsys`/`ls`/`cat`-Abfragen, keine Steuerbefehle). Nicht von mir erzeugt, unverändert belassen.

### 1.3 Reboot-Gegenprobe (Kernbeleg)

Gerät neu gestartet, `sys.boot_completed` gepollt, danach `dumpsys media.camera` und `ps -A` wiederholt abgefragt:

```
BOOT_COMPLETED_AT=14:00:47.954

== Camera service events log ==
14:00:48 : REMOVE device 100, reason: (Device status changed from 1 to 0)
14:00:40 : USER_SWITCH previous allowed user IDs: <None>, current allowed user IDs: 0
14:00:35 : ADD device 100, reason: (Device added)
14:00:35 : ADD device 100, reason: (Device added)
```

`ps -A | grep -iE 'drainq|camera'` **zweimal** geprüft (~t+90s und ~t+135s nach Boot):

```
cameraserver   418     1   ...   android.hardware.camera.provider@2.4-service
cameraserver   488     1   ...   cameraserver
u0_a36        1653   409   ...   com.android.camera2
```

**`com.uip.drainq.one` erscheint in keinem der beiden Snapshots.** Die App war zum Zeitpunkt des `REMOVE`-Events (14:00:48, ~1s nach Boot-Completed) und danach mehrere Minuten lang **nicht im Prozessbaum** — sie kann den Dienst also in diesem Zeitfenster nicht per `ctl.stop`/`SystemProperties` angefasst haben. Das widerlegt den Hauptverdacht direkt und belegt.

Zusätzlicher Beleg aus der laufenden Session (vor dem Reboot): Der Dienst crashte einmal live mit `SIGPIPE` (`init: Service 'vendor.camera-provider-2-4-ext' (pid 2459) received signal 13` → `Sending signal 9` → Auto-Restart durch `init`). Ursache war **nachweislich mein eigener Diagnosebefehl** `dumpsys media.camera | head -20` — im selben Log-Ausschnitt steht `Failed to write while dumping service media.camera: Broken pipe`, ausgelöst durch das abgeschnittene `| head`. Danach lief der Dienst **5 Minuten durchgehend stabil** (13:54:42–13:59:38, Messung alle 5s, `prop=running`, gleiche PID) — **während die App die ganze Zeit nicht lief**. Auch das spricht gegen die App als Ursache.

### 1.4 Was nicht ermittelt werden konnte

Die konkrete Log-Zeile, die den Boot-Stopp auslöst (z. B. ein `Control message: Processed ctl.stop for 'vendor.camera-provider-2-4-ext' from pid: N (Prozessname)`, wie sie für andere Dienste — z. B. `idmap2d` von `system_server` — im selben Log sichtbar war), wurde **trotz gezielter Suche nicht gefunden**:

- `dmesg` nach dem Reboot beginnt strukturell erst bei Kernel-Uptime ~10,8 s (geprüft: erste Zeile im Ringpuffer). `ro.boottime.vendor.camera-provider-2-4-ext` liegt bei ~4,0 s — der Systemstart-Log-Eintrag ist zu diesem Zeitpunkt bereits aus dem Ringpuffer verdrängt (zu viel Boot-Logging in der Zwischenzeit). Strukturell per `adb` nicht mehr einsehbar, auch nicht durch sofortiges `adb shell dmesg -w` nach dem Reboot, weil `adbd` selbst laut Log erst bei Uptime ~30–35 s hochkommt — also nach dem gesuchten Ereignis.
- `logcat -b all` (volle Puffer) enthält für dieses Boot **keine einzige Zeile** mit `camera-provider-2-4-ext`. Die früheste `I/init`-Kontrollnachricht in diesem Mitschnitt betrifft `bootanim` um 14:00:59 — später als das gesuchte `REMOVE`-Ereignis um 14:00:48. `logd` hat den fraglichen Moment offenbar nicht mitgeschnitten.
- Zusätzliche Einschränkung: Die Geräte-Uhr springt beim Boot spürbar (siehe bekannter Gotcha „Uhr fällt zurück, kein NTP" aus dem Projektgedächtnis) — Kernel- und `logd`-Zeitstempel im Boot-Fenster sind untereinander nicht exakt sekundengenau vergleichbar. Kausale Zuordnung auf Sekundenebene in diesem Fenster ist deshalb grundsätzlich unsicher.

**Ehrliches Ergebnis: Der tatsächliche Auslöser des Boot-Stopps ist nicht gefunden.** Belegt ist nur, was er **nicht** ist: nicht die DrainQ-App (Prozess nicht vorhanden), nicht `drainq-postboot.sh` (Skriptinhalt gelesen, kein Kamerabezug), kein Code im App-Repo.

---

## Frage 2 — Bildbeweis über Standard-Camera2

Wegwerf-Testprojekt unter `C:\Temp\cam2probe` (außerhalb des Repos, DrainQ-App nicht angefasst).

### 2.1 Vorgehen

1. `am force-stop com.uip.drainq.one` + `start vendor.camera-provider-2-4-ext`.
2. Minimal-App (`com.probe.cam2`, ein `Activity` mit `TextureView`), öffnet die Kamera mit `LENS_FACING_EXTERNAL` über `CameraManager.openCamera()` (Standard-Android-API, kein V4L2/root-Zugriff auf `/dev/video0`).
3. Ziel-Auflösung 1280×720 aus `StreamConfigurationMap` gewählt, `setRepeatingRequest` für Preview gestartet.
4. Screenshot + Logcat-Auswertung.

### 2.2 Ergebnis: Bild vorhanden

Kamera geöffnet, **echtes Live-Bild** angezeigt (Kamerawagen auf Holzboden, sichtbare Bewegung zwischen zwei zeitversetzten Screenshots — Beweis für Live-Stream, kein statisches Bild):

- Screenshot 1: `C:\Temp\cam2probe\cam2probe_live.png`
- Screenshot 2 (später, mit erweiterter Latenzmessung, Bewegung im Bild sichtbar): `C:\Temp\cam2probe\cam2probe_live2.png`

Logcat-Auszug:
```
Kameras: id=100 facing=2
Öffne Kamera id=100 Zielgröße=1280x720 ...
Kamera geöffnet in 7.8 ms — starte Preview 1280x720
ERSTES FRAME: open()->frame Latenz=233.1 ms
LIVE 1280x720 | fps=30.0 | frames=545 | open->frame1=233.1ms | sensor->app avg=42.5ms min=35.4ms max=54.6ms (n=545)
```

### 2.3 Messwerte

- **Auflösung:** 1280×720 (exakt getroffen, aus `StreamConfigurationMap.getOutputSizes()`).
- **Bildrate:** stabil **30,0 fps** (Fenstermessung über 2 s, zwei unabhängige Läufe, Schwankung 29,9–30,1 fps, über 545+ Frames ohne sichtbaren Frame-Drop).
- **Latenz — zwei Teilmessungen, KEINE echte Glas-zu-Glas-Messung:**
  - `open()` → erstes Frame (Kaltstart, einmalig): **233,1 ms** (Lauf 2) bzw. 394,9 ms (Lauf 1, direkt nach manuellem `start` des Providers — vermutlich Warmlauf-Overhead der HAL). Das ist reine Startlatenz, keine laufende Pipeline-Latenz.
  - Sensor→App-Callback-Latenz (`SENSOR_TIMESTAMP` aus `CaptureResult` gegen `SystemClock.elapsedRealtimeNanos()` beim Empfang des Callbacks, gleiche Boottime-Domäne): **Ø 42,5–43,9 ms, min 35,4–36,8 ms, max 48,8–54,6 ms** (n=545 bzw. n=182 Frames über zwei Läufe). Das ist die Zeit von Sensor-Erfassung im ISP/Treiber bis der App-Callback mit dem fertigen Frame feuert — **ohne** Anzeige-Compositing (SurfaceFlinger/HWC).

**Explizit NICHT gemessen:** die echte Glas-zu-Glas-Latenz vergleichbar mit dem historischen ~220-ms-Referenzwert des bisherigen Pfads. Eine solche Messung erfordert das physische Halten der Kamera vor eine laufende Millisekunden-Stoppuhr und das gleichzeitige Fotografieren von Stoppuhr und Tablet-Anzeige (oder eine gleichwertige Zwei-Geräte-Apparatur). Diese Session lief ausschließlich über `adb` ohne physischen Zugriff auf das Gerät — das kann ich nicht nachstellen. Siehe Abschnitt „Was nicht geprüft werden konnte".

Kein Absturz, kein Fehlerfall, kein schwarzes Bild — die Kamera öffnete beim ersten Versuch erfolgreich.

---

## Empfehlung

**Der reguläre Weg (Standard-Camera2-API mit `LENS_FACING_EXTERNAL`) trägt technisch:** echtes Live-Bild, korrekte Zielauflösung 1280×720, stabile 30 fps, niedrige Sensor→App-Latenz (~43 ms Durchschnitt) — ohne `chmod 666`, ohne `su`, ohne exklusiven Direktzugriff auf `/dev/video0`.

**Aber:** Frage 1 zeigt, dass der Provider-Dienst nach dem Boot unabhängig von der App in den Zustand `stopped` fällt — der Verursacher ist unbekannt, aber das Verhalten selbst ist belegt und reproduzierbar (zweimal beobachtet: einmal genuine Boot-Sequenz, einmal Live-Session). Ein Umbau auf Camera2 träfe auf **dasselbe** Problem: Ohne einen aktiven Trigger, der den Dienst vor dem `openCamera()`-Aufruf sicher in den Zustand `running` versetzt, bleibt die Kamera in der Produktiv-App schwarz — genau wie beim V4L2-Pfad heute der `chmod`-Trick nötig ist.

Ein Umbau müsste konkret anfassen:
1. **Root-Trigger beim App-Start**, der `su -c "start vendor.camera-provider-2-4-ext"` absetzt und den erfolgreichen Übergang zu `running` abwartet, bevor `CameraManager.openCamera()` aufgerufen wird — strukturell analog zu `DeviceFilePermissionBootstrap.kt`, aber `ctl.start` statt `chmod`. Das bleibt **root-abhängig**, also **kein Vorteil** gegenüber dem heutigen `chmod-666`-Trick in Bezug auf Produktionsreife/Platform-Signing (vgl. `docs/adr/0005-platform-signing.md`).
2. **Kompletter Videopfad-Umbau**: `V4L2Camera`/`CameraFrameBus` (MJPEG-Frames aus direktem `/dev/video0`-Open) müsste durch `CameraDevice`/`CaptureSession`/`ImageReader`- oder `SurfaceTexture`-basierten Camera2-Code ersetzt werden. `OneVideoServer` (RTSP/H.264-Fan-out) und der Hardware-Encoder-Aufnahmepfad (Welle 5, `useHardwareRecorder`) müssten an das neue Frame-Format angepasst werden.
3. **Kein Ersatz für die offene Boot-Stopp-Frage**: Solange der Verursacher unbekannt ist, bleibt unklar, ob der Dienst z. B. nach längerer Inaktivität erneut stoppt (Langzeitverhalten nicht getestet, siehe unten).

Ohne Klärung von Frage 1 wäre ein Umbau auf Camera2 kein sauberer Fix, sondern ein Wechsel des Startup-Workarounds — von `chmod` zu `ctl.start`, mit demselben Root-Abhängigkeits-Problem.

---

## Was ich NICHT prüfen konnte

1. **Wer den Boot-Stopp auslöst** — Log-Fenster-Lücke (siehe 1.4): weder `dmesg` (Ringpuffer verdrängt den Zeitpunkt strukturell) noch `logcat -b all` (kein `camera-provider-2-4-ext`-Eintrag im gesamten Mitschnitt) deckten das fragliche Zeitfenster ab. Auch ein `adb shell dmesg -w` unmittelbar nach `wait-for-device` käme zu spät, da `adbd` selbst erst nach dem fraglichen Ereignis hochfährt.
2. **Echte Glas-zu-Glas-Latenz** vergleichbar mit dem ~220-ms-Referenzwert — erfordert physische Stoppuhr-Fotografie, per `adb` allein nicht durchführbar.
3. **Langzeitverhalten** des Camera2-Preview-Pfads (Speicherlecks, Reconnect bei Kopfwechsel C10/C18, Verhalten über Stunden) — nur wenige Minuten getestet.
4. **Verhalten bei parallel laufender DrainQ-App** — die App war während des gesamten Bildbeweis-Tests bewusst gestoppt (Vorgabe der Aufgabe); ob Camera2-Zugriff und die App-eigene Hardware-Anbindung (Serial `/dev/ttyS5`, HardwareModeDetector) sich gegenseitig stören, wurde nicht getestet.
5. **USB-Re-Plug-Verhalten** des Camera2-Pfads (Kopfwechsel während laufender Preview) — nicht getestet.
6. **Verhalten auf Louis' Gerät** — laut Vorgabe nicht angefasst, keine Aussage über Geräte-Streuung möglich.

---

## Bereinigung

Test-App `com.probe.cam2` vom Gerät deinstalliert, temporäre Skripte/Screenshots in `/data/local/tmp` entfernt, DrainQ-App (`com.uip.drainq.one/com.uip.oneapp.MainActivity`) wieder gestartet — Gerät im ursprünglichen Betriebszustand hinterlassen. Kein Code im Repo geändert.

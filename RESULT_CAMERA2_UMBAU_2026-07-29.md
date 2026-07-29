# RESULT: Camera2-Umbau — AP-4 Geräteabnahme (2026-07-29)

Branch `feature/camera2-umstieg`, Testgerät `233b4bd2865177ed`. Fortsetzung von AP-1..3
(Commit `5e65972`) durch die Plattformsignatur-Prüfung (siehe unten) bis in die
Geräteabnahme AP-4.

## 0. Plattformsignatur + Update-Weg (Vorbereitung für AP-4)

| Schritt | Ergebnis |
|---|---|
| `ONE_PLATFORM_KEYSTORE` / `ONE_PLATFORM_PASS` gesetzt | Ja, geprüft |
| `assembleDebug` mit Plattformsignatur | Erfolgreich, `keytool -printcert -jarfile` bestätigt `SHA256: 2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22` |
| Erster Installationsversuch (601/0.6.1, ohne `sharedUserId`) | **Gescheitert**: `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` — Gerät hatte noch Variante B (`sharedUserId="android.uid.system"`) aus dem Signaturtestlauf vom 29.07. installiert (600/0.6.0), das Manifest auf diesem Branch hatte das Attribut beim Camera2-Umbau (AP-1..3) unbemerkt verloren |
| Korrektur | `android:sharedUserId="android.uid.system"` in `AndroidManifest.xml` wiederhergestellt, gemäß ADR-0005 Abschnitt 4 (CEO-Entscheid 29.07., Variante B — ohne System-UID nur generischer Hotspot-Name) |
| Zweiter Installationsversuch | **Erfolgreich** als normales `adb install -r`-Update, keine Deinstallation, keine Datenlöschung. Bestätigt: `versionCode=601 versionName=0.6.1 sharedUser=android.uid.system/1000` |

**Zusätzliche Absicherung eingebaut** (auf ausdrücklichen Wunsch, weil das Attribut heute
unbemerkt verschwunden war):
- `app/build.gradle.kts`: bricht **hart ab**, wenn Plattformsignatur aktiv ist
  (`ONE_PLATFORM_KEYSTORE`+`ONE_PLATFORM_PASS` gesetzt) aber `APP_VERSION_CODE`/
  `APP_VERSION_NAME` fehlen (sonst stiller Rückfall auf 401/0.4.1 → Downgrade-Blocker beim
  Geräte-Update, wie oben passiert). Reiner Debug-Bau ohne Plattformsignatur bekommt
  stattdessen nur eine sichtbare Warnung im Bauprotokoll, damit normale Entwicklungsbauten
  nicht blockiert werden.
- `app/src/test/java/com/uip/oneapp/signing/PlatformSigningManifestTest.kt`: JVM-Unit-Test,
  der `AndroidManifest.xml` auf `android:sharedUserId="android.uid.system"` prüft, mit
  Verweis auf ADR-0005 Abschnitt 4 in der Fehlermeldung.
  - **Negativprobe durchgeführt:** Attribut testweise entfernt → Test schlägt fehl
    (`AssertionError` in `PlatformSigningManifestTest.kt:33`, `1 test completed, 1 failed`).
  - **Positivprobe durchgeführt:** Attribut wiederhergestellt → Test grün.
  - Damit ist belegt, dass die Absicherung tatsächlich anschlägt, nicht nur plausibel klingt.

## 1. AP-4 — Ergebnistabelle (Stand nach Nachbesserung, s. Abschnitt 6)

Ursprünglich (Abschnitt 2) war Punkt 1 durch den `su`-Blocker verdeckt — es war nicht
feststellbar, ob der Camera2-Bildpfad selbst überhaupt funktioniert. Nach dem Umbau von
`CameraServiceSelfStarter` auf `SystemProperties` (Abschnitt 6, gleicher Tag) zeigten sich
**zwei neue, eigenständige Befunde** dahinter (Abschnitt 7 + 8), die das Ergebnis unten
begründen.

| # | Punkt | Ergebnis | Beleg |
|---|---|---|---|
| 1 | Reboot, App startet, Live-Bild ohne manuellen Eingriff | **NEIN** — App startet nicht automatisch (s. u.), UND selbst mit manuellem Start bleibt das Bild schwarz (Ursache: Abschnitt 8, JPEG/BLOB-HAL-Fehler) | `_ap4_evidence/ap4b_03_clean_reboot_single_attempt.png` |
| 2 | 60s-Aufnahme mit Pause, Länge/fps/kein Zeitraffer | **Mit Diagnose-Workaround (nicht committet) belegt möglich**, mit produktivem Code weiterhin blockiert (Abschnitt 8) | `_ap4_evidence/ap4d_recording_60s.mp4`, ffprobe: h264, 1280×720, 61,56 s, 834 Frames, Ø ~13,5 fps |
| 3 | Foto aus Livebild, OSD-Einbrennung | **Mit Diagnose-Workaround belegt möglich** (REC/PAUSE-Anzeige korrekt, Aufnahme mit „Mit Einblendung“ gewählt), mit produktivem Code weiterhin blockiert | `_ap4_evidence/ap4d_01_paused_mid.png`, `ap4d_02_resumed_running.png` |
| 4 | Kabel ab-/anstecken, Bild kommt von allein zurück | **Offen** — macht der CEO selbst am Gerät. Vorbereitung s. Abschnitt 3 (Hinweis: erst nach Klärung von Abschnitt 8 sinnvoll testbar) | — |
| 5 | Glas-zu-Glas-Verzögerung vs. ~220 ms | **Offen** — macht der CEO selbst am Gerät. Vorbereitung s. Abschnitt 3 (dito) | — |
| 6 | Gesamte Testsuite grün | **Ja** (erneut nach Umbau geprüft) | `gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, alle Module grün |

**Zusätzlich unerwartet vorgefunden:** Nach dem Reboot war `com.uip.drainq.one` **nicht**
die aktive HOME-Activity — `cmd package resolve-activity ... HOME` liefert
`com.android.launcher3.uioverrides.QuickstepLauncher`, obwohl die App selbst
`android.intent.category.HOME` deklariert (`MainActivity`-IntentFilter bestätigt). Die
frühere Annahme „App ist Kiosk/HOME und startet immer automatisch" (aus
`KAMERA_BEFUND_PROMPT.md`) trifft auf diesem Gerät **aktuell nicht** zu — ob das an der
Neuinstallation/Signaturwechsel liegt (Standard-App-Zuordnungen werden bei manchen
Android-Versionen bei Erstinstallation eines Pakets zurückgesetzt) oder einem anderen
Grund, wurde nicht weiter untersucht (außerhalb des Auftrags). App wurde für den Test
manuell gestartet (`am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity`) — das ist
selbst schon eine Abweichung von „ohne jeden manuellen Eingriff", zusätzlich zum Kamera-Blocker.

## 2. Root Cause — Punkt 1 (belegt, nicht vermutet)

`CameraServiceSelfStarter` (AP-2, `app/src/main/java/com/uip/oneapp/bootstrap/CameraServiceSelfStarter.kt`)
prüft und startet `vendor.camera-provider-2-4-ext` **ausschließlich über `su`**
(`runSuCapture`/`runSu`, Zeilen 100–134). Der Klassenkommentar geht davon aus: „Gerät ist
geroutet … keine Plattform-Signatur nötig" (Zeile 27).

Diese Annahme stimmt auf `233b4bd2865177ed` in der aktuellen Konfiguration **nicht** für
den App-Prozess:

```
$ adb shell ls -laZ /system/xbin/su
-rwsr-x--- 1 root shell u:object_r:su_exec:s0  11088 2009-01-01 00:00 /system/xbin/su
```

`su` ist nur für `root` und die Gruppe `shell` ausführbar (Modus 750). Der App-Prozess läuft
weder als `root` noch in der Gruppe `shell` — auch nicht mit dem jetzt aktiven
`sharedUserId="android.uid.system"` (uid 1000, keine Mitgliedschaft in Gruppe `shell`/2000).
Jeder `Runtime.exec(arrayOf("su", ...))`-Aufruf aus der App scheitert daher unabhängig vom
tatsächlichen Dienst-Zustand:

```
CameraServiceSelfStart: su capture failed (getprop init.svc.vendor.camera-provider-2-4-ext):
  Cannot run program "su": error=13, Permission denied
  at com.uip.oneapp.bootstrap.CameraServiceSelfStarter.runSuCapture(CameraServiceSelfStarter.kt:123)
  at com.uip.oneapp.bootstrap.CameraServiceSelfStarter.isRunning(CameraServiceSelfStarter.kt:98)
  at com.uip.oneapp.bootstrap.CameraServiceSelfStarter.ensureRunning(CameraServiceSelfStarter.kt:49)
  at com.uip.oneapp.network.internal.Camera2FrameSource.openCameraBlocking(Camera2FrameSource.kt:124)
```

Wichtige Nebenwirkung für die Bewertung von AP-1: `isRunning()` (Zeile 97–98) selbst
scheitert schon am `su`-Aufruf für die reine **Lese**-Abfrage `getprop` — es kommt also gar
nicht erst zu einer echten Zustandsprüfung. `ensureRunning()` interpretiert die
Exception als „nicht running" und versucht danach den Start ebenfalls über `su`, der aus
demselben Grund scheitert. `Camera2FrameSource.openCameraBlocking()` (Zeile 124–129) bricht
bei `Result.Failed` **vor** dem eigentlichen `CameraManager.openCamera()`-Aufruf ab.

**Das bedeutet konkret:** Ich habe den Dienst zu Diagnosezwecken per `adb root` +
`start vendor.camera-provider-2-4-ext` manuell hochgefahren und mit `getprop` bestätigt
(`running`). Trotzdem blieb der Fehlerbildschirm nach Neuöffnen des Inspektionsbildschirms
bestehen — weil `CameraServiceSelfStarter.isRunning()` durch den `su`-Fehler nie bis zur
echten Zustandsabfrage kommt und die App den tatsächlich laufenden Dienst nicht erkennt.
**Ob der eigentliche Camera2-Bildpfad (AP-1) auf diesem Gerät noch funktioniert, ist damit
weiterhin ungeklärt** — nicht weil das Bild schwarz blieb, sondern weil der Code nie bis zum
Öffnen der Kamera vorgedrungen ist. Kein plausibel klingendes Ergebnis behauptet, wo keins
belegt ist.

`DeviceFilePermissionBootstrap.kt` (gleiches `su`-Muster, Zeile 11–12: „auf gerooteten
Tablets vorinstalliert … auf der BWELL/Bominwell-Hardware ist das gegeben") ist von
demselben Effekt betroffen, fällt hier aber nicht auf, weil `/dev/ttyS5` laut Log bereits
weltzugänglich ist und der `su`-Zweig dadurch übersprungen wird (Zeile 47–52) — die
Telemetrie (`OneInternalHW`-Log-Zeilen) läuft im aktuellen Test durchgehend.

**Keine Reparatur vorgenommen** — das ist ein Blocker außerhalb des Geräteabnahme-Auftrags
(„bei einem Blocker: anhalten und melden, nicht drumherum bauen"). Für die Entscheidung, wie
es weitergeht, folgender technischer Hinweis ohne Umsetzung: `isRunning()` bräuchte für die
reine Leseabfrage kein `su` — `getprop` ist für jeden Prozess ohne Root lesbar
(`SystemProperties.get()` per Reflection oder `Runtime.exec(arrayOf("getprop", ...))` direkt,
ohne `su`-Wrapper). Nur der tatsächliche `start $SERVICE`-Befehl bräuchte weiterhin
erhöhte Rechte.

## 3. Vorbereitung für Punkt 4 + 5 (CEO macht das selbst am Gerät)

**Update nach Abschnitt 6–8:** Der `su`-Blocker ist behoben, der manuelle `adb root`-Schritt
unten ist für den Selbststart selbst **nicht mehr nötig** — die App startet den Dienst jetzt
korrekt selbst (Abschnitt 6). Trotzdem bleibt aktuell **kein reales Live-Bild** zu erwarten,
weil Abschnitt 8 (JPEG/BLOB-HAL-Fehler) unabhängig davon weiterhin blockiert — das ist kein
Kabel-/Verbindungsproblem (vom CEO am Gerät bestätigt). Die Befehle unten bleiben als
Diagnose-Hilfsmittel stehen, falls für Punkt 4/5 ein warmer Dienst gebraucht wird, sind aber
nicht mehr die Ursache, wenn kein Bild kommt.

```
adb -s 233b4bd2865177ed root
adb -s 233b4bd2865177ed shell start vendor.camera-provider-2-4-ext
adb -s 233b4bd2865177ed shell getprop init.svc.vendor.camera-provider-2-4-ext   # erwartet: running
adb -s 233b4bd2865177ed unroot
```

Danach die App neu öffnen bzw. den Inspektionsbildschirm neu betreten (Zurück-Pfeil oben
links, dann erneut „Inspektion").

**Logcat-Filter, parallel mitlaufen lassen** (ein Terminal reicht):
```
adb -s 233b4bd2865177ed logcat -c
adb -s 233b4bd2865177ed logcat "Camera2FrameSource:V" "CameraServiceSelfStart:V" "V4L2Bridge:V" "DqLatencyOsd:V" "*:S"
```
- `Camera2FrameSource` — `onDisconnected` (Zeile 182) bzw. `onError code=...` (Zeile 189)
  zeigen, ob/wie die App das Ziehen des Kabels bemerkt; nach dem Wiederanstecken zeigt ein
  neuer `openCameraBlocking`-Durchlauf, ob die App selbständig neu verbindet.
- `CameraServiceSelfStart` — falls der Dienst durchs Kabel-Ziehen ebenfalls stoppt, greift
  hier wieder der in Abschnitt 2 beschriebene Blocker.
- `DqLatencyOsd` — falls für die Verzögerungsmessung (Punkt 5) ein Zeitstempel-Overlay im
  Bild aktiviert werden kann; nicht verifiziert, ob dieser Tag aktuell tatsächlich Daten
  liefert (nur im Quellcode gefunden, `Camera2FrameSource.kt:77/242`).

**Ablage für die Stoppuhr-Aufnahme (Punkt 5):** `_ap4_evidence/` im Repo-Root wurde für
diesen Lauf angelegt und ist `git`-unversioniert (Screenshots dieses Laufs liegen dort).
Bitte eigenes Video/Foto der Stoppuhr-Messung dort ablegen, z. B.
`_ap4_evidence/ap4_punkt5_glass_to_glass.mp4`, und im Nachgang benennen, welche Differenz
abgelesen wurde.

## 4. Was nicht geprüft werden konnte

- Ob der JPEG/BLOB-HAL-Fehler (Abschnitt 8) auf allen drei ausgelieferten Geräten gleich
  auftritt oder gerätespezifisch ist — nur auf `233b4bd2865177ed` geprüft.
- Ob der ~100-ms-Wettlauf (Abschnitt 7) auf langsameren/schnelleren Geräten größer/kleiner
  ausfällt.
- Warum die App nicht mehr automatisch HOME-Activity ist (Abschnitt 9): außerhalb des
  Auftrags, nur festgestellt und dokumentiert.
- Punkt 4 (Kabeltest) und Punkt 5 (Glas-zu-Glas): bewusst offengelassen für den CEO selbst,
  s. Abschnitt 3 — mit dem Hinweis, dass ohne Klärung von Abschnitt 8 kein Bild zu erwarten ist.

## 5. Empfehlung (Stand vor Abschnitt 6 — historisch)

Kein Merge, kein weiterer Fortschritt in AP-4 sinnvoll, bevor der `su`-Blocker in
`CameraServiceSelfStarter.isRunning()` geklärt ist — er verdeckt aktuell jede Aussage über
den eigentlichen Camera2-Pfad. Vorschlag (nicht umgesetzt): `isRunning()` ohne `su` lesen,
nur den `start`-Befehl weiterhin über `su`/Plattformrechte absetzen. Damit ließe sich
zumindest der Fall „Dienst läuft bereits" (z. B. nach manuellem Start durch den CEO für
Punkt 4/5) korrekt erkennen, ohne den ungeklärten Boot-Stopp-Mechanismus selbst zu lösen.

**Umgesetzt am selben Tag — siehe Abschnitt 6.** Die `su`-Sackgasse war eine falsche Annahme
in der ursprünglichen Vorgabe (CEO-Aussage), nicht ein Fehler der Umsetzung. Nach dem Umbau
zeigten sich zwei weitere, unabhängige Befunde (Abschnitt 7 + 8), die die aktuelle
AP-4-Ergebnistabelle (Abschnitt 1) begründen.

## 6. Nachbesserung: `CameraServiceSelfStarter` ohne `su` (SystemProperties per Reflection)

**Ursache der `su`-Sackgasse:** keine, die code-seitig behebbar war — die Annahme „Gerät ist
geroutet" traf für den App-Prozess nicht zu (Abschnitt 2). Da DrainQ.ONE plattformsigniert
ist und als `sharedUserId="android.uid.system"` läuft (ADR-0005), braucht es kein `su`:
`android.os.SystemProperties` (versteckte API) ist per Reflection erreichbar und für diesen
Zweck ausreichend.

**Umbau:** `CameraServiceSelfStarter.kt` liest den Dienstzustand jetzt über
`SystemProperties.get("init.svc.<service>")` und startet über
`SystemProperties.set("ctl.start", "<service>")` — kein `Runtime.exec`, kein `su` mehr in
dieser Klasse. `ensureCameraPermission()` erzwingt die CAMERA-Berechtigung nicht mehr über
`su pm grant` (auf `233b4bd2865177ed` ohnehin `SYSTEM_FIXED|GRANTED_BY_DEFAULT`, per
`dumpsys package` bestätigt — durch uid=system automatisch erteilt), sondern meldet nur noch
den Ist-Zustand.

**Getrennter Beleg, wie gefordert — nicht nur Gesamterfolg:**

1. **Lesen liefert den echten Zustand — getrennt für beide Zustände geprüft:**
   - *Gestoppt:* Dienst vorher per `adb root` + `stop vendor.camera-provider-2-4-ext`
     nachweislich gestoppt (`getprop` → `stopped`), App neu gestartet:
     ```
     16:03:51.344 W CameraServiceSelfStart: vendor.camera-provider-2-4-ext nicht running —
       starte via SystemProperties ctl.start
     ```
   - *Laufend:* zweiter Eintritt in den Inspektionsbildschirm, Dienst inzwischen laufend:
     ```
     16:04:52.359 I CameraServiceSelfStart: vendor.camera-provider-2-4-ext bereits running
     ```
     Kein erneuter Startversuch — der Lesepfad erkennt den laufenden Zustand korrekt, statt
     ihn (wie vorher der `su`-Pfad) grundsätzlich als „nicht running" zu behandeln.

2. **Starten wirkt tatsächlich aus dem App-Prozess heraus — nicht durch vorherigen manuellen
   Start:** Dienst gezielt per `adb root` gestoppt UND verifiziert (`getprop` → `stopped`),
   App per `am force-stop` beendet, dann **ausschließlich per `am start` neu gestartet** (kein
   manueller Diensteingriff danach). Log:
   ```
   16:03:51.344 W CameraServiceSelfStart: vendor.camera-provider-2-4-ext nicht running — starte via SystemProperties ctl.start
   16:03:51.350 I CameraServiceSelfStart: vendor.camera-provider-2-4-ext erfolgreich gestartet
   ```
   Unabhängig (per `adb getprop`, außerhalb der App) bestätigt: Dienst lief danach tatsächlich
   (`running`). Wiederholt mit sauberem Reboot (nicht nur Force-Stop) — derselbe Ablauf,
   gleiches Ergebnis.

`SystemProperties.set("ctl.start", …)` scheiterte in keinem Durchlauf — die Bedingung „wenn
das Setzen scheitert: anhalten und melden" ist nicht eingetreten.

Build (`assembleDebug`) und komplette Testsuite (`testDebugUnitTest`) nach dem Umbau erneut
grün geprüft.

## 7. Neuer Befund A: Wettlauf zwischen `ctl.start` und HAL-Geräteregistrierung (~100 ms)

Mit funktionierendem Selbststart trat ein **anderer, bisher verdeckter** Fehler zutage: beim
allerersten Kamera-Öffnen nach `ctl.start` meldet die App weiterhin
„Keine externe Kamera (LENS_FACING_EXTERNAL) gefunden".

Beleg (sauberer Reboot, realistische Bedienzeiten — App-Start, Dialog-Bestätigung,
Bildschirmwechsel, keine künstliche Eile):
```
16:06:29.541 W CameraServiceSelfStart: … nicht running — starte via SystemProperties ctl.start
16:06:29.547 I CameraServiceSelfStart: … erfolgreich gestartet
16:06:29.553 I OneInternalHW: Kamera nicht verfügbar: Keine externe Kamera (LENS_FACING_EXTERNAL) gefunden
16:06:29.646 I CamPrvdr@2.4-external: ExtCam: adding /dev/video0 to External Camera HAL!
```
`init.svc.<service>=running` kippt sofort beim Prozessstart der HAL — die externe
Kamera-HAL braucht danach noch **rund 100 ms**, um `/dev/video0` tatsächlich zu enumerieren
und bei `cameraserver` zu registrieren (Zeitstempel 29.541 → 29.646). `Camera2FrameSource`
prüft die Kamera-ID-Liste aber schon 12 ms nach dem erfolgreichen `ctl.start` (29.553) — zu
früh, `findExternalCameraId()` findet noch nichts und `openCameraBlocking()` bricht endgültig
ab (`Camera2FrameSource.kt:140-144`), ohne Wiederholung.

Zweiter, unmittelbar folgender Eintritt in den Bildschirm (Dienst und HAL inzwischen warm)
funktioniert — das bestätigt: reines Timing-Problem beim Kaltstart, kein grundsätzlicher
Defekt in der Registrierung. Nicht behoben (außerhalb des heutigen Auftrags) — Vorschlag:
`ensureRunning()` müsste nach `ctl.start` nicht nur auf `init.svc.…=running` pollen, sondern
zusätzlich auf das tatsächliche Erscheinen der Kamera-ID in `CameraManager.cameraIdList`
warten, bevor `Camera2FrameSource` einen endgültigen Fehler meldet.

## 8. Neuer Befund B: JPEG/BLOB-Pfad der externen Kamera-HAL liefert kein Bild

**Das ist der Grund, warum AP-4 Punkt 1–3 mit dem produktiven Code weiterhin fehlschlagen,
unabhängig von Abschnitt 6 und 7.** Nutzerrückmeldung am Gerät bestätigt: Kabel fest
angeschlossen, Kamera hat Strom — kein Anschlussproblem.

`Camera2FrameSource.pickFormatAndSize()` bevorzugt bewusst `ImageFormat.JPEG`
(Kommentar Zeile 54: „MJPEG-nativer MS2109-Chip", kein Re-Encode nötig). Mit diesem Format
meldet die externe Kamera-HAL:
```
D CamPrvdr@2.4-external: format is BLOB or YV12, use software NV12ToI420
E ExtCamDevSsn@3.4: threadLoop: Convert V4L2 frame to YU12 failed! res -1
```
— und zwar bei **1582 von 1587** Frame-Zyklen (99,7 %) im beobachteten Zeitraum. Die App
merkt davon nichts: `CameraManager.openCamera()` gelingt, `onOpened` feuert,
`ImageReader`-Buffer werden alloziert — aber es kommen keine gültigen Bilddaten an, ohne dass
ein Fehler propagiert wird. Ergebnis: dauerhaft schwarzes Bild, kein Fehlerbanner.

**Diagnose-Gegenprobe (durchgeführt, danach vollständig zurückgesetzt, nicht committet):**
`pickFormatAndSize()` testweise auf `ImageFormat.YUV_420_888` vor `JPEG` umgestellt.
Ergebnis:
- `Convert V4L2 frame to YU12 failed`: **0** Treffer (vorher 1582).
- Echtes Live-Bild sichtbar (`_ap4_evidence/ap4c_01_yuv_diagnostic.png`), Meterzähler live
  (`0.15 m`).
- 60s-Aufnahme mit Pause erfolgreich: `_ap4_evidence/ap4d_recording_60s.mp4` — ffprobe
  bestätigt h264, 1280×720, Dauer 61,56 s, 834 Frames, **Ø ~13,5 fps**.
- Aufnahme lief über den echten HW-Encoder (`c2.rk.avc.encoder`), kein
  `FallbackRecorder`/`LocalBitmapRecorder`-Rückfall diesmal (anders als beim JPEG-Pfad zuvor,
  wo „HW-Encoder-Start fehlgeschlagen — unsichtbarer Rückfall auf LocalBitmapRecorder" +
  `gestartet=false` geloggt wurde).

**Wichtiger Trade-off, deshalb bewusst NICHT übernommen:** Ø ~13,5 fps liegt deutlich unter
dem Ziel von ~30 fps und unter den in Welle 5 gemessenen ~27,55 fps des HW-Pfads. Vermutliche
Ursache: `YUV_420_888` erzwingt pro Frame die CPU-seitige Konvertierung
`yuvToJpegBytes()` (Zeile 241), die der ursprüngliche JPEG-Vorrang laut Kommentar genau
deshalb vermeiden sollte. Das ist eine Performance-Abwägung, keine reine Bugfix-Entscheidung
— deshalb zurückgesetzt und nicht Teil dieses Commits. `git diff` bestätigt: Datei ist wieder
identisch zum committeten Stand.

**Nicht behoben, nur belegt.** Offene Fragen für die Produktentscheidung:
1. Ob der JPEG/BLOB-Pfad der externen Kamera-HAL grundsätzlich reparierbar ist (Hersteller-
   anfrage nötig, liegt außerhalb des App-Codes) oder ob DrainQ.ONE dauerhaft auf
   `YUV_420_888` umstellen soll — mit der gemessenen fps-Einbuße als Kompromiss.
2. Ob es eine dritte Option gibt (z. B. ein anderes Zielformat/-Auflösung, das die
   Software-Konvertierung umgeht), die hier nicht geprüft wurde.

## 9. Separat festgehalten, nicht heute repariert: App nicht mehr HOME-Activity

Wie in Abschnitt 1 (Fußnote) bereits vermerkt: nach jedem Reboot in diesem Lauf war
`com.android.launcher3.uioverrides.QuickstepLauncher` die aktive HOME-Activity, nicht
`com.uip.drainq.one`, obwohl die App `android.intent.category.HOME` deklariert
(`MainActivity`-IntentFilter bestätigt, `cmd package resolve-activity` liefert den
System-Launcher). Die frühere Annahme „App ist Kiosk/HOME, startet immer automatisch" trifft
auf diesem Gerät **aktuell nicht** zu.

**Ursache offen** — nicht untersucht, eigene Baustelle, ausdrücklich nicht Teil dieses Laufs.
Für jeden App-Start in diesem Bericht war ein manueller `am start` nötig, was für sich genommen
bereits „ohne jeden manuellen Eingriff" (AP-4 Punkt 1) verletzt, unabhängig von Abschnitt 7/8.

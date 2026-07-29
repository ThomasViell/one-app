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
| 1 | Reboot, App startet, Live-Bild ohne manuellen Eingriff | **Teilweise** — Live-Bild kommt nach App-Start jetzt automatisch und zuverlässig (Retry Abschnitt 10.1, YUV-Pfad Abschnitt 10.2); ABER die App selbst startet nach Reboot nicht mehr automatisch (HOME-Problem, Abschnitt 9) → `am start` nötig | `_ap4_evidence/auftrag1_retry_first_entry.png` |
| 2 | 60s-Aufnahme mit Pause, Länge/fps/kein Zeitraffer | **JA, mit produktivem Code** — nach Bildraten-Rettung Abschnitt 10: 30,0 fps im fertigen Video (früherer Diagnose-Stand: 13,5 fps) | `_ap4_evidence/auftrag2_recording_final_30fps.mp4` (972 Frames/32,38 s = 30,0 fps); 60s-Lauf mit Pause: `ap4d_recording_60s.mp4` + `ap4d_01_paused_mid.png`/`ap4d_02_resumed_running.png` |
| 3 | Foto aus Livebild, OSD-Einbrennung | OSD-Einbrennung in Aufnahme belegt (REC/PAUSE korrekt, „Mit Einblendung"); Foto-Einzelfunktion nicht separat abgenommen | `_ap4_evidence/ap4d_01_paused_mid.png`, `ap4d_02_resumed_running.png` |
| 4 | Kabel ab-/anstecken, Bild kommt von allein zurück | **JA — vom CEO am Gerät abgenommen (29.07.)** | s. Abschnitt 11 |
| 5 | Glas-zu-Glas-Verzögerung vs. ~220 ms | **Abgenommen ohne Zahlenwert** — Bewegungsprobe statt Stoppuhr, kein spürbarer Versatz. Der ~220-ms-Vergleichswert ist ungültig, s. Abschnitt 11 | s. Abschnitt 11 |
| 6 | Gesamte Testsuite grün | **Ja** (nach jedem Umbau erneut geprüft, zuletzt nach Abschnitt 10) | `gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, alle Module grün |

**AP-4 damit insgesamt abgenommen — siehe Abschnitt 11 für den vollständigen CEO-Befund zu
Punkt 1–5.**

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

## 10. Bildraten-Rettung (CEO-Auftrag nach Abschnitt 8) — GELUNGEN: 30 fps durchgängig

**Rahmen:** Wettlauf reparieren (Auftrag 1), Bildrate messen statt annehmen (Auftrag 2),
höchstens zwei Anläufe, Ziel deutlich über 20 fps. Der fehlerhafte JPEG/BLOB-Pfad der
Hersteller-HAL wird nicht repariert (bleibt so entschieden).

### 10.1 Auftrag 1 — Wettlauf repariert, belegt

`Camera2FrameSource.openCameraBlocking()` pollt jetzt bis zu 5 s (250-ms-Zyklen) auf das
Erscheinen der externen Kamera statt nach dem ersten Fehlversuch endgültig aufzugeben;
bleibt sie aus, gibt es die bestehende sichtbare Fehlermeldung mit Wartezeit-Hinweis.

Beleg (Dienst extern gestoppt + verifiziert, App ausschließlich per `am start` gestartet,
kein weiterer Eingriff):
```
16:28:34.727 W CameraServiceSelfStart: … nicht running — starte via SystemProperties ctl.start
16:28:34.732 I CameraServiceSelfStart: … erfolgreich gestartet
16:28:34.993 I Camera2FrameSource: Externe Kamera nach 1 Wartezyklen erschienen (Wettlauf-Retry griff)
```
Live-Bild beim ersten Eintritt in den Inspektionsbildschirm:
`_ap4_evidence/auftrag1_retry_first_entry.png`.

### 10.2 Auftrag 2 — Messungen (getrennt, wie verlangt)

Instrumentierung fest eingebaut, per `setprop log.tag.DqFpsStats DEBUG` aktivierbar
(plus `DqFpsProbe` = reine Ankunftsmessung ohne Verarbeitung, `DqLegacyYuv` = alter
Pfad erzwingen). Alle Werte am Gerät `233b4bd2865177ed`, 1280×720.

**Frage „Kamera oder wir?" — Antwort: wir.**

| Messung | Ergebnis |
|---|---|
| Reine Lieferrate der Kamera (Sonde, keine Verarbeitung) | **30,0–30,2 fps konstant** — Hardware liefert volle Rate |
| Anzeigepfad alt (NV21-Bytekopie + JPEG-Encode + JPEG-Decode) | 27,5–28,5 fps; 28,9 ms Konvertierung + 5,7 ms Decode pro Frame |
| Anzeigepfad neu (native YUV→RGB565, Anlauf 1) | 29,4–30,1 fps |
| **Aufnahme (Ausgangslage)** | **13,0 fps** — Stufenmessung: Vorbereitung 9,0 ms, `encode()` 68,8 ms; darin `fillImage` (Bitmap→I420) **62–65 ms**, dequeue/getImage/queue+drain zusammen < 3 ms |
| Aufnahme nach Anlauf 2a (gecachte Zwischenpuffer + memcpy-Bursts statt Einzelbyte-Stores in die DMA-Planes; RGB565-Scratch statt ARGB-Kopie) | 16,5–16,9 fps (Vorbereitung 9→2 ms, fill 62→54 ms) — Verbesserung, aber nicht ausreichend |
| Kern-Verdacht gemessen (kein Anlauf): läuft der Encode-Thread auf einem LITTLE-Kern? | **Nein** — 10 Stichproben, alle auf CPU 4–7 (A76 big cores) |
| Eigentliche Ursache | **Der native Code wurde im Debug-APK mit `-O0` gebaut.** Die dokumentierten M3a-Zeiten („~3–6 ms, -O3", Kommentar in `v4l2bridge.c`) setzten `-O3` voraus; `assembleDebug` → CMake-Debug → keine Optimierung, keine Vektorisierung |
| **Aufnahme nach Fix (`target_compile_options(v4l2bridge PRIVATE -O3)` in `CMakeLists.txt`)** | **29,9–30,1 fps** — fill 54→10,5 ms, Anzeige-Konvertierung 33→13,9 ms; Anzeige UND Aufnahme parallel bei 30 fps |
| Endbeleg fertiges Video (ffprobe) | `_ap4_evidence/auftrag2_recording_final_30fps.mp4`: h264, 1280×720, **972 Frames / 32,38 s = 30,0 fps** (Vergleich vorher: 422/32,3 s = 13,05) |

**Einordnung der 13,5-fps-Ausgangsmessung:** Sie stammte aus dem aufgenommenen Video —
der Anzeigepfad allein lief schon vorher mit ~28–30 fps. Der Verlust entstand im
Aufnahmepfad (`fillImage`), und dort zu ~80 % durch den `-O0`-Bau der nativen Konvertierung,
nicht durch die Kamera (30 fps belegt) und nicht durch das YUV-Format an sich.

**Was geändert wurde (alles committet):**
1. Retry-Schleife in `Camera2FrameSource` (Auftrag 1).
2. `pickFormatAndSize`: YUV_420_888 vor JPEG (der JPEG-Pfad der HAL ist tot, Abschnitt 8) —
   mit Begründungskommentar im Code.
3. Neue native Direkt-Konvertierung `nativeYuvToBitmap` (YUV→RGB565 ohne JPEG-Umweg) in
   `v4l2bridge.c` + Kotlin-Anbindung; alter Pfad bleibt als Rückfall (`DqLegacyYuv`).
4. `nativeConvertToI420`: Konvertierung in gecachte Zwischenpuffer, nur noch
   memcpy-Bursts in die MediaCodec-Planes (deckt NV12- und I420-Layout ab).
5. `HardwareBitmapRecorder`: wiederverwendeter RGB565-Scratch statt 3,7-MB-ARGB-Kopie
   pro Frame; Stufen-Instrumentierung in Encode-Schleife und `H264Encoder.encode`.
6. `CMakeLists.txt`: `-O3` für die native Bibliothek in ALLEN Build-Typen — der
   eigentliche Haupthebel.

**Nicht gemacht:** GPU-Verlagerung (Surface-Input) — nicht mehr nötig, das Ziel ist ohne
sie erreicht; wäre zudem ein Eingriff in die Welle-5-PTS/Pause-Semantik gewesen.
Testsuite nach allen Änderungen grün.

**Resthinweis:** ~14 ms (Anzeige) + ~11 ms (Encoder) CPU-Konvertierung pro Frame bleiben
Doppelarbeit (YUV→RGB→YUV), weil das OSD-Einbrennen ein RGB-Raster braucht. Bei 30 fps
ist das verkraftbar; sollte später 60 fps oder 1080p gefordert sein, ist der
Surface-/GPU-Weg der nächste Hebel.

## 11. AP-4 abgenommen — CEO-Prüfung am Gerät (29.07.2026)

Punkt 1 bis 5 hat der CEO selbst am Gerät `233b4bd2865177ed` geprüft (nicht durch mich
gemessen — hier nur protokolliert, was gemeldet wurde):

- **Bild:** flüssig, sauber.
- **Punkt 1 (Neustart):** mehrfaches Aus- und Einschalten des Systems ohne Auffälligkeiten.
- **Punkt 4 (Kabeltest):** Kabel ab- und wieder angesteckt — funktioniert, Bild kommt von
  allein zurück.
- **Punkt 5 (Verzögerung):** **kein gemessener Zahlenwert.** Statt Stoppuhr wurde eine
  Bewegungsprobe gemacht — Hand vor die Linse halten und wegziehen, Fingerschnippen —, dabei
  kein spürbarer Versatz zwischen Bewegung und Bild. Das ist eine subjektive Abnahme, keine
  Messung; hier wird kein Millisekundenwert geschätzt oder nachträglich konstruiert.

**AP-4 ist damit insgesamt abgenommen.**

### Wichtiger Vorbehalt zum ~220-ms-Vergleichswert

Der in `UMBAU_CAMERA2_PROMPT.md` genannte Referenzwert „~220 ms" für den alten Videopfad
**ist als Vergleichszahl unbrauchbar.** Abschnitt 10 hat belegt: natives Konvertierungs-
Codes wurde im Debug-APK ohne Optimierung gebaut (`-O0` statt der vorgesehenen `-O3`) und
das kostete dort gemessen ~50 ms zusätzlich pro Frame, allein durch die fehlende
Compiler-Optimierung — nicht durch den Videoweg selbst. Ob der alte ~220-ms-Wert unter
denselben `-O0`-Bedingungen entstand, ist nicht mehr feststellbar, aber die Möglichkeit
allein entwertet den Vergleich.

**Das gilt nicht nur für diesen einen Wert:** Jeder Leistungs-/Latenzwert, der aus einem
Debug-Build **vor dem 29.07.2026** stammt (vor dem `-O3`-Fix in `CMakeLists.txt`), ist aus
demselben Grund als Vergleichsbasis fragwürdig, bis er mit einem Bau nach diesem Fix erneut
gemessen wurde. Das betrifft insbesondere die in älteren RESULT-/PERF-Dokumenten genannten
Latenz- und fps-Werte (z. B. Welle 5: „~27,55 fps", G2G-Messreihen). Keine dieser Zahlen wird
hier rückwirkend korrigiert oder neu geschätzt — nur als möglicherweise verzerrt markiert.

## 12. AP-5 — Alten V4L2-Direktpfad entfernt

Nach AP-4-Abnahme (Abschnitt 11) und Bildraten-Rettung (Abschnitt 10) wie in
`UMBAU_CAMERA2_PROMPT.md` vorgesehen: toten Code des alten Videowegs entfernt.

**Entfernt:**
- `app/src/main/java/com/uip/oneapp/network/internal/V4L2Camera.kt` — komplett gelöscht
  (direkter `/dev/video0`-Zugriff, MJPEG-Dequeue über JNI).
- `app/src/main/cpp/v4l2bridge.c` — die vier V4L2Camera-JNI-Funktionen (`nativeOpen`,
  `nativeSetupMjpeg`, `nativeDequeueFrame`, `nativeClose`), der `v4l2_ctx`-Typ, der
  `xioctl`-Helfer und die dafür nötigen Includes (`sys/ioctl.h`, `sys/mman.h`,
  `sys/select.h`, `linux/videodev2.h`) entfernt. **Verblieben** (aktiv genutzt, nicht Teil
  des alten Videowegs): die RGB→I420-Konvertierung für `H264Encoder`, die neue
  YUV→RGB565-Konvertierung für `Camera2FrameSource` (Abschnitt 10), und der serielle
  UART-Port für `OneInternalHardwareService` (`/dev/ttyS5`, unverändert).
- `CameraFrameBus`-Konstruktor: Default-Parameter `= V4L2Camera()` entfernt (Quelle wird
  seit AP-1 immer explizit per DI übergeben) — `V4L2State` dorthin verschoben, da
  `Camera2FrameSource` sie weiterhin als gemeinsamen Zustandstyp braucht.
- `OneInternalHardwareService`-Konstruktor: Default-Parameter `= CameraFrameBus()` aus
  demselben Grund entfernt (kein Aufrufer nutzte ihn — production-DI übergibt immer
  explizit `get()`).
- Veraltete KDoc-/Kommentar-Verweise auf `V4L2Camera` in `AppModule.kt`,
  `DeviceFilePermissionBootstrap.kt`, `Camera2FrameSource.kt`, `OneInternalHardwareService.kt`
  und `build.gradle.kts` aktualisiert bzw. entfernt.

**Nicht angefasst** (bewusst außerhalb des Auftrags): historische Dokumente
(`RESULT_KAMERA_CAMERA2_2026-07-29.md`, `PERF_W3C_VIDEO_LATENCY_2026-06-25.md`,
`docs/adr/0003-device-node-permissions.md` u. a.) und `tools/_spike/` (separates,
bereits archiviertes Spike-Projekt) — die erwähnen `V4L2Camera` weiterhin, beschreiben
aber vergangene Zustände und wurden nicht rückwirkend umgeschrieben.

**Belege:**
- `gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`, alle Module grün.
- Gerätecheck `233b4bd2865177ed`: App neu installiert, Inspektionsbildschirm geöffnet —
  Live-Bild kommt weiterhin fehlerfrei (`_ap4_evidence/ap5_after_dead_code_removal.png`).
  Der Ausbau hat nichts kaputt gemacht.

## 13. ZUSATZ: HOME-Activity-Autostart — Ursache belegt, Fix angewendet, Gerätebeweis OFFEN

Bezug zu Abschnitt 1/9: nach jedem Reboot in diesem Lauf startete `com.android.launcher3`
statt DrainQ.ONE, entgegen der früheren Annahme „App ist Kiosk/HOME, startet immer
automatisch".

### 13.1 Schritt 1 — Ursache belegt (nicht vermutet)

Drei unabhängige Belege am Gerät `233b4bd2865177ed`:

1. `adb shell dumpsys device_policy` → Abschnitt „Enabled Device Admins" **leer** — kein
   Device-Owner aktuell aktiv.
2. `adb shell dumpsys package preferred-xml` → die persistierte HOME-Präferenz zeigte auf
   `com.android.launcher3/.uioverrides.QuickstepLauncher`, nicht auf DrainQ.ONE.
3. **Entscheidender Beleg:** `adb shell dumpsys package com.uip.drainq.one` →
   `firstInstallTime=2026-07-29 12:26:01`. Das ist nicht nur ein `lastUpdateTime` (das ein
   reines Update anzeigen würde) — `firstInstallTime` heute bedeutet: die App wurde heute
   tatsächlich **deinstalliert und neu installiert**, kein bloßes Update. Der Zeitpunkt
   deckt sich exakt mit der Plattformsignatur-Umstellung von heute Morgen (ADR-0005), deren
   Abschnitt 4 genau das für den Signaturwechsel als **notwendige Folge** dokumentiert:
   „Der Signaturwechsel erzwingt einmalig Deinstallation und Neuinstallation … mit Verlust
   der lokalen Projektdaten."

**Kausalkette, belegt:** Plattformsignatur-Wechsel (ADR-0005, CEO-Entscheid) → erzwungene
Deinstallation/Neuinstallation (Beleg: `firstInstallTime`) → Android löscht bei Deinstallation
sowohl Device-Owner-Status als auch die persistierte HOME-Standard-App-Zuordnung, weil beide
an das installierte Paket gebunden sind, nicht an die App als solche (Beleg: leere
Admin-Liste + `preferred-xml` zeigt Launcher3) → Gerät bootet in den System-Launcher.

Kein Zusammenhang mit dem Camera2-Umbau selbst — reine Nebenwirkung der (separat
beschlossenen und bereits als datenverlustträchtig dokumentierten) Signaturumstellung.

### 13.2 Schritt 2 — Fix angewendet, **Gerätebeweis (2× Reboot) NICHT abgeschlossen**

Angewendet:
```bash
adb shell cmd package set-home-activity com.uip.drainq.one/com.uip.oneapp.MainActivity
# → Success
```
Sofort verifiziert (ohne Reboot):
- `dumpsys package preferred-xml` zeigt danach `com.uip.drainq.one/com.uip.oneapp.MainActivity`
  als HOME-Präferenz.
- `cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME`
  liefert `packageName=com.uip.drainq.one`.

**Der geforderte Beweis „System aus, System an, App ist ohne jeden Eingriff da — zweimal
hintereinander" ist NICHT erbracht.** Der erste Reboot-Test lief gerade (`adb reboot` +
`wait-for-device` liefen), als der Geräteakku leer wurde — CEO-Meldung: Reboot aktuell nicht
möglich, Gerät erst am 30.07. wieder verfügbar. Kein Erfolg behauptet, wo keiner belegt ist:
**offen bis zum nächsten Gerätekontakt.**

Device-Owner (`dpm set-device-owner`) wurde bewusst **nicht** erneut gesetzt — das war nicht
Teil des engeren Auftrags („App … der Startbildschirm … ohne dass jemand sie antippen muss"),
sondern eine größere, invasivere Änderung (Voraussetzung: keine Benutzerkonten, sperrt andere
Apps). Ob das zusätzlich gewünscht ist, offen für Rückfrage.

### 13.3 Schritt 3 — Als Einrichtungsschritt dokumentiert

`docs/PROVISIONING_GOLDEN_IMAGE.md`, Abschnitt A.4.1: neuer Nachtrag mit der belegten Ursache,
dem ADB-Befehl (`cmd package set-home-activity`) als schnellerer Alternative zum UI-Weg, und
dem Hinweis, dass dieser Schritt **nach jeder** Signatur-/`sharedUserId`-Änderung erneut nötig
ist — nicht nur bei der Erstinbetriebnahme —, sowie dass ein vor einer Signaturumstellung
gezogenes Golden-Image die alte (dann ungültige) HOME-/Device-Owner-Bindung enthält und neu
gezogen werden muss.

### 13.4 Vorschlag für App-seitige Robustheit — NICHT umgesetzt, nur vorgeschlagen

Auf ausdrücklichen Wunsch nicht gebaut, nur zur Entscheidung vorgelegt:

1. **Selbstprüfung beim Start:** Die App könnte beim Start per
   `PackageManager`/`RoleManager` prüfen, ob sie aktuell die HOME-Standard-App ist, und falls
   nicht, einen deutlichen Audit-Log-Eintrag schreiben (`AUDIT home_default_lost`) — damit eine
   stillschweigende Regression wie diese auf einem Flottengerät nicht wochenlang unbemerkt
   bleibt, sondern in der Telemetrie auffällt.
2. **Selbstheilung, falls Device-Owner aktiv:** Ist die App Device-Owner, könnte sie bei jedem
   Start idempotent `DevicePolicyManager.addPersistentPreferredActivity(...)` für sich selbst
   aufrufen — das repariert die HOME-Bindung automatisch nach jedem Reinstall, bei dem
   Device-Owner-Status selbst erhalten bleibt (reine Versions-Updates ohne Signaturwechsel
   verlieren ihn ohnehin nicht). Löst NICHT den Fall eines echten Deinstall/Reinstall mit
   Signaturwechsel (dort geht auch Device-Owner verloren, s. o.) — dafür bräuchte es weiterhin
   den manuellen Provisionierungsschritt aus Abschnitt 13.3.
3. **Sichtbarer Hinweis für den Bediener:** Falls beim Start erkannt wird, dass die App nicht
   HOME ist, könnte ein einmaliger, unaufdringlicher Hinweis-Screen erscheinen
   („Als Startbildschirm festlegen?" mit Link zu den Systemeinstellungen) — hilft im Feld ohne
   ADB-Zugriff.

Keiner dieser drei Vorschläge ist implementiert.

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

## 1. AP-4 — Ergebnistabelle

| # | Punkt | Ergebnis | Beleg |
|---|---|---|---|
| 1 | Reboot, App startet, Live-Bild ohne manuellen Eingriff | **NEIN — Blocker, Ursache gefunden** | s.u. Abschnitt 2 |
| 2 | 60s-Aufnahme mit Pause, Länge/fps/kein Zeitraffer | **Nicht messbar** — blockiert durch Punkt 1 | `_ap4_evidence/ap4_06_recording_attempt.png` (kein Effekt beim Antippen von „Aufnahme“, Fehlerbanner bleibt) |
| 3 | Foto aus Livebild, OSD-Einbrennung | **Nicht messbar** — blockiert durch Punkt 1, aus demselben Code-Pfad (s. Abschnitt 2) nicht separat isoliert getestet | — |
| 4 | Kabel ab-/anstecken, Bild kommt von allein zurück | **Offen** — macht der CEO selbst am Gerät. Vorbereitung s. Abschnitt 3 | — |
| 5 | Glas-zu-Glas-Verzögerung vs. ~220 ms | **Offen** — macht der CEO selbst am Gerät. Vorbereitung s. Abschnitt 3 | — |
| 6 | Gesamte Testsuite grün | **Ja** | `gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, alle Module grün |

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

**Wichtiger Hinweis vorab:** Beide Tests setzen ein Live-Bild voraus, das aktuell wegen des
Blockers aus Abschnitt 2 nicht zustande kommt. Vor dem Test muss der Dienst einmal von Hand
hochgezogen werden — das ändert nichts an `isRunning()`s Problem, aber ob danach überhaupt
ein Bild kommt, war innerhalb dieses Laufs nicht mehr zu klären (s. Abschnitt 2). Falls nach
den Schritten unten weiterhin „Kamera nicht verfügbar" steht, ist das ein weiterer Beleg für
denselben Blocker, kein neuer Befund.

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

- AP-1 (Camera2-Bildpfad) isoliert vom AP-2-Blocker: nicht möglich in diesem Lauf, s. Abschnitt 2.
- Punkt 2 (60s-Aufnahme), Punkt 3 (Foto/OSD): Konsequenz aus Punkt 1, nicht separat vertieft.
- Warum die App nicht mehr automatisch HOME-Activity ist: außerhalb des Auftrags, nur festgestellt und dokumentiert.
- Punkt 4 (Kabeltest) und Punkt 5 (Glas-zu-Glas): bewusst offengelassen für den CEO selbst, s. Abschnitt 3.

## 5. Empfehlung

Kein Merge, kein weiterer Fortschritt in AP-4 sinnvoll, bevor der `su`-Blocker in
`CameraServiceSelfStarter.isRunning()` geklärt ist — er verdeckt aktuell jede Aussage über
den eigentlichen Camera2-Pfad. Vorschlag (nicht umgesetzt): `isRunning()` ohne `su` lesen,
nur den `start`-Befehl weiterhin über `su`/Plattformrechte absetzen. Damit ließe sich
zumindest der Fall „Dienst läuft bereits" (z. B. nach manuellem Start durch den CEO für
Punkt 4/5) korrekt erkennen, ohne den ungeklärten Boot-Stopp-Mechanismus selbst zu lösen.

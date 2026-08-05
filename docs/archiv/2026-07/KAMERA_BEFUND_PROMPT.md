# AUFTRAG: Kamera über Standard-Camera2 — Verursacher finden und Bildbeweis führen

ROLLE: Android-Ingenieur im Repo `C:\Projekte\drainq.one`, Branch `feature/dual-mode`.

**Dieser Lauf sucht und misst. Er baut NICHTS um.** Kein Umbau des Videopfads, kein Entfernen von `v4l2bridge.c`, kein Merge, kein Publish. Am Ende steht ein Bericht, keine Änderung am Produktivcode.

## AUSGANGSLAGE — am Gerät `233b4bd2865177ed` bereits bewiesen, NICHT erneut prüfen
- Die ONE bringt ab Werk den External-Camera-HAL mit: `vendor.camera-provider-2-4-ext`.
- Er **startet beim Booten** (`ro.boottime.vendor.camera-provider-2-4-ext` gesetzt) und ist danach **gestoppt** (`init.svc.vendor.camera-provider-2-4-ext` = stopped).
- `dumpsys media.camera` zeigt `ADD device 100` um 13:40:36 und `REMOVE device 100, reason: (Device status changed from 1 to 0)` um 13:40:49.
- `/vendor/etc/external_camera_config.xml` existiert und listet 1280×720 bis 60 fps.
- **Gegenprobe gelaufen:** nach `adb root` + `am force-stop com.uip.drainq.one` + `start vendor.camera-provider-2-4-ext` meldet `dumpsys media.camera` **Number of camera devices: 1** (vorher 0).
- Der Direktzugriff auf `/dev/video0` ist app-seitig unmöglich (Gruppe `camera` unerreichbar) — abgeschlossen, nicht neu aufrollen.

## OFFENE FRAGEN — das ist der Auftrag

### FRAGE 1: Wer stoppt den Kameradienst?
`init.drainq.rc` liegt als `.disabled` in der Overlay-Schicht und wird von init nicht mehr geparst. **Trotzdem** ist der Dienst nach einem Neustart gestoppt und `/dev/video0` steht auf `0666`. Es gibt also einen zweiten, unbekannten Mechanismus.

Hauptverdacht: **die App selbst.** Sie ist auf diesem Gerät die HOME-Activity (Kiosk/Device-Owner) und startet nach jedem Neustart automatisch — „App nicht öffnen" ist auf diesem Gerät nicht möglich.

Zu prüfen, in dieser Reihenfolge:
1. Repo-Suche im gesamten `drainq.one`-Quellcode nach allem, was den Dienst stoppt oder Rechte setzt: `camera-provider`, `ctl.stop`, `ctl.start`, `SystemProperties.set`, `chmod`, `0666`, `video0`, `Runtime.exec`, `ProcessBuilder`. Fundstellen mit Datei:Zeile belegen.
2. Auf dem Gerät nach weiteren aktiven Startskripten suchen, die `video` oder `camera-provider` anfassen — auch in Overlay-Schichten und `/data/local`.
3. **Gegenprobe am Gerät:** Gerät neu starten, sofort (vor dem ersten App-Start, falls möglich) `getprop init.svc.vendor.camera-provider-2-4-ext` und `ls -l /dev/video0` messen. Dann die App laufen lassen und erneut messen. Der Unterschied zeigt, ob die App der Verursacher ist.

Ergebnis muss eine **belegte** Aussage sein, welcher Code den Dienst stoppt — nicht eine plausible Vermutung. Findest du ihn nicht, schreib das so.

### FRAGE 2: Kommt über den regulären Weg ein echtes Bild?
Bisher ist nur belegt, dass sich die Kamera **anmeldet**. Ein Bild wurde nicht gezeigt.

Beweise es mit einem **Wegwerf-Testprojekt außerhalb des Repos** (z. B. unter `C:\Temp\cam2probe`) — die DrainQ-App wird dafür nicht angefasst:
- Minimale App, die die Kamera mit `LENS_FACING_EXTERNAL` über die Standard-Kamera-API öffnet und ein Vorschaubild anzeigt.
- Vorher `am force-stop com.uip.drainq.one` und `start vendor.camera-provider-2-4-ext`, damit der Verursacher nicht dazwischenfunkt.
- Beleg: **Screenshot mit sichtbarem Live-Bild** vom Kabelkopf, plus die Auflösung und die gemessene Bildrate.
- Zusätzlich messen und im Bericht nennen: Verzögerung vom Bild zur Anzeige, grob per Stoppuhr oder Zeitstempel. Der bisherige Wert des alten Pfads liegt bei rund 220 Millisekunden — der neue Weg muss da mithalten, sonst ist er für die Inspektion unbrauchbar.

Scheitert das Öffnen oder bleibt das Bild schwarz: **das ist ein gültiges Ergebnis.** Fehlermeldung wörtlich in den Bericht, keine Reparaturversuche ins Blaue.

## HARTE REGELN
1. **Kein `git add -A`**, keine repo-weiten Git-Befehle. Am Produktivcode wird in diesem Lauf ohnehin nichts geändert.
2. Das Testprojekt liegt **außerhalb** von `C:\Projekte\drainq.one`.
3. Nur Gerät `233b4bd2865177ed`. Louis' Gerät wird nie angefasst.
4. Kein Merge, kein Tag, kein Publish, kein Umbau des Videopfads.
5. `adb root` ist für Messungen erlaubt. Dauerhafte Änderungen am Gerät (Dateien in `/vendor`, `/system`, neue init-Skripte) sind **verboten** — außer der Dienst muss für eine Messung gestartet werden, das ist flüchtig und in Ordnung.

## BERICHT
Schreibe `RESULT_KAMERA_CAMERA2_2026-07-29.md` in den Repo-Root:

1. **Frage 1 — Verursacher:** wer stoppt den Dienst, mit Datei:Zeile bzw. Gerätebeleg. Oder „nicht gefunden" mit Angabe, wo überall gesucht wurde.
2. **Frage 2 — Bildbeweis:** Screenshot-Pfad, Auflösung, Bildrate, Verzögerung. Oder die wörtliche Fehlermeldung.
3. **Empfehlung:** trägt der reguläre Weg, ja oder nein, und was müsste der Umbau konkret anfassen.
4. **Was du NICHT prüfen konntest.** Vollständig. Ein ehrliches „nicht messbar" ist mehr wert als ein plausibel klingendes Ergebnis.

Committe am Ende nur diese eine Datei, dann **STOPP**.

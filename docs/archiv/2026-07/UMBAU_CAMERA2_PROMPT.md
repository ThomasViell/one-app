# AUFTRAG: Videoweg auf Standard-Camera2 umbauen

ROLLE: Android-Ingenieur im Repo `C:\Projekte\drainq.one`.

Lies zuerst `RESULT_KAMERA_CAMERA2_2026-07-29.md` (Commit `c9b0542`). Dieser Auftrag setzt dessen Empfehlung um.

## AUSGANGSLAGE — bereits bewiesen, NICHT neu prüfen
- Die ONE stellt die USB-Kamera ab Werk über die Standard-Camera2-API bereit (`LENS_FACING_EXTERNAL`), sobald `vendor.camera-provider-2-4-ext` läuft.
- Bildbeweis erbracht: 1280×720, stabil 30 fps, ~43 ms Sensor→App (Wegwerf-App `C:\Temp\cam2probe`).
- Direktzugriff auf `/dev/video0` ist app-seitig unmöglich — abgeschlossen.
- **Ungeklärt:** Der Dienst stoppt ~1 s nach Boot-Completed. Die App als Verursacher ist widerlegt, ebenso `/system/etc/init/init.drainq.rc` + `drainq-postboot.sh`. Nicht erneut jagen — dieser Auftrag umgeht das Problem, siehe AP-2.

## CEO-ENTSCHEIDUNG 29.07.
Umbau auf den regulären Weg. Die App startet den Kameradienst selbst. **Das ist bewusst eine Krücke und kein Ursachenfix** — so und nicht anders dokumentieren, keine Formulierung, die es als gelöste Ursache darstellt.

## ARBEITSPAKETE

### AP-1 — Videoquelle umstellen
Ersetze den nativen V4L2-Pfad durch Camera2:
- Neue Frame-Quelle, die die Kamera mit `LENS_FACING_EXTERNAL` öffnet und Frames in denselben Datenfluss einspeist, den `VideoSource`/`LocalBitmapVideoPlayer` heute konsumiert. **Der Rest der App — UI, OSD-Einbrennung, Aufnahme, PDF, Fotos — bleibt unangetastet.** Das ist die Naht, an der gearbeitet wird, sonst nichts.
- Auflösung 1280×720, Ziel 30 fps.
- Kein `/dev/video0`, kein `open()`, keine V4L2-ioctls mehr im neuen Pfad.
- `v4l2bridge.c` und die zugehörige NDK-Einbindung **noch nicht löschen** — erst in AP-5, nach der Geräteabnahme. Bis dahin bleibt der alte Pfad als Rückfall im Code, aber inaktiv.

### AP-2 — Kameradienst-Selbststart (die Krücke)
Die App stellt beim Start sicher, dass `vendor.camera-provider-2-4-ext` läuft:
- Prüfen, ob der Dienst läuft; wenn nein, starten.
- Wenn das Starten fehlschlägt: **sichtbare, verständliche Meldung an den Nutzer** („Kamera nicht verfügbar" mit Hinweis), kein stiller Fehlschlag und kein schwarzes Bild ohne Erklärung. Ereignis ins Audit-Log.
- Im Code an der Stelle einen Kommentar, der den Sachverhalt festhält: unbekannter Mechanismus stoppt den Dienst nach dem Booten, Ursache am 29.07. nicht auffindbar, dies ist eine bewusste Kompensation.
- **Default-Prüfung:** Sicherstellen, dass dieser Selbststart in der ausgelieferten Konfiguration tatsächlich aktiv ist und nicht hinter einem abgeschalteten Schalter liegt.

### AP-3 — Altlasten stilllegen
- `init.drainq.rc` (Overlay-Variante) wird nicht mehr gebraucht — im Repo als überholt kennzeichnen, Entfernungsanweisung fürs Gerät dokumentieren, aber **nicht** eigenmächtig am Gerät löschen.
- Prüfen und im Bericht festhalten, ob irgendwo im Code noch etwas `/dev/video0`-Rechte setzt oder den Kameradienst stoppt. Falls ja: raus.

### AP-4 — Geräteabnahme (Pflicht vor jedem Commit-Abschluss)
Auf `233b4bd2865177ed`, mit Belegen:
1. Gerät neu starten, App normal starten. **Kommt das Live-Bild ohne jeden manuellen Eingriff?** Screenshot.
2. Aufnahme starten, mindestens 60 Sekunden, mit Pause. Video prüfen: Länge, Bildrate, keine Zeitraffer-Effekte.
3. Foto aus dem Livebild, OSD-Einbrennung prüfen.
4. Kabel ab- und wieder anstecken. Kommt das Bild von allein zurück?
5. Bildverzögerung: so nah wie möglich an Glas-zu-Glas messen — laufende Stoppuhr auf einem zweiten Bildschirm abfilmen und Differenz ablesen. Referenz des alten Pfads: ~220 ms. **Wenn der neue Weg deutlich träger ist, ist das ein Blocker** — dann melden und stoppen, nicht schönreden.
6. Gesamte Testsuite grün.

Jeder Punkt mit Beleg. Punkte, die nicht messbar sind, ausdrücklich als „nicht messbar" ausweisen.

### AP-5 — Aufräumen, erst nach AP-4
`v4l2bridge.c`, NDK-Einbindung und toter Code entfernen. Danach Tests erneut grün.

## HARTE REGELN
1. **Kein `git add -A`**, keine repo-weiten Git-Befehle. Nur namentlich genannte Pfade stagen.
2. Arbeit auf einem eigenen Branch ab `feature/dual-mode`, z. B. `feature/camera2-umstieg`. **Kein Merge nach master, kein Tag, kein Publish.**
3. Nur Gerät `233b4bd2865177ed`. Louis' Gerät wird nie angefasst.
4. Keine dauerhaften Änderungen am Gerät in `/vendor` oder `/system`.
5. Zwischenstände regelmäßig committen und pushen — unverfolgte Dateien sind hier schon einmal teuer geworden.
6. Bei einem Blocker: anhalten und melden, nicht drumherum bauen.

## BERICHT
`RESULT_CAMERA2_UMBAU_2026-07-29.md` im Repo-Root: was geändert wurde, AP-4 mit allen sechs Belegen, gemessene Verzögerung im Vergleich zu ~220 ms, was nicht geprüft werden konnte, und die verbleibenden Risiken.

Danach **STOPP** zur CEO-Abnahme.

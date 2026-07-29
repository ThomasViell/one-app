# FIX — Kamera-Node-Freigabe /dev/video0 persistent (drainq.one)

Rolle: Du bist Software-Ingenieur/Produktowner von drainq.one (ONE-Schiebekamera,
RK3588/Android, Repo C:\Projekte\drainq.one). Arbeite streng nach dem Repo-Regelwerk.

VORARBEIT (zwingend, vor jeder Änderung):
- Lies CLAUDE.md und docs/engineering/*.md (Regelwerk 00–06 + Audits) sowie
  docs/PROVISIONING_GOLDEN_IMAGE.md und app/src/main/cpp/v4l2bridge.c (Kopf-Kommentar).
- ADR-Pflicht beachten: die Grundsatzentscheidung wird als ADR dokumentiert.

PROBLEM (am Gerät belegt, 16.07.2026):
Auf einer fabrikneu geflashten ONE bleibt das Kamerabild schwarz. Kopf-Erkennung
(C10/C18) und Licht laufen (beide über Serial /dev/ttyS5), aber der Videoweg nicht.
Ursache gemessen:
- /dev/video0 (UVC MS2109) hat Rechte crw-rw---- media camera.
- Die App läuft als untrusted_app ohne Gruppe camera -> open() scheitert mit
  "Permission denied" (logcat V4L2Bridge: open /dev/video0 failed: Permission denied).
- SELinux ist permissive (avc-Denials mit permissive=1) -> reines DAC-Problem.
- chmod 666 /dev/video0 (per adb root) behebt es sofort, ist aber NICHT persistent:
  /dev/video0 liegt in devtmpfs und wird bei jedem Boot neu vom Treiber erzeugt.
  Nach Reboot ist das Bild wieder weg (verifiziert).
- App-seitig NICHT lösbar: die App hat kein su (Runtime.exec("su") -> Permission denied),
  und android.permission.CAMERA mappt bei Fremd-Apps NICHT auf die Gruppe camera.
- adb root funktioniert -> Gerät ist ein userdebug/eng-Build.

ZIEL: Eine persistente Freigabe des Kamera-Node, die einen Reboot UND das
USB-Wiederanstecken übersteht, ohne manuellen Eingriff pro Boot. Der Fix gehört auf
Image-Ebene (ueventd), nicht in die App.

AUFGABEN (strikt in dieser Reihenfolge):
1. Gerät bestimmen: `adb devices -l`. Genau ein Gerät -> nutze es. Mehrere -> auflisten
   und STOPP (frag mich). Alle adb-Aufrufe mit -s <serial>.
2. Diagnose dokumentieren: `adb shell "cat /vendor/ueventd*.rc /ueventd*.rc 2>/dev/null | grep -i video"`
   und `adb shell ls -l /dev/video*`. Ausgabe festhalten.
3. Persistente ueventd-Regel setzen:
   - adb root; adb disable-verity; adb reboot; warten; adb root; adb remount
   - In /vendor/ueventd.rc die Zeile ergänzen:  /dev/video0   0666   root   root
     (Prüfe per ls -l /dev/video*, ob das UVC-Gerät mehrere Nodes erzeugt; wenn ja,
     nimm /dev/video*   0666   root   root. Begründe die Wahl.)
   - Falls /vendor nicht schreibbar bleibt: NICHT tricksen. Dokumentiere, dass der
     Fix nur über Reflash der vendor/super-Partition ins Golden-Image geht, und
     liefere die exakte einzutragende Regel.
4. Verifizieren (messen, nicht annehmen): adb reboot; nach Reboot
   `adb shell ls -l /dev/video0` -> muss crw-rw-rw- zeigen. Dann App starten
   (adb shell am start -n com.uip.drainq.one/com.uip.oneapp.MainActivity), Videoscreen
   öffnen, `adb logcat -s V4L2Bridge -d` prüfen: kein "Permission denied", Stream läuft.
   Beleg (ls-Ausgabe + logcat-Auszug) in den Ergebnisbericht.
5. ADR anlegen (docs/adr/0003-device-node-permissions.md): Entscheidung
   "Kamera-Node-Freigabe per ueventd im Image, nicht per App/Manuell", mit Kontext,
   Alternativen (App-su verworfen, CAMERA-gid verworfen, manueller chmod verworfen),
   Konsequenzen.
6. docs/PROVISIONING_GOLDEN_IMAGE.md korrigieren:
   - In Abschnitt B/C ergänzen, dass das Golden-Image die vendor/super-Partition MIT
     der ueventd-Regel enthalten muss (userdata allein reicht NICHT).
   - In Abschnitt A einen Schritt "Kamera-Node-Freigabe (ueventd-Regel setzen/prüfen)"
     aufnehmen.
   - Randnotiz aufnehmen: adb root ist auf der Produktionshardware möglich
     (userdebug-Build) -> als offener Sicherheits-/CRA-Punkt markieren, nicht hier fixen.

CONSTRAINTS:
- Ein Commit je logischer Änderung (ADR getrennt von Doku). Kein add -A.
- KEIN Push, KEIN Merge ohne meine Freigabe.
- Kein su, keine Reflection-Hacks, kein SELinux-permanent-ausschalten.
- Schreib einen kurzen Ergebnisbericht RESULT_VIDEO_PERMISSION_PERSIST.md (Repo-Root):
  was gesetzt wurde, Belege vor/nach Reboot, offene Punkte (vendor-Anfrage + adb-root).

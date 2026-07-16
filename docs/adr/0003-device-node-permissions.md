# ADR 0003 — Kamera-Node-Freigabe (`/dev/video*`) per ueventd im Image, nicht per App/Manuell

**Status:** Accepted (2026-07-16)
**Datum:** 2026-07-16
**Entscheider:** Thomas Viell (CEO)
**Bezug:** `RESULT_VIDEO_PERMISSION_PERSIST.md`, `docs/PROVISIONING_GOLDEN_IMAGE.md`,
`docs/SOFTAP_WERKS_PRIVILEG.md` (gleiche Kategorie: Werks-Image-Privileg), Memory
`project-one-bominwell-conflict` (Erst-Diagnose 2026-07-03)
**Betroffene Ebene:** Geräte-Image (`/vendor/etc/ueventd.rc`) — **NICHT** App-Code.
App-Code nur lesend referenziert: `app/src/main/cpp/v4l2bridge.c`
(`nativeOpen("/dev/video0")`), `network/internal/V4L2Camera`.

---

## Kontext

Auf einer **fabrikneu geflashten ONE** (RK3588, Android 12) bleibt das Kamerabild schwarz,
obwohl Kopf-Erkennung (C10/C18) und Licht laufen. Diese laufen über Serial (`/dev/ttyS5`),
der Videoweg nicht. Ursache am Gerät gemessen (2026-07-16, Device `cc1615f07da5e76f`,
`rk3588_s`, `ro.build.type=userdebug`, Android 12):

- `ls -l /dev/video*` → `crw-rw---- media camera` (Mode **0660**). Der UVC-Wandler
  **MACROSILICON MS2109** legt **zwei** Nodes an: `/dev/video0` (Capture) und `/dev/video1`
  (Metadaten).
- Die Rechte kommen aus `/vendor/etc/ueventd.rc`:
  - Z. 21–24: `/dev/video0`…`/dev/video3  0660  media  camera`
  - Z. 186 (Wildcard): `/dev/video*  0660  media  camera`
- Die DrainQ-App läuft als `untrusted_app` **ohne** die Linux-Gruppe `camera` →
  `open("/dev/video0")` scheitert mit `EACCES` → Logcat `V4L2Bridge: open /dev/video0
  failed: Permission denied`.
- **SELinux ist permissive** (avc-Denials mit `permissive=1`) → es ist ein **reines
  DAC-Problem** (Datei-/Gruppenrechte), kein MAC-Problem.
- `chmod 666 /dev/video0` (per `adb root`) behebt es **sofort**, ist aber **nicht
  persistent**: Der Node liegt in `devtmpfs` und wird bei **jedem Boot** und bei **jedem
  USB-Wiederanstecken** vom `uvcvideo`-Treiber neu erzeugt — mit den Default-Rechten der
  ueventd-Regel. Nach Reboot ist das Bild wieder schwarz (verifiziert).
- **App-seitig nicht lösbar:** Die App hat kein `su` (`Runtime.exec("su")` → Permission
  denied), und `android.permission.CAMERA` mappt bei Fremd-Apps **nicht** auf die Gruppe
  `camera` (die gid erhalten nur `cameraserver`/`mediaserver`).
- `adb root` funktioniert → das Board ist ein `userdebug`/`eng`-Build.

Ziel: eine **persistente Freigabe** des Kamera-Nodes, die einen **Reboot UND das
USB-Wiederanstecken** ohne manuellen Eingriff pro Boot übersteht. Der Fix gehört auf
**Image-Ebene (ueventd)**, nicht in die App.

---

## Entscheidung

Die Kamera-Node-Freigabe erfolgt als **ueventd-Regel im Geräte-Image** (vendor-Partition),
angehängt als **letzte** Regel in `/vendor/etc/ueventd.rc`:

```
/dev/video*   0666   root   root
```

Begründung der einzelnen Festlegungen:

1. **ueventd (nicht chmod/Boot-Skript).** `ueventd` ist der Init-Daemon, der Device-Nodes
   anlegt und ihre Rechte setzt — bei **jedem** `add`-uevent, also bei Boot **und** bei
   USB-Re-Plug. Das ist die **einzige** Stelle, die beide Fälle deterministisch und ohne
   dauerhaft laufenden Wächter abdeckt. Ein einmaliger `chmod` überlebt weder Reboot
   (devtmpfs neu) noch Re-Plug (Node neu).

2. **Wildcard `/dev/video*` statt nur `/dev/video0`.** (a) Die MS2109 legt zwei Nodes an
   (`video0` Capture + `video1` Metadaten); (b) die Enumerationsreihenfolge kann über
   USB-Re-Plug/Reboot **wechseln** → der Capture-Node ist nicht garantiert `video0`. Die
   Wildcard ist reihenfolge-unabhängig und deckt alle Video-Nodes ab. Sie passt zudem zum
   bereits vorhandenen Stil der Datei (dort existiert schon eine `/dev/video*`-Regel).

3. **Als letzte Regel angehängt.** ueventd wertet bei mehreren Treffern die **zuletzt
   geladene** Regel aus. Am Dateiende überschreibt die neue Regel alle bestehenden
   `media:camera 0660`-Regeln (Z. 21–24 und 186) für jeden Video-Node.

4. **Mode `0666`, Owner `root:root`.** `0666` (world-rw) löst das DAC-Problem, ohne die App
   der `camera`-gid zuordnen zu müssen (für Fremd-Apps ohnehin nicht möglich). Bei `0666`
   gewähren die World-Bits den Zugriff; die Gruppen-/Owner-Zuordnung ist für den Zugriff
   irrelevant, daher `root:root`.

### Am Gerät verifiziert (2026-07-16)

Nach `adb disable-verity` → Reboot → `adb remount` (overlayfs) → Regel angehängt → Reboot:

```
crw-rw-rw- 1 root root 81, 0 /dev/video0      (vorher: crw-rw---- media camera)
crw-rw-rw- 1 root root 81, 1 /dev/video1
V4L2Bridge: Opened /dev/video0 -> fd=101
V4L2Bridge: VIDIOC_S_FMT MJPEG 1280x720 OK (driver accepted 1280x720)
V4L2Bridge: Stream started with 4 buffers
```

Kein `Permission denied`; Live-Bild auf dem Inspektions-Screen. Details in
`RESULT_VIDEO_PERMISSION_PERSIST.md`.

---

## Konsequenzen

### Positiv
- Videoweg funktioniert nach Kaltstart und nach USB-Re-Plug **ohne** manuellen Eingriff.
- Kein `su`, kein SUID-Helfer, keine Reflection, kein permanentes Abschalten von SELinux —
  minimaler, deklarativer Ein-Zeilen-Eingriff auf Image-Ebene.
- Reihenfolge-robust (Wildcard) gegen wechselnde UVC-Enumeration.

### Negativ / Risiken
- **`0666` ist world-rw:** jede App auf dem Gerät könnte den Node öffnen. Auf einer
  Appliance mit kontrolliertem App-Satz (Kiosk, ein Fach-App) vertretbar; für einen
  Multi-App-/Consumer-Kontext wäre `0660` mit einer dedizierten gid + gruppierter App
  sauberer (hier nicht möglich, da Fremd-App). Bewertung: akzeptabel für die
  Feldgerät-Topologie.
- **Persistenz nur bei Einbacken ins Image:** Der per `adb remount` gesetzte Fix lebt in
  einer **overlayfs**-Schicht (`upperdir=/mnt/scratch/overlay/vendor/upper`). Diese
  überlebt Reboot, aber **NICHT** einen Re-Flash der vendor/super-Partition. Ohne Einbacken
  ins Golden-Image ist nach jedem Flotten-Flash das Bild wieder schwarz (belegt: der
  Erst-Fix vom 2026-07-03 wurde durch den Werks-Flash dieses Geräts entfernt).

### KRITIS / Sicherheit
- Rein lokale Rechte-Änderung an einem Device-Node; kein neuer Netz-/Datenfluss, keine neue
  App-Permission.
- **Offener CRA-/Härtungspunkt (nicht hier gelöst):** `adb root` ist auf der
  Produktionshardware möglich (`userdebug`-Build) — ein Angreifer mit USB-Zugang erlangt
  root. Siehe „Offene Punkte".

---

## Alternativen (verworfen)

- **App führt `chmod`/`su` aus:** verworfen — Fremd-App hat kein `su`; ein SUID-Helfer oder
  Root-Daemon widerspricht der Härtung und wäre ohne dauerhaften Wächter nicht
  re-plug-persistent.
- **App an `camera`-gid via `android.permission.CAMERA`:** verworfen — die CAMERA-Permission
  mappt bei Fremd-Apps **nicht** auf die Linux-Gruppe `camera`; greift also nicht auf
  `/dev/video0`.
- **Manueller `chmod 666` pro Boot (adb/Dienst):** verworfen — nicht feldtauglich (ein
  Eingriff je Boot **und** je USB-Re-Plug), fehleranfällig, kein Golden-Image-tauglicher
  Zustand.
- **SELinux dauerhaft ausschalten / eigene sepolicy:** nicht nötig (SELinux ist bereits
  permissive; das Problem ist rein DAC). Permanentes Deaktivieren ist zudem Constraint-widrig.
- **Kamera über die externe Camera-HAL** (wie die OEM-App `minipush`) statt direktem
  V4L2-`open`: architektonisch anderer Weg, nicht Gegenstand dieser ADR. DrainQ.ONE nutzt
  bewusst den direkten V4L2-Pfad (`v4l2bridge.c`, MJPEG-mmap).

---

## Offene Punkte

- **Golden-Image (Pflicht vor Flottenauslieferung):** Die Regel muss in die
  **vendor/super-Partition** des Golden-Images eingebacken werden.
  `docs/PROVISIONING_GOLDEN_IMAGE.md` (Abschnitt A/B/C) ist entsprechend ergänzt. Bis dahin
  ist der Fix je Gerät per `adb remount` zu setzen — **nicht** flottentauglich.
- **`adb root` auf Produktionshardware (Sicherheit/CRA):** `userdebug`-Build → root im Feld
  möglich. Kandidat für eine eigene ADR (Produktions-`user`-Build statt `userdebug`, oder
  adb im Feld sperren). Hier nur markiert, nicht gelöst.
- **Wartungsprozess:** Als **CHG-05** (Typ „Wartung/Zielumgebung", Impact-Matrix-Zeile
  „Paketierung, Bereitstellungsweg, Zielumgebung → 05-deployment") in
  `docs/engineering/06-maintenance_one.md` zu registrieren; CEO-Bestätigung der Einordnung
  ausstehend (bewusst nicht eigenmächtig eingetragen, da §2/§4 die menschliche
  Klassifikationsbestätigung verlangen).

# RESULT — Persistente Kamera-Node-Freigabe (`/dev/video*`)

**Datum:** 2026-07-16
**Gerät:** `cc1615f07da5e76f` — `rk3588_s`, Android 12, `ro.build.type=userdebug`
**Branch:** `feature/dual-mode` (nicht gepusht, nicht gemergt — Freigabe steht aus)
**Bezug:** ADR 0003 (`docs/adr/0003-device-node-permissions.md`),
`docs/PROVISIONING_GOLDEN_IMAGE.md` (Abschnitt 0/A/B/C), App-Version am Gerät 0.5.16

---

## Problem (am Gerät belegt)

Fabrikneu geflashte ONE → **Kamerabild schwarz**. Kopf-Erkennung (C10/C18) und Licht laufen
(über Serial `/dev/ttyS5`), der Videoweg nicht. Ursache: reines **DAC-Problem** (SELinux ist
permissive). `/dev/video0` kommt als `0660 media:camera` hoch; die App läuft als
`untrusted_app` ohne `camera`-gid → `open()` scheitert mit `Permission denied`. Ein `chmod 666`
behebt es, überlebt aber weder Reboot noch USB-Re-Plug (devtmpfs-Node wird neu erzeugt).

## Was gesetzt wurde

Eine **ueventd-Regel** als **letzte Zeile** in **`/vendor/etc/ueventd.rc`** (nicht
`/vendor/ueventd.rc` — die Datei liegt unter `/etc/`):

```
/dev/video*   0666   root   root
```

- **`/dev/video*` (Wildcard)** statt nur `video0`: Die MS2109 legt **zwei** Nodes an
  (`/dev/video0` Capture + `/dev/video1` Metadaten); die Enumerationsreihenfolge kann über
  Re-Plug/Reboot wechseln → reihenfolge-unabhängige Wildcard. Als letzte Regel überschreibt
  sie die bestehenden `media:camera 0660`-Regeln (Z. 21–24 und 186). Begründung: ADR 0003.
- Gesetzt per `adb root; adb disable-verity; adb reboot; adb root; adb remount` →
  overlayfs-`rw` auf `/vendor` (`disable-verity` meldete **„using overlayfs"**), dann Regel
  angehängt (mit erklärendem Kommentarblock, Z. 201–211 der Datei).
- **Backup** der Original-Datei: `tools/_oem/ueventd.rc.orig` (lokal, nicht committet) +
  `/data/local/tmp/ueventd.rc.orig` am Gerät.

## Beleg — vorher

```
# ls -l /dev/video*
crw-rw---- 1 media camera 81, 0 /dev/video0
crw-rw---- 1 media camera 81, 1 /dev/video1

# grep video /vendor/etc/ueventd.rc
21:/dev/video0   0660  media  camera
22:/dev/video1   0660  media  camera
23:/dev/video2   0660  media  camera
24:/dev/video3   0660  media  camera
186:/dev/video*  0660  media  camera
# (App zuvor: "V4L2Bridge: open /dev/video0 failed: Permission denied")
```

## Beleg — nach Reboot (persistent, kein manueller Eingriff)

```
# adb reboot ; ls -l /dev/video*
crw-rw-rw- 1 root root 81, 0 /dev/video0
crw-rw-rw- 1 root root 81, 1 /dev/video1

# tail -1 /vendor/etc/ueventd.rc           (Regel nach Reboot noch da, Z. 211)
/dev/video*   0666   root   root

# /vendor overlay nach Reboot automatisch remounted (Fix persistiert)
overlay /vendor overlay ... upperdir=/mnt/scratch/overlay/vendor/upper ...
```

App gestartet, Inspektions-Screen geöffnet → **frischer, erfolgreicher Open**:

```
V4L2Bridge: Opened /dev/video0 -> fd=101
V4L2Bridge: VIDIOC_S_FMT MJPEG 1280x720 OK (driver accepted 1280x720)
V4L2Bridge: Stream started with 4 buffers
```

Kein `Permission denied` (device-weiter Logcat-Grep leer). **Live-Bild** auf dem
Inspektions-Screen bestätigt (Screenshot `tools/_oem/_verify_inspection.png` — Kamerawagen
auf Holzboden, OSD „0.00 m").

## USB-Re-Plug

Nicht physisch getrennt (die App hielt den fd; Treiber-Unbind hätte das Gerät gefährdet).
Abgedeckt ist der Fall durch dieselbe Mechanik: `ueventd` wendet die Regel bei **jedem**
`add`-uevent an — exakt der Pfad, den der Reboot-Test ausgeübt hat (`uvcvideo` legt den Node
neu an → ueventd setzt `0666`). Der Reboot-Beweis deckt den Re-Plug transitiv ab.

## Persistenz-Grenze (wichtig für die Auslieferung)

Der Fix lebt aktuell in einer **overlayfs**-Schicht (`/mnt/scratch/overlay/vendor/upper`).
Diese überlebt **Reboot**, aber **NICHT** einen **Re-Flash** der `vendor`/`super`-Partition
(belegt durch die Historie: der Erst-Fix vom 2026-07-03 auf einem anderen Gerät wurde durch
den Werks-Flash dieses Geräts entfernt). **Für die Flotte muss die Regel ins Golden-Image der
`vendor`/`super`-Partition eingebacken werden** — `userdata` allein reicht nicht.
`docs/PROVISIONING_GOLDEN_IMAGE.md` ist entsprechend korrigiert (Abschnitt 0/A.6.1/B/C).

## Offene Punkte

1. **Vendor-Anfrage (Golden-Image):** Die Zeile `/dev/video*   0666   root   root` ist vom
   Board-Vendor (NSP3CT.PRO) **nativ in die `vendor`-Partition der Basis-Firmware** aufzunehmen
   (oder das `vendor`/`super`-Image offline patchen), damit sie im gesicherten `super.img`
   enthalten ist und den Re-Flash übersteht. Gleiche Kategorie wie
   `docs/SOFTAP_WERKS_PRIVILEG.md`.
2. **`adb root` auf Produktionshardware (Sicherheit/CRA):** Das Board ist `userdebug` → root
   im Feld per USB möglich. Offener Härtungspunkt, hier bewusst **nicht** gelöst (Kandidat für
   eigene ADR: Produktions-`user`-Build oder adb im Feld sperren). In ADR 0003 unter „Offene
   Punkte" vermerkt.
3. **Wartungsprozess (CHG-05):** Diese Änderung ist nach `06-maintenance_one.md` §2/§4 als
   **CHG-05** (Typ „Wartung/Zielumgebung", Impact-Matrix-Zeile „Paketierung, Bereitstellungsweg,
   Zielumgebung → 05-deployment") zu registrieren. **Nicht eigenmächtig eingetragen**, weil das
   Regelwerk die menschliche Klassifikationsbestätigung vor der Umsetzung verlangt — CEO-Freigabe
   der Einordnung ausstehend.

## Commits (auf `feature/dual-mode`, nicht gepusht)

- `docs: ADR 0003 — Kamera-Node-Freigabe per ueventd im Image`
- `docs(provisioning): Golden-Image muss vendor/super mit ueventd-Kamera-Regel enthalten`
- `docs: RESULT-Bericht persistente Kamera-Node-Freigabe`

Kein Push, kein Merge ohne Freigabe.

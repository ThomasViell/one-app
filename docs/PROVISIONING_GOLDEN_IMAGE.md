# DrainQ.ONE — Golden-Master-Image (Provisionierung der ONE-Flotte)

**Ziel:** Ein einziges, fertig konfiguriertes „Golden"-Gerät erstellen, dieses als Image sichern
und 1:1 auf alle ONE-Geräte flashen. Jedes Gerät ist danach identisch eingerichtet
(App, alle europäischen Tastatur-Sprachen, Kiosk, Autostart, Netzwerk) — ohne Handarbeit pro Gerät.

Hardware: RK3588 (Android). Geflasht wird per USB mit dem **Rockchip RKDevTool** im Maskrom-/Loader-Modus.

---

## 0. Wichtigster Punkt zuerst (sonst geht die Konfiguration verloren)

Die Einrichtung (installierte App, aktivierte Tastatur-Sprachen, Kiosk, Einstellungen) liegt in der
**`userdata`-Partition** + den Android-Settings. Ein Standard-Firmware-Image legt `userdata` **leer** an.

→ Das Golden-Image MUSS die `userdata`-Partition **mit-sichern und mit-flashen**, sonst sind die
Geräte zwar „sauber", aber wieder un-konfiguriert.

Ebenso liegt die **Kamera-Node-Freigabe** in der **`vendor`/`super`-Partition**
(`/vendor/etc/ueventd.rc`, Regel `/dev/video*   0666   root   root` — siehe A.6.1 und
**ADR 0003**). Ein per `adb remount` gesetzter Fix lebt nur in einer overlayfs-Schicht und
wird von jedem Re-Flash entfernt.

→ Das Golden-Image MUSS **auch die `vendor`/`super`-Partition mit dieser ueventd-Regel**
mit-sichern und mit-flashen, sonst bleibt nach dem Flotten-Flash das **Kamerabild schwarz**
(`V4L2Bridge: open /dev/video0 failed: Permission denied`) — `userdata` allein reicht **nicht**.

**Vor dem ersten Golden-Image mit dem Hardware-Partner (NSP3CT.PRO / Board-Vendor) klären:**
1. Liefert der Vendor die Basis-Firmware + `parameter.txt` (Partitionslayout) + RKDevTool-Setup?
2. Ist die `userdata`-Partition **unverschlüsselt**? Bei aktiver Datenverschlüsselung (FBE) lässt sich
   ein geklontes `userdata` auf einem ANDEREN Gerät nicht entschlüsseln → Klon scheitert. Für
   Appliance-Images ist die Verschlüsselung üblicherweise aus; bitte bestätigen lassen.
3. Gibt es gerätespezifische IDs (Seriennummer, MAC, HWID), die NICHT geklont werden dürfen?
   Diese müssen außerhalb von `userdata` liegen oder beim ersten Boot neu erzeugt werden.

---

## A. Golden-Gerät einrichten (einmalig, von Hand)

1. **Basis-Firmware** des Vendors flashen (sauberer Ausgangszustand).
2. **DrainQ.ONE installieren** (finale Release-APK, nicht Debug).
3. **Alle europäischen Tastatur-Sprachen aktivieren** (einmalig hier, danach im Klon enthalten):
   Einstellungen → System → Sprachen & Eingabe → Bildschirmtastatur → Sprachen →
   „Systemsprache verwenden" AUS → alle gewünschten EU-Sprachen hinzufügen
   (de-DE, en-GB, fr-FR, es-ES, it-IT, nl-NL, pl-PL, pt-PT, nb-NO/no, sv-SE, da-DK, fi-FI,
   cs-CZ, sk-SK, hu-HU, ro-RO, bg-BG, el-GR, hr-HR, sl-SI, et-EE, lv-LV, lt-LT, ga-IE, mt-MT …).
   Die Layouts (QWERTZ, AZERTY …) sind in der AOSP-Tastatur bereits enthalten — sie werden hier nur
   eingeschaltet. Die DrainQ-App wählt zur Laufzeit automatisch die passende (per Sprach-Hinweis).
4. **Kiosk + Autostart** setzen (Feldgerät-Härtung) — siehe Detailschritte A.4.1:
   - Device-Owner setzen (ADB). Der Kiosk ist seit der Kette kiosk-pflicht (03.09.2026)
     im DIRECT-Modus **Pflicht** — es gibt keinen Schalter mehr; die App startet direkt gesperrt.
   - DrainQ.ONE als HOME-Launcher (Default) wählen.

### A.4.1 Device-Owner + LockTask + HOME (konkret)

Die App bringt seit BETA-Welle 1 alles Nötige mit (DeviceAdminReceiver, HOME-Intent-Filter,
LockTask-Logik). Es bleibt **ein einmaliger ADB-Schritt** pro Golden-Gerät:

```bash
# Voraussetzung: frisch eingerichtetes Gerät, KEINE weiteren Accounts angelegt
# (sonst lehnt Android set-device-owner ab). DrainQ.ONE muss installiert sein.
adb shell dpm set-device-owner com.uip.drainq.one/.bootstrap.OneDeviceAdminReceiver
# Erwartete Ausgabe: "Success: Device owner set to package com.uip.drainq.one"
```

> **Entfallen (Kette kiosk-pflicht, 03.09.2026):** Der frühere Grant
> `pm grant com.uip.drainq.one android.permission.WRITE_SECURE_SETTINGS` ist nicht mehr nötig —
> die Messung vom 03.09.2026 hat belegt, dass `navigation_mode=0` die launcher3-Taskbar nicht
> beseitigt (`docs/evidence/2026-09-03_taskbar_navigationmode_messung.md`). Die App nutzt die
> Permission nicht mehr (Manifest ohne Eintrag); die Taskbar-Behandlung läuft über die
> Legacy-Immersive-Flags + Stash-Impuls (TaskbarRestash).

Danach:
1. DrainQ.ONE öffnen. Der Kiosk ist **automatisch aktiv** (Kette kiosk-pflicht, 03.09.2026:
   Pflicht im DIRECT-Modus, kein Schalter). Als Device-Owner ruft die App
   `setLockTaskPackages(...) + startLockTask()` → Home/Recents/Wischen sind gesperrt.
   Verlassen einmalig über Einstellungen → **„App verlassen"** (Bestätigung in der Karte) —
   beim nächsten Start ist der Kiosk wieder aktiv.
2. **HOME-Launcher:** Einstellungen → Apps → Standard-Apps → Start-App → **DrainQ.ONE** wählen
   (oder beim ersten HOME-Druck DrainQ.ONE + „Immer"). Damit bootet das Gerät direkt in die App.
3. Beides landet in `userdata` und wird mit dem Golden-Image geklont (Abschnitt B/C).

**Ohne Device-Owner** (z. B. Dev-Gerät): Der Kiosk besteht aus Vollbild + Balken-Behandlung +
HOME-Rolle — **kein** LockTask und kein Screen-Pinning mehr (Kette kiosk-pflicht, E4: der
System-Anpinn-Dialog war am 03.09.2026 als Vollbild-Falle gemessen worden und ist entfernt).
Device-Owner kann nur auf einem Gerät OHNE Benutzerkonten gesetzt werden; ggf. vorher Werksreset.
Entfernen (für Service): `adb shell dpm remove-active-admin com.uip.drainq.one/.bootstrap.OneDeviceAdminReceiver`.

> **Wichtiger Nachtrag (29.07.2026, belegt auf `233b4bd2865177ed`):** Device-Owner-Status UND
> die HOME-Standard-App-Zuordnung sind an das **installierte Paket** gebunden, nicht an die App
> als solche. Beide gehen bei jeder Deinstallation/Neuinstallation verloren — auch wenn danach
> exakt dieselbe App wieder installiert wird. Konkret beobachtet: die Plattformsignatur-
> Umstellung (ADR-0005) erzwingt laut deren Abschnitt 4 „einmalig Deinstallation und
> Neuinstallation" — belegt durch `adb shell dumpsys package com.uip.drainq.one`, Feld
> `firstInstallTime`, das exakt den Zeitpunkt der Signaturumstellung zeigte (nicht nur
> `lastUpdateTime`, was ein reines Update anzeigen würde). Nach diesem Reinstall stand die
> HOME-Präferenz auf dem System-Launcher (`dumpsys package preferred-xml` zeigte
> `com.android.launcher3/.uioverrides.QuickstepLauncher` statt DrainQ.ONE) und
> `dumpsys device_policy` zeigte keine aktiven Device Admins mehr.
>
> **Folge für die Praxis:** Nach JEDEM Reinstall mit geänderter Signatur/`sharedUserId`
> (Plattformsignatur-Wechsel, Keystore-Wechsel, o. ä.) — nicht nur bei der Erstinbetriebnahme —
> müssen HOME-Zuordnung und ggf. Device-Owner **erneut** gesetzt werden, sonst bootet das Gerät
> in den System-Launcher statt in die App. Schneller ADB-Weg statt der UI-Klickstrecke oben:
> ```bash
> adb shell cmd package set-home-activity com.uip.drainq.one/com.uip.oneapp.MainActivity
> ```
> (Getestet auf Android 12 / SDK 32 — `cmd package set-home-activity` existiert erst ab einer
> bestimmten Android-Version; auf älteren Geräten den UI-Weg oben nutzen.) Device-Owner danach
> ggf. erneut per `dpm set-device-owner` setzen (Voraussetzung: keine Benutzerkonten).
>
> **Für die Flotte:** Diese Reihenfolge — App installieren → HOME setzen → Device-Owner setzen
> → Golden-Image ziehen (Abschnitt B) — muss auf dem Golden-Gerät **nach jeder** Signatur-
> änderung wiederholt werden, bevor ein neues Master-Image gesichert wird. Ein Master-Image,
> das vor der Signaturumstellung gezogen wurde, enthält die alte HOME-/Device-Owner-Bindung
> und muss neu gezogen werden.
5. **Netzwerk/Default-Einstellungen** wie gewünscht (WLAN ONE_xx, IP-Bereich 172.169.10.x usw.).
6. **Kamera-Node-Freigabe (ueventd-Regel setzen/prüfen)** — **Pflicht**, sonst bleibt das
   Kamerabild schwarz. Konkret siehe **A.6.1**; Grundsatzentscheidung in **ADR 0003**.
7. **Funktionstest** auf dem Golden-Gerät: Inspektion (**Live-Bild erscheint**), Aufnahme,
   Tastatur folgt Sprache (QWERTZ bei DE). Kamera-Node zusätzlich per ADB prüfen:
   `adb shell ls -l /dev/video0` → muss `crw-rw-rw-` zeigen (nicht `crw-rw---- media camera`).

### A.6.1 Kamera-Node-Freigabe (ueventd-Regel für `/dev/video*`) — konkret

Der UVC-Wandler (MACROSILICON MS2109) legt `/dev/video0` (Capture) + `/dev/video1`
(Metadaten) mit Default `0660 media:camera` an. Die DrainQ-App läuft als `untrusted_app`
ohne `camera`-gid → `open()` scheitert (`Permission denied`), Bild bleibt schwarz. SELinux
ist permissive → reines DAC-Problem. Fix: eine ueventd-Regel, die jeden Video-Node beim
Anlegen auf `0666` setzt (greift bei Boot **und** USB-Re-Plug). Begründung Wildcard/Owner:
**ADR 0003**.

```bash
# Voraussetzung: userdebug-Build (adb root möglich). Auf dem Golden-Gerät einmalig:
adb root
adb disable-verity      # meldet "using overlayfs" + "Now reboot ..."
adb reboot
# nach dem Boot:
adb root
adb remount             # /vendor wird overlayfs-rw

# Regel als LETZTE Zeile anhängen (überschreibt die media:camera-0660-Regeln oben):
adb shell 'printf "\n/dev/video*   0666   root   root\n" >> /vendor/etc/ueventd.rc'

# Wirksam machen + prüfen:
adb reboot
adb shell ls -l /dev/video0     # erwartet: crw-rw-rw- root root
```

> **WICHTIG:** Dieser Weg (`adb remount`) schreibt in eine **overlayfs-Schicht**
> (`/mnt/scratch/overlay/vendor/upper`). Sie überlebt Reboot, aber **NICHT** einen Re-Flash
> der `vendor`/`super`-Partition. Für die **Flotte** muss die Regel in das **Golden-Image
> der `vendor`/`super`-Partition** eingebacken werden (Abschnitt B/C), nicht nur per
> `adb remount` gesetzt. Die exakt einzutragende Regel ist:
> ```
> /dev/video*   0666   root   root
> ```

> **Randnotiz (offener Sicherheits-/CRA-Punkt, hier NICHT gelöst):** Dass `adb root` auf der
> Produktionshardware funktioniert, bedeutet, dass das ausgelieferte Board ein
> `userdebug`-Build ist → ein Angreifer mit USB-Zugang erlangt root. Das ist ein bekannter,
> **offener** Härtungspunkt (Kandidat: Produktions-`user`-Build statt `userdebug`, oder adb
> im Feld sperren) — siehe ADR 0003 „Offene Punkte".

---

## B. Golden-Image sichern (inkl. userdata)

Gerät in den **Maskrom-/Loader-Modus** bringen (Recovery-/Maskrom-Taste + USB), dann mit RKDevTool
(oder `rkdeveloptool` / `rkDumper` unter Linux) die Partitionen **auslesen** — wichtig: **inklusive
`userdata`** (dort steckt die Konfiguration) **und inklusive `super`/`vendor`** (dort steckt die
Kamera-Node-ueventd-Regel aus A.6.1 / ADR 0003).

> **Kamera-Regel vor dem Sichern verankern:** Die per `adb remount` gesetzte Regel liegt in
> einer overlayfs-Schicht und ist **nicht** Teil des `vendor`-Blockimages, das RKDevTool ausliest.
> Für ein sauberes Golden-Image muss die Zeile `/dev/video*   0666   root   root` **nativ in der
> `vendor`-Partition** stehen (vom Board-Vendor in die Basis-Firmware eingebaut, oder das
> `vendor`/`super`-Image offline gepatcht) — sonst enthält das gesicherte `super.img` die Regel
> nicht und die Flotte bootet wieder mit schwarzem Bild. Nach dem Sichern per
> `adb shell ls -l /dev/video0` (`crw-rw-rw-`) gegenprüfen, dass das gezogene Image die Regel trägt.

Ergebnis: ein Satz Partition-Images bzw. eine `update.img`, die als **Master** abgelegt wird.

Empfehlung: pro DrainQ-Release ein eigenes, benanntes Master-Image, z. B.
`DrainQ.ONE_golden_vX.Y.Z_YYYY-MM-DD.img`. So ist immer klar, welcher Stand auf der Flotte ist.

---

## C. Auf Flotte flashen

Jedes Zielgerät in den Maskrom-Modus, RKDevTool → Master-Image (inkl. `userdata` **und
`super`/`vendor`**) flashen → fertig. Alle Geräte sind danach identisch (App + Sprachen + Kiosk +
Settings **+ Kamera-Node-Freigabe**). Kein Skript, keine Handarbeit pro Gerät.

> **Abnahme je geflashtem Gerät:** `adb shell ls -l /dev/video0` → `crw-rw-rw-` und einmal den
> Inspektions-Screen öffnen (Live-Bild). Zeigt der Node `crw-rw---- media camera`, fehlt die
> ueventd-Regel im geflashten `super`/`vendor` (Abschnitt B nicht sauber verankert).

---

## D. Updates / neuer Stand

Bei neuer App-Version oder geänderter Konfiguration:
1. Golden-Gerät aktualisieren (App neu, ggf. Einstellungen).
2. Neues Master-Image ziehen (Schritt B), neu benennen (Version/Datum).
3. Flotte mit dem neuen Master neu flashen.

(Für kleinere App-Updates ohne Re-Flash kann später ein In-App-Update über drainq-cloud dazukommen —
das ersetzt den Golden-Reflash aber nicht für Sprach-/System-Änderungen.)

---

## Zuständigkeiten

- **Hardware-Partner / Vendor:** Basis-Firmware, `parameter.txt`, RKDevTool-Setup, Bestätigung
  userdata-Verschlüsselung & gerätespezifische IDs, Unterstützung beim Image-Auslesen.
- **DrainQ (wir):** App-Release, Einrichtungs-Checkliste (Abschnitt A), Funktionstest, Versionierung
  der Master-Images.

## Offene Punkte, die hier mitgelöst werden

- Tastatur folgt App-Sprache (#14): App-Seite fertig (Sprach-Hinweis) — fehlende Layouts werden über
  das Golden-Image bereitgestellt.
- Kiosk-Hard-Lock (Device-Owner/LockTask) und Autostart: gehören in Abschnitt A der Provisionierung.

---

## Anhang: Selbst auslesen & klonen — OHNE Vendor (rkdeveloptool)

Geht auch ohne Hersteller-Tooling. Wir lesen das Gerät selbst aus und bauen das Image.

**Was man braucht:** Linux-Rechner (oder Windows mit WSL/RKDevTool-GUI), `rkdeveloptool`
(Open Source: rockchip-linux/rkdeveloptool), USB-Kabel, einen **RK3588-Loader**
`rk3588_spl_loader_*.bin` (idealerweise vom Board; sonst der öffentliche Rockchip-Loader aus dem
`rkbin`-Repo). Gerät per USB anschließen und in den **Maskrom-Modus** bringen.

**Read-back-Test (am Golden-/Testgerät):**
```bash
# 1) Gerät erkannt? (zeigt "Maskrom")
rkdeveloptool ld

# 2) Loader laden -> Gerät wechselt in Loader/Rockusb-Modus
rkdeveloptool db rk3588_spl_loader_xxx.bin

# 3) Erneut prüfen (jetzt "Loader")
rkdeveloptool ld

# 4) Flash-Groesse + Partitionstabelle auslesen
rkdeveloptool rfi          # Gesamtgroesse (Sektoren a 512 Byte)
rkdeveloptool ppt          # Partitionstabelle: Name / Start-Sektor / Laenge
```
Zeigt `ppt` die Tabelle **inkl. `userdata`** → Auslesen funktioniert, wir sind vom Vendor unabhängig.

**Partitionen dumpen** (Start/Laenge aus `ppt` einsetzen):
```bash
# rkdeveloptool rl <StartSektor> <LaengeSektoren> <Datei>
rkdeveloptool rl <start_userdata> <len_userdata> userdata.img
rkdeveloptool rl <start_super>    <len_super>    super.img      # system/vendor/product
# weitere analog: boot, uboot, misc, dtbo, vbmeta, ...
```
`rkDumper` macht das automatisch für ALLE Partitionen laut Tabelle.

**Auf andere Geräte zurückschreiben (Klon):**
```bash
rkdeveloptool db rk3588_spl_loader_xxx.bin
rkdeveloptool wl <start_userdata> userdata.img
rkdeveloptool wl <start_super>    super.img
# ... alle Partitionen, danach: rkdeveloptool rd   (reboot)
```
Komfortabler unter Windows: die gedumpten Partitionen + Tabelle in **RKDevTool (GUI)** laden und
„Upgrade Firmware" — gleiche Wirkung, klickbar.

**Wichtig:**
- `ppt`/`rl`/`wl` funktionieren ERST nach `db` (Loader). Im reinen Maskrom nicht.
- Immer zuerst an EINEM Testgerät verifizieren (Brick-Risiko gering, da Maskrom erholbar — aber real).
- `userdata` nur cross-device klonbar, wenn **unverschlüsselt** (vorher mit `ppt`/Test prüfen).
- Wir klonen das Binär-Image; Quellcode/OS-Umbau ist damit nicht möglich (für den Golden-Klon auch nicht nötig).


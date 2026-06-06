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
   - Device-Owner setzen (ADB), dann in DrainQ.ONE: Kiosk-Schalter AN.
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

Danach:
1. DrainQ.ONE öffnen → Einstellungen → **Kiosk-Schalter AN**. Die App ruft als Device-Owner
   automatisch `setLockTaskPackages(...) + startLockTask()` → Home/Recents/Wischen sind gesperrt.
   (Der Kiosk-Schalter bleibt in den Einstellungen erreichbar, um den Modus wieder zu verlassen.)
2. **HOME-Launcher:** Einstellungen → Apps → Standard-Apps → Start-App → **DrainQ.ONE** wählen
   (oder beim ersten HOME-Druck DrainQ.ONE + „Immer"). Damit bootet das Gerät direkt in die App.
3. Beides landet in `userdata` und wird mit dem Golden-Image geklont (Abschnitt B/C).

**Ohne Device-Owner** (z. B. Dev-Gerät): Der Kiosk-Schalter aktiviert nur normales Screen-Pinning
(manuell per Back+Übersicht verlassbar) und die System-Bars werden ausgeblendet — kein Hard-Lock.
Device-Owner kann nur auf einem Gerät OHNE Benutzerkonten gesetzt werden; ggf. vorher Werksreset.
Entfernen (für Service): `adb shell dpm remove-active-admin com.uip.drainq.one/.bootstrap.OneDeviceAdminReceiver`.
5. **Netzwerk/Default-Einstellungen** wie gewünscht (WLAN ONE_xx, IP-Bereich 172.169.10.x usw.).
6. **Funktionstest** auf dem Golden-Gerät: Inspektion, Aufnahme, Tastatur folgt Sprache (QWERTZ bei DE).

---

## B. Golden-Image sichern (inkl. userdata)

Gerät in den **Maskrom-/Loader-Modus** bringen (Recovery-/Maskrom-Taste + USB), dann mit RKDevTool
(oder `rkdeveloptool` / `rkDumper` unter Linux) die Partitionen **auslesen** — wichtig: **inklusive
`userdata`** (dort steckt die Konfiguration).

Ergebnis: ein Satz Partition-Images bzw. eine `update.img`, die als **Master** abgelegt wird.

Empfehlung: pro DrainQ-Release ein eigenes, benanntes Master-Image, z. B.
`DrainQ.ONE_golden_vX.Y.Z_YYYY-MM-DD.img`. So ist immer klar, welcher Stand auf der Flotte ist.

---

## C. Auf Flotte flashen

Jedes Zielgerät in den Maskrom-Modus, RKDevTool → Master-Image (inkl. `userdata`) flashen → fertig.
Alle Geräte sind danach identisch (App + Sprachen + Kiosk + Settings). Kein Skript, keine Handarbeit
pro Gerät.

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


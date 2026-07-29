# Ergebnis — Plattformsignatur-Testlauf am Gerät (2026-07-29)

**Bezug:** `docs/adr/0005-platform-signing.md`, Auftrag `PLATTFORMSIGNATUR_TESTLAUF_PROMPT.md`
**Gerät:** `233b4bd2865177ed` (per CEO-Bestätigung 29.07. für diesen Lauf freigegeben — die ursprüngliche
Zuordnung „Thomas-Arbeitsgerät, tabu" aus dem Auftragsprompt wurde während dieser Session per
Prompt-Update und expliziter Chat-Bestätigung aufgehoben)
**Build:** 0.6.0 / versionCode 600, `assembleDebug` + Plattformsignatur (Keystore `bominwellalias`,
Env-Vars `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS`), **nicht** `assembleRelease`

---

## Wichtige Einschränkung des Testaufbaus (zuerst, weil sie beide Antworten relativiert)

Beide APKs wurden reibungslos aber **regulär per `adb install`** installiert (`/data/app`), **nicht**
nach `/system/priv-app` verschoben. ADR-0005 Abschnitt 4 und Abschnitt 8 (Umsetzungsschritt 6/7)
sehen als Produktionsweg ausdrücklich die Installation als **priv-app auf der System-Partition** vor.
Dieser Testlauf prüft also nur: *„Reicht die Plattformsignatur bei einer regulären App-Installation?"*
— nicht: *„Reicht die Plattformsignatur bei einer priv-app-Installation?"* Das ist eine andere,
schwächere Testbedingung als die, für die die ADR den eigentlichen Produktionsnutzen verspricht.
Die Kamera-Ergebnisse unten sind daher als **„bei regulärer Installation nicht gelöst"** zu lesen,
nicht als „grundsätzlich durch Plattformsignatur nicht lösbar".

---

## Ergebnistabelle

| Variante | Kamera ohne ueventd-Regel | Hotspot eigene SSID | Belege |
|---|---|---|---|
| **A — ohne `sharedUserId`** | ❌ `Permission denied` auf `/dev/video0` (0660 media:camera) | ⚠️ Hotspot läuft, aber **generischer** Name `AndroidShare_2331` (LOHS-Fallback, kein SecurityException auf `NETWORK_SETTINGS` selbst, aber `SoftAP-Start fehlgeschlagen: needs-privilege … MAINLINE_NETWORK_STACK`) | `logcat_variantA_full.txt`, `screen_A_inspection.png` (schwarzes Bild), `screen_A_hotspot_on.png`, PC-WLAN-Scan bestätigt SSID sichtbar (BSSID `2c:c6:82:f8:d2:c7`) |
| **B — mit `sharedUserId="android.uid.system"`** | ❌ `Permission denied` auf `/dev/video0`, **obwohl Prozess mit `uid=system` läuft** (`ps` bestätigt) | ✅ **`DrainQ-ONE-233b4bd2865177ed`** — echter `ROLE_SOFTAP_TETHERED`, kein SecurityException, `setSoftApConfiguration uid=1000` erfolgreich | `logcat_variantB_full.txt`, `screen_B_inspection.png` (schwarzes Bild), `screen_B_hotspot.png`, PC-WLAN-Scan bestätigt SSID sichtbar (gleiche BSSID) |

*(Alle Rohdaten — APKs, Logcats, Screenshots — liegen außerhalb des Repos im Session-Scratchpad, nicht committet, da reine Testartefakte.)*

### Kamera — Detailbefund

Vor jeder Messung wurde `/dev/video0` nachweislich auf `crw-rw---- media camera` (0660) zurückgesetzt
(Beleg unten). In **beiden** Varianten scheitert `V4L2Bridge` mit derselben Meldung:

```
V4L2Bridge: open /dev/video0 failed: Permission denied
```

Auch mit `sharedUserId=android.uid.system` (Variante B, Prozess läuft nachweislich als `uid=system`)
ändert sich daran nichts. Weder die Plattformsignatur allein noch zusätzlich die System-UID verschaffen
dem Prozess die Gruppenzugehörigkeit `camera`, die für den direkten V4L2-Zugriff nötig ist — das deckt
sich mit dem in ADR-0003 dokumentierten Befund, dass `android.permission.CAMERA` bei Fremd-Apps nicht
auf die Gruppe `camera` mappt. Für den hier getesteten Installationsweg (regulär, nicht priv-app) ist
die ADR-0005-Gate-Hypothese aus Abschnitt 5 damit **klar widerlegt**: Die ueventd-Abhängigkeit
(ADR-0003) entfällt **nicht** durch Plattformsignatur allein.

Offen bleibt (s. Einschränkung oben), ob eine echte priv-app-Installation (`/system/priv-app` +
ggf. `privapp-permissions`-Allowlist) ein anderes Ergebnis liefert — das wurde in diesem Lauf nicht
geprüft, weil Schritt 3 des Auftrags ausdrücklich nur `assembleDebug`-Bauten ohne Image-Eingriff
verlangte.

### Hotspot — Detailbefund

Variante A startet einen Hotspot, aber über den **LOHS-Fallback** (`LocalOnlyHotspot`), weil der
privilegierte SoftAP-Start an der fehlenden `MAINLINE_NETWORK_STACK`-Berechtigung scheitert. Das
Ergebnis ist ein funktionsfähiges, aber **generisch benanntes** Netz (`AndroidShare_2331`) — für den
eigentlichen Zweck (Kunde/Support erkennt die ONE am Namen, das Pairing-QR zeigt den Namen im Klartext)
nicht produktionstauglich.

Variante B startet den **echten, tethered SoftAP** mit dem gewünschten Namen
`DrainQ-ONE-233b4bd2865177ed`, sauber ohne jede SecurityException. Das bestätigt: **in diesem
Installationsweg ist `sharedUserId` die entscheidende Variable für den Hotspot-Namen** — nicht die
Plattformsignatur allein.

### Nicht geprüft / Einschränkungen

- **Priv-app-Installation** (der eigentliche ADR-Produktionsweg) — nicht getestet, s. o.
- **Zweites unabhängiges Gerät** zur SSID-Sichtprüfung: Die harte Regel erlaubt in dieser Session
  ausschließlich `233b4bd2865177ed`. Ersatzweise wurde die SSID-Sichtbarkeit per WLAN-Scan von diesem
  Entwicklungsrechner aus verifiziert (BSSID-Abgleich mit dem Logcat-Eintrag, exakte Übereinstimmung).
  Das ist eine passive Sichtprüfung, kein tatsächlicher Verbindungsversuch eines zweiten Android-Geräts.
- **Ursache des Kamera-Ausgangszustands (0666 statt 0660) vor der manuellen Korrektur:** Das bekannte,
  von uns selbst gepflegte `/vendor/etc/init/init.drainq.rc` wurde für diesen Test deaktiviert
  (umbenannt in `init.drainq.rc.disabled`, Backup gesichert) und `ueventd.rc` trägt nachweislich nur
  0660-Regeln — trotzdem kam `/dev/video0` nach Reboot weiterhin als 0666 hoch. Die Quelle dieses
  zusätzlichen, einmaligen Boot-Zeit-Chmods konnte in der verfügbaren Zeit **nicht** identifiziert
  (kein Treffer in allen `*.rc`-Dateien unter `/vendor/etc/init/` inkl. `hw/`-Unterordner, keine
  SELinux-Erzwingung — `permissive=1`). Für die Messung selbst wurde der Zustand per einmaligem
  `adb root && chmod 0660` sauber hergestellt und über 25s als stabil (kein aktiver Watcher)
  verifiziert — die Kamera-Ergebnisse oben sind darauf aufbauend valide. Der eigentliche
  Boot-Zeit-Mechanismus bleibt aber ein offener Punkt, falls dieses Gerät künftig wieder für den
  Normalbetrieb (mit funktionierender Kamera) gebraucht wird.
- **`init.drainq.rc` wurde NICHT wiederhergestellt** (bewusst, um den Testzustand nicht zu vermischen).
  Backup liegt außerhalb des Repos im Session-Scratchpad dieser Konversation
  (`platform_signing_testlauf/backup/init.drainq.rc.backup`), nicht committet. Wiederherstellung:
  `adb -s 233b4bd2865177ed root && adb -s 233b4bd2865177ed remount && adb -s 233b4bd2865177ed shell mv /vendor/etc/init/init.drainq.rc.disabled /vendor/etc/init/init.drainq.rc && adb -s 233b4bd2865177ed reboot`

---

## Empfehlung

**Vor einer Entscheidung zwischen Variante A und B fehlt noch der eigentliche Produktionstest:
Installation als priv-app auf `/system/priv-app`.** Ohne den ist die Kamera-Frage nicht abschließend
beantwortet — beide hier getesteten Varianten scheitern gleichermaßen, aber unter einer schwächeren
Bedingung als der, die die ADR eigentlich vorschlägt.

Für die **Hotspot-Frage allein** ist das Bild klar: Ohne `sharedUserId` bekommt man nur einen
funktionsfähigen, aber falsch benannten Hotspot — das verfehlt den in ADR-0005 §1/§6 genannten
eigentlichen Zweck der Übung (Kunde/Support erkennt die ONE namentlich). Sollte sich beim
priv-app-Test herausstellen, dass auch dort `sharedUserId` nötig ist, um den Hotspot-Namen zu
bekommen (unabhängig vom Kamera-Ergebnis), wäre das ein Gegenargument zur in ADR-0005 §4
geäußerten Präferenz „ohne `sharedUserId`" — dort ging es primär um die Kamera-Abwägung, nicht um
den Hotspot-Namen.

**Konkreter nächster Schritt:** Beide Varianten zusätzlich als priv-app-Bau testen (Kopie ins
`/system/priv-app/` der Testpartition, `adb remount` + Reboot statt `adb install`), bevor Abschnitt 4
der ADR final entschieden wird. Das ist außerhalb des Umfangs dieses Auftrags („nur bis zum
Messergebnis, keine Flottenumstellung") und braucht einen eigenen Folgeauftrag.

Keine Schönfärberei: Der Hauptbefund dieses Laufs ist ein **Nicht-Erfolg** für die Kamera-Hypothese
der ADR (Abschnitt 5) unter der getesteten Bedingung, und ein **differenzierter Teilerfolg** für den
Hotspot (läuft in beiden Varianten, aber nur mit `sharedUserId` auch mit dem richtigen Namen).

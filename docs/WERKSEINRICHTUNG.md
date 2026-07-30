# DrainQ.ONE — Werkseinrichtung (Kurzanleitung)

Werkzeug: `tools/werkseinrichtung/` (als Ordner mit App, adb, Skript versendbar — läuft auf
jedem Windows-Rechner, auch ohne vorinstallierte Software).

**Vor dem Versenden:** Die App-Datei (`app/DrainQ-ONE_<Version>_<Code>_platform.apk`) liegt
NICHT im Git-Repo (`*.apk` ist global ausgeschlossen, wie alle anderen APKs in diesem Repo —
bewusste Konvention, Binärdateien wachsen sonst unbegrenzt in der Historie). Vor jedem Versand
frisch bauen:
```powershell
$env:ONE_PLATFORM_KEYSTORE = "<Pfad zum bominwellalias.keystore>"
$env:ONE_PLATFORM_PASS = "<Passwort>"
$env:APP_VERSION_CODE = "<z.B. 603>"
$env:APP_VERSION_NAME = "<z.B. 0.6.3>"
.\gradlew.bat :app:assembleDebug
copy app\build\outputs\apk\debug\app-debug.apk `
     "tools\werkseinrichtung\app\DrainQ-ONE_$($env:APP_VERSION_NAME)_$($env:APP_VERSION_CODE)_platform.apk"
```
Der Dateiname muss exakt dem Muster `DrainQ-ONE_<Version>_<Code>_platform.apk` folgen — das
Skript liest Soll-Version daraus. `Werkseinrichtung.ps1` prüft die Signatur beim Start selbst
(kein Vertrauensvorschuss nötig); stimmt der Fingerabdruck nicht mit dem Plattformschlüssel
überein, bricht der gesamte Lauf ab, bevor irgendein Gerät angefasst wird.

---

## ★ Die wichtigste Regel

**Eine fabrikneue ONE wird NIEMALS zurückgesetzt (kein Werksreset).**

Gemessen am 29.07.2026: Der Werksreset schaltet die USB-Wartungsverbindung ab, nicht das
Gerät an sich. Eine fabrikneue ONE ist bereits kontenfrei und lässt sich sofort einrichten —
ein Reset macht sie nur schwerer erreichbar. Der Werksreset ist ausschließlich für Geräte
gedacht, die aus dem Feld zurückkommen und schon ein Benutzerkonto/Projekte tragen.

---

## Weg 1 — Neugerät (Regelfall, kein Werksreset)

1. Tablet auspacken, per USB an den PC anschließen.
2. Im Ordner `tools/werkseinrichtung/` die Datei **`Start-Werkseinrichtung.cmd`** doppelklicken.
3. Erscheint am Tablet ein Dialog „USB-Debugging zulassen?" — bestätigen (nur beim
   allerersten Anschließen eines Geräts nötig), dann die Datei erneut starten.
4. Warten. Mehrere Geräte gleichzeitig anschließen ist ausdrücklich vorgesehen — das
   Werkzeug richtet alle angeschlossenen Geräte parallel ein.
5. Am Ende steht je Gerät **GRÜN** oder **ROT** in der Konsole und im Protokoll
   (`tools/werkseinrichtung/logs/Werkseinrichtung_<Zeitstempel>.csv`).
6. **Erfolgskontrolle am Gerät:** USB-Kabel abziehen, Tablet **zweimal** aus- und
   wieder einschalten. Nach jedem Start muss DrainQ.ONE von allein erscheinen (kein
   Werks-Startbildschirm), und das Kamerabild muss ohne jeden Eingriff da sein.

### Bei ROT
Das Skript schreibt den Grund in Klartext ins Protokoll — es hat dabei nichts Unwiderrufliches
am Gerät verändert:
- **„kein fabrikneues Gerät"** (Konto vorhanden / App schon installiert) → Gerät gehört auf
  Weg 2 (Rückläufer), nicht hier weitermachen.
- **„anderer Geräteeigentümer gesetzt"** → nicht selbst weitermachen, Rückfrage halten.
- **alles andere** (z. B. Kiosk-Bestätigung, Installation) → `<Seriennummer>_<Zeitstempel>.log`
  im `logs`-Ordner öffnen, dort steht jeder ausgeführte Befehl mit Antwort. Meist hilft ein
  zweiter Versuch (Kabel/Hub-Problem); bei wiederholtem Rot Rückfrage halten.

---

## Weg 2 — Rückläufer aus dem Feld (Sonderfall)

**Vor dem Werksreset sind die Projekte auf dem Gerät unwiederbringlich weg, wenn sie nicht
vorher gesichert wurden.**

1. **Projekte sichern:** in der App → Projekte → Export (USB), auf den PC kopieren.
2. **Werksreset** am Gerät durchführen (Einstellungen → System → Zurücksetzen).
3. **Entwicklermodus freischalten:** Einstellungen → Über das Tablet → 7× auf die
   Build-Nummer tippen → zurück → Entwickleroptionen → **USB-Debugging** aktivieren.
4. Ab hier weiter wie **Weg 1, Schritt 2**.

---

## Rückholweg — Gerät versehentlich eingerichtet oder Einrichtung mittendrin abgebrochen

Android lässt einen einmal gesetzten Geräteeigentümer **nicht** über den regulären Befehl
(`dpm remove-active-admin`) entfernen, solange die App nicht `android:testOnly` ist — bei
DrainQ.ONE bewusst so, weil es eine Auslieferungs-App ist. Der reguläre Befehl scheitert mit
`SecurityException: Attempt to remove non-test admin`.

Getesteter Weg (30.07.2026, am Gerät bestätigt) — **kein Werksreset**, USB-Verbindung bleibt
die ganze Zeit erhalten:

**Automatisiert:**
```
tools\werkseinrichtung\Rueckholweg-DeviceOwner-entfernen.ps1 -Serial <Seriennummer>
# zusätzlich die App entfernen (löscht deren Daten auf dem Gerät):
tools\werkseinrichtung\Rueckholweg-DeviceOwner-entfernen.ps1 -Serial <Seriennummer> -AuchAppEntfernen
```
Fragt vor jeder Änderung eine ausdrückliche Bestätigung (`JA` eintippen) ab.

**Von Hand, die einzelnen Befehle:**
```bash
adb -s <Seriennummer> root
adb -s <Seriennummer> wait-for-device

# Sicherung (falls die Dateien existieren):
adb -s <Seriennummer> shell "cp /data/system/device_owner_2.xml /data/local/tmp/device_owner_2.xml.bak"
adb -s <Seriennummer> shell "cp /data/system/device_policies.xml /data/local/tmp/device_policies.xml.bak"

# Geräteeigentümer-Policy löschen:
adb -s <Seriennummer> shell "rm -f /data/system/device_owner_2.xml /data/system/device_policies.xml"

adb -s <Seriennummer> reboot
adb -s <Seriennummer> wait-for-device
# warten bis: adb -s <Seriennummer> shell getprop sys.boot_completed  ->  1

# Prüfen: muss "no owners" zeigen
adb -s <Seriennummer> shell dpm list-owners

# Nur falls die App komplett runter soll (löscht ihre Daten auf dem Gerät):
adb -s <Seriennummer> uninstall com.uip.drainq.one
```

Wann das gebraucht wird: ein Gerät wurde aus Versehen mit angeschlossen und eingerichtet, die
Einrichtung ist mittendrin abgebrochen (Stromausfall, Kabel raus) und das Gerät soll wieder in
einen sauberen Ausgangszustand, oder ein Testgerät soll nach einer Messung wieder freigegeben
werden. Ist die App danach noch installiert (ohne `-AuchAppEntfernen`), kann `Start-Werkseinrichtung.cmd`
das Gerät direkt weiter einrichten — die Vorprüfung erkennt „App vorhanden, aber kein
Geräteeigentümer" nicht als Sonderfall und würde in diesem Zustand **rot** melden, weil die
App bereits installiert ist, ohne dass unsere Einrichtung sie dorthin gebracht hätte in dieser
Sitzung; in diesem Fall zusätzlich `-AuchAppEntfernen` verwenden, um wieder bei „App fehlt"
anzufangen.

---

## Wann die Einrichtung erneut nötig wird

**Nach jeder Signatur- oder `sharedUserId`-Änderung der App** (siehe ADR-0005) — nicht nur bei
der Erstinbetriebnahme. Ein Signaturwechsel erzwingt Deinstallation + Neuinstallation, und
Android löscht dabei sowohl den Geräteeigentümer-Status als auch die Startbildschirm-Zuordnung
(belegt 29.07.2026, siehe `docs/PROVISIONING_GOLDEN_IMAGE.md`, Abschnitt A.4.1). In diesem Fall
ist das betroffene Gerät nach der Neuinstallation kein „fabrikneues" Gerät mehr im Sinne der
Vorprüfung (die App ist ja schon drauf) — hier hilft derselbe Rückholweg wie oben
(`-AuchAppEntfernen`), danach normal über Weg 1 neu einrichten.

## Was das Werkzeug bei jedem Gerät automatisch mit erledigt

- Entfernt die werkseitig vorinstallierte App `com.bominwell.minipush`, falls vorhanden — sie
  startet sich beim Booten selbst und überschreibt sonst den Autostart von DrainQ.ONE,
  unabhängig von der Startbildschirm-Zuordnung (Befund 30.07.2026).
- Prüft die Signatur der mitgelieferten App-Datei gegen den Plattformschlüssel, **bevor**
  irgendein Gerät angefasst wird — bei falscher Signatur bricht der gesamte Lauf sofort ab.

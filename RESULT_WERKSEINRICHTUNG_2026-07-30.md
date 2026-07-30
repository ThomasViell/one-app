# RESULT: Werkseinrichtungs-Werkzeug (USB, Serienbetrieb) — 2026-07-30

Auftrag: `WERKSEINRICHTUNG_PROMPT.md`. Branch `feature/werkseinrichtung` (ab `feature/camera2-umstieg`).
Testgeräte: `e92df62d2dbd2143` (fabrikneu, freigegeben laut Auftrag) und — nach Rückfrage im Lauf
freigegeben — `80cfaba8f63b8362` (ebenfalls fabrikneu). Louis' Gerät wurde nicht angefasst.

---

## Phase 0 — die zwei Messungen (VOR dem Bauen durchgeführt)

### 1. Ist das Gerät kontenfrei, lässt sich der Kiosk-Betrieb direkt setzen?

**Ja, uneingeschränkt.** `dumpsys account` zeigt auf dem fabrikneuen Gerät 0 Accounts, ein
einziger Nutzer `Owner`. `dpm set-device-owner com.uip.drainq.one/com.uip.oneapp.bootstrap.OneDeviceAdminReceiver`
lief beim ersten Versuch durch: `Success: Device owner set to package com.uip.drainq.one/...`,
bestätigt über `dpm list-owners`.

Nebenbefund: `dpm remove-active-admin` scheitert an dieser App mit
`SecurityException: Attempt to remove non-test admin`, weil `OneDeviceAdminReceiver` bewusst
NICHT `android:testOnly` ist (Auslieferungs-App). Für die Rückführung eines Testgeräts musste
ich per `adb root` die Policy-Dateien `/data/system/device_owner_2.xml` +
`device_policies.xml` sichern und löschen, dann neu starten — siehe „Rückholweg" unten. Dabei
blieb die USB-Verbindung durchgehend erhalten, kein Werksreset nötig — bestätigt die harte
Regel aus dem Auftrag.

### 2. Braucht die App beim allerersten Start ein Netz?

**Nein.** `MainActivity` zeigt direkt `SplashScreen` → `NavGraph` mit
`startDestination = Screen.Home.route`. Kein Login-, Lizenz- oder Aktivierungs-Zwang
dazwischen. Ein Cloud-Login-Screen existiert, ist aber laut Code-Kommentar selbst als
„DEAKTIVIERT — es gibt noch keine echte Authentifizierung" markiert und nur manuell über
Settings erreichbar. Alle Koin-Bindings sind lazy, kein synchroner Netzwerk-Call beim Start.
**Konsequenz:** WLAN ist kein Teil der Werkseinrichtung. Die App geht per Kabel drauf.

---

## Phase 1 — was gebaut wurde

`tools/werkseinrichtung/`:
- **`Start-Werkseinrichtung.cmd`** — Doppelklick-Einstieg (setzt `chcp 65001`, startet das
  PowerShell-Skript mit Bypass-Policy, kein Vorwissen nötig).
- **`Werkseinrichtung.ps1`** — Orchestrator: prüft die mitgelieferte App-Datei, sucht
  angeschlossene Geräte, startet je Gerät einen eigenen Hintergrund-Job (echte
  Parallelverarbeitung), sammelt Ergebnisse ein, schreibt das Protokoll.
- **`Invoke-DeviceSetup.ps1`** — der eigentliche Ablauf für EIN Gerät (Vorprüfung → Installation
  → minipush entfernen → Startbildschirm → Berechtigung → Geräteeigentümer → Kiosk aktivieren),
  jeder Schritt einzeln nachgeprüft, nicht nur „kein Fehler zurückgekommen".
- **`Rueckholweg-DeviceOwner-entfernen.ps1`** — Rückholweg für versehentlich eingerichtete oder
  mittendrin abgebrochene Geräte (siehe unten), mit Pflicht-Bestätigung.
- **`adb/`** — mitgeliefertes adb (adb.exe, AdbWinApi.dll, AdbWinUsbApi.dll, NOTICE.txt).
- **`app/DrainQ-ONE_0.6.2_602_platform.apk`** — plattformsignierter Debug-Bau (Alias
  `bominwellalias`, `sharedUserId="android.uid.system"`, `enableV1Signing` aktiv für den
  Signaturbeleg). **Kein Keystore im Paket** — nur die fertig signierte APK. **Nicht im
  Git-Repo committet** (`*.apk` ist repo-weit ausgeschlossen — bestehende Konvention, siehe die
  vielen unversionierten APKs im Repo-Root); Bauanleitung in `docs/WERKSEINRICHTUNG.md`. Liegt
  aktuell nur lokal im Arbeitsverzeichnis für den Testlauf.
- **`logs/`** — Protokoll je Lauf (`.gitignore`t, Belegwerte stehen unten in diesem Bericht).

Neu im App-Code: **`ProvisioningReceiver.kt`** (`com.uip.oneapp.bootstrap`) + Manifest-Eintrag.
Grund: der Kiosk-Schalter in den Einstellungen ist ein reiner UI-Toggle (DataStore-Boolean
`kiosk_mode`), ohne adb-Weg. Koordinaten-Taps auf die Compose-Oberfläche wären für ein
Serien-Werkzeug zu fragil (Displaygröße/Sprache). Der neue Receiver schreibt denselben
DataStore-Schlüssel und startet die App neu, ist aber **`exported=false`** — Drittapps auf dem
Gerät erreichen ihn nicht, adb (nur mit `adb root`) schon, über den vollen Klassennamen. Das
wurde am Gerät geprüft: ohne root liefert `am broadcast` `Permission Denial: ... is not
exported`; als root funktioniert es. `adb root` ist auf diesen (`userdebug`-)Boards ohnehin
bereits ein akzeptierter, dokumentierter Charakterzug der Flotte.

### Die drei nachträglichen Ergänzungen

1. **Rückholweg dokumentiert** — eigener Abschnitt in `docs/WERKSEINRICHTUNG.md` mit den
   genauen Befehlen (automatisiert per Skript UND von Hand), siehe unten.
2. **Vorprüfung erweitert** — bricht für ein Gerät ab, wenn: Accounts > 0, ODER ein anderer
   Geräteeigentümer bereits gesetzt ist, ODER die App schon installiert ist und (Version nicht
   passt ODER der gesetzte Geräteeigentümer nicht unsere eigene App ist). Nur wenn Version UND
   Geräteeigentümer exakt zu unserer App passen, wird das als eigene, angebrochene Einrichtung
   fortgesetzt (idempotent) — alles andere ist ein harter Stopp mit Klartext-Grund.
3. **Signaturprüfung** — SHA-256-Fingerabdruck der mitgelieferten APK wird **vor jedem
   Geräte-Zugriff** gegen `2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22`
   geprüft — rein per .NET (`System.Security.Cryptography.Pkcs.SignedCms` über
   `META-INF/*.RSA`), **kein `keytool`/JDK nötig**, das Muster passt zu „läuft auf einem
   Rechner, auf dem nichts installiert ist". Bei Mismatch bricht der GESAMTE Lauf ab, kein
   Gerät wird angefasst.

### Nebenfund während des Baus: `com.bominwell.minipush`

Das fabrikneue Gerät bringt werkseitig `com.bominwell.minipush` mit — eine OEM-App mit
eigenem `BOOT_COMPLETED`-Empfänger, der **unabhängig von der HOME-Zuordnung** seine eigene
Activity startet und damit den Autostart von DrainQ.ONE nach jedem Neustart überschreibt.
Das Werkzeug entfernt sie jetzt automatisch (`pm uninstall --user 0`, mit Nachprüfung), sonst
wäre „App startet nach dem Einschalten von allein" auf keinem fabrikneuen Gerät erfüllbar
gewesen. Ist die App nicht vorhanden (z. B. auf einem anderen Werksstand), wird der Schritt
ohne Fehler übersprungen.

### Zwei Bugs, gefunden und behoben beim ersten echten Testlauf

- **`ProcessStartInfo.ArgumentList` liefert auf dieser Windows-PowerShell-5.1-Version `$null`**
  statt einer leeren Collection → `.Add()` warf „Methode für einen Ausdruck mit NULL-Wert".
  Fix: adb-Aufrufe laufen jetzt über den nativen PowerShell-Aufrufoperator (`& $AdbPath @args`)
  statt über `ProcessStartInfo`.
- **`(Where-Object {...}).Count` ist `$null` statt `0`, wenn GENAU EIN Objekt zurückkommt** (in
  Windows PowerShell 5.1 gibt ein Einzeltreffer kein Array zurück) → mit genau einem
  angeschlossenen Gerät wartete die Fortschritts-Schleife des Orchestrators überhaupt nicht,
  meldete sofort „unerwartet abgebrochen". Fix: `@(...)` erzwingt an allen betroffenen Stellen
  ein Array. Ohne das Erst-Testgerät (genau 1 Stück) wäre dieser Fehler bei einem
  Mehrgeräte-Erstlauf vermutlich unbemerkt geblieben.
- Zusätzlich: die `.ps1`-Dateien mussten mit **UTF-8-BOM** geschrieben werden — ohne BOM
  interpretiert Windows PowerShell 5.1 (anders als PowerShell 7) die Umlaute in den
  String-Literalen der Quelldatei falsch (Mojibake in der Konsole).

---

## Phase 2 — Erprobung, mit Belegen

### Einzelgerät (`e92df62d2dbd2143`, aus echtem fabrikneuen Zustand)

Über den echten Werkzeug-Pfad (`Werkseinrichtung.ps1`, nicht manuell):

```
Gerät e92df62d2dbd2143: >>> GRUEN <<<
  Version 0.6.2/602, Dauer 10.7 s
```
**Gemessene Gesamtzeit (Kaltstart PowerShell bis fertig): 14 s.**

Danach zweimal vollständig neu gestartet (`adb reboot`, warten auf `sys.boot_completed=1`,
+4 s Nachlauf), **beide Male ohne jeden Eingriff:**
- Neustart 1: `topResumedActivity=...com.uip.drainq.one/com.uip.oneapp.MainActivity`,
  `mLockTaskModeState=LOCKED`.
- Neustart 2: dieselben Werte, zusätzlich `dpm list-owners` zeigt weiterhin
  `admin=com.uip.drainq.one/com.uip.oneapp.bootstrap.OneDeviceAdminReceiver,DeviceOwner`.

### Zwei Geräte gleichzeitig (`e92df62d2dbd2143` + `80cfaba8f63b8362`, beide aus echtem fabrikneuen Zustand)

```
Gerät 80cfaba8f63b8362: >>> GRUEN <<<   Version 0.6.2/602, Dauer 10.3 s
Gerät e92df62d2dbd2143: >>> GRUEN <<<   Version 0.6.2/602, Dauer 10.2 s
```
**Gemessene Gesamtzeit für BEIDE Geräte parallel: 14 s** — praktisch identisch zur
Einzelgerät-Zeit. Die geforderte Eigenschaft „vier Anlagen sollen kaum länger brauchen als
eine" ist damit im Kleinen (2 Geräte) belegt; eine Messung mit 4 Geräten gleichzeitig stand
kein passender USB-Verteiler mit 4 freigegebenen Geräten zur Verfügung (siehe „Nicht geprüft").

Danach beide Geräte zweimal gemeinsam neu gestartet — beide Male beide Geräte ohne Eingriff im
Kiosk mit DrainQ.ONE im Vordergrund, `dpm list-owners` weiterhin korrekt gesetzt.

### Kamerabild ohne Eingriff

Auf beiden Geräten Inspektionsbildschirm geöffnet (per adb-Tap, keine App-Änderung): auf
beiden ein **Live-Bild** (Kamera-Status „C18" grün, Meterstand angezeigt, Bildinhalt zwischen
den beiden Geräten sichtbar unterschiedlich — kein Standbild). Screenshots:
`_werkseinrichtung_evidence/geraet1_home.png`, `geraet1_kamera.png`, `geraet2_kamera.png`.
Kein manueller Eingriff außer dem Wechsel auf den Inspektionsbildschirm (normale Bedienung,
kein Einrichtungsschritt).

### Sicherheitsnetze — geprüft, nicht nur behauptet

- **Zwischenzustand (App installiert, aber unser Geräteeigentümer entfernt):** Werkzeug bricht
  korrekt mit `ROT` und Klartext-Grund „App ist bereits installiert ... das ist KEIN
  fabrikneues Gerät ..." ab, fasst das Gerät nicht an.
- **Erneuter Lauf auf einem bereits von diesem Werkzeug fertig eingerichteten Gerät:** korrekt
  `GRUEN` (idempotent), kein Fehlalarm.
- **Falsch/nicht verifizierbar signierte App-Datei** (Debug-Schlüssel statt Plattformschlüssel
  eingesetzt): Werkzeug bricht **vor jedem Gerätezugriff** ab
  („Keine Signaturdatei ... gefunden" bzw. Fingerabdruck-Mismatch je nach Signaturvariante),
  kein Gerät wird gesucht oder angefasst.

---

## Rückholweg — Ergebnis

Getestet (Abschnitt oben, Phase 0 Punkt 1): Policy-Dateien sichern + löschen + Neustart entfernt
den Geräteeigentümer zuverlässig, ohne Werksreset, USB bleibt erhalten. Als eigenständiges,
bestätigungspflichtiges Skript gebaut (`Rueckholweg-DeviceOwner-entfernen.ps1`) und mehrfach
in diesem Lauf selbst benutzt, um die Testgeräte zwischen den Messungen wieder in einen echten
fabrikneuen Zustand zu bringen — jedes Mal erfolgreich, `dpm list-owners` danach `no owners`.
Vollständig dokumentiert (inkl. der einzelnen Befehle von Hand) in
`docs/WERKSEINRICHTUNG.md`, Abschnitt „Rückholweg".

---

## Anleitung

`docs/WERKSEINRICHTUNG.md` — eine Seite, zwei Wege (Neugerät / Rückläufer), Rückholweg-Abschnitt,
Hinweis auf Wiederholung nach Signaturänderungen. `QR_EINRICHTUNG_PROMPT.md` als überholt
markiert (Hinweisblock am Dateianfang), nicht gelöscht.

---

## Was NICHT geprüft werden konnte

- **Vier Geräte gleichzeitig** (nur laut Auftrag als Wunschgröße genannt) — es standen nur zwei
  freigegebene Testgeräte zur Verfügung. Da die Parallelverarbeitung technisch pro Job
  unabhängig ist (kein gemeinsamer Flaschenhals außer USB-Bandbreite beim `adb install`),
  ist eine deutlich längere Gesamtzeit bei 4 Geräten unwahrscheinlich, aber nicht gemessen.
- **Echter „jungfräulicher" Rechner ohne jede Software** — der Lauf fand auf der
  Entwicklungsmaschine statt. Die beiden gefundenen PowerShell-5.1-Kompatibilitätsfehler
  wurden genau deshalb sichtbar (Windows-PowerShell 5.1 ist die Voreinstellung auf jedem
  Windows-Rechner, auch einem frischen) — das erhöht die Zuversicht, dass das Muster
  „ohne Vorinstallation" grundsätzlich funktioniert, ist aber kein Ersatz für einen Lauf auf
  einem tatsächlich unberührten Rechner eines Kollegen.
- **minipush auf jedem Werksstand vorhanden?** Nur auf den zwei getesteten Geräten bestätigt.
  Ist die App auf einem anderen Werksstand nicht vorinstalliert, überspringt das Werkzeug den
  Schritt sauber (kein Fehler) — aber ob sie auf JEDEM Gerät vorhanden ist, wurde nicht an
  einer größeren Stichprobe geprüft.
- **Verhalten bei einem echten Rückläufer mit echtem Benutzerkonto/echten Projektdaten** — aus
  gutem Grund nicht an einem echten Kundengerät geprobt; die Logik wurde stattdessen an einem
  konstruierten Zwischenzustand geprüft (siehe oben).
- **USB-Verteiler mit mehr als 2 Ports unter Last** (Stromversorgung, gleichzeitige
  `adb install` über einen einzigen Hub) — nicht geprüft, da kein größerer Verteiler zur
  Verfügung stand.

---

## Sicherheitshinweis (nicht im Code, nur hier vermerkt)

Das Passwort für `ONE_PLATFORM_KEYSTORE`/`ONE_PLATFORM_PASS` wurde während dieser Sitzung einmal
im Klartext in einer Chat-Nachricht übertragen (nicht in einer Datei, nicht committet — geprüft).
Falls diese Konversation an einem Ort landet, der nicht denselben Vertraulichkeitsgrad hat wie
der Passwort-Tresor selbst: das Passwort vorsorglich ändern.

---

**STOPP zur CEO-Abnahme.** Kein Merge, kein Tag, kein Publish.

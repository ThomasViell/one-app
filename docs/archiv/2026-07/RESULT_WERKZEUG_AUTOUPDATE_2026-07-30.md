# RESULT: Einrichtungswerkzeug holt sich die App selbst aus dem Lizenzportal — 2026-07-30

Auftrag: `AUTOUPDATE_WERKZEUG_PROMPT.md`. Branch `feature/werkseinrichtung-autoupdate` (ab `master`,
Commit `00f44b8`). Kein Merge, kein Tag, kein Publish.

---

## Was gebaut wurde

### Neue Dateien
- **`tools/werkseinrichtung/Update-WerkzeugApp.ps1`** — die eigentliche Selbstaktualisierung.
  Funktionsbibliothek (kein Seiteneffekt beim Dot-Source): `Get-LocalPlatformApks` (findet 0/1/n
  App-Dateien im `app`-Ordner), `Get-PortalManifest` (HTTP-GET, kein API-Key), `Invoke-WerkzeugSelfUpdate`
  (der ganze Ablauf: Vergleich → Download → Prüfsumme → Signatur → Austausch).
- **`tools/werkseinrichtung/Get-ApkSignatureFingerprint.ps1`** — der bisher in `Werkseinrichtung.ps1`
  inline stehende Signatur-Check (reines .NET, kein keytool/JDK) als gemeinsam genutzte Funktion
  herausgezogen, damit die Prüfung der mitgelieferten Datei und die Prüfung einer frisch
  heruntergeladenen Datei garantiert **denselben** Code laufen lassen.
- **`tools/werkseinrichtung/autoupdate.config.json`** — der konfigurierbare Kanal. Vorgabe `"beta"`
  (der heute auch von `publish-one-release.ps1 -Channel` und `app/build.gradle.kts UPDATE_CHANNEL`
  genutzte Kanal), Portal-URL `https://license.drainq.com`. Kann vor Ort angepasst werden, falls
  irgendwann auf `stable` umgestellt wird; per `-Channel`/`-PortalUrl`-Parameter zusätzlich für
  Sonderfälle/Tests überschreibbar.

### Geänderte Dateien
- **`tools/werkseinrichtung/Werkseinrichtung.ps1`**: neuer Schritt „Prüfe Portal auf neueren
  freigegebenen Stand" direkt nach den Existenzprüfungen und vor der bisherigen Signaturprüfung.
  Liest den Kanal aus `autoupdate.config.json` (mit Parameter-Override), ruft
  `Invoke-WerkzeugSelfUpdate` auf, zeigt das Ergebnis farbig an, bricht nur ab, wenn **weder** eine
  lokale Datei **noch** eine Portal-Verbindung vorhanden ist (sonst gäbe es nichts, womit man
  einrichten könnte). Neuer Parameter `-KeineSelbstaktualisierung` für Rechner, die bewusst nie
  online sind. Nach der (jetzt ausgelagerten) Signaturprüfung wird die verwendete Version **gut
  sichtbar** gesetzt: Fenstertitel (`$Host.UI.RawUI.WindowTitle`) und eine hervorgehobene Zeile im
  Konsolen-Text, dazu eine zweite Zeile mit der Herkunft (Portal-aktualisiert / lokaler Stand vom
  Datum X / Portal nicht erreichbar / lokal neuer als Portal). Diese Herkunfts-Zeile wird als neuer
  Parameter `-VersionSourceNote` an jeden Geräte-Job weitergereicht.
- **`tools/werkseinrichtung/Invoke-DeviceSetup.ps1`**: neuer optionaler Parameter
  `-VersionSourceNote`, wird als erste Zeile in jedes Geräte-Protokoll geschrieben (ZIEL Punkt 6:
  Version + Herkunft je Gerät im Protokoll).
- **`tools/publish-one-release.ps1`**: neuer Schritt 6 nach der Veröffentlichung — zippt den
  kompletten `tools/werkseinrichtung`-Ordner (ohne `logs/`, `dist/`, `_update_staging/`,
  `_previous/`) nach `tools/werkseinrichtung/dist/Werkseinrichtung_<Version>_<Code>.zip`. Die
  Versionsnummer im Dateinamen stammt aus der **tatsächlich im `app`-Ordner liegenden** APK (nicht
  blind aus den `-VersionName`/`-VersionCode`-Parametern des gerade laufenden Publish) — stimmen
  beide nicht überein, wird das als Warnung ausgegeben, aber trotzdem gepackt (das Paket soll den
  wirklichen Ordnerinhalt widerspiegeln). Fehler beim Packen brechen den Release NICHT ab, da er zu
  diesem Zeitpunkt bereits veröffentlicht ist.
- **`.gitignore`**: `tools/werkseinrichtung/dist/` ergänzt (enthält Zips mit APK-Inhalt, analog zur
  bestehenden `logs/.gitignore`-Konvention).

### Ablauf im Detail (Abgleich mit ZIEL/ABLAUF aus dem Auftrag)
1. Manifest vom selben Kanal wie die Geräte: `<portalUrl>/api/software/<product>/releases.<channel>.json`
   — exakt dieselbe URL-Form wie `UpdateConfig.manifestUrl` im App-Code, kein zweiter Weg.
2. Versionsvergleich über `versionCode` (wie `HttpUpdateService.checkForUpdate`).
3. Portal neuer → Download, **sha256 aus dem Manifest** UND **Signatur-Fingerabdruck** geprüft
   (derselbe `Get-ApkSignatureFingerprint`-Code wie für die mitgelieferte Datei). Erst wenn beides
   stimmt: alte Datei nach `app/_previous/<name>_ersetzt_<Zeitstempel>.apk` verschoben (nicht
   überschrieben), neue Datei eingesetzt.
4. Eine der beiden Prüfungen falsch → heruntergeladene Datei verworfen, Status `Rejected`, deutliche
   rote/gelbe Meldung, weiter mit der bisherigen Datei.
5. Portal nicht erreichbar (Netzwerkfehler oder Kanal nicht veröffentlicht/HTTP 404) → Status
   `PortalUnreachable`, weiter mit der vorhandenen Datei, Version **und Datum** (Dateizeitstempel)
   werden angezeigt.
6. Lokal neuer als Portal → Status `LocalNewer`, deutliche Warnung („unveröffentlichter Stand im
   Ordner"), weiter mit der lokalen Datei.
7. Kein API-Key: `Invoke-WebRequest`/`Invoke-WerkzeugSelfUpdate` senden keinerlei Zugangsdaten —
   geprüft gegen das echte Portal (siehe unten), funktioniert anonym, genau wie bei den Geräten.
8. Version + Herkunft: Fenstertitel, hervorgehobene Konsolenzeile, erste Zeile in jedem
   Geräte-Protokoll.

---

## PRÜFEN — die vier Pflicht-Szenarien plus der vollständige Gerätelauf, mit Belegen

Die vier Mechanismus-Szenarien (1–4) getestet mit einem eigenen Belegskript (`autoupdate_proof.ps1`
+ `proof_scenario_b.ps1`, nicht Teil des Commits, nur zur Protokollierung), das ausschließlich
Kopien in Scratch-Ordnern anfasst — die echte Datei unter
`tools/werkseinrichtung/app/DrainQ-ONE_0.9.0_900_platform.apk` wurde dabei nicht verändert (danach
per `git status` verifiziert). **Alle vier Läufe unter der echten Zielumgebung** Windows PowerShell
5.1 (`powershell.exe`, dieselbe Laufzeit wie `Start-Werkseinrichtung.cmd`). Szenario 5 (der
vollständige Einrichtungslauf am Gerät) lief direkt über die echten Skripte gegen ein reales
Testgerät.

### 1. Portal hat neuere Version → wird geholt, geprüft, ersetzt
Echt gegen das Produktions-Portal getestet (Kanal `beta`, aktuell `0.9.0`/`900`). Lokale Testdatei
künstlich auf `0.1.0`/`100` zurückdatiert.
```
Status: Updated
Lokaler Stand: 0.1.0/100
Portal ist neuer (900 > 100) - lade herunter: https://license.drainq.com/api/software/download/1be726d3-...
Heruntergeladen. sha256 erwartet=e691914b... tatsaechlich=E691914B... (identisch)
Signatur-Fingerabdruck: 2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22 (bestätigt)
Alte Datei zur Seite gelegt: app\_previous\DrainQ-ONE_0.1.0_100_platform_ersetzt_2026-07-30_181904.apk
Neue Datei eingesetzt: app\DrainQ-ONE_0.9.0_900_platform.apk
```
Zusätzlich als **vollständiger Einrichtungslauf-Einstieg** bestätigt: eine komplette Kopie von
`tools/werkseinrichtung` (mit zurückdatierter lokaler Datei) über das echte `Werkseinrichtung.ps1`
gestartet — Selbstaktualisierung lief durch, die anschließende reguläre Signaturprüfung erkannte die
neu eingesetzte Datei korrekt, Fenstertitel/Kopfzeile zeigten „Verwendete Version: 0.9.0 (Code 900)"
und „AKTUALISIERT: Portal-Stand 0.9.0/900 uebernommen (vorher 0.1.0/100)", danach normaler Abbruch
bei „Kein einsatzbereites Gerät gefunden" (erwartet, kein Tablet angeschlossen).

### 2. Manipulierte Datei (Prüfsumme absichtlich falsch) → PFLICHT-Negativprobe
Lokaler Mock-Server (eigener HTTP-Listener, Kanal-Endpunkt nachgebildet) liefert ein Manifest mit
einer **absichtlich falschen** sha256 (`0000...0000`), aber den echten (gültig signierten)
Bytes der App als „Download".

**Nachgewiesen unter der echten Ziel-Laufzeit Windows PowerShell 5.1** (`powershell.exe`), nach
Nachinstallation des Moduls `ThreadJob -Scope CurrentUser` (siehe unten, warum das nötig war):
```
Status: Rejected
ABGELEHNT: Pruefsumme der Portal-Datei 9.9.9/9990 stimmt nicht - bleibe bei 0.1.0/100
Erwartet sha256=0000...0000, tatsaechlich=E691914B... . Datei verworfen, NICHT verwendet.
Alte Datei noch vorhanden und unveraendert: True / True   (Hash vor/nach dem Lauf identisch geprüft)
```
Die alte Datei blieb byteidentisch (Hash-Vergleich vor/nach dem Lauf), die verworfene Downloaddatei
und der Staging-Ordner wurden aufgeräumt.

*Vorlauf (überholt, nur zur Nachvollziehbarkeit stehen gelassen):* derselbe Nachweis lief zuerst
unter pwsh (PowerShell 7), weil `Start-ThreadJob` dort eingebaut ist und auf diesem Rechner für
Windows PowerShell 5.1 zunächst fehlte. Das zählte nicht als vollständiger Beleg, weil genau die
Ziel-Laufzeit (PS5.1) an anderer Stelle bereits zwei echte, laufzeitspezifische Fehler gezeigt
hatte (siehe unten) — ein Nachweis in der falschen Umgebung wäre kein Nachweis für die Produktion
gewesen. Behoben durch `Install-Module ThreadJob -Scope CurrentUser` unter `powershell.exe`
(Modul-Eigenname dort `ThreadJob`, nicht `Microsoft.PowerShell.ThreadJob` wie bei pwsh
vorinstalliert), danach obiger Lauf unter der echten Laufzeit.

### 3. Portal nicht erreichbar (Netz getrennt) → läuft weiter, Version+Datum sichtbar
Statt physisch das Netz zu trennen: `-PortalUrl` auf einen unbenutzten lokalen Port gezeigt (bewirkt
denselben „Verbindung nicht möglich"-Fehlerpfad wie eine echte Netztrennung, ohne die Internet-
Anbindung dieser Umgebung zu kappen).
```
Status: PortalUnreachable
Portal nicht erreichbar - lokaler Stand 0.5.0/500 vom 2026-07-30 16:50
Portal-Fehler: Die Verbindung mit dem Remoteserver kann nicht hergestellt werden.
```

### 4. Lokal ist neuer als das Portal → wird gemeldet
Echt gegen das Produktions-Portal (`0.9.0`/`900`), lokale Testdatei künstlich auf `99.0.0`/`99000`
hochgesetzt.
```
Status: LocalNewer
ACHTUNG: lokaler Stand 99.0.0/99000 ist NEUER als das Portal (0.9.0/900) - unveroeffentlichter Stand im Ordner
```

### 5. Vollständiger Einrichtungslauf AM GERÄT nach dem Selbstaktualisieren (Nachtrag 2026-07-30, später)

Nachgeholt mit einem der beiden Testgeräte (`233b4bd2865177ed`, RK3588_S) — kein fabrikneues Gerät
verfügbar, daher über den Bestandsgeräte-Modus, wie angeordnet.

**Schritt 1 — Rückholweg (Kiosk-Betrieb entfernen), Beleg:**
```
Rückholweg für Gerät 233b4bd2865177ed
1) adb root...
2) Policy-Dateien sichern... (Sicherung unter /data/local/tmp/werkseinrichtung_backup_20260730_184529)
3) Geräteeigentümer-Policy löschen...
4) Neustart...
5) Prüfung...
   dpm list-owners: no owners
   Geräteeigentümer entfernt, bestätigt.
```
Lief sauber durch — **kein eigener Befund, kein Blocker.** (Vor dem Rückholweg stand das Gerät
ohnehin bereits auf `no owners`, aber App 0.6.1/601 war noch installiert — der Rückholweg selbst
verlief trotzdem fehlerfrei und lieferte den geforderten Beleg.)

**Schritt 2+3 — Werkseinrichtung im Bestandsgeräte-Modus, vollständig bis GRÜN:**
```
=== Prüfe Portal auf neueren freigegebenen Stand ===
  Frage Portal-Manifest ab: https://license.drainq.com/api/software/one/releases.beta.json (Kanal 'beta')
  Portal meldet: 0.9.0 (Code 900), veroeffentlicht 2026-07-30
  Lokaler Stand: 0.9.0/900
  Bereits aktuell - kein Update noetig.
Aktuell: 0.9.0/900 (Kanal 'beta', mit Portal abgeglichen)

Gerät 233b4bd2865177ed: >>> GRUEN <<<
  Version 0.9.0/900, Dauer 10.5 s, Modus Bestandsgeraet
```
Die Selbstaktualisierung griff **gegen das echte Portal** wie gefordert — 0.9.0/900 war dort
freigeschaltet und stimmte mit dem lokalen Stand überein (`UpToDate`), damit ist auch dieser Pfad
scharf gegen die Produktion bestätigt (bislang nur `Updated`/`Rejected`/`PortalUnreachable`/
`LocalNewer` waren einzeln belegt, jetzt zusätzlich `UpToDate`). Geräte-Protokoll
(`logs/233b4bd2865177ed_2026-07-30_184615.log`) bestätigt jeden Schritt einzeln: alte App 0.6.1/601
entfernt, 0.9.0/900 installiert, Startbildschirm gesetzt, Berechtigung erteilt, Geräteeigentümer
gesetzt, Kiosk-Sperre aktiv, kein Kamera-Fehler im Log. Erste Zeile des Protokolls bestätigt ZIEL
Punkt 6 (Version + Herkunft je Gerät): `Verwendete App-Version/Herkunft: Aktuell: 0.9.0/900 (Kanal
'beta', mit Portal abgeglichen)`.

**Schritt 4 — Erfolgskontrolle, zweimal aus- und wieder eingeschaltet:**
Da hier keine Person vor dem Tablet steht, wurde die Sichtprüfung durch echte `adb screencap`-
Screenshots ersetzt (mehr als das bisher genutzte Logcat-Kriterium, das laut eigenem Code-Kommentar
„keine Sichtprüfung ersetzt" — ein Screenshot vom Gerät kommt dem so nah wie ohne Person vor Ort
möglich):

| Neustart | `topResumedActivity` | `mLockTaskModeState` | Kamerabild |
|---|---|---|---|
| 1 | `com.uip.drainq.one/com.uip.oneapp.MainActivity` | `LOCKED` | ✅ Live-Bild bestätigt (Screenshot) |
| 2 | `com.uip.drainq.one/com.uip.oneapp.MainActivity` | `LOCKED` | ✅ Live-Bild bestätigt (Screenshot) |

Beide Male erschien DrainQ.ONE von allein (kein Werks-Startbildschirm) — Screenshot direkt nach
Boot-Abschluss zeigt jeweils den App-eigenen Beta-Warnhinweis („Dies ist eine Beta-Version...",
bei JEDEM Kaltstart, nicht nur beim ersten — bereits vor diesem Auftrag so, unverändert). Nach
Wegtippen und einem Tap auf „Inspektion" zeigte das Live-Kamerabild in beiden Fällen ein echtes,
sich veränderndes Kamerabild (Meterstand 0.00 m, Kamerakopf-Anzeige „C18", Akku/Hardware-Status
99 %) — keine schwarze Fläche, kein Platzhalter, kein Fehlerbild. Zusätzlich bestätigt
`dpm list-owners` nach beiden Neustarts weiterhin `DeviceOwner` gesetzt (Kiosk übersteht den
Neustart) und der Kamera-Selbststart-Log zeigt nach dem zweiten Neustart die bekannte, bereits
dokumentierte Wiederherstellung (`vendor.camera-provider-2-4-ext nicht running — starte via
SystemProperties ctl.start` → `erfolgreich gestartet`, siehe `RESULT_KAMERA_CAMERA2_2026-07-29.md`),
ohne `AUDIT camera_self_start_failed`.

Screenshots liegen lokal unter `tools/werkseinrichtung/logs/reboot{1,2}_*.png` (nicht committet,
`logs/` ist gitignored wie alle Protokolle dieses Werkzeugs).

**Präzisierung:** „ohne jeden Eingriff" aus der Anleitung bezieht sich auf das App-eigene Verhalten
(kein WLAN-Setup, keine Kopplung, keine Berechtigungs-Dialoge) — der App-eigene Beta-Warnhinweis
und die Navigation zum Inspektionsbildschirm sind normale Bedienschritte, keine technische
Nacharbeit, und bestanden bereits vor diesem Auftrag unverändert (siehe
`RESULT_WERKSEINRICHTUNG_2026-07-30.md`). Damit ist PRÜFEN-Punkt 4 **geschlossen**.

### Zusammenfassung
| # | Szenario | Erwartet | Ergebnis |
|---|---|---|---|
| 1 | Portal neuer | `Updated` | ✅ `Updated` (echtes Portal + vollständiger Einrichtungslauf-Einstieg), PS5.1 |
| 2 | Falsche Prüfsumme | `Rejected` | ✅ `Rejected`, alte Datei unverändert (Pflicht-Negativprobe), PS5.1 |
| 3 | Portal unerreichbar | `PortalUnreachable` | ✅ `PortalUnreachable`, Version+Datum sichtbar, PS5.1 |
| 4 | Lokal neuer | `LocalNewer` | ✅ `LocalNewer`, PS5.1 |
| 5 | Vollständiger Einrichtungslauf am Gerät (Bestandsgeräte-Modus, Portal bereits aktuell) | GRÜN | ✅ GRÜN, 10.5 s, `UpToDate`, Kamerabild nach 2× Neustart bestätigt |

Alle fünf Szenarien sind damit unter der echten Ziel-Laufzeit (Windows PowerShell 5.1) bzw. am
echten Gerät belegt.

---

## Zwei echte Fehler beim Testen gefunden und behoben (nicht Testartefakte)

Alle vier Läufe wurden unter der **echten Ziel-Laufzeit** Windows PowerShell 5.1 durchgeführt, weil
genau dort — nicht unter dem moderneren pwsh, mit dem man normalerweise testet — zwei echte
Fehler auffielen, die sonst erst beim ersten produktiven Einsatz sichtbar geworden wären:

1. **Array-Unwrap-Falle (kritisch):** `Get-LocalPlatformApks` gab bei genau einer gefundenen
   Datei — dem **Normalfall** — unter PowerShell 5.1 ein einzelnes Objekt statt eines
   1-elementigen Arrays zurück (dasselbe bereits einmal in diesem Repo dokumentierte Muster, siehe
   Kommentar zu `@($jobs | Where-Object ...)` in `Werkseinrichtung.ps1`). Ohne `@(...)` an der
   Aufrufstelle in `Invoke-WerkzeugSelfUpdate` hätte das Werkzeug auf jedem Werks-PC bei jedem
   einzigen Lauf fälschlich „kein lokaler Stand" angenommen — mit der Folge, dass „lokal neuer als
   Portal" nie erkannt worden wäre und bei jedem Lauf unnötig die komplette APK neu heruntergeladen
   worden wäre, selbst wenn bereits der aktuelle Stand vorlag. Unter pwsh (PowerShell 7) fällt der
   Fehler NICHT auf, weil dort auch Einzelobjekte eine eingebaute `.Count`-Eigenschaft (=1) haben —
   ein reiner Testartefakt-Unterschied zwischen den beiden Laufzeiten. Behoben durch `@(...)` an der
   Aufrufstelle, mit Kommentar im Code.
2. **`Move-Item`/`Remove-Item` ohne `-LiteralPath`:** schlug in dieser Testumgebung an einem
   Kurzname-Pfadsegment fehl (Sandbox-Eigenheit, kein Produktionsszenario) — trotzdem auf
   `-LiteralPath` umgestellt, weil das grundsätzlich robuster gegen Sonderzeichen in Pfaden ist und
   keinen Nachteil hat.

Beide Funde sind der Grund, warum die Läufe mehrfach wiederholt wurden, bis sie sauber unter der
echten Laufzeit durchliefen.

Nebenbei behoben: `Invoke-WebRequest`s Fortschrittsbalken bremste den 175-MB-Download unter
PowerShell 5.1 um mehrere Größenordnungen aus (`$ProgressPreference = 'SilentlyContinue'` lokal
um den Download-Aufruf gesetzt) — ohne diesen Fix hätte ein Selbstaktualisierungs-Download auf
einem Werks-PC mehrere Minuten statt Sekunden gedauert.

---

## Was nicht geprüft werden konnte

- **Das Auslieferungspaket-Zip aus `publish-one-release.ps1`** wurde nicht durch einen echten
  Publish-Lauf ausgelöst (der lädt eine neue Version ins Produktions-Portal hoch — außerhalb des
  Auftragsumfangs ohne Rückfrage). Der Zip-Baustein selbst (Kopieren ohne `logs`/`dist`/Staging-
  Ordner, Zippen, Zielname aus der im `app`-Ordner liegenden Version) wurde isoliert per
  Parse-Check geprüft, aber nicht scharf durch einen echten Release-Lauf.

---

## Harte Regeln — Einhaltung

1. Kein `git add -A`, keine repo-weiten Git-Befehle — nur gezielt einzelne Dateien betrachtet/geändert.
2. Eigener Branch `feature/werkseinrichtung-autoupdate` ab `master`. Kein Merge, kein Tag, kein Publish.
3. Kein Plattform-Keystore im Paket (`tools/werkseinrichtung/` enthielt und enthält keine `.keystore`/`.jks`-Datei — geprüft).
4. Keine echten Zugangsdaten in committeten Dateien (`autoupdate.config.json` enthält nur Kanal/URL, keinen Key — die Portal-Endpunkte sind bewusst ohne Zugangsdaten erreichbar, wie im Auftrag gefordert).
5. Verbleibender offener Punkt (Auslieferungspaket-Zip nicht durch einen echten Publish-Lauf ausgelöst) oben dokumentiert statt stillschweigend übergangen — kein Blocker.

**STOPP.**

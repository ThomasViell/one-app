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

## PRÜFEN — die vier Pflicht-Szenarien, mit Belegen

Getestet mit einem eigenen Belegskript (`autoupdate_proof.ps1` + `proof_scenario_b.ps1`, nicht Teil
des Commits, nur zur Protokollierung), das ausschließlich Kopien in Scratch-Ordnern anfasst — die
echte Datei unter `tools/werkseinrichtung/app/DrainQ-ONE_0.9.0_900_platform.apk` wurde dabei nicht
verändert (danach per `git status` verifiziert). Alle vier Läufe unter der **echten Zielumgebung**
Windows PowerShell 5.1 (`powershell.exe`, dieselbe Laufzeit wie `Start-Werkseinrichtung.cmd`) —
Ausnahme Szenario B, siehe dort.

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
Bytes der App als „Download". Getestet unter pwsh (PowerShell 7) — siehe Hinweis unten, warum nicht
unter der Ziel-Laufzeit.
```
Status: Rejected
ABGELEHNT: Pruefsumme der Portal-Datei 9.9.9/9990 stimmt nicht - bleibe bei 0.1.0/100
Erwartet sha256=0000...0000, tatsaechlich=E691914B... . Datei verworfen, NICHT verwendet.
Alte Datei noch vorhanden und unveraendert: True / True   (Hash vor/nach dem Lauf identisch geprüft)
```
Die alte Datei blieb byteidentisch (Hash-Vergleich vor/nach dem Lauf), die verworfene Downloaddatei
und der Staging-Ordner wurden aufgeräumt.

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

### Zusammenfassung
| # | Szenario | Erwartet | Ergebnis |
|---|---|---|---|
| 1 | Portal neuer | `Updated` | ✅ `Updated` (echtes Portal + vollständiger Einrichtungslauf-Einstieg) |
| 2 | Falsche Prüfsumme | `Rejected` | ✅ `Rejected`, alte Datei unverändert (Pflicht-Negativprobe) |
| 3 | Portal unerreichbar | `PortalUnreachable` | ✅ `PortalUnreachable`, Version+Datum sichtbar |
| 4 | Lokal neuer | `LocalNewer` | ✅ `LocalNewer` |

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
echten Laufzeit durchliefen — siehe „Was nicht geprüft werden konnte" für die eine verbleibende
Einschränkung.

Nebenbei behoben: `Invoke-WebRequest`s Fortschrittsbalken bremste den 175-MB-Download unter
PowerShell 5.1 um mehrere Größenordnungen aus (`$ProgressPreference = 'SilentlyContinue'` lokal
um den Download-Aufruf gesetzt) — ohne diesen Fix hätte ein Selbstaktualisierungs-Download auf
einem Werks-PC mehrere Minuten statt Sekunden gedauert.

---

## Was nicht geprüft werden konnte

- **Szenario 2 (Pflicht-Negativprobe) lief unter pwsh (PowerShell 7), nicht unter der echten
  Ziel-Laufzeit PowerShell 5.1.** Grund: Der Mock-HTTP-Server dafür braucht einen In-Prozess-
  Hintergrundlauf (`Start-ThreadJob`); das Modul `Microsoft.PowerShell.ThreadJob` ist auf diesem
  Rechner nur für pwsh, nicht für Windows PowerShell 5.1 installiert (`Start-Job`, das PS5.1-native
  Äquivalent, startet einen eigenen Prozess, dessen Loopback-Verbindung in dieser Sandbox den
  Client nicht erreichte — separat verifiziert: TCP-Verbindung zum Port gelang, die Anfrage kam im
  Job aber nie an). Die geprüfte Ablehnungslogik (Prüfsumme aus dem Manifest gegen den Hash der
  heruntergeladenen Datei vergleichen, bei Unterschied verwerfen) enthält keinen der beiden oben
  gefundenen PS5.1-spezifischen Fehler (reiner String-/Datei-Vergleich, kein Array-Return, der
  Move-Item-Pfad wird in diesem Zweig gar nicht erreicht) — die Aussagekraft unter pwsh ist daher
  hoch, aber ein zusätzlicher Nachweis unter der echten Laufzeit steht aus. Empfehlung: bei
  Gelegenheit `Install-Module ThreadJob -Scope CurrentUser` unter Windows PowerShell 5.1 auf einem
  Testrechner nachholen und Szenario 2 dort wiederholen.
- **Ein vollständiger Einrichtungslauf AM GERÄT nach dem Selbstaktualisieren** (PRÜFEN-Punkt 4)
  konnte nicht durchgeführt werden — in dieser Umgebung ist kein Android-Gerät per USB
  angeschlossen (`adb devices` liefert eine leere Liste). Ersatzweise geprüft: der komplette
  Einstieg von `Werkseinrichtung.ps1` bis einschließlich Geräteerkennung lief unter der echten
  Laufzeit fehlerfrei durch (Selbstaktualisierung → reguläre Signaturprüfung → Versionsanzeige →
  Gerätesuche), der Lauf endet danach korrekt und erwartungsgemäß mit „Kein einsatzbereites Gerät
  gefunden". Der Teil danach (Installation, Kiosk-Aktivierung usw.) war schon vor diesem Auftrag
  unverändert und ist durch `RESULT_WERKSEINRICHTUNG_2026-07-30.md` belegt — dieser Auftrag hat an
  diesem Teil nichts geändert.
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
5. Blocker (fehlendes Gerät für Punkt 4, fehlendes ThreadJob-Modul für Szenario 2 unter PS5.1) oben dokumentiert statt stillschweigend übergangen.

**STOPP.**

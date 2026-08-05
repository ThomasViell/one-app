# AUFTRAG: Plattformsignatur — Testlauf am Gerät

ROLLE: Software-Ingenieur im Repo `C:\Projekte\drainq.one`, Branch `feature/dual-mode` (HEAD `42addb1`, synchron mit origin).

Lies zuerst `docs/adr/0005-platform-signing.md` vollständig. Der Auftrag setzt diese ADR um — aber NUR bis zum Messergebnis. Keine Flottenumstellung, kein Merge, kein Publish.

## VORAUSSETZUNG, die der CEO bereitstellt
- Keystore liegt unter `C:\Projekte\DrainQ\DrainQ - Projektinfos un Produktowner\bominwellalias.keystore` (AUSSERHALB des Repos). Pfad kommt aus der Umgebungsvariable `ONE_PLATFORM_KEYSTORE`, nicht hartkodieren.
- Passwort in der Umgebungsvariable `ONE_PLATFORM_PASS` (Store- und Key-Passwort identisch annehmen; falls nicht, nachfragen und STOPP).
- **Testgerät `233b4bd2865177ed`** per adb erreichbar. (CEO-Bestätigung 29.07.: dieses Gerät ist für den Lauf freigegeben, die Daten darauf werden nicht mehr gebraucht. Die frühere Zuordnung „233b… = Arbeitsgerät, tabu" ist überholt.)

Prüfe beides zu Beginn. Fehlt eines: STOPP mit klarer Meldung, nichts bauen.

## HARTE REGELN
1. **KEIN `git add -A`**, keine repo-weiten Git-Befehle. Nur namentlich genannte Pfade stagen.
2. Der Keystore und das Passwort kommen **NIE** ins Repo, nie in eine committete Datei, nie in eine Commit-Message, nie in einen Log. Pfad und Passwort ausschließlich über Umgebungsvariablen.
3. Kein Merge nach master, kein Tag, kein Portal-Publish.
4. Es wird **ausschließlich** `233b4bd2865177ed` angefasst. Taucht ein weiteres Gerät in `adb devices` auf, wird es nicht berührt — alle adb-Befehle mit `-s 233b4bd2865177ed` bzw. `$env:ANDROID_SERIAL="233b4bd2865177ed"`. Louis' Gerät wird nie angefasst.
5. Git-Befehle lokal in PowerShell.

## SCHRITT 1 — Doku committen
Zwei Dateien liegen neu bzw. geändert im Arbeitsbaum. Stage genau diese und committe:

```
PROJECT_STATUS.md
docs/adr/0005-platform-signing.md
```
Message:
```
docs: PROJECT_STATUS auf Stand 29.07. + ADR-0005 Plattformsignatur

Statusdatei war inhaltlich auf dem 13.07. stehengeblieben.
ADR-0005 = Vorschlag, Entscheidung offen.
```
Danach `git push origin feature/dual-mode`.

## SCHRITT 2 — Signaturkonfiguration
Ergänze in `app/build.gradle.kts` eine `signingConfig` namens `platform`, die Keystore-Pfad und Passwort aus den Umgebungsvariablen `ONE_PLATFORM_KEYSTORE` und `ONE_PLATFORM_PASS` liest, Alias `bominwellalias`. Fehlen die Variablen, muss der bisherige Debug-Bau unverändert weiterlaufen — der normale Entwicklungsbau darf durch diese Änderung NICHT kaputtgehen.

Versionsstand für diesen Lauf: **0.6.0 / versionCode 600**. Der Sprung auf 0.6.x markiert bewusst den Bruch der Signaturlinie.

Committe diese Änderung noch NICHT — erst nach erfolgreichem Testlauf, gemeinsam mit dem Ergebnisbericht.

## SCHRITT 3 — Zwei Bauten
Baue zwei APKs mit Plattformsignatur:
- **A:** ohne `sharedUserId` (bevorzugter Weg laut ADR-0005 Abschnitt 4)
- **B:** mit `android:sharedUserId="android.uid.system"` im Manifest

Beide mit `assembleDebug` plus Plattformsignatur — NICHT `assembleRelease`, NICHT der alte `oneapp-release.keystore`.
Belege für jede APK, dass die Signatur stimmt: `keytool -printcert -jarfile <apk>` muss den SHA-256 `2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22` zeigen. Ohne diesen Beleg nicht weitermachen.

## SCHRITT 4 — Testgerät in den Auslieferungszustand bringen
Der Kamera-Test ist nur aussagekräftig, wenn `/dev/video0` als **`crw-rw---- media camera` (0660)** hochkommt — also OHNE die ueventd-Sonderregel aus ADR-0003.

1. Zuerst messen: `adb -s 233b4bd2865177ed shell ls -l /dev/video0` und `grep video /vendor/etc/ueventd.rc`.
2. **Steht dort schon 0660 und keine `video`-Regel:** nichts ändern, Zustand protokollieren, weiter mit Schritt 5.
3. **Steht dort 0666 bzw. eine `video`-Regel:** `/vendor/etc/ueventd.rc` aus `tools/_oem/ueventd.rc.orig` bzw. `/data/local/tmp/ueventd.rc.orig` wiederherstellen, neu starten, erneut messen.

Ohne den Beleg `0660 media camera` ist der Kamera-Test wertlos — dann im Bericht ausdrücklich als „nicht messbar" ausweisen statt ein Ergebnis zu behaupten. Wenn die Wiederherstellung nicht sicher gelingt: STOPP und melden, nichts erzwingen.

## SCHRITT 5 — Messung
**Wichtig:** Wegen des Signaturwechsels ist hier `adb install` ausdrücklich erlaubt und nötig — der Portal-Update-Weg funktioniert über einen Signaturwechsel hinweg nicht. Das ist die dokumentierte Ausnahme von der sonst geltenden Portal-Regel, und sie gilt NUR für dieses Testgerät.

Für Variante A und danach Variante B jeweils:
1. Alte App deinstallieren, neue installieren
2. **Frage 1 — Kamera:** Inspektionsbildschirm öffnen. Kommt ein Live-Bild? Beleg: `logcat` mit `V4L2Bridge: Opened /dev/video0` oder dem Fehler, plus Screenshot.
3. **Frage 2 — Hotspot:** Hotspot mit eigenem Namen aus der App starten. Läuft er? Beleg: `logcat` (kein `SecurityException` auf `NETWORK_SETTINGS`), Screenshot, und ein zweites Gerät sieht die SSID.

Beide Fragen für beide Varianten beantworten, auch wenn A schon erfolgreich ist — die Antwort auf B entscheidet mit über die ADR.

## SCHRITT 6 — Bericht
Schreibe `RESULT_PLATTFORMSIGNATUR_2026-07-29.md` in den Repo-Root mit einer Ergebnistabelle:

| Variante | Kamera ohne ueventd-Regel | Hotspot eigene SSID | Belege |
|---|---|---|---|

Dazu: welche Variante empfiehlst du und warum, und was am Gerät NICHT geprüft werden konnte. Keine Schönfärberei — ein ehrliches „nicht messbar" ist mehr wert als ein plausibel klingendes Ergebnis.

Committe zum Schluss namentlich:
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml   (nur falls geändert)
RESULT_PLATTFORMSIGNATUR_2026-07-29.md
```
Message:
```
feat(signing): Plattformsignatur-Konfiguration + Messlauf 0.6.0/600

Ergebnis siehe RESULT_PLATTFORMSIGNATUR_2026-07-29.md. ADR-0005 bleibt offen bis CEO-Entscheid.
```
Push. **Dann STOPP.** Keine weiteren Geräte, kein Merge, kein Publish.

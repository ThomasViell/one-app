# Testplan BETA On-Device — drainq.one

**Gerät:** ONE (Serial `233b4bd2865177ed`) · **Stand:** Branch `feature/beta-wave-1` (`48c454f`) · **Datum:** 2026-06-06
**Zweck:** 1:1-Abnahme am Objekt. Jeder Test wird im Chat gemeldet; Korrekturen werden sofort festgelegt oder gesammelt in Welle 2 behoben.

## Meldeformat (Chat)

- `T05 OK`
- `T07 FEHLER: <was passiert ist>` — wenn möglich + Foto/Screenshot und Logcat-Auszug (Tag steht je Test)
- `T12 ÜBERSPRUNGEN: <Grund>`
- Gern blockweise melden (z. B. „Block A: T01–T07 OK, T08 FEHLER: …").

## Material / Voraussetzungen

- ONE-Gerät geladen, beide Kameraköpfe **C10 und C18** zur Hand
- Sonden-**Ortungsempfänger** (für T07)
- PC mit adb-Verbindung zur ONE (USB), PowerShell
- Etwas Schiebestrecke für Meterzähler (T05)
- **WICHTIG:** Block F (Device-Owner/Kiosk) erst ganz am Ende — Device-Owner lässt sich ohne Factory-Reset praktisch nicht entfernen.

## Vorbereitung (einmalig)

```powershell
cd C:\Projekte\drainq.one
git fetch origin; git checkout feature/beta-wave-1; git pull
$env:JAVA_HOME="C:\Android\jdk17"; $env:ANDROID_SERIAL="233b4bd2865177ed"
.\gradlew.bat installDebug
```

Logcat-Fenster parallel offen halten:

```powershell
adb -s 233b4bd2865177ed logcat -s OneInternalHW
```

---

## Block A — Inbetriebnahme & Hardware-Basis

### T01 — App-Start
**Schritte:** App starten. Splash → Home.
**Erwartet:** Kein Crash, Home-Screen im SA-Design (Amber), Navi-Leiste mit DrainQ-Bildmarke.

### T02 — Live-Bild & Endpunkte (V3)
**Schritte:** Inspektion öffnen (ohne Projekt). Logcat beobachten.
**Erwartet:** Live-Bild vom Kamerakopf; Logcat zeigt `probeEndpoints: video=true serial=true`.
**Bei Fehler:** Logcat-Zeilen `probeEndpoints` + `TX failed` melden.

### T03 — Akku-Chip
**Schritte:** Akku-Chip oben in der Inspektion ablesen, mit Android-Systemanzeige (Statusleiste/Einstellungen) vergleichen.
**Erwartet:** Gleicher Prozentwert (Quelle = System-Akku).

### T04 — Kamerakopf-Erkennung C10/C18 (V4)
**Schritte:** Mit C10 starten → Chip ablesen. Gerät aus, Kopf auf C18 wechseln, neu starten → Chip ablesen.
**Erwartet:** Chip zeigt jeweils korrekt **C10** bzw. **C18** (Detektion, ohne manuelles Dropdown).
**Bei Fehler:** Logcat `RX grp=23` Zeilen (payload[4]-Wert) melden.

### T05 — Meterzähler + Nullung
**Schritte:** Kamera ca. 2–3 m schieben, Anzeige beobachten; dann Meter-0 auslösen.
**Erwartet:** Zähler läuft plausibel mit (Richtung + Größenordnung), Nullung setzt sofort auf 0.00.

### T06 — Licht −/+
**Schritte:** Licht über die −/+ Tasten in mehreren Stufen ändern.
**Erwartet:** Sichtbare Helligkeitsänderung am Kopf, keine UI-Hänger.

### T07 — Sonde an/aus + Frequenz physisch (V2)
**Schritte:** Sonde einschalten, nacheinander 512 Hz / 640 Hz / 33 kHz wählen; mit dem Ortungsempfänger jede Frequenz orten; OSD-Chip ablesen.
**Erwartet:** **Gewählte = geortete = angezeigte** Frequenz (TX/RX-Inversion ist gefixt — genau das hier bestätigen). Sonde aus = kein Signal mehr.
**Bei Fehler:** Welche Kombination abweicht (gewählt/geortet/angezeigt) genau notieren.

### T08 — Hardtasten F1–F8 (V1)
**Schritte:** In der Inspektion jede physische Taste unter dem Display einmal drücken.
**Erwartet:** Jede Taste löst die Aktion ihres positionsgleichen Softbuttons aus (Foto, Aufnahme, Schaden, …).
**Bei Fehler:** Melden, welche Tasten tot sind — wir entscheiden dann, ob der serielle btn-Byte-Pfad gebaut werden muss.

---

## Block B — Erfassung & Workflow

### T09 — Schnellaufnahme ohne Projekt
**Schritte:** Ohne Projekt in der Inspektion: 1 Foto auslösen, dann noch 1 Foto.
**Erwartet:** Kein stummes Nichtstun; es entsteht **ein** Projekt „Schnellaufnahme_<Datum>", beide Fotos liegen darin (nicht zwei Buckets).

### T10 — Foto sofort nach Einstieg (0-Byte-Falle)
**Schritte:** Inspektion frisch öffnen und **sofort** (unter 1 s) Foto auslösen. Danach Foto in der Galerie öffnen.
**Erwartet:** Entweder gültiges Bild oder klare Fehlermeldung — **kein** Erfolgs-Blitz mit leerer/0-Byte-Datei.

### T11 — Projekt anlegen + Tastatur (Feldtest #4)
**Schritte:** Neues Projekt über Formular anlegen; in jedem Textfeld: Eingabe → „Fertig"-Taste der Tastatur; auch einmal außerhalb tippen.
**Erwartet:** Tastatur fährt **immer** zuverlässig ein (Done, Tap-außerhalb, Dialog-Schließen); GPS/Wetter-Felder ohne Hänger; Speichern legt das Projekt an.

### T12 — Schaden erfassen (DamageDialog)
**Schritte:** In Projekt-Inspektion Schaden anlegen: Code wählen, Freitext eingeben, Tastatur via Done schließen, speichern.
**Erwartet:** Tastatur schließt hart (Quick-Fix `7c0aa2a`), Dialog nicht von Tastatur verdeckt (ADJUST_RESIZE), Schaden erscheint in der Liste.

### T13 — Notiz mit Audio (NoteDialog)
**Schritte:** Notiz anlegen, Audio-Aufnahme starten (erste Nutzung → Permission-Dialog), stoppen, speichern.
**Erwartet:** Permission-Flow sauber, Audio gespeichert und abspielbar, Tastatur-Verhalten ok.

### T14 — Video-Aufnahme HD (lokal)
**Schritte:** In HD-Projekt Aufnahme starten, ~20 s schieben, stoppen. Videos-Tab öffnen, abspielen.
**Erwartet:** MP4 vorhanden, abspielbar, Bild flüssig, Datei bleibt nach App-Neustart erhalten.

### T15 — SD-Aufnahme 720×576 (V9, neu AP3)
**Schritte:** Projekt mit **SD** anlegen, ~10 s aufnehmen. Datei prüfen (am PC: `adb pull` oder Eigenschaften im Player).
**Erwartet:** Video ist **720×576**; T14-HD-Video dagegen native Auflösung.

### T16 — OSD-Burn-in Lokal-Aufnahme (V10, neu AP3)
**Schritte:** Einmal „Mit Overlay" aufnehmen, einmal „Ohne Overlay". Beide MP4s abspielen.
**Erwartet:** „Mit" = Distanz/Datum/Feststellungs-Flash **im Video eingebrannt**; „Ohne" = rohes Bild.

### T17 — Hardware-OSD-Schalter (V11, neu AP3)
**Schritte:** Einstellungen → Hardware-OSD **AN** → kurze Aufnahme → abspielen. Danach wieder AUS.
**Erwartet:** App brennt **kein** Software-OSD mehr ein; was die ONE-Hardware selbst einblendet, hier notieren (Verhalten dokumentieren wir als Referenz).

### T18 — Galerie, Wiederfinden & Annotation
**Schritte:** ProjektDetail öffnen: Tabs Fotos/Schäden/Videos/Notizen durchsehen; ein Foto im **Bearbeiten-Dialog** öffnen und per Doppeltipp annotieren (Pfeil/Kreis), speichern.
**Erwartet:** Alle Artefakte aus T09–T16 auffindbar; Annotation aus dem Bearbeiten-Dialog funktioniert (Quick-Fix `359a05a`) und bleibt gespeichert.

### T19 — Navigation/Zurück
**Schritte:** Projektliste → Projekt → Inspektion → Zurück-Pfeil → ProjektDetail → Zurück → Liste. Auch Bottom-Nav/Rail quer durchklicken.
**Erwartet:** Keine Sackgasse, Zurück aus der Inspektion vorhanden und landet richtig.

---

## Block C — Lifecycle & Stabilität

### T20 — Background/Re-Init (V7, neu AP8)
**Schritte:** In laufender Inspektion: Recents → andere App → zurück zu DrainQ.ONE. 3× wiederholen.
**Erwartet:** Live-Bild und Telemetrie (Meter/Akku/Kopf-Chip) kommen **jedes Mal** zurück, kein schwarzes Bild, kein Hänger.

### T21 — Datenpersistenz nach Neustart
**Schritte:** App komplett beenden (Recents wegwischen), neu starten. Projekte/Fotos/Videos prüfen.
**Erwartet:** Alles vorhanden — **kein** Datenverlust (M4: destruktiver Fallback ist raus).

---

## Block D — Export & Berichte

### T22 — Export PDF+XML als ZIP (V12)
**Schritte:** Projekt mit Fotos/Schäden/Video: Export „PDF" mit Option „XML einschließen" → Teilen → ZIP am PC öffnen. Zusätzlich einmal das **Schnellaufnahme**-Projekt exportieren.
**Erwartet:** ZIP enthält PDF **und** XML; XML trägt strukturierte Schadensfelder (mainCode, Uhrzeit, Quantifizierung) und **keine** „DIN EN 13508-2"-Behauptung mehr (Kennzeichnung „DrainQ-XML"); alle Fotos/Videos/Audios im ZIP; Dateinamen beim Schnellaufnahme-Projekt nicht leer („Bericht_<id>…", Quick-Fix `186464d`).

### T23 — Berichte-Übersicht (M11, neu AP10)
**Schritte:** Einstellungen → Berichte (neuer Zugang) öffnen.
**Erwartet:** Alle erzeugten PDFs gerätweit gelistet, Öffnen + Teilen funktioniert; CTA führt nicht mehr ins Leere.

### T24 — Verbindungs-Screen (M10, neu AP10)
**Schritte:** Einstellungen → Verbindung öffnen, Anzeige prüfen.
**Erwartet:** Screen erreichbar, zeigt sinnvollen ONE-Status (keine Pseudo-URL `rtsp://local:8554/1234` mehr); kein Crash.

### T25 — Löschdialog lokalisiert (M12)
**Schritte:** App-Sprache auf **Englisch** stellen → Projekt löschen wählen (Dialog lesen, dann **Abbrechen**!) → Sprache zurück auf Deutsch.
**Erwartet:** Datenverlust-Warnung komplett englisch (keine deutschen Hardcodes).

---

## Block E — Self-Update

### T26 — Ehrliche Update-Anzeige ohne Release (M15) — VOR dem Release-Publish
**Schritte:** Einstellungen → „Nach Updates suchen".
**Erwartet:** Ehrliche Meldung (Update-Quelle nicht erreichbar/nicht konfiguriert) — **nicht** „App ist aktuell".

### T27 — Self-Update komplett (V5) — NACH dem Release-Publish
**Vorbereitung:** GitHub-Release nach `docs/RELEASE_PUBLISHING.md` publizieren (APK `versionCode > 3` + `releases.stable.json`).
**Schritte:** „Nach Updates suchen" → Update installieren.
**Erwartet:** Update gefunden, Download läuft, **System-Installdialog erscheint** (B3-Receiver), App startet in neuer Version.

---

## Block F — Kiosk & Provisionierung (ZULETZT — nicht rückbaubar ohne Factory-Reset)

> Voraussetzung für Device-Owner: **kein** Google-/Nutzerkonto auf dem Gerät eingerichtet.

### T28 — Device-Owner setzen
**Schritte:**
```powershell
adb -s 233b4bd2865177ed shell dpm set-device-owner com.uip.drainq.one/.bootstrap.OneDeviceAdminReceiver
```
**Erwartet:** „Success: Device owner set…".
**Bei Fehler:** Genaue Fehlermeldung melden (häufig: vorhandene Accounts).

### T29 — Kiosk Hard-Lock (V6, neu AP2)
**Schritte:** Kiosk-Schalter AN. Dann nacheinander: Home-Geste/-Taste, Recents, Wischen von oben/unten. Kiosk wieder AUS → erneut versuchen.
**Erwartet:** Mit Kiosk AN **kein** Verlassen der App möglich (LockTask aktiv); mit AUS verhält sich das Gerät normal.

### T30 — HOME-Boot (V8, neu AP2)
**Schritte:** Beim Home-Dialog DrainQ.ONE als Standard-Launcher wählen (oder per Kiosk AN), Gerät **rebooten**.
**Erwartet:** ONE bootet direkt in die App, Inspektion danach voll funktionsfähig (Serial/Video init nach Boot ok).

---

## Nach dem Durchlauf

Ergebnis je Test im Chat melden. Dann entscheiden wir pro FEHLER: Sofort-Fix (kleiner CC-Lauf) oder Sammel-Korrekturlauf „BETA-Welle 2" mit allen Befunden. Danach: PR #3 Review/Merge → `master` → Tag `v0.4.0`.

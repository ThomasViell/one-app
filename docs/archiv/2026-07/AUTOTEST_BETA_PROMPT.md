# AUTOTEST-AUFTRAG: BETA-Welle 1 + 2 vollautomatisch am Gerät testen

**Rolle:** Du bist Test-Automatisierer für DrainQ.ONE. Du testest ALLE Änderungen der
letzten zwei Tage (BETA-Welle 1 vom 06.06. + BETA-Welle 2 vom 07.06.) direkt am
angeschlossenen Gerät — vollständig selbstständig, ohne Rückfragen an den Benutzer,
außer am Anfang (Vorbedingungen) und wenn etwas physisch unmöglich ist.

**Gerät:** ONE-Hardware, `$env:ANDROID_SERIAL="233b4bd2865177ed"`, per USB verbunden.
**Paket:** `com.uip.drainq.one` (Debug-Build, bereits installiert). Activity: `com.uip.oneapp.MainActivity`.
**Hardtasten per adb:** `adb shell input keyevent <code>` — 131=Licht, 132=Sonde,
133=Aufnahme/Pause-Toggle, 134=Stop, 135=Foto, 136=Galerie, 137=UNBELEGT (entfernt!), 138=Einstellungen.
Diese Codes funktionieren nur, wenn die Inspektions-Ansicht offen ist.
**Ergebnisse:** alles nach `tools\_autotest\` (Screenshots, Dateien, Logs).
**Bericht:** `TESTREPORT_BETA_AUTOTEST.md` im Repo-Root (Format siehe unten).

## Arbeitsweise (WICHTIG)

1. **Adaptiv, nicht stur:** UI-Knöpfe NIE über feste Koordinaten. Immer erst
   `adb shell uiautomator dump` + XML lesen, Text/content-desc suchen, Bounds-Mitte antippen.
   Compose-Texte stehen teils in `text=`, teils in `content-desc=`.
2. **Beweise sammeln:** Vor/nach jedem Test Screenshot (`adb shell screencap -p /sdcard/Download/x.png`
   + `adb pull`). Du kannst PNG-Dateien selbst ansehen (Read) — nutze das zur Verifikation.
3. **Logcat als Wahrheit:** vor jedem Block `adb logcat -c`, nach dem Block
   `adb logcat -d` auswerten. App-Tags: `InspectionScreen`, `LocalBitmapRecorder`,
   `OneInternalHW`, `FfmpegRtspRecorder`, `MainActivity`, `UsbExportService`.
4. **Bei Fehlschlag:** 2× anders probieren (anderer Text, kurz warten, Screen prüfen),
   dann als ROT mit Diagnose (Screenshot + Logauszug + Deine Vermutung) in den Report — NICHT hängenbleiben.
5. **Gerät nicht zerstören:** KEIN Factory-Reset, KEIN `dpm set-device-owner`,
   KEINE App-Deinstallation, KEINE Daten löschen (bestehende Projekte sind Testbestand!).
6. Wische zwischen Tests die App nie weg; navigiere in der App (Zurück-Pfeil oben links,
   Reiter unten/links: Home, Inspektion, Projekte, Einstellungen).

## Vorbedingungen (einmalig prüfen, dann loslegen)

- `adb devices` → Gerät da. App im Vordergrund? Sonst per monkey/LAUNCHER starten,
  Splash mit „Weiter"-Knopf wegtippen, evtl. „App is pinned"-Hinweis mit GOT IT bestätigen.
- `adb shell appops set com.uip.drainq.one MANAGE_EXTERNAL_STORAGE allow` ausführen
  (schaltet den USB-Export-Test frei, ohne Einstellungs-Dialog).
- Falls ein USB-Stick im Gerät steckt: Test 12 komplett fahren. Wenn nicht: Test 12
  nur bis zur „Kein USB-Stick erkannt"-Anzeige prüfen und als TEILWEISE markieren.

## OFFENER BEFUND AUS DEM VORLAUF (zuerst klären!)

**T0 — Aufnahme-Dialog öffnet nicht?** Im starren Skript-Lauf öffnete `keyevent 133`
in der Inspektion KEINEN „Aufnahme starten"-Dialog (Screenshots ohne Dialog, keine
Recorder-Logs), während `keyevent 135` (Foto) nachweislich funktionierte.
Kläre das ZUERST: Inspektion öffnen, 5 s warten (Schnellaufnahme-Projekt + Video-Quelle
müssen initialisiert sein), `adb logcat -c`, dann `keyevent 133`, 2 s, ui-dump + Screenshot
+ Logcat. Wenn kein Dialog: prüfe im Dump, ob die untere Softbutton-Leiste sichtbar ist;
tippe alternativ den Aufnahme-Softbutton direkt an (Video-Mitte antippen blendet die
Leiste ein; Knopf heißt „Aufnahme"). Dokumentiere exakt, was den Dialog öffnet und was
nicht — das entscheidet, ob es ein echter Bug (Hardtaste) oder ein Skript-Artefakt war.
Falls Bug: Ursache im Code suchen (`runHwButton` in
`app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt`), fixen,
`.\gradlew installDebug`, erneut testen. Build-Umgebung: `$env:JAVA_HOME="C:\Android\jdk17"`.

## TESTLISTE

### Block A — Welle 2 (neu von heute)

**T1 Aufnahme komplett (über den geklärten Weg aus T0):**
Aufnahme starten → Dialogknopf „Mit Overlay" → 10 s aufnehmen → 133 (Pause) →
Screenshot: Chip oben rechts muss „PAUSE" (gelb/amber) zeigen, Timer steht →
5 s warten → 133 (Weiter) → Chip wieder „REC", Timer läuft weiter → 8 s → 134 (Stop).
ERFOLG: genau EINE neue MP4 unter
`/sdcard/Android/data/com.uip.drainq.one/files/recordings/project_<neuestes>/`,
Größe > 100 KB. MP4 nach `tools\_autotest\t1_pause_aufnahme.mp4` ziehen.
Bonus-Beweis: Dauer der MP4 ≈ 18 s (10+8, NICHT 23) — die Pausenzeit darf nicht im Video sein.
(Dauer ermitteln: falls ffprobe auf dem PC fehlt, Datei einfach mitnehmen — wird extern geprüft.)

**T2 OSD eingebrannt + Unicode:** Während einer LAUFENDEN Aufnahme (oder neuer kurzer
Aufnahme „Mit Overlay"): Schaden anlegen — Video-Mitte tippen (Leiste einblenden) →
„Schaden"-Kachel → im Dialog: Schadenstyp wählen, Beschreibung exakt
`Wurzeleinwuchs übermäßig ÄÖÜß è ł` eintippen (`adb shell input text` kann keine
Umlaute — über uiautomator aufs Feld tippen und mit
`adb shell am broadcast -a ADB_INPUT_TEXT` geht NICHT; nutze stattdessen die
Zwischenablage NICHT, sondern tippe per `input text 'Wurzeleinwuchs'` den ASCII-Teil
und prüfe Umlaute stattdessen über einen vorhandenen Preset-Typ mit Umlaut, falls
vorhanden — sonst Unicode-Teil als MANUELL markieren) → Speichern.
Danach neuestes Schadensfoto aus `files/damages/project_<id>/` ziehen
(`t2_schadenfoto.jpg`) und ANSEHEN: OSD-Zeilen oben/unten + gelber Schadens-Flash
müssen im Bild eingebrannt sein, Text lesbar (keine „?"-Zeichen).

**T3 F7 entfernt:** In der Inspektion Video-Mitte tippen → untere Leiste sichtbar →
ui-dump: Es darf KEINE „Tag/Nacht"-Kachel geben (8 Kacheln: Power, Licht, Sonde,
Aufnahme, Stop, Foto, Galerie, Einstellungen). `keyevent 137` senden → nichts darf
passieren (Screenshot vorher/nachher identischer Zustand, kein Crash im Logcat).

**T4 HW-OSD-Schalter weg:** Rechtes Panel öffnen (Video-Mitte tippen; Panel rechts) →
ui-dump: kein „Hardware-OSD"-Schalter. In Einstellungen → OSD-Bereich aufklappen →
kein „Hardware-OSD", kein „Neigung/Sonde zeigen" — nur Meterwert + Datum + Darstellung.

**T5 Helligkeits-Slider:** Einstellungen → „Bildschirmhelligkeit": Schalter auf manuell →
Slider erscheint → auf ~20 % ziehen → Screenshot → auf ~100 % → Screenshot →
die beiden Screenshots müssen sichtbar unterschiedlich hell sein (ansehen!).
Danach Schalter zurück auf Automatisch.

**T6 XML restlos weg:** Projekte → ein Projekt MIT Schäden öffnen (z. B. Schnellaufnahme
vom 02.06.) → „Archiv"-Aktion → Export-Dialog: ui-dump darf KEINE XML-Option zeigen
(nur Fotos einschließen + Karte einschließen) → Export starten → fertig warten →
neueste ZIP aus `files/exports/` ziehen (`t6_export.zip`) → entpacken: Es darf KEINE
.xml-Datei enthalten sein; PDF + fotos/ müssen drin sein.

**T7 PDF-Bericht:** Im selben Projekt „PDF" → Export → neueste PDF aus
`files/reports/project_<id>/` ziehen (`t7_bericht.pdf`). Datei > 20 KB = OK.

**T8 USB-Export (nur wenn Stick steckt):** Projekt → „USB"-Aktion → Dialog muss Stick
anzeigen (nicht „Kein USB-Stick erkannt", appops wurde ja gesetzt) → „Komplettes
Projekt" → Export starten → Erfolgsmeldung mit Zielpfad → per
`adb shell ls -R /storage/<STICK-ID>/DrainQ/` verifizieren, dass Dateien da sind.
Stick-ID findest du über `adb shell ls /storage/` (8-stellig mit Bindestrich).

### Block B — Welle 1 vom 06.06. (On-Device-Nachweise)

**T9 Sonde-Frequenz (V2-Teil):** Inspektion → `logcat -c` → `keyevent 132` → Popup
erscheint (Screenshot) → per ui-dump „512 Hz" antippen → Logcat: `OneInternalHW`-TX-Zeile
mit gesetzter Frequenz vorhanden. Danach Popup mit „Aus" schließen.

**T10 Licht (TX):** `logcat -c` → `keyevent 131` → Logcat: TX-Zeile mit Licht-Wert 30
(Zyklus 0→30) → nochmal 131 → Wert 60 → dann 2× weiter bis wieder 0.

**T11 Meter-Reset:** Rechtes Panel → „Absolut → 0" und „Strecke → 0" antippen →
kein Crash, Meter zeigt 0.00 (Kabel steckt evtl. nicht — Wert bleibt dann 0.00, auch OK).

**T12 Lifecycle/Hintergrund (V7):** `keyevent 3` (Home) — ACHTUNG: Wenn die App
gepinnt ist, passiert nichts (auch ein gültiges Ergebnis: Pinning hält = Kiosk wirkt!).
Falls Home durchgeht: 5 s warten → App wieder in den Vordergrund
(`monkey -p com.uip.drainq.one 1`) → Inspektion: Live-Bild muss wieder laufen
(Logcat: neues `probeEndpoints`/`startPolling`). Screenshot.

**T13 Kamerakopf-Chip:** Inspektion-Screenshot oben rechts: Chip „C10" oder „C18"
sichtbar (seriell erkannt) — Logcat `grp=23`-Zeilen laufen (haben wir schon gesehen,
nur bestätigen).

**T14 Crash-Sweep:** Am Ende einmal durch alle Screens navigieren (Home, Projekte,
Projekt-Detail mit allen 4 Reitern, Einstellungen inkl. Unterseiten Netzwerk/Berichte/
Offline-Karten/Verbindung, zurück) → `logcat -d` → 0 × „FATAL EXCEPTION".

### NICHT testen (bewusst)

- Self-Update (kein GitHub-Release publiziert — bekannt offen)
- Device-Owner/echter Kiosk-Lock (Provisionierung kommt später; Pinning-Verhalten aus T12 reicht)
- Mikrofon/Audio (physischer Test — macht der Benutzer mit `tools\check-microphone.ps1`)
- Sonde orten, Lichtwirkung visuell, Meterzähler mit Kabelbewegung, Kopf umstecken (physisch)

## Berichtsformat (`TESTREPORT_BETA_AUTOTEST.md`)

Pro Test eine Zeile in einer Tabelle: Nr | Test | GRÜN/ROT/TEILWEISE/ÜBERSPRUNGEN |
Beweis (Dateiname/Logzeile) | Anmerkung. Darunter: Abschnitt „Rote Befunde im Detail"
(je: Symptom, Screenshot, Logauszug, Code-Verdacht, ggf. durchgeführter Fix + Retest)
und Abschnitt „Für den Benutzer verbleibende manuelle Tests" (kurze Checkliste).
Am Ende: Einzeiler-Fazit „BETA-tauglich: ja/nein/mit Einschränkungen".

Wenn Du in T0 einen echten Bug gefunden und gefixt hast: Fix als eigenen Commit
(`fix(beta): ...`) auf dem aktuellen Branch committen — sonst NICHTS committen.

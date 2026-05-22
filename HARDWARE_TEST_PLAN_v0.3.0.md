# Hardware-Test-Plan v0.3.0 — NSP3CT ONE + TWO

## Ziel
APK v0.3.0 auf echtem NSP3CT-Inspektionssystem validieren — vor allem die Punkte, die im Labor-Build nicht beweisbar waren: zwei parallele RTSP-Sessions (R3), Live-FPS auf realer Hardware, OSD-Burn-In im MP4 mit echter Kamera, Recording-Dauer-Stabilitaet.

## Voraussetzungen
- DrainQ_ONE_v0.3.0.apk auf Android-Tablet installiert
- NSP3CT ONE Inspektionsfahrwagen betriebsbereit
- NSP3CT TWO Inspektionsfahrwagen betriebsbereit
- Mind. 8 GB freier Speicher auf Tablet
- Stoppuhr / Kamera fuer FPS-Beobachtung
- Spuelwasser / Test-Rohrabschnitt fuer realen Stream-Inhalt (nicht zwingend, aber besser als statischer Hintergrund)

## Test-Matrix

### A. NSP3CT ONE

| # | Test | Erwartung | Pass/Fail | Notiz |
|---|---|---|---|---|
| A1 | App-Installation aus APK | App startet, Splash sichtbar | | |
| A2 | Geraete-Discovery (UDP 8555) | ONE-Crawler erscheint in Connection-Screen | | |
| A3 | TCP-Connect (Port 12345) | Hardware-Status (Licht/Sonde/Meter) live in UI | | |
| A4 | Live-Stream RTSP | Video laeuft, sichtbares Stocken < 1x pro Minute | | |
| A5 | FPS subjektiv | Fluessig, geschaetzt 20-25 fps | | |
| A6 | OSD-Overlay Top-Bar | Projekt + Auftraggeber sichtbar oben | | |
| A7 | OSD-Overlay Bottom-Bar | Meter + Datum + Sondenfrequenz sichtbar unten | | |
| A8 | Meterstand-Update | Beim Vorfahren aendert sich der OSD-Meterwert live | | |
| A9 | Schaden anlegen | findingFlash gelb 5s sichtbar, dann weg | | |
| A10 | Quick-Photo | JPEG hat OSD eingebrannt (am Tablet pruefen) | | |
| A11 | Recording 60 s starten | rotes REC-Indicator blinkt, MP4 wird angelegt | | |
| A12 | OSD im Recording | MP4 abspielen: OSD pixelgebrannt sichtbar | | |
| A13 | Meterstand im Recording | OSD-Meter aendert sich im MP4 mit Fahrtbewegung | | |
| A14 | Recording-Dauer 5 min | MP4 ohne Audio-Sync-Drift, ohne Abbruch | | |
| A15 | App-Restart unter Last | App schliessen und neu oeffnen, Stream kommt wieder | | |
| A16 | PDF-Export | Report mit Foto + OSD im Bild erzeugbar | | |

### B. NSP3CT TWO (kritisch fuer R3)

| # | Test | Erwartung | Pass/Fail | Notiz |
|---|---|---|---|---|
| B1 | HTTP CGI Auth | App connected gegen 172.169.10.65:80 (admin:bmw12345) | | |
| B2 | Live-Stream RTSP 554 | Video laeuft stabil | | |
| B3 | OSD-Overlay sichtbar | wie A6 + A7 | | |
| B4 | **R3-Verifikation: Display + Recording gleichzeitig** | Recording starten waehrend Live-View laeuft: BEIDE bleiben stabil | | **Schluesseltest** |
| B5 | R3-Fail-Verhalten | Falls B4 fail: bricht Display ab? Recording-Crash? Was sieht User? | | |
| B6 | OSD im Recording (MP4) | Wie A12 | | |
| B7 | Meterstand im Recording | Wie A13 — aber TWO hat keinen lokalen Meter (MQTT erst Phase 2 Roadmap) | | OSD ohne Meter, nur Datum + Frequenz |
| B8 | PTZ Steuerung waehrend Aufnahme | Zoom/Tele/Wide blockiert nicht den Recording-Thread | | |

### C. Negativ-Tests

| # | Test | Erwartung | Pass/Fail |
|---|---|---|---|
| C1 | RTSP-URL falsch | Fehlermeldung im UI, kein App-Crash | |
| C2 | Crawler waehrend Aufnahme abziehen | Recording schliesst MP4 sauber ab, kein Datenverlust | |
| C3 | Tablet sleep waehrend Recording | Recording laeuft weiter oder schliesst sauber ab | |
| C4 | Storage full | Sauberer Fehler, App stabil | |

## R3-Entscheidungsbaum

Nach Test B4:

- **B4 = PASS:** v0.3.0 ist fuer NSP3CT TWO freigegeben. Architektur (zwei RTSP-Sessions) bestaetigt. Phase 2 ADR-Annahme verifiziert.
- **B4 = FAIL:** Recording schaltet bei TWO automatisch in den Modus "Display pausieren waehrend Aufnahme" — muss in Code nachgerueistet werden. Aufwand: 2-3 Tage. Bis dahin v0.3.0 nur fuer NSP3CT ONE freigegeben.

## Akzeptanz fuer v0.3.0-Release

Mindestens:
- Alle A-Tests PASS auf NSP3CT ONE
- B1, B2, B3, B6 PASS auf NSP3CT TWO
- B4 entweder PASS oder klar dokumentiertes Workaround-Verhalten
- Keine C-Test-Crashs

## Ergebnis-Erfassung

Pro Test:
- Datum, Tester, Geraete-Seriennummer
- Pass / Fail / Workaround
- Falls Fail: Screenshot oder kurzer Video-Clip + APK-Logcat-Auszug (`adb logcat -d > log.txt`)

Ergebnisse in `RESULT_HARDWARE_TEST_v0.3.0.md` im Repo-Root sammeln.

## Logcat-Helper

Auf dem Test-PC mit adb am Tablet:
```powershell
# Live-Log filtert auf App
adb logcat | Select-String "uip.oneapp|FfmpegRtspRecorder|OsdRenderer|VideoPlayer"

# Snapshot vor / nach Test
adb logcat -d > pre_test.log
# Test ausfuehren ...
adb logcat -d > post_test.log
```

## Aufgabenverteilung Vorschlag

- **Du:** Geraete bereitstellen, Tests A1-A8, B1-B4 ausfuehren (UI- und Funktions-Sicht)
- **Entwickler / Werkstatt:** A9-A16, B5-B8, C1-C4 (technische Tiefe)
- **Ich (Claude):** Auswertung der Ergebnis-MD, Bug-Triage, Patches falls Test fail

# SZENARIEN — Welle geraetezeit (11.09.2026)

Erfolgskriterien aus `AUFTRAG.md`: Z-1 eigene Seite fuer Datum, Uhrzeit und
Zeitzone in der App (Kiosk bleibt zu); Z-2 ein blockierter Sprung in eine
Systemeinstellung meldet sich sichtbar. Je Szenario: Vorbedingung, Handlung,
Messbefehl, Erwartung.

**Geraet: keines in dieser Runde** (CEO-Entscheid 11.09.2026, Nachtrag 2).
Der Klickdurchgang wandert an Louis: `C:\Projekte\_ketten\geraetezeit\KLICKDURCHGANG_LOUIS.md`
(fuenf Faelle ohne Werkzeuge, mit Fotobitte). Die Geraetefaelle G-1 bis G-5
sind hier als **nicht hergestellt** benannt, nicht hergeleitet; was am
Schreibtisch belegbar ist, steht als JUnit-Referenz dabei.

## Szenario 1 (G-1) — Zeitzone UTC → Europe/Berlin, Export zeigt Ortszeit

- Vorbedingung: Geraet mit dieser Welle (plattformsignierter Bau,
  `uid=1000`), Geraet auf UTC, Kiosk aktiv.
- Handlung: Einstellungen → „Datum & Uhrzeit stellen" → Seite erscheint
  **in der App** → Zeitzone `Europe/Berlin` waehlen → „Uebernehmen".
- Messbefehl: `adb shell getprop persist.sys.timezone` (Erwartung
  `Europe/Berlin`); danach eine Aufnahme um hh:mm starten und per USB
  exportieren, `ls` des Stickordners.
- Erwartung: Snackbar „Uhrzeit und Zeitzone sind gesetzt."; Dateiname
  traegt `_hhmmss_` in Ortszeit, nicht UTC−2 — die Gegenprobe, dass
  `UsbExportNames.kt` unangetastet richtig ist.
- Louis: Klickdurchgang Fall 3 (Zeitzone) und Fall 4 (Dateiname).

## Szenario 2 (G-2) — Uhr von 2021 auf heute

- Vorbedingung: Geraeteuhr auf 2021 zurueckgefallen (stromlos) oder
  `adb shell date 010100002021.00`.
- Handlung: App starten → Projektformular oeffnen (Warnung
  „Geraeteuhr steht falsch" erscheint) → ueber Einstellungen → „Datum &
  Uhrzeit stellen" Datum und Zeit setzen → Formular erneut oeffnen.
- Messbefehl: `adb shell date`; Screenshot des Formulars vorher/nachher.
- Erwartung: Warnung ist weg; ein neues Projekt bekommt das richtige
  Datum. Das Datumsfeld der neuen Seite ist in diesem Zustand **nicht
  vorbelegt** (E-3: Vorbelegung nur, wenn die Geraeteuhr plausibel ist).
- Louis: Klickdurchgang Fall 2 (Uhr stellen) und Fall 5 (Knopf unter der
  Warnung).

## Szenario 3 (G-3) — Kiosk bleibt zu

- Vorbedingung: wie G-1.
- Handlung: der komplette Weg aus G-1.
- Messbefehl: `dumpsys activity activities | grep topResumedActivity`
  waehrenddessen.
- Erwartung: zu keinem Zeitpunkt wechselt der Vordergrund zu
  `com.android.settings` — die neue Seite ist ein App-eigener Bildschirm
  (Route `datetime`), keine LockTask-Ausnahme (CEO-Entscheid Weg A).

## Szenario 4 (G-4) — Bestand nach Neustart

- Vorbedingung: Geraet mit gesetzter Zone (nach G-1).
- Handlung: `adb reboot`, danach pruefen.
- Messbefehl: `adb shell getprop persist.sys.timezone` und `adb shell date`.
- Erwartung: Zone bleibt `Europe/Berlin`; die Uhr bleibt, solange das
  Geraet Strom hatte (RTC ohne Puffer ist Geraetethema, nicht Gegenstand).

## Szenario 5 (G-5) — Knopf im Projektformular (R-1 = JA)

- Vorbedingung: Geraet mit dieser Welle, Warnung sichtbar (Uhr falsch).
- Handlung: unter der Warnung „Geraeteuhr steht falsch" den Knopf druecken.
- Messbefehl: Screenshot.
- Erwartung: dieselbe neue Seite wie in Szenario 1 oeffnet sich — der
  Knopf navigiert jetzt auf `datetime` statt still in die gesperrte
  Android-Einstellung.
- Louis: Klickdurchgang Fall 5.

## Szenario 6 — Datum vor dem Baujahr wird abgewiesen (E-3)

- Vorbedingung: Seite offen, Datum z. B. 01.01.2021 gewaehlt.
- Handlung: „Uebernehmen".
- Messbefehl: JUnit
  `SystemTimeSetterTest.setDateTime_epochBeforePlausibleYear_invalidTime_andNoCall`;
  am Geraet Snackbar.
- Erwartung: Meldung „Datum vor {year} ist nicht zulaessig …", kein
  `setTime`-Aufruf (Fake belegt: kein Aufruf), die Uhr bleibt unangetastet.
  Der 2021-Fall ist genau der Fehler, den die Seite beheben soll — er wird
  nicht wieder herstellbar gemacht.

## Szenario 7 — Zurueck ohne „Uebernehmen" aendert nichts

- Vorbedingung: Seite offen, Werte geaendert, nichts uebernommen.
- Handlung: Zurueck-Pfeil.
- Messbefehl: `adb shell date`, `getprop persist.sys.timezone`.
- Erwartung: Systemuhr und Zone unveraendert — erst „Uebernehmen" ruft
  den Setter; die Auswahl lebt nur im Bildschirmzustand.

## Szenario 8 — Suchfeld und Tastatur der Zonenliste

- Vorbedingung: Seite offen, Zonenkarte.
- Handlung: Suchfeld „berl" tippen; Tastatur ueber KeyboardHideButton
  ausblenden; Eintrag waehlen.
- Messbefehl: JUnit `DateTimeScreenTest.filterZones_*` (Teilzeichenkette,
  gross/klein egal, leere Eingabe = volle Liste, Reihenfolge stabil);
  Sichtpruefung am Geraet.
- Erwartung: Liste zeigt `Europe/Berlin`; die Liste ist genau die vom
  Geraet gemeldete (`ZoneId.getAvailableZoneIds()`), sortiert, keine
  Laenderliste (E-2).

## Szenario 9 — Testsuite und Golden-Tor

- Vorbedingung: Bau abgeschlossen.
- Handlung: `./gradlew.bat :app:testDebugUnitTest`;
  `.\tools\manual\verify.ps1 -Langs "de,en"`.
- Messbefehl: JUnit-XML auszaehlen (nicht nur Exit-Code); Exit-Code von
  verify.ps1.
- Erwartung: Basislinie 506 + 39 neue Tests = 545, 0 Failures;
  verify.ps1 nach `-Update` der zwei freigegebenen Goldens
  `…scr09_settings_de….png` / `…_en….png` (R-4) Exit 0.

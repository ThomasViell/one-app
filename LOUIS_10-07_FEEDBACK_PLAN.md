# Fix-Plan — Louis-Feedback 0.5.5-beta (10.07.2026)

Quelle: Louis Wigman, Mail „AW: DrainQ.ONE 0.5.5-beta" vom 10.07.2026, 10:18.
Getestet: 0.5.5-beta / 505, Sprache Englisch, ONE am Objekt (mit Kabel/Meterzähler).
Branch-Basis: `feature/dual-mode` (`ec52c0b`), kein Merge, kein Tag.

Reihenfolge = Auslieferungs-Priorität. Confidence: [Sicher] Beleg im Code/Mail, [Wahrscheinlich] starke Herleitung, [Schätzung] Lücke gefüllt.

---

## Welle 1 — Blocker (vor JEDER weiteren Kundenauslieferung)

### B1 — Automatik-Datum kaputt: alle Reports auf 01.01.2021
- **Symptom (Louis):** Datum zurück auf 01.01.2021. „Auto"-Schalter im Menü wirkt nicht. Erst Neustart bringt korrektes Datum.
- **Ursache [Wahrscheinlich]:** `ProjectFormViewModel.kt` Z.~38 setzt `inspektionsdatum = LocalDate.now()` **einmalig bei Konstruktion**. Steht die Android-Systemuhr auf 2021 (RTC-Reset / kein NTP), wird 2021 eingefroren. Die App setzt die Systemzeit nie selbst; der Menü-Schalter ist ein No-Op, solange die App nicht Device-Owner ist (Louis' Gerät = kein Device-Owner). Neustart hilft nur, weil NTP bis dahin synchronisiert hat.
- **Fix:**
  1. Als Device-Owner beim Start `setAutoTimeEnabled(true)` + `setAutoTimeZoneEnabled(true)` setzen (analog `DeviceOwnerLocationProvisioner`, gleicher Gateway-Seam). Auf Nicht-Owner-Geräten graceful no-op.
  2. Plausibilitätsschutz im Formular: liegt `LocalDate.now()` vor z.B. 2015, Feld nicht still mit Falschdatum füllen, sondern Warnhinweis „Geräteuhr prüfen".
  3. Menü-Schalter „Auto" muss die Systemzeit-Automatik tatsächlich anstoßen (Device-Owner-Pfad), sonst aus dem UI nehmen — ein Schalter, der nichts tut, ist schlimmer als keiner.
- **Datei:** `ProjectFormViewModel.kt`, `bootstrap/DeviceOwnerLocationProvisioner.kt` (+ Gateway), Settings.
- **Aufwand:** M. **Risiko:** hängt am Device-Owner-Status → koppelt an Signatur-/Golden-Image-Entscheidung.

### B2 — Feldeingabe hängt weiter (Louis' #8, in W3 fälschlich als behoben gemeldet)
- **Symptom (Louis):** In 4 neuen Projekten bleibt die Eingabe bei „inspector"/„diameter" hängen; C18→C10-Wechsel schaltet die Felder frei, bleibt nach Rückwechsel erhalten. Egal ob WLAN an/aus.
- **Ursache [Wahrscheinlich]:** `ProjectFormScreen.kt` `dismissKeyboardOnScroll` gated auf `NestedScrollSource.UserInput` in der Annahme, `bringIntoView` melde `SideEffect`. Auf ONE/RK3588 + Compose 1.7 meldet das IME-Resize-/moveFocus-Auto-Scroll offenbar `UserInput` → `clearFocus()` reißt den gerade gesetzten Fokus ab. C18→C10 erzwingt Recompose und überdeckt das Symptom.
- **Fix [eindeutig]:** Scroll-zum-Schließen nicht mehr über `nestedScroll` + Quellenklassifizierung lösen (fragil, versionsabhängig). Stattdessen: die `nestedScroll(dismissKeyboardOnScroll)`-Verdrahtung entfernen und Tastatur-Schließen allein über (a) den bereits vorhandenen `KeyboardHideButton` in der TopBar und (b) `detectTapGestures` auf Freifläche halten. Kein automatisches `clearFocus` mehr während Scroll/Fokuswechsel.
- **Verifikation:** Pflicht am Gerät — 3 neue HD-Projekte, Durchmesser/Länge/Start/Ende direkt eintippen, ohne SD-Umweg, ohne Kopfwechsel. Unit-Test kann das NICHT beweisen (gerätespezifisches Scroll-Verhalten).
- **Datei:** `ProjectFormScreen.kt`.
- **Aufwand:** S. **Risiko:** niedrig (entfernt fragile Logik). Ohne Gerätetest kein Merge.

---

## Welle 2 — Korrektheit Report/Video (nächster Beta-Build)

### M1 — Video zweite Wiedergabe zu schnell
- **Symptom (Louis):** Erste Wiedergabe Echtzeit + Meterzähler eingeblendet (korrekt). Zweite Wiedergabe läuft schneller.
- **Ursache [Schätzung, Code-Prüfung nötig]:** Vermutlich wird die für den RTSP-Live-Pfad gedachte „Live-Speed-Catch-up"-Logik (PlaybackParameters > 1.0) auf die lokale Datei bei Replay angewandt, oder Decoder-/Timebase-State wird beim erneuten Play nicht zurückgesetzt.
- **Rechtlicher Kontext (Louis):** DK + Schweden verbieten Zeitraffer in Behörden-Reports (schwedische Pushrod-Norm). Für TWO Blocker, für ONE-Beta hoch.
- **Fix:** Live-Catch-up strikt auf RTSP-Quelle begrenzen; lokale Wiedergabe immer Speed 1.0, Player-State bei Replay sauber resetten.
- **Datei:** `FfmpegVideoPlayer` / `VideoPlaybackDialog.kt`. **Aufwand:** M. **Risiko:** mittel.

### M2 — Kameratyp fehlt im PDF-Report
- **Symptom:** Auto-Auswahl (C10/C18) in Quick Capture funktioniert, erscheint aber nicht im erzeugten PDF.
- **Ursache [Wahrscheinlich]:** Report-Renderer gibt `kameratyp` bei Quick-Capture-Projekten nicht aus.
- **Fix:** Feld in den Report aufnehmen.
- **Datei:** `export/ProjectExportService.kt`. **Aufwand:** S. **Risiko:** niedrig.

### M3 — „Schnellaufnahme" nicht übersetzt
- **Symptom:** In EN-Sprache erscheint „schnellaufnahme" wörtlich in Dateiname und Report statt „Quick capture".
- **Ursache [Wahrscheinlich]:** Hartkodierter deutscher Bucket-/Label-String im Quick-Capture-Pfad (Muster „Schnellaufnahme_ddMMyy").
- **Fix:** String über `S(...)`/L10n führen; Dateinamen-Präfix sprachneutral oder EN-fallback.
- **Datei:** Quick-Capture-Recorder + `LocalizationManager.kt`. **Aufwand:** S. **Risiko:** niedrig (Dateinamens-Schema mit Bestand abgleichen).

### M4 — Route-Feld ohne Pfeil zwischen Start/Ende
- **Symptom:** „Revision entrance house connection Kitchen" — Start und Ende kleben aneinander.
- **Gewünscht:** „Revision entrance house connection → Kitchen".
- **Fix:** Im Report Start-/Endpunkt mit „ → " verbinden.
- **Datei:** `export/ProjectExportService.kt`. **Aufwand:** S. **Risiko:** niedrig.

---

## Welle 3 — Hardbutton-Bedienung

### H1 — „Damages"-Hardbutton führt in Galerie statt Schaden-Dialog
- **Symptom:** Hardbutton Schaden öffnet Galerie-Übersicht; soll den Schaden-anlegen-Dialog öffnen.
- **Fix:** KeyCode-Handler auf Schaden-Dialog umbiegen.
- **Aufwand:** S. **Risiko:** niedrig.

### H2 — „Gallery overview"-Hardbutton tot
- **Symptom:** Keine Reaktion.
- **Fix:** KeyCode auf Galerie verdrahten (bzw. mit H1 tauschen).
- **Aufwand:** S.

### H3 — Licht/Sonde/Aufnahme-Hardbuttons können Werte nicht ändern
- **Symptom:** Licht-Hardbutton nur nach Ausblenden + zweitem Druck; Sonde-Button aktiviert nur Status, kein Wertwechsel, Statusbox nur per Touch schließbar; Aufnahme-Button öffnet nur die Mit/Ohne-Einblendung-Box, ohne Auswahl per Taste.
- **Ursache [Schätzung]:** Hardbuttons öffnen den jeweiligen Dialog, sind aber nicht mit dessen Werte-Zyklus/Bestätigung verbunden; kein Auto-Dismiss.
- **Fix:** Wiederholter Tastendruck zyklt Wert; Timeout-Dismiss der Statusbox.
- **Aufwand:** M. **Risiko:** mittel (Tastenbelegung am Gerät iterieren).
- **In Ordnung (Louis):** Hardbuttons Stop + Setting, Akku-Status (rot/grün/Ladepfeil), alle Touch-Funktionen.

---

## Welle 4 — Bekannt / verschoben

- **USB-Export:** kryptische Dateinamen, Fotos/Videos nicht öffenbar. Louis testet nächste Woche im Büro → danach fixen. `export/UsbExportService.kt`.
- **Speicheranzeige (System + USB):** heute nur im Android sichtbar. Louis akzeptiert, will Handbuch-Hinweis + wünscht Grafiklösung in DrainQ. Feature, nicht gebaut.

---

## OFFENE RÜCKFRAGE AN LOUIS (blockiert Merge-Gate)

Der wichtigste Test wurde nicht sauber beantwortet: **Foto/Schaden aus dem fertigen Video → stimmt der angebotene Meterwert exakt mit dem ins Bild eingebrannten Wert überein?** Louis bestätigt nur, dass das Video die Meterinfo trägt. Gezielt nachfragen: an 3 Stellen Foto + Schaden aus dem Video ziehen, jeweils prüfen, ob der Dialog-Meterwert = eingebrannter Bildwert. Leerer Wert = tolerierbar; falscher Zahlenwert = kritisch.

---

## Empfehlung Reihenfolge
1. B1 + B2 sofort (Blocker, beide gerätepflichtig zur Abnahme).
2. B1 an die Signatur-/Device-Owner-Entscheidung koppeln — ohne Device-Owner bleibt Datum-Automatik strukturell offen.
3. Welle 2 im selben Build mitnehmen (kleine, risikoarme Report-Fixes + M1).
4. Welle 3 danach, am Gerät iterieren.
5. Merge `feature/dual-mode` → master erst, wenn B2 UND die Meterwert-Rückfrage am Gerät bestätigt sind.

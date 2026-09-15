# Szenarien — Welle `zeitseite-nachzug`

Regel 17. Basis: `AUFTRAG.md`, `PLAN.md` Abschnitt 3.1, `PLAN_NACHTRAG.md` (Freigabe fuer
Schritt 1, 2, 3, 5 — **Schritt 4 / Z-3, Zonenliste zweistufig, ist in dieser Welle NICHT
gebaut** und daher hier nicht aufgefuehrt; er geht in die Bedienwelle).

| Nr | Szenario | Vorbedingung | Handlung | Messung | Erwartung | JUnit-Referenz |
|---|---|---|---|---|---|---|
| S-1 | Automatik AN, Vorab-Dialog | `auto_time=1` | Uebernehmen | Foto | Dialog **vor** dem Setzen, kein Portzugriff (`setTime`/`setTimeZone` nicht gerufen) | `setZoneAndTime_autoTimeOn_doesNotSetWithoutConsent`, `setZoneAndTime_autoTimeZoneOn_doesNotSetWithoutConsent` |
| S-2 | Vorab-Dialog abgebrochen | wie S-1 | Abbrechen | Foto | Meldung „Nicht gesetzt. Die Zeitautomatik ist weiterhin an.", Uhr unveraendert | `ConsentCancelled`-Zweig, Screen-`when` (Kompilierzwang, Regel 34) |
| S-3 | Automatik AUS, gesetzt und behalten | `auto_time=0` | Uebernehmen | Foto nach 10 s | `Applied`-Text, Diagnosezeile read1≈read2 | `setDateTime_secondReadRunningClockAdvancesNormally_applied` |
| S-4 | Gesetzt, nach 10 s zurueckgestellt (Louis Fall 2) | Automatik an (Rennlage) oder unbekannte Ursache | Uebernehmen | Foto | `Overwritten`-Text, zwei Varianten (Automatik-Ursache bekannt/unbekannt) | `setDateTime_secondReadRevertedToOldTime_overwritten` (RB-1) |
| S-5 | Port wirft (Lesezugriff auf Automatik-Status) | nur Fake | — | Test | `Denied`/`Applied` bleibt bestehen, kein Prozessabbruch, Log traegt `?` | `logResult_portThrowsInCatchPath_yieldsDeniedNotCrash`, `autoTimeState_portThrows_returnsNullNotCrash` (RB-3) |
| S-8 | Diagnosezeile ohne adb | nach S-3/S-4 | aufklappen | Foto | Eine Zeile je `CallRecord`, Werte technisch + lesbare Zeitform, `?` bei unlesbarer Automatik | `diagnosticLines_countMatchesRecords`, `diagnosticLines_unreadableAuto_showsQuestionMark`, `diagnosticLines_containsBothTimeForms` |
| S-9 | Diagnose ueberlebt Neustart (PLAN_NACHTRAG B-1) | nach S-3/S-4, Geraet hart neu gestartet | Zeitseite oeffnen | Foto | Zuletzt abgelegter Datensatz erscheint sofort, ohne neuen Versuch | `persistDiagnostic_writtenThenRead_isFieldEqual` (NACHBESSERUNG Runde 3, N-2 — Schreiben/Lesen jetzt mit eigenem Test; der Neustart selbst bleibt **nicht hergestellt am Geraet**) |
| S-10 | Diagnose ueberlebt Coroutine-Abbruch (PLAN_NACHTRAG B-2) | Seite waehrend der 10 s verlassen | Seite verlassen, erneut oeffnen | Foto | Erster Teil-Datensatz (`read2=null`, Zweig „pending") bleibt lesbar | `setDateTime_secondReadPending_onProgressCarriesFirstReadOnly` — **nicht hergestellt am Geraet** |

## Nicht-Reichweite (unveraendert, siehe `PLAN.md` 1.1/1.3)

- `DateTimeScreen.kt:295`/`:299` (`systemZone()` beim Oeffnen der Seite) bleibt auf dem
  Hauptfaden — kein Uhrsprung davor, Umzug waere Strukturaenderung (K-3).
- `heightIn(max = 300.dp)` an der Zonenliste bleibt (K-2) — Schritt 4 entfaellt.
- Z-3 (Zonenliste Kontinent → Stadt) ist **verschoben**, nicht gebaut (`PLAN_NACHTRAG.md`).

## Am Geraet — nicht hergestellt

Alle Szenarien, die ein physisches Geraet mit Zeitautomatik/Netzzeitquelle brauchen (S-1
bis S-5, S-9, S-10 in ihrer Bildschirm-Auspraegung), sind in dieser Welle **nicht
hergestellt** (L-213) — nur die reinen Funktionen/der Setter sind gegen den Fake-Port
gemessen. Die Rot-Beweise RB-1 bis RB-3 stehen fuer S-1, S-4, S-5.

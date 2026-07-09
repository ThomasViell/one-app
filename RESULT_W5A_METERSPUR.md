# RESULT — Welle 5a: Nachtrag Meter-Spur & Absturz-Ehrlichkeit

**Branch:** `feature/dual-mode` (kein Merge, kein Tag) · **Build:** `.\gradlew.bat assembleDebug testDebugUnitTest` — **grün**.
**Commits:** `335bfe7` (Befund 1+2 Kern) · `27f7229` (Befund 2 Sichtbarkeit + Befund 3) · `<Review-Fixes>` (Pre-Merge).
**Bezug:** `WELLE5A_NACHTRAG_METERSPUR_PROMPT.md`, aufbauend auf Welle 5 (`RESULT_W5_VIDEOWEG.md`, ADR 0002).

Welle 5 ist am Gerät bestätigt (**8,3 → 27,55 fps**, Videodauer == Echtzeit). Dieser Nachtrag behebt
drei Befunde aus der Messung der echten Dateien. **Alle drei betreffen die Integrität der
Berichtsdaten** (Station + Vollständigkeit der Aufnahme). KRITIS: rein lokal, kein neuer Netz-/
Logging-Datenfluss, keine neue Permission.

---

## Befund 1 — Absturz zerriss die Meter-Spur (Datenfehler)

**Gemessen** (Kill-Test `…141721.mp4`): Video **24,82 s / 674 Frames**, aber die zugehörige
`…141721.mp4.meter.jsonl` endete bei **11,94 s** (letzte Zeile mitten im Schreiben abgerissen) →
der geretteten Aufnahme fehlten **~13 s Stationen**. Ursache: das H264-Journal wird pro Access-Unit
absturzsicher geschrieben, die Sidecar hing dagegen an einem `BufferedWriter`, dessen Puffer beim
`force-stop` verloren ging.

**Fix** (`MeterTrackWriterV3`): `onSample` zwingt die Spur **mindestens jede Sekunde Medienzeit**
(`tUs − lastFlushTUs ≥ 1 000 000`) mit `flush()` auf die Platte. Ein Absturz kostet damit höchstens
~1 s Spur statt der gesamten Aufnahme. Kein Format-Wechsel (v3 bleibt). Während einer Pause steht
`tUs` still → kein unnötiges Flush, kein aufgeblähter Verlust. `flush()` schützt gegen force-stop
(nicht gegen Stromausfall) — genau die richtige Zusage für diesen Befund.

## Befund 2 — Nachschlagen klemmte still auf einen falschen Wert (Datenfehler)

Nach Befund 1 endete die Spur 13 s vor dem Video; ein Foto bei Sekunde 20 bekam **stillschweigend**
die Station von Sekunde 11,94 — falsch, plausibel, ohne Warnung.

**Fixes:**
- `lookupMeterV3` gibt **`null`** zurück, wenn die Position **> 500 ms** hinter dem letzten Sample
  liegt → leeres Pflichtfeld (Stufe-1-Verhalten aus Welle 4), **nie eine geratene Station**. Innerhalb
  von 500 ms (normales Videoende, letztes Sample ~1 Frame vor dem letzten Frame) wird weiter geklemmt;
  Anfang-Klemmen unverändert.
- `MeterTrackReaderV3` verlangt eine **vollständige** Sample-Zeile (`matchEntire` inkl. schließender
  Klammer, CRLF-robust) → eine abgerissene letzte Zeile wird still verworfen, ohne die restliche Spur
  zu entwerten (vorher hätte `find()` `…"m":1.` fälschlich als `1.0` gelesen).
- **Sichtbar** statt nur geloggt: Endet die Spur > 1 s vor dem Video, zeigt `VideoPlaybackDialog` ein
  Amber-Banner „Meter-Spur endet bei X s — für spätere Positionen keine Station".

## Befund 3 — Wiederherstellung war stumm (Ehrlichkeit)

`RecorderJournalMuxer.recoverOrphanJournals()` baute nach einem Absturz eine abspielbare MP4 und sagte
es niemandem — obwohl die Aufnahme zwangsläufig unvollständig ist (die zuletzt gepufferten Bilder
fehlen).

**Fix:** Ein wiederhergestelltes Video wird dauerhaft markiert (`<video>.mp4.recovered`, nur bei
erfolgreichem Mux). Sichtbar
- in der **Video-Liste** (Badge „Nach Absturz wiederhergestellt — evtl. unvollständig"),
- im **PDF-Bericht** (Hinweiszeile mit den betroffenen Aufnahme-Namen, nur für noch existierende Videos).

**Export-Entscheidung (begründet):** Der Marker ist ein **app-internes** Hilfsmittel und wird aus
ZIP-/USB-Export **ausgeschlossen** (wie `.frag.mp4`/`.h264j`/`.meter.jsonl`). Der ehrliche Hinweis
reist stattdessen **im PDF** mit — so sieht der Empfänger den Vermerk im Bericht, nicht eine kryptische
`.recovered`-Sidecar-Datei. Beim Löschen eines Videos werden Marker + Sidecar mitentfernt (kein
verwaister Phantom-Eintrag im Bericht).

---

## Testausgabe (JVM-Unit-Tests, grün)

- `lookupMeterV3`: Position innerhalb 500 ms hinter dem letzten Sample → letzter Wert; darüber → `null`;
  leere Spur → `null`; vor dem ersten Sample → erster Wert; Interpolation unverändert; Single-Sample.
- `MeterTrackReaderV3`: abgerissene letzte Zeile → alle vollständigen Samples gelesen, kein Absturz;
  v1/v2-Kopf/leer weiterhin abgewiesen.
- `MeterTrackWriterV3`: nach simuliertem Abbruch ohne `stop()` sind die Samples bis ~1 s vor Abbruch
  auf der Platte (deterministisch, separater Reader auf der offenen Datei).
- `RecoveredMarkerTest`: markiertes Video wird als markiert erkannt.

Welle-2/3/4/4b-Tests unverändert grün (v2-Stack + „do-not-change"-Fläche unangetastet).
`.\gradlew.bat assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL**.

## Pre-Merge-Review (nicht-bauendes Modell, hoch)

**Verdict: SHIP** — alle drei Befunde korrekt behoben, Tests scheitern nachweislich am Vor-Fix-Code,
keine Regression der „do-not-change"-Fläche (nextPtsUs/eine-Uhr, Encoder-Pfad, Journal-Format,
Feature-Flag, Rückfallebene). Umgesetzte Review-Punkte: (1) Löschen eines Videos entfernt Marker +
Sidecar mit; (2) PDF-Hinweis listet nur Marker mit noch existierendem Video; (3) Reader `trimEnd()`
für CRLF-Robustheit.

## Geräte-Abnahme (danach erst Freigabe an Louis)

- [ ] Kill-Test wiederholen: Spur des geretteten Videos endet **innerhalb 1 s** der Videodauer (bisher 13 s Lücke).
- [ ] Im geretteten Video ein Foto **hinter** dem Spurende erfassen → Station bleibt **leer** (keine stille Zahl); Banner sichtbar.
- [ ] Wiederhergestelltes Video: Badge in der Liste, Hinweis im PDF-Bericht.
- [ ] **Aufnahme mit laufendem Meterzähler** (Fahrwagen/Haspel) → Foto/Schaden aus dem Video an 3 Stellen: Station == **eingebrannter** Meterwert. (Louis' ursprünglicher Fehler; einziger noch ungeprüfter Punkt — an der Test-ONE war die Spur bisher durchgehend 0.00.)
- [ ] Rückfallschalter (Hardware-Recorder aus) → alter Weg unverändert.
- [ ] Lange Aufnahme ≥ 5 min mit Pause → Dauer == Echtzeit minus Pause.

# Auftrag: Welle 5a — Nachtrag Meter-Spur & Absturz-Ehrlichkeit (vor Freigabe an Louis)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Build:** OHNE Keystore — `cd C:\Projekte\drainq.one; .\gradlew.bat assembleDebug test`. NIE `assembleRelease`/`KEYSTORE_`-Env.
**Commit-Präfix:** `fix(w5a): …` — nach jedem Schritt Build + Tests grün, dann committen.
**Git-Regel:** Zustand NIE über Sandbox-bash beurteilen — Host-Read/Grep ist Schiedsrichter.
**Modell/Effort:** Sonnet/mittel. Pre-Merge-Review durch ein **nicht-bauendes** Modell/hoch.
**Zuerst:** Skill `drainq-kritis-compliance` konsultieren (Integrität der Berichtsdaten).

Welle 5 ist am Gerät gemessen und im Kern richtig: **8,3 → 27,55 fps**, Videodauer == Echtzeit (60 s Aufnahme → 62,47 s Datei bei Stoppuhr-Toleranz; alte Aufnahme lief 1,45× zu schnell). Dieser Nachtrag behebt drei Befunde aus der Messung der echten Dateien.

## Befund 1 — Absturz zerreißt die Meter-Spur (Datenfehler)

Gemessen an der Kill-Test-Datei `…141721.mp4`:
- Video: **24,82 s**, 674 Frames.
- Zugehörige `…141721.mp4.meter.jsonl`: letztes Sample bei **11,94 s**, letzte Zeile **mitten im Schreiben abgerissen**.

Ursache: Das H264-Journal wird pro Access-Unit geschrieben (absturzsicher), die Sidecar hängt dagegen an einem `BufferedWriter`, dessen Puffer beim `force-stop` verloren geht. Der geretteten Aufnahme fehlen ~13 s Stationen.

**Fix:** Die Spur regelmäßig auf die Platte zwingen — spätestens **jede Sekunde** bzw. alle N Samples `flush()`. Ein Absturz darf höchstens ~1 s Spur kosten. Kein Format-Wechsel (v3 bleibt).

## Befund 2 — Nachschlagen klemmt still auf einen falschen Wert (Datenfehler)

`MeterSampleV3.kt` Z. 52:
```kotlin
if (targetUs >= samples.last().tUs) return samples.last().meter
```
Nach Befund 1 endet die Spur 13 s vor dem Video. Ein Foto bei Sekunde 20 bekommt damit **stillschweigend** die Station von Sekunde 11,94 — falsch, plausibel, ohne Warnung. Die vorhandene Prüfung in `VideoPlaybackDialog` (Z. 101–109) schreibt nur `Log.w` und **verhindert nichts**.

**Fix:**
- `lookupMeterV3` gibt **`null`** zurück, wenn `targetUs` mehr als eine kleine Toleranz (Vorschlag: **500 ms**) hinter dem letzten Sample liegt. Innerhalb der Toleranz darf weiter geklemmt werden (normales Videoende, letztes Sample liegt ~1 Frame vor dem letzten Frame).
- Klemmen am **Anfang** (Z. 51) bleibt wie es ist.
- `null` ⇒ leeres Pflichtfeld (Stufe-1-Verhalten aus Welle 4). **Nie** eine geratene Station.
- Eine abgerissene letzte Zeile muss der Reader still verwerfen, ohne die restliche Spur zu entwerten (Verhalten absichern, Test).
- Ist die Spur deutlich kürzer als das Video (Differenz > 1 s), im Erfassungsdialog **sichtbar** kenntlich machen, dass für diesen Bereich keine Station vorliegt — nicht nur loggen.

## Befund 3 — Wiederherstellung ist stumm (Ehrlichkeit)

`RecorderJournalMuxer.recoverOrphanJournals()` baut nach einem Absturz eine abspielbare MP4 und sagt es niemandem. Die Aufnahme ist zwangsläufig unvollständig (die zuletzt gepufferten Bilder fehlen), sieht aber aus wie jede andere.

**Fix:** Wiederhergestellte Videos dauerhaft markieren (z. B. Marker-Datei neben der MP4 oder Feld am Datensatz) und die Markierung **sichtbar machen**: in der Video-/Projektliste und im **Bericht** als Hinweis „nach Absturz wiederhergestellt, möglicherweise unvollständig". Keine Fehlermeldung, kein Blocker — ein ehrlicher Vermerk. Marker beim Export mitführen oder bewusst ausschließen (entscheiden und begründen).

## Nicht ändern

- Zeitbasis (`nextPtsUs()` speist Encoder **und** Meter-Sample — eine Uhr, bewiesen korrekt).
- Encoder-Pfad, Journal-Format, Feature-Flag, Rückfallebene.
- Welle 2/3/4/4b-Verhalten. Deren Tests bleiben grün.

## Tests

- `lookupMeterV3`: Position hinter dem letzten Sample innerhalb 500 ms → letzter Wert; darüber → `null`. Leere Spur → `null`. Position vor dem ersten Sample → erster Wert. Interpolation unverändert.
- Reader: Datei mit abgerissener letzter Zeile → alle vollständigen Samples werden gelesen, kein Absturz.
- Writer: nach simuliertem Abbruch ohne `stop()` sind die Samples bis ~1 s vor Abbruch auf der Platte.
- Recovery: markiertes Video wird als markiert erkannt.

## Geräte-Abnahme (danach erst Freigabe an Louis)

- [ ] Kill-Test wiederholen: Spur des geretteten Videos endet **innerhalb 1 s** der Videodauer (bisher 13 s Lücke).
- [ ] Im geretteten Video ein Foto **hinter** dem Spurende erfassen → Station bleibt **leer** (keine stille Zahl).
- [ ] Wiederhergestelltes Video ist als solches erkennbar; Hinweis erscheint im Bericht.
- [ ] **Aufnahme mit laufendem Meterzähler** (Fahrwagen/Haspel angeschlossen — an der Test-ONE bisher nicht möglich, Spur war durchgehend 0.00): Foto/Schaden aus dem fertigen Video an drei Stellen → Station == **eingebrannter** Meterwert im Bild. Das ist Louis' ursprünglicher Fehler und der einzige noch ungeprüfte Punkt.
- [ ] Rückfallschalter (Hardware-Recorder aus) → alter Weg unverändert.
- [ ] Lange Aufnahme ≥ 5 min mit Pause → Dauer == Echtzeit minus Pause.

## Bericht

`RESULT_W5A_METERSPUR.md`: die drei Befunde mit den gemessenen Zahlen, die Fixes, Testausgabe, Geräte-Checkliste, Export-Entscheidung zum Marker.

**KRITIS:** rein lokal, kein neuer Netz-/Logging-Datenfluss, keine neue Permission. Alle drei Punkte betreffen die **Integrität der Berichtsdaten** (Station, Vollständigkeit der Aufnahme) — als Datenqualitäts-Fixes dokumentieren.

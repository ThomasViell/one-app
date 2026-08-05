# RESULT: Welle 4b — Korrektur der Zeitbasis der Meter-Spur

**Branch:** `feature/dual-mode` · **Basis:** Welle-4 Stufe 2 (`7cc9bbf`, fehlerhaft) → korrigiert hier
**Build + Tests:** grün (`:app:test`, 20 Frame-Index-Tests + Stufe-1 unverändert)
**Stufe 1 aus Welle 4 (`6c63311`): unangetastet.**

---

## 1. Ursache (code-belegt)

Die Meter-Sidecar stempelte mit **Wall-Clock** (`MeterTrackWriter` v1, Timer alle 200 ms).
`LocalBitmapRecorder` startet ffmpeg mit `-f image2pipe -framerate f -i <fifo>` → ffmpeg vergibt
die Präsentationszeit **nach Frame-Index**: `Medienzeit = frameIndex / f`. Die Schreibschleife
wartet `1000/f` ms **und** leistet danach Arbeit (Bitmap-Copy, OSD-Burn-in, JPEG-Compress,
FIFO-Write) → die reale Frame-Periode ist **immer** größer als `1/f`, also
`frames/f < Wall-Clock-Elapsed`. Die Wall-Clock-Spur lief dem Video **linear mit der
Aufnahmedauer davon**; der Fehler wuchs mit Länge und Last (HD). Ein Foto bei Wiedergabe­position
T bekam die Station eines **späteren** Aufnahmemoments — plausibel, aber falsch. Ein kurzer
Testclip zeigt das nicht.

## 2. Korrektur — Frame-Index statt Uhr

Die Uhr verschwindet vollständig aus dem Problem.

**Sidecar v2** (`<video>.meter.jsonl`):
```
{"v":2,"fps":15}
{"f":0,"m":0.00}
{"f":15,"m":0.31}
```
`fps` = der **tatsächlich verwendete** Wert (`f = fps.coerceIn(5,30)`), nicht der angeforderte.

- **`MeterTrackWriter`** (`network/MeterTrackWriter.kt`): `start(fps)` schreibt die Kopfzeile;
  `onFrame(frameIndex, meter)` wird vom Recorder pro FIFO-Frame gerufen und dezimiert auf ~5 Hz
  (jeder `max(1, fps/5)`-te Frame; Frame 0 immer). Ausgabe mit `Locale.US` (erzwingt `.` als
  Dezimaltrenner — deutsches Locale würde `0,31` schreiben und das JSON zerbrechen). Kein Timer,
  keine Coroutine, kein `pause()/resume()` mehr.
- **`LocalBitmapRecorder`**: Frame-Zähler in der Schreibschleife, **nur** hochgezählt wenn ein
  JPEG wirklich in die FIFO geht (nicht in Pause, nicht bei fehlendem Bitmap). Erster Frame = 0
  (deckt sich mit ffmpeg-PTS 0). `pause()/resume()` brauchen keinen Writer-Hook mehr: in `PAUSED`
  wird kein Frame geschrieben → Index steht von selbst still, `resume()` läuft in derselben Datei
  nahtlos weiter.
- **`MeterSample.kt`**: `MeterSample(frameIndex, meter)`, `MeterTrack(fps, samples)`,
  `frameIndexForPosition(positionMs, fps) = round(positionMs*fps/1000)`, `lookupMeter(track,
  positionMs)` (Interpolation zwischen umgebenden Samples nach Frame-Index; `fps<=0`/leer → null).
- **`MeterTrackReader`**: parst die v2-Kopfzeile. **Fehlt der Kopf oder `v != 2` (alte
  Wall-Clock-Spur) → `MeterTrack.EMPTY`** → Stufe-1-Fallback. Ein falscher Wert ist schlimmer als
  keiner, also werden v1-Spuren **nie** gelesen.
- **`VideoPlaybackDialog`**: lädt `MeterTrack` (IO), Foto/Schaden/Notiz belegen die Station mit
  `lookupMeter(meterTrack, exoPlayer.currentPosition)` vor (weiterhin editierbar). Fehlt die Spur
  → leeres Pflichtfeld (Stufe 1).
- **Remux (Welle 2) unangetastet**: `-c copy` erhält die Timestamps → Zuordnung bleibt gültig.

## 3. RTSP-Pfad — Entscheidung: KEINE Spur

`FfmpegRtspRecorder` bekommt **keine** Meter-Spur mehr. Begründung (geprüft, nicht angenommen):

- **Keine app-seitige Frame-Schleife.** Das ffmpeg-Kommando ist
  `-rtsp_transport tcp -i <url> … -f mp4 …` — die Medienzeit stammt aus den **Stream-PTS**, nicht
  aus einem app-kontrollierten Frame-Index. Der Frame-Index-Ansatz greift hier nicht.
- **Wall-Clock ≠ Stream-Medienzeit** wegen Netzwerklatenz und Stalls (im Feld gemessen). Eine
  app-seitig gestempelte Spur wäre wieder plausibel-aber-falsch.
- **Pause betrifft die RTSP-Aufnahme nicht.** Der RECORD-Knopf pausiert ausschließlich den
  `localRecorder` (`InspectionScreen` Z. 494/497, Gate `localRecorder.isRecording`); im RTSP-Modus
  ist er während der Aufnahme ein No-op. Die einzige „Pause" im RTSP-Modus ist das Anhalten des
  **Anzeige**-ExoPlayers (Z. 470) — die unabhängige FFmpeg-RTSP-Session läuft weiter. Es gäbe also
  keine Pausenlücke, aber auch keinen Frame-Index zum Stempeln.
- **`out_time` wäre denkbar** (FFmpegKit-Statistics), braucht aber On-Device-Validierung der
  Semantik bei fragmentiertem MP4 und ist kein sicherer Wert ohne Messung. Bewusst auf eine
  spätere Welle verschoben.

→ Für RTSP-Aufnahmen fällt die Wiedergabe auf das leere Pflichtfeld (Stufe 1). **Kein geratener
Wert.** Gegenüber Welle-4 Stufe 2 (die dort eine falsche Wall-Clock-Spur schrieb) ist das eine
strikte Korrektheits-Verbesserung, keine Regression.

## 4. Invariante als Selbstschutz

`VideoPlaybackDialog` prüft, sobald Spur geladen und Player bereit: `|Videodauer −
letzterFrameIndex/fps| > 1 Frame` → `Log.w`. Deckt jede künftige Verletzung der Zeitbasis sofort
auf. `MeterTrack.expectedDurationMs()` kapselt `letzterFrameIndex/fps`. Endgültige Bestätigung per
`ffprobe` in der Geräteabnahme.

## 5. Sidecar & Export — Entscheidung: NICHT exportieren (jetzt erzwungen)

Die Sidecar ist ein internes App-Hilfsmittel, kein Berichtsdatum, und für Fremdtools unlesbar.
**Befund:** `UsbExportService` und `ProjectExportService` sammelten bisher *jede* Datei im
`recordings`-Ordner außer `*.frag.mp4` — die `.meter.jsonl` **wäre also mitexportiert worden**
(und hätte Louis' bestehende „kryptische Dateinamen"-Klage auf dem USB-Stick verschärft). Beide
Filter schließen die Sidecar jetzt explizit aus (`!it.name.endsWith(METER_SIDECAR_SUFFIX)`).

## 6. KRITIS

Rein lokale Sidecar-Datei neben der Aufnahme; **kein** neuer Netz-/Logging-Datenfluss, **keine**
neue Permission. Der Fix betrifft die **Richtigkeit der Berichtsdaten** (Station) → Datenqualitäts-Fix.

## 7. Tests (`:app:test`, grün)

`LookupMeterTest` (23 Tests):
- `lookupMeter`: leere Spur, fps=0, Single-Sample-Clamp, Position vor erstem/nach letztem, exakter
  Treffer, Interpolation, **negative Position** (ExoPlayer-Fehlerzustand → klemmt).
- `frameIndexForPosition`: fps 5/12/15/30 inkl. Rundung an Sample-Grenzen (**fps=12 =
  Produktionsrate**, plus 6-Hz-Sample-Roundtrip).
- **Drift-Test** `frameIndex_lookup_is_immune_to_slow_write_loop`: simuliert eine Schreibschleife
  mit 60 % der Soll-Rate; die Frame-Index-Zuordnung bleibt exakt (7,50 m), während der Wall-Clock-
  Ansatz 4,50 m getroffen hätte — genau der Fehler, der jetzt behoben ist.
- Reader: valides v2, `v=1`→EMPTY, v1-ohne-Kopf→EMPTY, fehlender Kopf→EMPTY, nur-Kopf→EMPTY,
  kaputte Zeilen übersprungen + sortiert, `fps=0`→EMPTY.
- Invariante: `expectedDurationMs`.

`MeterTrackWriterTest` (4 Tests, Writer→Reader-Roundtrip auf der JVM): Frame 0 immer gesampelt +
Dezimierung, **Locale-Unabhängigkeit** (deutsches Locale → JSON bleibt `.`-Dezimaltrenner),
ungültige fps → EMPTY, `stop()` idempotent.

`MeterInputTest` (Stufe 1): unverändert grün.

## 8. Geräte-Abnahme (die kurze Aufnahme beweist nichts)

- [ ] **Lange HD-Aufnahme, ≥ 5 min**, **eine Pause** in der Mitte, Fahrwagen bewegt sich.
- [ ] Foto/Schaden aus dem fertigen Video an **drei** Stellen: kurz nach Beginn, direkt **nach der
      Pause**, kurz vor Ende. Jedes Mal: vorbelegte Station **==** eingebrannter OSD-Meterwert.
- [ ] Gleiche Prüfung im **SD**-Modus (andere Skalierung, gleiche Zeitbasis).
- [ ] Altaufnahme (v1-Sidecar oder keiner) → leeres Pflichtfeld, keine stille 0.00.
- [ ] `ffprobe`-Dauer == `letzterFrameIndex / fps` (Toleranz 1 Frame); im Logcat keine
      „Zeitbasis-Abweichung"-Warnung.
- [ ] RTSP/Remote-Aufnahme → leeres Pflichtfeld (bewusst keine Spur), keine stille 0.00.
- [ ] USB-/Projekt-Export enthält **keine** `.meter.jsonl`-Datei.

## 9. Adversariale Selbst-Review (Pre-Merge, nicht-bauende Modelle)

Ein Multi-Agent-Review (5 Dimensionen: Zeitbasis-Exaktheit, Rundungs-/fps-Mathematik,
RTSP+Pausen, Nebenläufigkeit, Stufe-1-Regression) mit anschließender adversarialer Verifikation
jedes Befunds durch unabhängige Skeptiker. Ergebnis:

**Bestätigt und behoben:**
- **Nebenläufigkeit (MeterTrackWriter/LocalBitmapRecorder):** Der Writer wurde vom geteilten
  Feld gelöst — er gehört jetzt **ausschließlich** der writeJob-Coroutine (einziger Producer),
  wird dort erzeugt, pro Frame beschrieben und im `finally` (nach dem letzten Frame) geschlossen.
  `stop()`/`cancel()` fassen ihn nicht mehr Cross-Thread an → keine Race, kein Write-after-close,
  garantiertes Flushen der letzten Samples. Zusätzlich `@Volatile` am `writer`-Feld (defensiv,
  Muster wie FfmpegRtspRecorder). Early-Return-Pfad (FIFO-Open-Fehler) schließt den Writer nun auch.
- **Test-Lücke fps=12:** fps=12 ist die reale Produktionsrate (InspectionScreen) und war
  ungetestet (Ganzzahl-Division `12/5=2` → 6 Hz). Neue Tests `frameIndex_fps12_production_default`
  + `roundtrip_fps12_with_6hz_sample_spacing`. Doc-Kommentar auf „~5–6 Hz" korrigiert (6 Hz ist
  unkritisch, da lookupMeter über den Frame-Index interpoliert, nicht über die Sample-Anzahl).
- **Test-Lücke negative Position / Frame 0:** `frameIndex_negative_position_clamps…` +
  `MeterTrackWriterTest.frame_zero_is_always_sampled…`.
- **Export-Ausschluss (selbst gefunden):** siehe §5 — beide Export-Filter hätten die `.meter.jsonl`
  mitgenommen; jetzt explizit ausgeschlossen.

**Adversarial widerlegt (kein Handlungsbedarf):**
- Mehrere „kritische" Befunde verglichen versehentlich gegen den **committeten** Fehlerstand
  (`7cc9bbf`, Wall-Clock) statt gegen den Arbeitsbaum; ihre eigenen Verdikte bestätigen „der
  Arbeitsbaum enthält den korrekten Fix". → Kein realer Defekt.
- `lookupMeter` `indexOfFirst()==0`/IndexOutOfBounds: durch die beiden Clamp-Guards
  (`target > first`, `target < last`) unmöglich → `afterIdx > 0` garantiert.
- Reader-Regex akzeptiert Exponentialschreibweise, die der Writer (`%.2f`) nie erzeugt: bewusst
  tolerant, kein Defekt.

**Kanten geprüft:** Rundung an Frame-Grenzen (fps 5/12/15/30), fps-Grenzwerte, Spur ohne Samples
(→ EMPTY), Video ohne Spur (→ Stufe-1-Fallback), Spur mit kaputtem/altem Kopf (→ EMPTY),
Position 0/negativ/nach Ende, Pause (Index steht still, Datei läuft nahtlos weiter).

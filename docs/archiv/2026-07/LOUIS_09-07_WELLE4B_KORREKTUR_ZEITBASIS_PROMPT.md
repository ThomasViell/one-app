# Auftrag: Welle 4b — Korrektur der Zeitbasis der Meter-Spur (Pre-Merge-Blocker)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Build:** OHNE Keystore — `cd C:\Projekte\drainq.one; .\gradlew.bat assembleDebug test`. NIE `assembleRelease`/`KEYSTORE_`-Env.
**Commit-Präfix:** `fix(louis-w4b): …` — nach jedem Schritt Build + Tests grün, dann committen.
**Git-Regel:** Zustand NIE über Sandbox-bash beurteilen (CRLF-Phantom-Diffs) — Host-Read/Grep ist Schiedsrichter.
**Modell/Effort:** Opus/hoch (Zeitbasis-Kern). Pre-Merge-Review durch ein **nicht-bauendes** Modell/hoch.

Stufe 1 aus Welle 4 (`6c63311`, keine stille 0.00 mehr) ist **richtig und bleibt**. Dieser Auftrag korrigiert ausschließlich Stufe 2 (`7cc9bbf`).

## Der Defekt (code-belegt, systematisch)

`MeterTrackWriter.kt` Z. 14–16 behauptet: *„Wall-Clock-Elapsed minus Pausen — entspricht der Medienzeit des Encoders."* Diese Annahme ist **falsch**.

- `LocalBitmapRecorder.kt` Z. 125 startet ffmpeg mit `-f image2pipe -framerate $f -i <fifo>`. Bei `image2pipe` vergibt ffmpeg die Präsentationszeit **nach Frame-Index**: `Medienzeit = frames / f`.
- Die Schreibschleife (Z. 137–171) wartet `frameIntervalMs = 1000/f` **und** leistet danach Arbeit (Bitmap-Copy, OSD-Burn-in, JPEG-Compress, FIFO-Write). Die reale Periode ist damit **immer** größer als `1/f`.
- Folglich gilt zwingend `frames / f < Wall-Clock-Elapsed (minus Pausen)`.
- `MeterTrackWriter` Z. 28–32 stempelt aber mit Wall-Clock. Die Meter-Spur läuft dem Video **linear mit der Aufnahmedauer davon**.

Wirkung: Ein Foto/Schaden bei Wiedergabeposition T bekommt eine Station aus einem **späteren** Aufnahmemoment. Der Fehler wächst mit der Aufnahmelänge und der Systemlast (HD!). Der Bericht sieht plausibel aus und ist falsch — die gefährlichste Fehlerklasse. Ein kurzer Testclip zeigt das **nicht**.

## Korrektur: Frame-Index statt Uhrzeit

Die Uhr verschwindet vollständig aus dem Problem.

1. **Sidecar neu indizieren.** Kopfzeile mit Version und Bildrate, danach Samples nach Frame-Index:
   ```
   {"v":2,"fps":15}
   {"f":0,"m":0.00}
   {"f":15,"m":0.31}
   ```
   `fps` ist der **tatsächlich verwendete** Wert (`f = fps.coerceIn(5,30)`, Z. 101) — nicht der angeforderte.
2. **Sampling in der Schreibschleife**, dort wo der Frame wirklich in die FIFO geht: Frame-Zähler mitführen, alle ~5 Hz (also jeden `max(1, f/5)`-ten Frame) `{"f":index,"m":meterProvider()}` schreiben.
3. **Pausen erledigen sich von selbst.** Während `State.PAUSED` (Z. 151–155) wird kein Frame geschrieben → der Index steht still. `MeterTrackWriter.pause()/resume()` und `totalPausedMs` entfallen ersatzlos.
4. **Nachschlagen bei Wiedergabe:** `frameIndex = round(positionMs * fps / 1000)`, dann nächster Sample bzw. lineare Interpolation zwischen den beiden umgebenden Samples.
5. **Remux (Welle 2) bleibt unangetastet** — `-c copy` erhält die Timestamps, die Zuordnung bleibt gültig.
6. **Alte Sidecars (v1, Wall-Clock) niemals lesen.** Fehlt die Kopfzeile oder ist `v != 2`: als „keine Spur" behandeln → Stufe-1-Fallback (leeres Pflichtfeld). Ein falscher Wert ist schlimmer als kein Wert.

## RTSP-Pfad: prüfen statt annehmen

`FfmpegRtspRecorder` (Z. 62, 76, 113–114) bekommt dieselbe `MeterTrackWriter`. Dort gibt es **keine app-seitige Schreibschleife** — die Medienzeit stammt aus den Stream-Timestamps, der Frame-Index-Ansatz greift nicht ohne Weiteres.

- Ermittle, welche Zeitbasis der RTSP-Aufnahme tatsächlich zugrunde liegt (ffmpeg-Kommando lesen, ggf. `out_time` aus dem Fortschritt nutzen).
- Der Kommentar behauptet, der RTSP-Recorder habe **keine Pause-Funktion** — die Bedienung pausiert aber sehr wohl. Prüfe, was beim Pausieren im RTSP-Modus real passiert (läuft die Aufnahme weiter? entsteht eine Zeitlücke?).
- **Lässt sich die Zuordnung dort nicht exakt herstellen, wird für RTSP-Aufnahmen KEINE Spur geschrieben** → Stufe-1-Fallback. Kein geratener Wert. Entscheidung im RESULT begründen.

## Invariante als Selbstschutz

Nach dem Stopp muss gelten: `Videodauer ≈ letzterFrameIndex / fps` (Toleranz ein Frame). Prüfe das einmalig (z. B. `ffprobe` in der Geräteabnahme) und logge bei Abweichung eine Warnung — das deckt jede künftige Verletzung der Zeitbasis sofort auf, statt sie im Bericht zu verstecken.

## Tests

- `lookupMeter` (reine Funktion): leere Spur, Position vor erstem / nach letztem Sample, exakter Treffer, Interpolation, fehlende/kaputte Kopfzeile, `v=1` → null.
- Umrechnung `positionMs → frameIndex` für fps 5, 15, 30; Rundung an Sample-Grenzen.
- Drift-Test: simuliere eine Schreibschleife, die **langsamer** als `f` liefert (z. B. 60 % der Soll-Rate). Die Frame-Index-Zuordnung muss trotzdem **exakt** bleiben — genau dieser Test wäre bei der Wall-Clock-Variante fehlgeschlagen.
- Stufe-1-Tests (`MeterInputTest`) grün halten.

## Geräte-Abnahme (die kurze Aufnahme beweist nichts)

- [ ] **Lange HD-Aufnahme, mindestens 5 Minuten**, mit **einer Pause** in der Mitte, Fahrwagen bewegt sich.
- [ ] Foto/Schaden aus dem fertigen Video an **drei** Stellen: kurz nach Beginn, direkt **nach der Pause**, kurz vor Ende.
- [ ] Jedes Mal: vorbelegte Station **==** der im Bild **eingebrannte** Meterwert. Das OSD ist die Wahrheit; Abweichung = Fehler.
- [ ] Gleiche Prüfung im **SD**-Modus (andere Skalierung, gleiche Zeitbasis).
- [ ] Altaufnahme (v1-Sidecar oder gar keiner) → leeres Pflichtfeld, keine stille 0.00.
- [ ] `ffprobe`-Dauer stimmt mit `letzterFrameIndex / fps` überein.

## Abschlussbericht

`RESULT_LOUIS_W4B.md` (Repo-Root): Ursache, Korrektur, RTSP-Entscheidung mit Begründung, Testausgabe, Geräte-Checkliste, adversariale Selbst-Review (Rundung an Frame-Grenzen, fps-Grenzwerte 5/30, Spur ohne Samples, Video ohne Spur, Spur ohne Video).

**KRITIS:** rein lokale Sidecar-Datei, kein neuer Netz-/Logging-Datenfluss, keine neue Permission. Der Fix betrifft die **Richtigkeit der Berichtsdaten** (Station) — als Datenqualitäts-Fix vermerken. Entscheide und begründe, ob die Sidecar-Datei beim Projekt-/USB-Export mitgeht.

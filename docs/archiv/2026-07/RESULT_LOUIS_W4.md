# RESULT: Louis-Punkt B — Meterwert bei Foto/Schaden/Notiz aus dem Video

**Branch:** `feature/dual-mode`
**Commits:** Stufe 1 `6c63311` · Stufe 2 `TBD`
**Build + Tests:** grün (beide Stufen)

---

## Befund (bestätigt)

Der Wiedergabe-Pfad (`VideoPlaybackDialog.kt`) verdrahtete hart `0f` als Meterwert:
- Foto-Button speicherte direkt ohne Dialog (keine Chance zur Korrektur)
- Schaden-Dialog und Notiz-Dialog starteten mit `currentMeter = 0f` vorbelegt

---

## Lösung

### Stufe 1 — Keine falsche Station mehr in den Bericht (Sofort-Korrektheit)

**`DamageDialog.kt` + `NoteDialog.kt`:**
- Signatur: `currentMeter: Float` → `currentMeter: Float?`
- `meterText` startet leer wenn `currentMeter == null` (Wiedergabe-Pfad ohne Spur)
- Neuer gemeinsamer Parser `parseMeterInput(text, fallback): Float?` in `MeterInput.kt`:
  Komma→Punkt, dann `toFloatOrNull() ?: fallback` — null blockiert Speichern (`?: return@…`)
- **8 Unit-Tests** in `MeterInputTest.kt`

**`VideoPlaybackDialog.kt`:**
- Foto-Button speichert **nicht mehr still**: Frame erfassen → Dialog öffnen → Station bestätigen
- Alle drei Dialoge: `currentMeter = null` (Stufe 1) → `currentMeter = currentMeterForDialog` (Stufe 2)

**Verhalten Live-Pfad unverändert:** `InspectionScreen` übergibt `currentMeter = meterValue` (non-null) — bleibt vorbelegt wie bisher.

---

### Stufe 2 — Station automatisch aus der Wiedergabeposition

**Neue Dateien:**

| Datei | Zweck |
|---|---|
| `network/MeterSample.kt` | `data class MeterSample(positionMs, meter)` + `lookupMeter()` |
| `network/MeterTrackWriter.kt` | Schreibt Sidecar parallel zur Aufnahme (~5 Hz) |
| `network/MeterTrackReader.kt` | Liest Sidecar bei Wiedergabe |

**Aufnahme-Sidecar:**
- Datei: `<video>.meter.jsonl` neben der MP4 (gleicher Basisname, `METER_SIDECAR_SUFFIX`)
- Format: eine Zeile JSON pro Sample: `{"t":12345,"m":10.25}` (`t` = Medienzeit ms, `m` = Meter)
- Zeitbasis: Wall-Clock-Elapsed minus Pausenintervalle — passt zur Encoder-Medienzeit:
  - `LocalBitmapRecorder` schreibt während Pause keine Frames → Sidecar pausiert ebenfalls
  - `FfmpegRtspRecorder` hat keine Pause → Wall-Clock-Elapsed = Medienzeit
  - Nach Remux (`-c copy`): Timestamps unverändert → Sidecar bleibt gültig ✓
- Beide Recorder (`FfmpegRtspRecorder` + `LocalBitmapRecorder`) erhalten neuen Parameter `meterProvider: (() -> Float)? = null`
- `InspectionScreen`: alle 4 Recorder-Starts übergeben `meterProvider = { meterValue }`

**Wiedergabe-Lookup:**
- `VideoPlaybackDialog` lädt Sidecar via `LaunchedEffect(videoFile)` auf IO-Dispatcher
- Beim Klick auf Foto/Schaden/Notiz: `lookupMeter(meterSamples, exoPlayer.currentPosition)`
  → lineare Interpolation zwischen den zwei umgebenden Samples
- Ergebnis als `currentMeterForDialog: Float?` an beide Dialoge übergeben
- **Fallback Altaufnahmen:** Sidecar fehlt → `lookupMeter` gibt `null` zurück → leeres Pflichtfeld (Stufe 1)

**`lookupMeter()` — reine testbare Funktion:**
- Leere Spur → null
- Position vor erstem/nach letztem Sample → Clamp auf Randwert (kein Rückextrapolieren)
- Exakter Treffer → Wert direkt
- Sonst → lineare Interpolation
- **12 Unit-Tests** in `LookupMeterTest.kt`

---

## Sidecar beim Export (Entscheidung)

**Nicht mitexportiert** (bewusst ausgeschlossen):
- USB-Export (`Welle 5`) und Projekt-XML-Export kennen das Format nicht
- Sidecar ist ein internes App-Hilfsmittel, kein Teil des Inspektionsberichts
- Vermeidet Komplexität und Risiko kryptischer Dateierweiterungen im Export (vgl. Louis-Feedback)
- `MeterTrackReader.read()` schlägt still fehl wenn keine Sidecar → Fallback auf Stufe 1

---

## Geräte-Checkliste (am Gerät zu prüfen)

- [ ] Foto **live während Aufnahme** → Station = laufender Meterzähler (unverändert korrekt)
- [ ] Foto **bei Pause** → Station korrekt (unverändert)
- [ ] Foto **aus dem fertigen Video** → Dialog erscheint, Station ≈ im Bild eingebrannter Meterwert, **nie 0.00 ungefragt gespeichert**
- [ ] Schaden aus dem fertigen Video → Dialog, Station vorbelegt, editierbar
- [ ] Notiz aus dem fertigen Video → Dialog, Station vorbelegt, editierbar
- [ ] **Altaufnahme ohne Meter-Spur** → leeres Pflichtfeld, keine stille 0.00
- [ ] Bericht zeigt für alle Fälle dieselbe Station wie das Videobild
- [ ] Aufnahme mit Pause → Sidecar-Zeitbasis korrekt (kein Versatz nach der Pause)
- [ ] Build + Tests grün

---

## Datenqualitäts-Hinweis

Dieser Fix erhöht die **Richtigkeit der Berichtsdaten** (Station/Position) für alle aus dem Video erfassten Schäden, Fotos und Notizen. Vor dem Fix enthielten diese Einträge immer `0.00 m`, was im PDF-Report und im XML-Export zu systematisch falschen Stationsangaben führte.

# Reparaturauftrag — Meterwert bei Foto/Schaden aus Video: floor statt Interpolation

Quelle: Code-Audit 12.07.2026 (Meter-Wert-Kette, 0.5.7). Louis' wichtigster Prüfpunkt: der bei
Foto/Schaden aus einem aufgenommenen Video **offerierte** Meter-Wert muss EXAKT die ins Bild
**eingebrannte** Zahl sein. Aktuell nicht garantiert.
Branch: `feature/dual-mode`. Kein Merge, kein Tag. **Merge-Gate-relevant** (Datenintegrität, DK/SE).
Empfohlenes Modell: **Sonnet / mittel** — logischer Fix mit Kantenfällen; sorgfältig, bestehende
Rand-/Toleranz-Behandlung 1:1 erhalten.

---

## Befund [Sicher — Code auditiert]

**B1 (kritisch nach Projektregel): Der Offer-Pfad interpoliert linear.**
`network/MeterSampleV3.kt`, `lookupMeterV3(...)` gibt zwischen zwei Samples
`s0.meter + t * (s1.meter - s0.meter)` zurück. Aufgerufen in
`ui/screens/projectdetail/VideoPlaybackDialog.kt` an drei Stellen (Foto-/Schaden-/Notiz-`onClick`):
`lookupMeterV3(meterTrack, exoPlayer.currentPosition)`. Damit kann der Dialog eine Zahl anbieten,
die in KEINEM Frame eingebrannt war. Projektregel: „Interpolation = Fälschung" → verboten.
Praktisch klein (v3 schreibt 1 Sample/Frame), aber nicht null und wörtlich regelwidrig.

**B3 (gering-mittel): Meter-Lesung pro Frame nicht atomar.**
`network/HardwareBitmapRecorder.kt`, encodeLoop: der Einbrenn-Text nutzt `meterValue` in
`prepareFrame` (`osdLine2Provider()`/`buildOsdLine2`), das Sidecar-Sample nutzt `meterProvider()`
NACH dem Encode. Zwei verschiedene Zeitpunkte desselben Frames → eingebrannt und Sample können sich
um einen Meter-Tick unterscheiden.

NICHT betroffen / bewusst so lassen: v2 (`MeterSample.kt` `lookupMeter`, `MeterTrackReader`) wird auf
dem Offer-Pfad nie gelesen; Zeitbasis (Encoder-PTS ↔ `currentPosition`), `"%.2f"`-Formatierung beидseitig,
Kantenfall-Nullwerte und Recovery-Warnung sind korrekt.

---

## Fix

### Teil 1 (Pflicht) — Offer-Pfad auf floor-Auswahl

Ziel: den Meter des Samples mit dem **größten `tUs ≤ targetUs`** zurückgeben (= exakt der Wert des
aktuell angezeigten Frames), statt zum nächsten Sample zu interpolieren.

**Vorgehen:** Prüfe zuerst alle Aufrufer von `lookupMeterV3`.
- Wird `lookupMeterV3` NUR auf dem Offer-Pfad (VideoPlaybackDialog) verwendet → dessen
  Interpolationszweig direkt durch floor ersetzen.
- Gibt es weitere Aufrufer, die die Interpolation brauchen → NEU `lookupMeterFloorV3(...)` daneben
  anlegen und in `VideoPlaybackDialog` die drei `onClick`-Aufrufe darauf umstellen; `lookupMeterV3`
  unangetastet lassen.

**Semantik (Kantenfälle 1:1 wie bisher erhalten — nur der Zwischenbereich wechselt von interpoliert
auf floor):**
```
fun lookupMeterFloorV3(track: List<MeterSampleV3>, positionMs: Long): Float? {
    if (track.isEmpty()) return null
    val targetUs = positionMs * 1000L
    val idx = track.indexOfLast { it.tUs <= targetUs }   // größtes Sample mit tUs <= target
    return when {
        idx >= 0 -> {
            // Nach dem letzten Sample nur innerhalb der bestehenden Toleranz gültig, sonst null
            // (kein Rateraum) — dieselbe LOOKUP_TOLERANCE_US-Klemmung wie im bisherigen Reader.
            if (idx == track.lastIndex && targetUs - track[idx].tUs > LOOKUP_TOLERANCE_US) null
            else track[idx].meter
        }
        // Vor dem ersten Sample: bisheriges Verhalten beibehalten (erster Meterwert; Frame bei t≈0
        // zeigt den ersten Wert). Falls das bisherige lookupMeterV3 hier null gab, exakt das spiegeln.
        else -> track.first().meter
    }
}
```
- `LOOKUP_TOLERANCE_US` und die genaue Vor-erstem-Sample-Regel aus dem bestehenden `lookupMeterV3`
  übernehmen, NICHT neu erfinden — Ziel ist byte-gleiche Kantenfall-Semantik, nur floor statt Interp.
- Keine Änderung an Reader/Writer/Formatierung.

### Teil 2 (empfohlen, gleiche Änderung) — B3 atomar

In `HardwareBitmapRecorder.kt` encodeLoop den Meterwert **einmal pro Frame** lesen und denselben
Wert an Einbrennen UND Sample geben:
```
val frameMeter = meterProvider()          // genau einmal pro Frame
// -> buildOsdLine2(frameMeter, …) für den Burn-in
// -> meterWriter?.onSample(ptsUs, frameMeter) für das Sidecar
```
Damit sind eingebrannte Zahl und gespeichertes Sample garantiert identisch.

---

## Verifikation

**Unit-Test (jetzt möglich, reine Funktion):**
- `lookupMeterFloorV3`: Samples (0µs→1.00, 40000µs→1.05, 80000µs→1.10).
  target=79000µs → **1.05** (floor, NICHT ~1.099 interpoliert). target=80000µs → 1.10.
  target zwischen zwei Samples gibt nie einen Wert, der nicht in `track` steht.
- Kantenfälle: leer → null; vor erstem Sample → erster Wert (bzw. bisheriges Verhalten);
  weit hinter letztem Sample (> Toleranz) → null; innerhalb Toleranz → letzter Wert.

**Am Gerät (Pflicht):**
1. Einstellungen → OSD-Einbrennung AN, „Meter anzeigen" AN.
2. Meterzähler nullen, Aufnahme mit laufendem Kabel, stoppen.
3. Video an 3 Stellen anhalten, je Foto + Schaden erzeugen: offerierter Wert == exakt die
   eingebrannte Zahl (auch bei schnellem Kabel an Frame-Grenzen kein Kippen der 2. Stelle mehr).

---

## Nicht anfassen
v2-Stack (`MeterSample.kt`/`MeterTrackReader.kt`, nicht auf Offer-Pfad), `MeterTrackReaderV3.read`,
Zeitbasis/PTS-Logik, `"%.2f"`-Formatierung, Recovery-/Gap-Warnung.

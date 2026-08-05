# Auftrag: Louis-Punkt B — Meterwert bei Foto/Schaden/Notiz aus dem aufgenommenen Video

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Build:** OHNE Keystore — `cd C:\Projekte\drainq.one; .\gradlew.bat assembleDebug test`. NIE `assembleRelease`/`KEYSTORE_`-Env.
**Commit-Präfix:** `fix(louis-w4): …` — nach jedem Schritt Build + Tests grün, dann committen.
**Git-Regel:** Zustand NIE über Sandbox-bash beurteilen (CRLF-Phantom-Diffs) — Host-Read/Grep ist Schiedsrichter.

## Befund (am Gerät bestätigt, im Code belegt)

Louis, 09.07.: „The metercounter failure comes up, when you want to make a foto out of the video you have made, so afterwards recording. Then it marks the position 0.00 m … If you make a Foto during Pausing the video, or even during the video recording, it works well."

Warum: Der **Live-Pfad** (`InspectionScreen.kt`) übergibt korrekt `meterValue` (aus `cable.meterReading`) — Foto Z. 438, DamageDialog Z. 1577, NoteDialog Z. 1645. Der **Wiedergabe-Pfad** (`ui/screens/projectdetail/VideoPlaybackDialog.kt`) verdrahtet dagegen Nullen:

- **Z. 171** — Foto aus dem Video: `position = 0f`, wird **ohne Dialog sofort gespeichert**. Der Nutzer bekommt keine Chance zu korrigieren → falsche Station landet direkt im Bericht.
- **Z. 216** — Schaden aus dem Video: `currentMeter = 0f` (im Dialog editierbar, aber falsch vorbelegt).
- NoteDialog im selben File: gleiches Muster.

Es ist also **kein** Telemetrie-/Fokus-Problem, sondern eine hart kodierte Null.

## Lösung in zwei Stufen — Stufe 1 zuerst committen

### Stufe 1 — Keine falsche Station mehr in den Bericht schreiben (Sofort-Korrektheit)

- **Foto aus dem Video** darf **nicht mehr still mit 0.00 m gespeichert** werden. Stattdessen denselben Weg wie „Schaden" gehen: Frame erfassen → Dialog öffnen → Station bestätigen/eingeben → speichern.
- Meterfeld in Schaden-/Notiz-/Foto-Dialog aus der Wiedergabe: **nicht mit 0.00 vorbelegen**, sondern leer lassen und als Pflichtfeld behandeln (bzw. mit dem Wert aus Stufe 2, sobald verfügbar).
- Verhalten des **Live-Pfads unverändert** — dort ist `meterValue` korrekt und muss weiter vorbelegt werden.

### Stufe 2 — Station automatisch aus der Wiedergabeposition (der eigentliche Fix)

Beim Aufnehmen eine **Meter-Spur** mitschreiben; bei der Wiedergabe die Station zur aktuellen Player-Position nachschlagen.

- **Aufnahme:** parallel zum Video eine Sidecar-Datei neben der MP4 (gleicher Basisname, z. B. `<video>.meter.jsonl`) mit Paaren `(medienzeit_ms, meter)`, ~4–10 Hz. Zeitbasis **muss die Medienzeit des Encoders** sein (nicht Wall-Clock), damit sie nach dem W2-Remux weiter passt — Pausen dürfen die Zuordnung nicht verschieben. Beide Recorder bedienen: `LocalBitmapRecorder` (V4L2) und `FfmpegRtspRecorder` (RTSP).
- **Wiedergabe:** `exoPlayer.currentPosition` → Nachschlagen in der Spur (nächster Sample bzw. lineare Interpolation) → als Station vorbelegen (weiterhin editierbar).
- **Fallback:** Fehlt die Spur (Altaufnahmen), gilt Stufe 1 — leeres Pflichtfeld, **nie** 0.00 automatisch.
- Die Nachschlage-Logik als **reine, unit-testbare Funktion** kapseln (`lookupMeter(trackSamples, positionMs): Float?`): leere Spur, Position vor erstem/nach letztem Sample, exakter Treffer, Interpolation.

## Abnahmekriterium (eindeutig, am Gerät prüfbar)

Das OSD ist **im Video eingebrannt** und damit die Wahrheit: Wird bei Wiedergabe an Position T ein Foto/Schaden erfasst, muss die gespeicherte Station **mit dem im Bild sichtbaren Meterwert übereinstimmen** — und genauso im Bericht stehen. Abweichung = Fehler.

## Leitplanken

- Aufnahme-Pipeline und Remux (Welle 2) nicht verändern — nur eine zusätzliche Sidecar-Spur.
- Kein Re-Encode, keine Auflösungs-/OSD-Änderung.
- Bestehende Tests grün; neue Tests für `lookupMeter` und für „Foto aus Video speichert nicht ungefragt".
- **KRITIS:** rein lokale Datei neben der Aufnahme, kein neuer Netz-/Logging-Datenfluss, keine neue Permission. Der Fix erhöht die **Richtigkeit der Berichtsdaten** (Station) — als Datenqualitäts-Fix im RESULT vermerken. Sidecar beim Projekt-/USB-Export mitnehmen oder bewusst ausschließen (entscheiden und begründen).
- **Modell/Effort:** Stufe 1 → Sonnet/mittel. Stufe 2 → Opus/hoch (Zeitbasis/Remux-Wechselwirkung). Pre-Merge-Review durch ein nicht-bauendes Modell/hoch.

## Abschlussbericht `RESULT_LOUIS_W4.md` (Repo-Root) mit Geräte-Checkliste

- [ ] Foto **live während Aufnahme** → Station = laufender Meterzähler (unverändert korrekt).
- [ ] Foto **bei Pause** → Station korrekt (unverändert).
- [ ] Foto **aus dem fertigen Video** → Dialog erscheint, Station = im Bild eingebrannter Meterwert, **nie 0.00** ungefragt.
- [ ] Schaden und Notiz aus dem fertigen Video → dito.
- [ ] Altaufnahme ohne Meter-Spur → leeres Pflichtfeld, keine stille 0.00.
- [ ] Bericht zeigt für alle Fälle dieselbe Station wie das Videobild.
- [ ] Build + Tests grün; adversariale Selbst-Review (Zeitbasis nach Pause/Remux, fehlende/kaputte Spur, Position 0 und Ende).

## Nicht Teil dieser Welle

- USB-Export: kryptische Dateinamen, Foto/Video nicht öffenbar (Louis testet im Büro) → Welle 5.
- Speicher-/Kapazitätsanzeige (Feature-Wunsch) → Welle 5.
- Signing-Key-Umstellung → eigene Entscheidungsvorlage, kein Code hier.

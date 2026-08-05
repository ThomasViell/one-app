# RESULT — Welle 5: Aufnahmeweg umgebaut (HW-Encoder, 25 fps, Echtzeit, absturzsicher)

**Branch:** `feature/dual-mode` (kein Merge, kein Tag)
**Build:** `.\gradlew.bat assembleDebug testDebugUnitTest` — **grün** (ohne Keystore, Debug).
**ADR:** `docs/adr/0002-hardware-recorder-crash-safety.md` (Grundsatz + Nachschärfung nach adversarialer Review).
**Commits (feature/dual-mode):** `b01efcd` ADR · `4fbe652` Fundament · `283cec0` HW-Aufnahmeweg produktiv.

---

## Schritt 0 — Baseline (am Gerät gemessen, vor jedem Umbau)

**60 s HD aufgenommen → 40 s Video.** `reale_fps ≈ 12 × (40/60) = 8 fps`. Wiedergabe **1,5× zu
schnell** (Zeitraffer). Ursache code-belegt (`LocalBitmapRecorder`): fünf Software-Schritte je Bild
(Bitmap-Copy → OSD → JPEG Q85 → FIFO → ffmpeg JPEG-Decode + libx264), plus PTS strikt nach
Frame-Index (`index/12`) bei real langsamerer Schleife → Video kürzer als Wirklichkeit.

Diese Zahl ist die Vergleichsbasis. „Besser" wird am Gerät gemessen (unten), nicht behauptet.

---

## Umbau (was implementiert wurde)

| Ziel | Umsetzung |
|------|-----------|
| **Ruckelfrei 25 fps HD** | HW-`MediaCodec`-Encoder (`H264Encoder`, Rockchip `c2.rk.avc.encoder`, auf diesem Board bewiesen ~27 fps @720p). Kein JPEG, keine FIFO, keine SW-Neukodierung. |
| **Echtzeit (kein Zeitraffer)** | VFR: `presentationTimeUs = (nanoTime − startNs − pausedAccum)/1000`, streng steigend, erster Frame ≈ 0 → Container-Dauer == pausenbereinigte Echtzeit **per Konstruktion**. |
| **Werte im Bild** | OSD über denselben `OsdRenderer.renderBitmap` wie Foto/Schaden (video-OSD == foto-OSD). |
| **Station stimmt** | Meter-Spur **v3** (`{"tUs":pts,"m":meter}`) auf derselben Uhr; Nachschlagen über `exoPlayer.currentPosition` (ms→µs), lineare Interpolation. Nur für tatsächlich kodierte Frames gestempelt. |
| **Absturzsicher** | Jede Access-Unit sofort ins self-framing-Journal `<video>.h264j`; Stopp → `drainFinal(EOS)` → `MediaMuxer` → MP4; **Kill → Auto-Recovery beim nächsten App-Start** (mirror von Welle-2 `cleanupOrphanFrags`). Torn tail wird positiv erkannt und verworfen. |
| **Rückfallebene** | `FeatureFlags.useHardwareRecorder` (Default **an**), Settings-Schalter (DIRECT). `LocalBitmapRecorder` vollständig erhalten, implementiert dieselbe `Recorder`-Schnittstelle. |
| **Bildrate an EINER Stelle** | `RecorderConfig.TARGET_FPS = 25` ersetzt die drei hartkodierten `12`. |

**Ein-Encoder-Ausschluss (RK3588 hat einen AVC-Encoder):** `CameraEncoderArbiter` — `OneVideoServer`
(RTSP) gibt seinen HW-Codec frei, solange lokal aufgenommen wird, und legt ihn danach neu an. Nie
zwei Encoder-Instanzen gleichzeitig.

### Vorgehen (Reihenfolge wie im Auftrag)
1. Schritt 0 gemessen (Baseline oben).
2. **ADR 0002** geschrieben **vor** dem Coden; durch eine adversariale Design-Review (7 Angreifer)
   als NO-GO eingestuft und um 8 code-belegte Blocker nachgeschärft (Ein-Encoder-Ausschluss,
   PTS/Pause/Monotonie, EOS-Drain, SD-Vorskalierung, immer-Kopie, Journal-Recovery/Quarantäne,
   Export-Filter) — erst dann Code.
3. Encoder-Pfad hinter Flag, OSD-Ebene, echte Zeitstempel.
4. Meter-Spur v3 + Reader-Weiche (v1/v2 → EMPTY).
5. Geräte-Abnahme (Checkliste unten) — **offen, on-device**.

---

## KRITIS / Datenqualität (Beweismittel-Integrität)

- **Rein lokale Verarbeitung** — kein neuer Netz-/Logging-Datenfluss, keine neue Permission
  (Bus, MediaCodec, MediaMuxer, Journal liegen im App-Verzeichnis).
- **Zeittreue:** VFR-PTS == pausenbereinigte Echtzeit → die Aufnahmedauer im Bericht entspricht der
  realen Befahrung; kein Zeitraffer/keine Zeitlupe verfälscht die Meterzuordnung.
- **Absturzsicherheit:** Self-Framing-Journal + Auto-Recovery bewahren die Aufnahme bei App-Kill
  (Eigenschaft aus Welle 2 erhalten, gegen abrupte Abbrüche durch positive Torn-Tail-Erkennung sogar
  robuster). Wiederholt nicht muxbare Journale → `.recovery_failed/` (kein stiller Verlust).

---

## Testausgabe (JVM-Unit-Tests, grün)

Neue reine Tests (kein Gerät nötig):
- `LookupMeterV3Test`, `MeterTrackWriterV3Test` — v3 Zeitachse, Interpolation, v1/v2→EMPTY, Locale.
- `H264JournalCodecTest` — Framing-Roundtrip + **positive Torn-Tail-Erkennung** (Kill-Präfix bleibt muxbar).
- `RecorderJournalMuxerTest` — PTS-Monotonie-Garantie für MediaMuxer.
- `CameraEncoderArbiterTest` — Ein-Encoder-Handshake (kein-RTSP/freigegeben/spät-frei/Timeout).
- `HardwareBitmapRecorderStateTest` — Zustands-Guards (Robolectric, ohne echten MediaCodec).

Unveränderte Welle-4b-Tests (`LookupMeterTest`, `MeterTrackWriterTest`) und Welle-2/3-Tests bleiben
grün (v2-Stack unangetastet, additive v3-Dateien).

`.\gradlew.bat assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL**.

> Encode/Mux/Kill/Recovery laufen über MediaCodec/MediaMuxer und sind **nur am Gerät** verifizierbar
> (Robolectric stubt sie). Die Zustands-, Framing-, Uhr- und Handshake-Logik ist oben unit-getestet.

---

## Geräte-Abnahme (offen — vor Freigabe an Louis auszufüllen)

- [ ] **60-s-Test:** Videolänge == 60 s (± 1 s). Kein Zeitraffer. → gemessene Länge: ____
- [ ] **≥ 5-min-HD mit einer Pause**, Fahrwagen bewegt: Länge == Aufnahmedauer − Pause. → ____
- [ ] **fps gemessen** (Videolänge vs. Frame-Anzahl, z. B. ffprobe): ≥ 24 im Mittel, sichtbar ruckelfrei. → ____
- [ ] OSD im Bild: Meter, Datum, Projekt korrekt; Befund-Flash erscheint.
- [ ] Foto/Schaden **aus dem fertigen Video** (Beginn / nach Pause / kurz vor Ende): Station == eingebrannter Wert.
- [ ] Gleiches in **SD** (720×576).
- [ ] **App während Aufnahme killen** → nach Neustart liegt eine **abspielbare** Datei vor (Auto-Recovery).
- [ ] **Flag AUS** → alter `LocalBitmapRecorder` funktioniert unverändert (Rückfallebene bewiesen).
- [ ] **Alte Aufnahmen (v1/v2-Spur)** → leeres Pflichtfeld, keine stille 0.00.
- [ ] **RTSP + Aufnahme gleichzeitig:** Gate greift (kein zweiter HW-Encoder; RTSP-Sicht friert während der Aufnahme, danach wieder live).
- [ ] **100-Frame In/Out-PTS-Ordnung** streng monoton (B-Frames=0 verifizieren).

---

## Rückfallebene bestätigt (Code-Ebene)

`FeatureFlags.useHardwareRecorder = false` → `RecorderFactory` liefert den unveränderten
`LocalBitmapRecorder`; alle Aufrufer nutzen die gemeinsame `Recorder`-Schnittstelle. Nichts gelöscht.
Umschalten wirkt auf die nächste Aufnahme; eine laufende Aufnahme kann der Flip nicht verwaisen (der
Recorder wird beim Betreten der Inspektion einmal gewählt, und das Verlassen der Inspektion bricht
eine Aufnahme ohnehin ab).

## Adversariale Selbst-Review

- **Vor dem Coden:** Design-Review der ADR (7 Angreifer + Synthese) → NO-GO, 8 code-belegte Blocker
  geschlossen (siehe ADR-Nachschärfung).
- **Vor dem letzten Commit:** Implementierungs-Review (nicht-bauende Modelle, 6 Code-Slices + adversariale
  Verifikation je Befund + Synthese). **Verdict: SHIP with fixes** — kein Blocker/Major im Recorder-Kern;
  Crash-Safety, Arbiter-Ausschluss, VFR-PTS, EOS-Drain, Journal-Framing, Meter-v3 und Export-Filter
  code-verifiziert korrekt. Behoben:
  - **MUST-FIX 1:** `H264JournalWriter.writeRecord` gab IO-Fehler still verloren → jetzt `Boolean`;
    der Recorder setzt `journalWriteFailed`, loggt `Log.e` und meldet den Fehler beim Stopp (der gültige
    Präfix wird weiterhin gemuxt — kein Totalverlust). Test `writer_reports_failure_before_header_and_roundtrips_after`.
  - **MUST-FIX 2:** Video-Liste (`ProjectDetailViewModel.scanRecordingFiles`) schloss `.h264j`/`.meter.jsonl`
    nicht aus → ein durch Kill verwaistes Journal erschien als kaputte „Aufnahme". Filter ergänzt.
  - **SHOULD-FIX 1:** `CameraEncoderArbiter` stellt das Interrupt-Flag im Sleep-Catch wieder her.
  - **Bewusst verworfen:** der vorgeschlagene `if (!configReported) return`-Guard in `H264Encoder.drainFinal()`
    wäre **schädlich** (übersprungene, noch nicht ausgegebene Frames beim Stopp verloren) — nicht übernommen.
  - **Gegenstandslos:** „Settings-Schalter während Aufnahme deaktivieren" — strukturell unmöglich erreichbar
    (das Verlassen der Inspektion in die Settings bricht eine Aufnahme ohnehin ab; der Recorder wird einmal
    per Factory gewählt) → dokumentiert, kein Code nötig.
- **Nach Fixes:** `.\gradlew.bat assembleDebug testDebugUnitTest` erneut grün.

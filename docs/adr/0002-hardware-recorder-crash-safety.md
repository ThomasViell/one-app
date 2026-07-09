# ADR 0002 — Hardware-Encoder-Aufnahmeweg + Absturzsicherheit (Welle 5)

**Status:** Accepted (2026-07-09)
**Datum:** 2026-07-09
**Entscheider:** Thomas Viell (CEO-Entscheid 09.07.: Umbau vor Louis' Abnahme)
**Bezug:** `WELLE5_VIDEOWEG_UMBAU_PROMPT.md`, ADR-loser Welle-2-Umbau (`RecorderRemux.kt`), Welle-4b (`MeterSample.kt`)
**Betroffener Code:** `network/LocalBitmapRecorder.kt` (bleibt), neu `network/HardwareBitmapRecorder.kt`, `network/video/H264Encoder.kt` (Reuse)

---

## Kontext

Der lokale Aufnahmeweg der ONE (`VideoSource.LocalBitmap`, V4L2 → `CameraFrameBus`) läuft
heute über `LocalBitmapRecorder`:

```
Bitmap.copy (CPU) → OSD-Canvas → JPEG Q85 → FIFO → ffmpeg image2pipe -framerate 12
                                                     → JPEG-Decode → libx264 ultrafast (SW)
```

Zwei belegte Fehler:

1. **Zeitraffer.** ffmpeg vergibt die PTS strikt nach Frame-Index (`Medienzeit = index/12`),
   die Schreibschleife liefert aber langsamer als 12 fps (sie wartet `1000/f` ms **und**
   arbeitet danach). **Geräte-Baseline (Schritt 0, gemessen 2026-07-09):** 60 s Aufnahme →
   **40 s Video → real 8 fps, Wiedergabe 1,5× zu schnell.**
2. **Zu langsam für 25 fps HD.** Fünf Software-Schritte pro Bild, davon zwei
   Voll-Codecs (JPEG-Encode + JPEG-Decode) und ein Software-H.264-Encode (libx264).

Ziel (Abnahme durch den CEO): ruckelfrei 25 fps in HD, Videodauer == echte Aufnahmedauer
minus Pausen, OSD korrekt eingebrannt, Station aus dem Video == eingebrannter Meterwert,
**absturzsicher** (Kill während Aufnahme → abspielbare Datei bleibt — Welle-2-Eigenschaft).

### Vorhandene Assets (entscheidungsrelevant)

- **`network/video/H264Encoder.kt`** kapselt bereits den **Plattform-`MediaCodec`
  (Rockchip-HW-Encoder `c2.rk.avc.encoder`)** und ist auf **genau diesem Gerät bewiesen**:
  RGB-`Bitmap` → HW-H.264, gemessen **~27 fps bei 1280×720, ffprobe-sauberes yuv420p**
  (RTSP-Pfad, Welle 3c / PERF-Doku 2026-07-03). Farbkonvertierung RGB→I420 über die native
  `libv4l2bridge` (M3a, ~3–6 ms/Frame) mit Kotlin-Fallback. Ausgabe: Annex-B-Access-Units +
  SPS/PPS mit `presentationTimeUs` je AU.
- **`OneVideoServer.encodeLoop`** liefert das bewiesene Konsum-Muster aus dem
  konflatierenden `CameraFrameBus.frames`-`StateFlow` (Poll + `!== last`-Guard,
  Drop-on-Backpressure).
- **Welle-2-Absturzsicherheit** (`RecorderRemux.kt`): live in fragmentierte MP4 schreiben,
  beim Stopp `-c copy`-Remux nach faststart; verwaiste Frags räumt `cleanupOrphanFrags` beim
  nächsten `start()` auf. Diese **Beim-nächsten-Start-Reparatur-Mechanik** ist die Vorlage.
- **`OsdRenderer.renderBitmap`** brennt das OSD per Canvas in ein `Bitmap` — **dieselbe
  Funktion, die auch Foto/Schaden benutzt.**

---

## Entscheidung 1 — Encoder-Eingang: `MediaCodec` per **ByteBuffer** (Reuse `H264Encoder`), NICHT GLES-Input-Surface

Der neue Aufnahmeweg nutzt den **bereits produktiv bewiesenen** `H264Encoder` (ByteBuffer-Pfad:
`getInputImage` + native RGB→I420) statt einer neuen `MediaCodec`-**Input-Surface + GPU/GLES**-Pipeline
(die der Prompt als *möglichen* Mechanismus vorschlägt).

**Pro Frame (RECORDING):** Bus-`Bitmap` → (bei „Mit Einblendung") mutable Kopie + `OsdRenderer`
→ `H264Encoder.encode(bmp, ptsUs)` → HW-H.264. **Weg:** JPEG-Encode, FIFO, ffmpeg-JPEG-Decode,
libx264. Statt fünf Software-Schritten bleiben: (OSD-Canvas auf Kopie) + native RGB→I420 + **HW**-Encode.

**Begründung (warum ByteBuffer statt Input-Surface):**

1. **Auf genau diesem Gerät bewiesen.** Der ByteBuffer-Pfad hält ~27 fps @720p auf der RK3588.
   Das fps-Ziel (25) ist damit belegt erreichbar — die Input-Surface wäre auf diesem Board
   *unbewiesen* und müsste erst validiert werden.
2. **OSD identisch zu Foto/Schaden.** Der ByteBuffer-Pfad rendert das OSD mit **derselben**
   `OsdRenderer.renderBitmap`-Funktion wie die Foto-/Schaden-Aufnahme. Damit ist der ins Video
   gebrannte Meterwert **pixelgleich** zu dem, den ein Standbild aus dem Video zeigt — das ist
   direkt Ziel #3 (Werte im Bild) und Ziel #4 (Station == eingebrannter Meterwert). Eine
   GLES-Pipeline müsste das OSD in GL neu zeichnen (zweite, potenziell abweichende Darstellung).
3. **Geringeres Surface-Lifecycle-Risiko.** Kein EGL-Context, kein GL-Texture-Upload, keine
   geteilte-Context-Teardown-Race — genau die Klasse Fehler, die die adversariale Review als
   Risiko listet. Der ByteBuffer-Pfad hat keinen Surface-Lebenszyklus.
4. **Kopf-Budget reicht.** Bei 25 fps stehen 40 ms/Frame zur Verfügung; die zusätzliche
   CPU-Last des Aufnahmewegs (Bitmap.copy ~2–5 ms + OSD-Canvas ~1–3 ms + native I420 ~3–6 ms)
   liegt weit darunter; der HW-Encode selbst läuft asynchron.

**Verworfen: GLES-Input-Surface.** Theoretisch verlagert sie die YUV-Konvertierung auf die GPU
(~3–6 ms native CPU gespart), bringt aber neuen EGL/GL-Code, ein zweites OSD-Rendering und
Surface-Lifecycle-Risiko — ohne belegten fps-Vorteil gegenüber dem bereits ausreichenden
ByteBuffer-Pfad. **Fallback-Option, falls die Geräte-Abnahme 25 fps HD verfehlt** (dann eigener
Folge-ADR).

**Konsequenz für `H264Encoder` (RTSP unberührt):** Neue Überladung
`encode(bm: Bitmap, ptsUs: Long)`. Das bisherige `encode(bm)` delegiert an sie mit der internen
`nanoTime`-Uhr → **RTSP-Verhalten bit-identisch**. Nur der Recorder speist eine eigene,
pausenbereinigte PTS ein (Entscheidung 3).

---

## Entscheidung 2 — Absturzsicherheit: **Self-Framing-Elementarstrom-Journal + Mux beim Stopp + Auto-Recovery beim nächsten Start**

`MediaMuxer` schreibt den `moov`-Index erst bei `stop()`; ein Prozess-Kill davor hinterlässt eine
`moov`-lose, **unspielbare** MP4. Das wäre ein Rückschritt gegenüber Welle 2 und ist **nicht
akzeptabel**. Gewählter Weg:

1. **Live:** Jede fertige Access-Unit des HW-Encoders wird **sofort append-only** in ein
   **Journal** (`<video>.h264j`) geschrieben — self-framing je Record:
   `[u32 len][u64 ptsUs][u8 keyframe][len Bytes Annex-B]`, Kopf einmalig mit `magic/version/w/h`
   + SPS/PPS (aus `onConfig`). Append-only = ein Kill hinterlässt einen **gültigen Präfix**; ein
   torn tail (halber letzter Record) ist an der Längenangabe erkennbar und wird verworfen.
2. **Gewollter Stopp:** Journal schließen → `muxJournalToMp4(journal, finalFile)` über
   `MediaMuxer` mit **exakter Per-Sample-PTS** → normale, sofort seekbare MP4 → Journal löschen.
3. **Kill während Aufnahme:** Journal bleibt liegen. **Beim nächsten App-Start** finalisiert
   `recoverOrphanJournals(dir)` jedes verwaiste Journal automatisch zu einer abspielbaren MP4 —
   **exakt dieselbe „Reparatur beim nächsten Start"-Mechanik wie `cleanupOrphanFrags` in Welle 2.**
   Der Anwender findet in der Projekt-Videoliste immer eine spielbare Datei; er sieht nie eine
   kaputte.

**Warum so, und nicht der Welle-2-ffmpeg-Weg unverändert:** Der ffmpeg-Fragment-MP4-Weg kann
die **variable, echte PTS nicht tragen**. `ffmpeg -c copy` eines rohen Annex-B-Elementarstroms
hat keine Container-Zeitbasis → ffmpeg re-imponiert `-framerate` (CFR) → **genau der Zeitraffer,
den wir beheben.** Um ffmpeg VFR-PTS zu geben, müsste man den Strom selbst zu MPEG-TS
paketieren (PCR/PTS) — mehr neuer Code und Risiko als `MediaMuxer`, ohne Absturzsicherheits-Gewinn
gegenüber dem Journal.

**Warum nicht segmentierte MP4 + Concat:** verliert beim Kill bis zu ein ganzes Segment; das
Journal verliert höchstens den letzten halben Frame. Zusätzlich Concat-Komplexität.

**Vergleich zur Welle-2-Eigenschaft (ehrlich):** Welle 2 hinterließ eine **direkt** (z. B. per
VLC) abspielbare `*.frag.mp4`. Das Journal ist roh und braucht die Recovery-Stufe, um in-App
abspielbar zu sein. Mitigation: Recovery läuft **automatisch beim nächsten Start** (gleiche
beobachtbare Semantik: „nach einem Kill ist die Aufnahme da und spielbar") und ist durch das
Self-Framing **robuster** gegen abrupte Abbrüche als ein torn MP4-Fragment. Der Datenverlust ist
auf die eine gerade nicht fertig geschriebene AU begrenzt. **Abnahme-Kriterium bleibt:** „App
während Aufnahme killen → zurückbleibende (nach Neustart finalisierte) Datei ist abspielbar."

---

## Entscheidung 3 — Echte Zeitstempel (VFR), pausenbereinigt

`presentationTimeUs` je Bild aus einer **monotonen Uhr** (`System.nanoTime()`),
**abzüglich der akkumulierten Pausenzeit**:

```
ptsUs = (nanoTime() − startNs − pausedAccumNs) / 1000
```

- **startNs** = `nanoTime` des ersten kodierten Frames → erster Frame ≈ PTS 0.
- **pausedAccumNs** wächst um die Dauer jeder Pause (Resume − Pause). Damit läuft die Medienzeit
  über eine Pause **nahtlos weiter** („Pause fehlt im Video", Welle-2-Verhalten) und ist per
  Konstruktion **streng monoton steigend** → `MediaMuxer`-tauglich.
- Da die Container-Dauer == letzte PTS, gilt **Videodauer == pausenbereinigte Echtzeit** —
  unabhängig davon, wie viele Bilder das Gerät real schafft. **Variable Bildrate ist erwünscht.**
- Der Ziel-fps-Wert (25) ist nur noch **Rate-Hint** für die Encoder-Ratensteuerung/GOP, nicht
  mehr die Zeitbasis. Der **tatsächlich erreichte** Mittelwert wird am Gerät gemessen und im
  RESULT protokolliert, nicht behauptet.

---

## Entscheidung 4 — Meter-Spur v3 (Zeitachse) als **additiver, paralleler Stack**

Weil die Zeitbasis jetzt eine echte Uhr ist, wird die Meter-Spur auf **dieselbe Achse** gestempelt,
die auch `presentationTimeUs` speist: Kopf `{"v":3}`, Samples `{"tUs":<pts>,"m":<meter>}`.
Nachschlagen bei Wiedergabe über `exoPlayer.currentPosition` (ms → µs), lineare Interpolation.

**Umsetzung ohne Regression (Kernentscheidung):** Der **gesamte v2-Stack bleibt unverändert** —
`MeterSample(frameIndex)`, `MeterTrack(fps)`, `lookupMeter` (frame-basiert), `MeterTrackReader`,
`MeterTrackWriter`. Damit bleiben **alle Welle-4b-Unit-Tests wortgleich grün** (`LookupMeterTest`,
`MeterTrackWriterTest`), und der (als Rückfallebene erhaltene) `LocalBitmapRecorder` schreibt
weiter v2. v3 kommt als **neue, eigenständige Dateien** hinzu: `MeterSampleV3(tUs)`,
`MeterTrackV3`, `lookupMeterV3` (zeit-basiert), `MeterTrackReaderV3` (liest **nur** v3),
`MeterTrackWriterV3`.

**Reader-Weiche in Produktion:** `VideoPlaybackDialog` liest ausschließlich **v3**. v1- und
v2-Sidecars ergeben dort per Versionsabgleich `MeterTrackV3.EMPTY` → **Stufe-1-Fallback (leeres
Pflichtfeld, nie stilles 0.00)** — genau das Abnahme-Kriterium „Alte Aufnahmen (v1/v2) → leeres
Pflichtfeld". „v2-Logik bleibt im Code, solange der alte Recorder existiert" ✓ (v2-Writer/Reader
unangetastet; nur nichts in Produktion liest v2 mehr).

---

## Entscheidung 5 — Feature-Flag `FeatureFlags.useHardwareRecorder` (Default AN) + Bildrate an EINER Stelle

- Neuer Weg hinter **`FeatureFlags.useHardwareRecorder`** (Default **AN** für den Testbuild),
  DataStore-gestützt (`app_settings`) und über einen **Schalter in den Einstellungen** zur
  Laufzeit umschaltbar. Der bestehende `LocalBitmapRecorder` bleibt **vollständig erhalten** und
  ist bei Flag AUS die Aufnahme-Engine. Umschalten wirkt auf die **nächste** Aufnahme (nicht
  mitten in einer laufenden — siehe adversariale Review). **Nichts wird gelöscht.**
- Die drei hartkodierten `12` (`LocalBitmapRecorder.start`-Default Z. 88, `InspectionScreen`
  Z. 1501/1549) werden durch **eine** Konstante `RecorderConfig.TARGET_FPS = 25` ersetzt.

---

## Konsequenzen

### Positiv
- 25 fps HD belegbar erreichbar (bewiesener HW-Encoder), Echtzeit per Konstruktion (VFR-PTS).
- OSD im Video == OSD im Foto (gemeinsamer `OsdRenderer`) → Ziel #3/#4 selbst-konsistent.
- Absturzsicherheit erhalten und im torn-tail-Fall robuster (Self-Framing).
- Rückfallebene bewiesen (alter Recorder unverändert, per Schalter erreichbar).
- Welle-2/3/4/4b-Unit-Tests bleiben grün (v2-Stack unangetastet, additive v3-Dateien).

### Negativ / Risiken (Mitigation in der adversarialen Review)
- Journal ist nach Kill nicht *direkt* in-App spielbar → Auto-Recovery beim nächsten Start.
- Neuer Code-Pfad (Encoder-Stall, Pause/Resume-PTS, Flag-Umschaltung) → adversariale Selbst-Review
  vor dem letzten Commit (Prompt-Leitplanke).
- Globaler mutabler Flag-Zustand → Single-Source-of-Truth in `FeatureFlags`, aus DataStore
  restauriert, nur über den Settings-Schalter geschrieben.

### KRITIS (Datenqualität als Beweismittel)
- **Rein lokale Verarbeitung** — kein neuer Netz-/Logging-Datenfluss, keine neue Permission
  (Bus, MediaCodec, MediaMuxer sind lokal; das Journal liegt im App-Verzeichnis).
- **Zeittreue** (VFR-PTS == Echtzeit) und **Absturzsicherheit** (Journal + Auto-Recovery) sind
  die beiden Datenqualitäts-Punkte; beide werden im RESULT dokumentiert und am Gerät abgenommen.

---

## Alternativen (verworfen)
- **GLES-Input-Surface** (Entscheidung 1): unbewiesen auf dem Board, zweites OSD-Rendering,
  Surface-Lifecycle-Risiko. Dokumentierter Fallback, falls ByteBuffer 25 fps HD verfehlt.
- **`MediaMuxer` allein, ohne Journal:** kein Crash-Schutz (moov erst bei stop()).
- **ffmpeg-Fragment-MP4 (Welle-2-Weg) mit HW-H.264:** `-c copy` kann VFR-PTS nicht tragen →
  CFR → Zeitraffer zurück; MPEG-TS-Eigenbau zu teuer.
- **Segmentierte MP4 + Concat:** Segmentverlust beim Kill, Concat-Komplexität.

## Offene Punkte (Geräte-Abnahme, vor Freigabe an Louis)
- 60-s-Test (Länge == 60 s ± 1 s), ≥ 5-min-Aufnahme mit Pause, gemessene fps ≥ 24 im Mittel.
- OSD korrekt; Station aus dem Video an 3 Stellen == eingebranntem Wert; SD wie HD.
- Kill-Test → abspielbare Datei; Flag AUS → alter Weg unverändert; alte v1/v2 → leeres Pflichtfeld.

---

## Nachschärfung nach adversarialer Review (2026-07-09)

Eine adversariale Design-Review (7 unabhängige Angreifer + Synthese, alle Befunde code-belegt)
stufte die ADR als **NO-GO wie geschrieben** ein und schloss acht mechanik-nahe Lücken. Die
Entscheidungen unten sind jetzt **implementierungs-vollständig**; sie präzisieren, ersetzen aber
nicht die obigen Grundsatzentscheidungen.

### B1 — Ein Encoder auf `c2.rk.avc.encoder`: **gegenseitiger Ausschluss statt Parallelität**
`OneApp.kt:65` startet im DIRECT-Modus `OneVideoServer` (H264Encoder #1, `encodeLoop` läuft
dauerhaft). Lokale Aufnahme läuft **im selben Modus** (nur wenn `rtspUrl.isEmpty()`). Ein zweiter
`H264Encoder` auf demselben HW-Codec ist unsicher (zweites `configure()/start()` wirft/verhungert,
`release()` des einen kann den anderen korrumpieren). **Entscheidung:** Ein DI-`single`
**`CameraEncoderArbiter`** koordiniert. `HardwareBitmapRecorder.start()` ruft
`arbiter.acquireForRecording()` (setzt Flag, wartet bis OneVideoServer seinen Codec **freigegeben**
hat, mit Timeout); `OneVideoServer.encodeLoop` gibt bei gesetztem Flag den Encoder frei
(`stop()`+null) und legt ihn nach `release()` neu an. **Nie zwei Encoder gleichzeitig.** RK3588 =
Ein-Encoder-Annahme; ob zwei Sessions gerätetauglich sind, wird separat abgenommen (dann Gate
lockerbar). RTSP-Sicht ist während der Aufnahme eingefroren (dokumentierte Einschränkung).

### B2/B3 — PTS-Uhr, Pause, Monotonie (H264Encoder-Erweiterung, RTSP unberührt)
- Neue Überladung **`H264Encoder.encode(bm, ptsUs: Long)`**; altes `encode(bm)` delegiert →
  RTSP bit-identisch (RTP-Zeitstempel sind relativ, daher unkritisch).
- **`startNs` wird beim ERSTEN kodierten Frame gesetzt** (lazy), nicht in `start()` → erster
  Frame ≈ PTS 0 (Container- und Meter-Achse aligned).
- `HardwareBitmapRecorder` führt `pausedAccumNs` über eine RECORDING↔PAUSED-Zustandsmaschine
  (ungepaarte Übergänge verworfen); `ptsUs = (nanoTime − startNs − pausedAccumNs)/1000`.
- **Monotonie-Garantie:** in der Mux-/Journal-Schreibseite `lastPtsUs` cachen; `ptsUs ≤ lastPtsUs`
  → `ptsUs = lastPtsUs + 1` (MediaMuxer verlangt streng steigende PTS).

### B4 — EOS/Final-Drain beim Stopp
`H264Encoder.stop()` verwirft heute die letzten gepufferten AUs. **Neu `drainFinal()`:** leeren
Input-Buffer mit `BUFFER_FLAG_END_OF_STREAM` einreihen, `dequeueOutputBuffer` bis EOS drainen,
alle AUs mit korrekter PTS ins Journal. **Stopp-Reihenfolge:** Frame-Zufuhr stoppen → Schleife
join → `drainFinal()` → `muxJournalToMp4` → Journal löschen.

### B5/B6 — SD-Skalierung + immer Kopie
- `HardwareBitmapRecorder` **skaliert das Bitmap VOR `encode()`** auf die Zielauflösung
  (SD 720×576 / HD nativ) via `Bitmap.createScaledBitmap`; der Encoder konfiguriert sich aus dem
  ersten **skalierten** Frame (keine Mid-Stream-Größenänderung).
- **Immer** eine mutable Kopie des Bus-Bitmaps vor `encode()` (mit ODER ohne OSD) — der
  konflatierende Producer recycelt/ersetzt Bitmaps; Direkt-Encode des geteilten Bitmaps racet
  `fillImage` (`getPixels`/native lock) gegen das Recycling. Die „no-copy"-Optimierung entfällt.

### B7 — Journal-Framing, Recovery, Quarantäne
- Journal-Kopf: `magic/version/w/h/SPS/PPS` (SPS/PPS aus dem ersten `INFO_OUTPUT_FORMAT_CHANGED`
  bzw. `onConfig`, **vor** dem ersten AU geschrieben). Jeder Record: `magic + [u32 len][u64 pts]
  [u8 keyframe][len Bytes]`, als **ein** gepufferter Write (atomar je Record).
- Recovery **einmal beim App-Start, serialisiert (File-Lock)**, VOR jeder möglichen Aufnahme:
  alle verwaisten `.h264j` sammeln → je best-effort muxen (Log je Datei) → dann Frag-Cleanup.
  Beim Muxen leitende Nicht-Keyframe-AUs überspringen (MediaMuxer: erstes Sample = Keyframe).
  Wiederholt nicht muxbare Journale → `.recovery_failed/` (kein Endlos-Retry, kein stiller Verlust).
- Torn-Tail **positiv** erkennen: Record-`magic` + Längenprüfung gegen Restbytes; unvollständiger
  letzter Record wird verworfen.

### B8 — Export
`const val JOURNAL_SUFFIX = ".h264j"`; in `UsbExportService` (Z. 79) und `ProjectExportService`
(Z. 390) analog zu `FRAG_SUFFIX`/`METER_SIDECAR_SUFFIX` ausschließen.

### Majors (bei der Umsetzung)
- **Meter v3 ↔ Containerzeit:** v3-Samples nur nach `encode()==true` schreiben (1:1 zum kodierten
  Frame); tUs == der gefütterten Input-PTS; erster Frame ≈ 0 ⇒ aligned mit `exoPlayer.currentPosition`.
- **`VideoPlaybackDialog`:** Invarianten-Check bei `MeterTrackV3.EMPTY` überspringen; v3-Dauer aus
  letzter PTS; Toleranz ±2 Frame-Dauern.
- **Abstraktion:** `interface Recorder` (`state: StateFlow<RecordingState>`, `isRecording/isPaused`,
  `start(…)`, `stop(onDone)`, `pause/resume/cancel`) + `RecorderFactory(context, useHardware, …)` +
  gemeinsames `RecordingState { IDLE, RECORDING, PAUSED, FINISHING }`; die zwei `start()`-Signaturen
  in `InspectionScreen` (1500/1548) zu **einer** mit Default-OSD-Parametern vereinen; beide
  Call-Sites verzweigen auf den `start()`-Rückgabewert (`isRecording = started`).
- **Flag-Sicherheit:** `FeatureFlags` in `OneApp.onCreate` eager (kurz blockierend) initialisieren;
  Settings-Schalter **während `isRecording` deaktivieren** (Flip kann keine aktive Aufnahme verwaisen).
- **Geräte-Abnahme zusätzlich:** RTSP+Aufnahme gleichzeitig (Gate greift), 100-Frame In/Out-PTS-
  Ordnung (streng monoton, B-Frames=0), Kill-bei-Frame-2 (erstes Sample = Keyframe), SD-nach-HD,
  Pause/Resume-Dauertreue.

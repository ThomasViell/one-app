# W3c-Video — Pipeline-Latenz senken (stationär ~1,5 s → Ziel ~300–500 ms)

**Branch:** `feature/dual-mode`  **Datum:** 2026-06-25
**Ausgang:** Nullpunkt/Rebase gelöst (Start 60 s → ~1,5 s); jetzt stationäre Pipeline-Latenz.
**Status:** 5 Hebel als Code umgesetzt, `assembleDebug` + Unit-Tests grün. Glass-to-Glass
neu messen = **TODO(device) Thomas** (s. u.).

## Commits (einzeln, je Hebel)

| Commit | Hebel | Datei(en) |
|---|---|---|
| `581cfd6` | 1 — GOP 1 s → 0,5 s | `H264Encoder.kt` + `H264EncoderFormatTest.kt` |
| `d33e716` | 4 — keine B-Frames | `H264Encoder.kt` + Test |
| `46545c7` | 3 — TCP_NODELAY zentral + Test | `SocketTuning.kt`, `RtspVideoServer.kt`, `SocketTuningTest.kt` |
| `b9d7935` | 2 — Live-Player Low-Latency-Decoder + Live-Kante | `FfmpegVideoPlayer.kt`, `VideoPlayer.kt` |
| `79524f6` | 5 — Encode-Latenz-Sample loggen | `H264Encoder.kt` |

---

## Hebel im Detail (größter zuerst)

### Hebel 1 — GOP 1 s → 0,5 s  *(erwartet: größter Mover)*
**Änderung:** `KEY_I_FRAME_INTERVAL` von `setInteger(1)` auf `setFloat(0.5)` (konfigurierbar
über `iFrameIntervalSec`, Sub-Sekunde via `setFloat`, da der Key erst ab API 25 float-fähig ist).
MediaFormat-Aufbau in die reine, testbare Funktion `buildAvcFormat()` ausgelagert.
**Erwartete Wirkung:** Die „1-GOP"-Wartezeit, bis Player/Decoder auf einen IDR (re)joinen bzw.
ihre GOP-proportionale Pufferung füllen, halbiert sich. Das ist der Posten, der die beobachteten
~1,5 s am ehesten erklärt (1 s GOP ≈ der Löwenanteil). 0,25 s ist als nächster Schritt denkbar.
**Risiko/Nebenwirkung:** Bei CBR kosten häufigere Keyframes Bits → minimal weniger P-Frame-Qualität;
`bitRate` (Default 4 Mbit/s) bei Bedarf leicht anheben. **TODO(device):** prüfen, ob der
Rockchip-HW-Encoder 0,5/0,25 s **ehrt** statt auf 0 (jeder Frame IDR) oder 1 zu runden — im Logcat
am IDR-Abstand / AU-Größen sichtbar.

### Hebel 2 — Live-Player: KEY_LOW_LATENCY-Decoder + Live-Kante  *(erwartet: zweitgrößter Mover)*
**Befund (neu):** Der **produktive** Live-Player ist `FfmpegVideoPlayer` (rendert den ONE-RTSP-Stream
in `InspectionScreen`). Er nutzte bisher nur `DefaultRenderersFactory` — **ohne** `KEY_LOW_LATENCY`
und **ohne** Live-Catch-up. Diese Optimierungen lagen nur im selten genutzten `VideoPlayer`
(ConnectionScreen). Die Annahme „LoadControl/Low-Latency ist schon aktiv" traf also auf den
**falschen** Player zu.
**Änderung:** `LowLatencyRenderersFactory`/`LIVE_TARGET_OFFSET_MS` auf `internal` gehoben; beide
Player teilen sie jetzt. `FfmpegVideoPlayer` bekommt zusätzlich `DefaultLivePlaybackSpeedControl`
und `MediaItem.LiveConfiguration` (Ziel-Offset 200 ms). LoadControl bleibt 0/100/0/0 (war hier
bereits korrekt aggressiv).
**Erwartete Wirkung:** `KEY_LOW_LATENCY` lässt den HW-Decoder jeden Frame sofort ausgeben statt zu
batchen (~1–2 Frame-Dauern, ~33–66 ms bei 30 fps) — und unterbindet vor allem ein decoderseitiges
Halten ganzer GOP-Fenster. Live-Catch-up verhindert das Aufsummieren von Drift (No-op, falls die
RTSP-Timeline nicht live geführt wird — dann harmlos).

### Hebel 3 — TCP_NODELAY (Nagle aus)  *(bereits aktiv; gehärtet + getestet)*
**Befund:** War im `acceptLoop` bereits gesetzt (`sock.tcpNoDelay = true`).
**Änderung:** In `tuneLowLatencySocket()` ausgelagert (Intent zentral, tolerant gegen Fehlschlag)
und per Loopback-Test abgesichert. Kein Verhaltenswechsel, aber Garantie nun getestet.
**Wirkung:** Kleine RTP-Pakete (FU-A-Fragmente, kurze NALs) gehen sofort raus statt vom Kernel
gebündelt zu werden — verhindert bis ~40 ms/Paket Coalescing-Latenz. (Bereits im ~1,5-s-Messwert
enthalten, daher kein zusätzlicher Gewinn — nur abgesichert.)

### Hebel 4 — Encoder ohne B-Frames  *(klein, aber sicher)*
**Befund:** `KEY_LATENCY=1`, `KEY_PRIORITY=0` (realtime), CBR waren bereits gesetzt.
**Änderung:** `KEY_MAX_B_FRAMES=0` explizit. Erzwingt reine I/P-Reihenfolge ⇒ kein
Decoder-Reorder-Delay (≈ eine Frame-Dauer) und keine encoderseitige Lookahead-Pufferung.
**Wirkung:** ≤ ~33 ms; Absicherung gegen einen B-Frame-Default des HW-Encoders.

### Hebel 5 — Encode-Latenz loggen  *(Diagnose, nicht latenzsenkend)*
**Änderung:** `sampleEncodeLatency()` misst alle 60 Frames (~2 s) die reine Encoder-Latenz
Input→Output: `now − (startNs + ptsUs·1000)`. Da `ptsUs` aus derselben monotonen Uhr wie `startNs`
stammt, ist das exakt die Zeit vom Einspeisen bis zur fertigen Access-Unit. Auch via
`lastEncodeLatencyMs` abgreifbar. Logcat: `H264Encoder: Latenz-Sample: encode=…ms AU=…B IDR/P frames=…`.
**Zweck:** auf dem Gerät den Encode-Anteil vom Rest (Netz/Decode/Render) trennen.

---

## Latenz-Budget (stationär) & verbleibende Hauptquelle

```
Kamera /dev/video0 (MS2109, MJPEG)
  → BitmapFactory JPEG-Decode        ~40–80 ms   ← Ingestion
  → CPU RGB→YUV420 (fillImage, 1280×720, Kotlin-Loop)  ~10–30 ms   ← Ingestion
  → HW-AVC-Encode                    Hebel 4/5, ~5–20 ms (on-device messbar)
  → RTP/FU-A + TCP (NODELAY)         Hebel 3, <1 ms + WLAN-RTT 2–10 ms
  → ExoPlayer Sample-Queue           LoadControl 0/100/0/0
  → HW-AVC-Decode (KEY_LOW_LATENCY)  Hebel 2, ~5–15 ms
  → TextureView + Compositor         ~16–33 ms (1–2 vsync)
```

Die Summe der **stationären** Schritte liegt theoretisch bei ~150–300 ms. Die gemessenen ~1,5 s
lagen also deutlich darüber — konsistent mit einem Posten, der **~1 GOP (= 1 s)** gepuffert/gewartet
hat: die **lange GOP (Hebel 1)** und der **fehlende Low-Latency-Decoder im echten Live-Player
(Hebel 2)**. Diese beiden sind daher die erwartet größten Mover.

**Verbleibende Hauptlatenzquelle nach diesen Hebeln (Erwartung):** die **Capture-Ingestion**
— BitmapFactory-JPEG-Decode **+** CPU-RGB→YUV — plus der **Display-Compositor** am Ende. Genau die
Ingestion adressiert der bereits dokumentierte **TODO(perf) Zero-Copy-Pfad** (V4L2-MJPEG →
HW-Decoder → Surface → HW-Encoder): er eliminiert JPEG-Decode + manuelles YUV, hebt auf volle 30 fps
und senkt CPU/Latenz — der nächste strukturelle Schritt, falls Hebel 1+2 noch nicht unter ~500 ms
bringen.

---

## TODO(device) — Thomas

1. **Glass-to-Glass neu messen** (Stoppuhr/Highspeed gegen Live-Timer): Ziel ~300–500 ms,
   stationär stabil (keine Drift über 1–2 min).
2. **Logcat `H264Encoder`** mitlesen: `encode=…ms` bestätigt, ob Encode billig ist (~erwartet
   <20 ms). Bleibt G2G trotz billigem Encode hoch → Rest sitzt in Ingestion/Player/Display.
3. **GOP-Honorierung** prüfen (IDR-Abstand/AU-Größen): ehrt der Encoder 0,5 s? Falls 0,25 s den
   Encoder nicht zu „alles IDR" zwingt und die Qualität hält → `iFrameIntervalSec = 0.25f` testen.
4. Bei sichtbarem Qualitätsverlust durch häufigere Keyframes: `bitRate` leicht anheben
   (Default 4 Mbit/s; SDP `b=AS` entsprechend nachziehen).

---

# Fortschreibung 2026-07-03 — Messung ~500 ms am Tablet, Ziel ≤250 ms

**Messkontext (E2E-Test, TESTREPORT_DUAL_MODE_E2E_2026-07-03.md):** ONE DIRECT → LOHS-Hotspot
(2,4 GHz, RSSI −44) → Tab A9+, ConnectionScreen-Preview. Encoder-Latenz real gemessen:
**25–43 ms** (höher als die erwarteten <20 ms, ≈ 1 Frame-Dauer), ~29 fps stabil.

## Latenz-Budget (Ist, erklärt die ~500 ms)

| Posten | Anteil | Beleg/Herleitung |
|---|---|---|
| **GOP-Join-Offset** — Server sendet ab PLAY sofort mitten in die GOP (kein IDR-Request, kein Keyframe-Gate in `RtspVideoServer.onAccessUnit`); Decoder wartet bis zum nächsten IDR (Ø ½ GOP), RTSP hat keinen Live-Catch-up → der Versatz bleibt dauerhaft | **~250 ms Ø** (0–500 ms je Join!) | Code `RtspVideoServer.kt` (`playing`-Flag ohne Keyframe-Gate); Doku-Hinweis „liveSpeedControl = No-op bei RTSP" |
| Ingestion: MJPEG→`BitmapFactory`(RGB_565) + `getPixels`+Kotlin-RGB→YUV | ~60–100 ms | `V4L2Camera.kt:85`, `H264Encoder.fillImage` |
| HW-Encode (inkl. Input-Queue) | ~25–43 ms | Logcat-Messung heute |
| WLAN inkl. **STA-Power-Save am Tablet** | ~20–80 ms | ping-RTT 1,6→64 ms Jitter; dumpsys zeigte Rx-Link 1 Mbps (Power-Save-Indiz) |
| Player Decode+Render (KEY_LOW_LATENCY aktiv, TextureView) | ~30–50 ms | Code verifiziert |
| Capture-Kadenz (Ø ½ Frame) + Fan-out-Sampling (2 ms-Poll) | ~18 ms | `OneVideoServer.encodeLoop` |

Summe ≈ 400–530 ms → deckt sich mit der beobachteten ~500-ms-Wahrnehmung.

## Maßnahmen (Wirkung/Aufwand, empfohlene Reihenfolge)

### M1 — IDR-on-PLAY + Keyframe-Gate (GRÖSSTER HEBEL, kleiner Eingriff)
Bei RTSP `PLAY`: (a) Encoder-Sync-Frame anfordern
(`MediaCodec.setParameters(PARAMETER_KEY_REQUEST_SYNC_FRAME)` — Methode `requestKeyframe()` in
`H264Encoder`, durchgereicht `RtspVideoServer → OneVideoServer`), (b) in `sendAccessUnit` erst ab
dem ersten Keyframe der Session senden (Gate; SPS/PPS-inband existiert schon).
**Erwartung: −250 ms Ø, Join deterministisch ~1 Frame.** Danach ist GOP 0,5 s für die stationäre
Latenz egal (könnte für Qualität sogar wieder rauf).

### M2 — WifiLock `WIFI_MODE_FULL_LOW_LATENCY` am Tablet (klein)
Während aktivem Stream halten (Player-Lifecycle), API 29+. Schaltet STA-Power-Save ab —
eliminiert die 60-ms-Bursts und glättet den Stream. **Erwartung: −30–150 ms + weniger Jitter.**
(Gleiches Problemfeld wie BWELLs `EthernetPreferUtil`-Befund; dort Netz-Pinning, hier Power-Save.)

### M3 — Ingestion ohne Bitmap-Umweg (strukturell, der Weg unter ~150 ms)
Stufe a (moderat): **libjpeg-turbo direkt zu YUV420** im vorhandenen `v4l2bridge` (NDK) — JPEG
einmal dekodieren, YUV direkt in die MediaCodec-Input-Planes; lokale Anzeige aus derselben
Dekodierung (RGB-Ausgabe) speisen. Ersetzt BitmapFactory+getPixels+Kotlin-YUV-Schleife.
**Erwartung: −40–70 ms + deutliche CPU-Entlastung.**
Stufe b (groß): Voll-Zero-Copy MJPEG → HW-MJPEG-Decoder (Rockchip `c2.rk.mjpeg.decoder`,
Existenz on-device prüfen: `dumpsys media.player | grep -i mjpeg` bzw. MediaCodecList) → Surface →
Encoder-InputSurface; lokale Anzeige als zweiter Surface-Konsument. **Erwartung: Ingestion ≈ 0,
volle 30 fps, minimale CPU** — nur nötig, falls Stufe a nicht reicht.

### M4 — Kleinkram/Absicherung
- Encoder-Input-Queue: prüfen, ob `KEY_LATENCY=1` vom `c2.rk.avc.encoder` geehrt wird
  (25–43 ms ≈ 1 Frame Queue); ggf. Rockchip-Vendor-Keys.
- Tablet-Renderpfad: TextureView kostet ~1 vsync vs. SurfaceView — bleibt vorerst
  (Screenshot/Foto-Feature hängt daran).
- **Messbarkeit:** ms-Zähler ins ONE-OSD → ein Foto mit beiden Screens = exakte G2G-Zahl.

**Prognose:** M1+M2 ≈ **200–250 ms** (Ziel „halbieren" erreicht), mit M3a ≈ **130–180 ms**.

---

## Verifikation 2026-07-03 (nachmittags) — M1+M2 on-device bestätigt

**Setup:** Beide Geräte auf 0.5.0-alpha/500 (debug-signiert, M1+M2 enthalten). Produkt-Topologie:
ONE = LOHS-Hotspot (`AndroidShare_3231`, Subnetz 192.168.78.x), Tab A9+ joint per
`cmd wifi connect-network`. **Auto-Kette lief komplett ohne Eingriff:** Discovery fand die ONE,
Telemetrie :12345 verbunden (Meter + Akku am Tablet), InspectionScreen spielte automatisch —
F1/F3 treten in dieser Topologie nicht auf (Broadcasts kommen im ONE-eigenen Subnetz an).

**M1 (IDR-on-PLAY + Keyframe-Gate) — bestätigt:**
```
16:48:09.536  RtspVideoServer: RTSP <- PLAY (CSeq 3)
16:48:09.538  H264Encoder:     Sync-Frame angefordert (Client-Join)
16:48:09.538  RtspVideoServer: PLAY -> Streaming aktiv (warte auf IDR)
16:48:09.593  RtspVideoServer: Erster IDR der Session -> Stream läuft
```
→ **55 ms** von PLAY bis erstem gesendetem IDR (vorher Ø ~250 ms ½-GOP-Offset, dauerhaft).

**M2 (WifiLock) — bestätigt:** `dumpsys wifi` am Tablet zeigt während der Wiedergabe
`WifiLock{DrainQ:RtspLowLatency type=4 …}` (type 4 = FULL_LOW_LATENCY).

**Nebenprüfung Dauer-IDR:** ONE-tx ~531 KB/s ≈ 4,3 MBit/s = normales GOP-Muster
(IDR ~72 KB + P ~12–17 KB); `requestKeyframe()` erzwingt also nur EINEN Sync-Frame beim Join.
Encode weiterhin 25–44 ms.

**Offen:** Subjektive/objektive G2G-Messung (Winke-Test bzw. ms-Zähler-OSD aus M4) steht aus.

---

## Runde 2 (2026-07-03 abends) — M5/M6/M7 + F1-Gateway-Probe + GOP-Umbau

**Anlass:** User-Beobachtung „Latenz steigt mit Verbindungsdauer" — bestätigt als Player-Drift:
ExoPlayers Live-Speed-Control ist bei RTSP ein No-op; jeder WLAN-Stall brannte sich dauerhaft ein.

**Umgesetzt:**
- **M5 Latenz-Trim** (`RtspLatencyTrimEffect`, beide Player): Puffer-Füllstand pollen (400 ms);
  > Engage-Schwelle → Speed-Aufholen; > 5 s → Stream-Rejoin (Notbremse, dank M1 ~100 ms).
- **M6 Drain-to-Latest** (`v4l2bridge.c`): V4L2-FIFO leert bis zum neuesten Puffer + Statistik-Log.
  Messung: 0 Frames Altlast selbst unter Volllast — auf RK3588 aktuell kein Gewinn, bleibt als
  Absicherung gegen CPU-Druck (Statistik beweist es künftig mit).
- **M7 Latenz-Mess-OSD** (`V4L2Camera`): Uptime-ms in jeden Frame eingebrannt, zuschaltbar per
  `adb shell setprop log.tag.DqLatencyOsd DEBUG`. Foto beider Bildschirme = exakte
  Display-zu-Display-Differenz.
- **F1-Fix Gateway-Probe** (`OneHardwareService` + AppModule-Lambda via `WifiManager.dhcpInfo`):
  Gateway als ERSTER Probe-Kandidat — im ONE-Hotspot IST das Gateway die ONE. Gemessen: Treffer
  in 17–46 ms. Grund: UDP-Broadcast-Discovery war auch in der Produkt-Topologie GLÜCKSSACHE
  (16:48/17:17 ok, 17:21 gefiltert) — Samsung filtert unterhalb der App.
- **GOP 0,5 s → 2 s** (`H264Encoder`): Seit M1 hängt die Join-Latenz nicht mehr an der GOP.
  Kurze GOP = 72-KB-IDR-Burst alle 15 Frames = ±100 ms Anlieferungs-Jitter.
- **Trim-Tuning** 200/60/1,1 → **250/120/1,05**: Telemetrie zeigte, dass Trimmen unter den
  Jitter-Boden selbst Underruns erzeugt (state=2-Stall, Mikro-Freeze, Latenz zurück).

**Telemetrie-Vergleich (je 90-s-Fenster, Trim-Poll alle 400 ms):**

| | GOP 0,5 s + Trim 200/60/1,1 | GOP 2 s + Trim 250/120/1,05 |
|---|---|---|
| Rebuffer-Stalls (state=2) | ~10 (JEDER Trim endete im Stall) | **2** (nur Start-Transiente, letzte 60 s stallfrei) |
| Saubere Trim-Zyklen | 0 | 2 („AUS Puffer 118 ms") |
| Puffer-Band | 111–229 ms, Sägezahn | 135–265 ms, gehalten, selbstheilend |

**Stand:** Latenz ist jetzt STABIL über die Zeit (kein Ratcheting mehr), Stream ruckelfrei ab
Sekunde ~30. Stehender Puffer Ø ~190 ms = größter Restposten; sein Boden ist der
Anlieferungs-Jitter (±40–50 ms verbleibend: Funk + Frame-Drops bei Encode-Backpressure).

**Nächste Hebel (offen):**
- **M3a** libjpeg-turbo→YUV direkt (−40–70 ms Ingestion, glättet zusätzlich den Encode-Takt).
- **M9-Idee:** `KEY_INTRA_REFRESH_PERIOD` (Rolling-Intra statt IDR-Bursts) — macht Frames
  gleich groß, könnte den Jitter-Boden und damit TRIM_RELEASE weiter senken (Rockchip-Support
  on-device prüfen).
- **M8-Idee:** Send-Queue im RtspVideoServer (blockierender TCP-Write sitzt im Encoder-Thread).
- Objektive G2G-Zahl per M7-Foto steht aus.

**G2G-Messung per M7-Foto (2026-07-03, User):** ONE 54940 / Tablet 54678 → **Δ 262 ms
Display-zu-Display** (deckungsgleich mit der Telemetrie-Herleitung: Puffer Ø ~190 + Encode ~30
+ Netz ~5 + Decode/Render ~40 ≈ 265). Volle Glass-to-Glass aufs Tablet ≈ Δ + Kamera→ONE-Anzeige
(~90–110 ms) ≈ ~350–370 ms, davor ~500 ms MIT Drift nach oben. Der Restposten ist der
Puffer-Boden (TRIM_RELEASE 120 ms + Rest-Jitter) → Weg unter 200 ms Δ: M9 Intra-Refresh
(gleich große Frames → Boden senken), M8 Send-Queue, M3a Ingestion.

---

## Runde 3 (2026-07-03 spät) — M3a nativ, M8 Send-Queue, M9-Befund

**Umgesetzt und on-device verifiziert (bis der ONE-Akku leer war):**
- **M3a** Native RGB→I420 (`v4l2bridge.c` + jnigraphics, `H264Encoder.fillImage`):
  AndroidBitmap_lockPixels + C-Schleife (565+8888, BT.601 studio swing) ersetzt
  getPixels+Kotlin (~25–40 ms → ~3–6 ms erwartet); Kotlin-Fallback bleibt (JVM-Tests,
  exotische Formate). Aktiv bestätigt (kein Fallback-Warning, 30 fps stabil).
- **M8** Send-Queue (`RtspVideoServer.Session`): 8-AU-Queue + Sender-Thread; der Encoder-
  Thread blockiert nie mehr am TCP-Write. Rückstau ⇒ Queue verwerfen + `onKeyframeNeeded()`
  (= `requestKeyframe`, M1-Hook verallgemeinert) ⇒ Resync am frischen IDR — kein Nachschieben
  alter Bildstände, keine wahllos gedroppten P-Frames (Artefaktkette). LIVE-VALIDIERT durch
  den Akku-Brownout: Empfänger brach weg → 2× sauberer Resync im Log.
- **M9-BEFUND:** `c2.rk.avc.encoder` meldet FEATURE_IntraRefresh NICHT → capability-gated
  Fallback auf GOP 2 s aktiv (Log „GOP=2.0s"). Blindes Erzwingen verworfen: ohne
  verifizierbaren Rolling-Refresh wäre GOP=∞ ein Feld-Risiko (Korruption heilt nie).
  Code bleibt drin — greift automatisch auf Hardware, die das Feature meldet.
  Achtung Mess-Falle: Latenz-Sample alle 60 Frames = Vielfaches der 2-s-GOP (60 Frames)
  → Samples treffen IDRs nie/immer (Aliasing); AU-Größen der Samples sind KEIN
  Intra-Refresh-Beweis.

**90-s-Fenster (vor Akku-Tod): 0 Trim-Eingriffe, 0 Stalls, 0 Resyncs** — Puffer blieb
durchgehend unter der 250-ms-Schwelle. Offen (nächste Geräte-Session): G2G-Foto mit diesem
Stand (Vergleich zu Δ 262 ms), längerer Soak, ggf. TRIM_RELEASE 120→80 ms wenn der Boden
stabil niedrig liegt.

**G2G-Messreihe R3-Stand (2026-07-03 22:19, 10 Fotos, Abend-Funkumgebung):**
Δ Display-zu-Display in ms: 198, 202, ~206–226 (verwischt), 200, 232, 233, 236, 237, 263, 199
→ **Median ~226 ms, Bestwert 198 ms, Maximum 263 ms** (Puffer atmet im Trim-Band 120–250).
Referenz Nachmittag (VOR M3a/M8, Einzelmessung): 262 ms. Jede Probe der Abend-Serie liegt
auf/unter der Referenz; der Median bestätigt die M3a-Erwartung (−25–35 ms im Delta-Pfad).
Messnotiz: Zähler-Rollover bei 100 000 in der Serie enthalten (98587 → 01148) — Foto 9
(01148/00885, Δ 263) fiel in eine Trim-Phase kurz nach Funk-Burst (Telemetrie 22:17: Puffer
612 ms von Trim eingefangen). Nächster Feinschliff-Kandidat: TRIM_RELEASE 120 → 80–100 ms
(sauberer Trim-Ausstieg bei 101 ms gemessen), Risiko: Stall-Rate in Abend-RF beobachten.

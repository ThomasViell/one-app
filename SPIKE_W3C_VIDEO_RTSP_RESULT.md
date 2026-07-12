# SPIKE W3c — Live-Video-Stream der ONE ins Netz (RTSP/H.264)

**Branch:** `spike/video-rtsp` (von `feature/dual-mode`) · **KEIN Merge** · Wegwerf-Prototyp
**Datum:** 2026-06-24 · **Gerät:** RK3588 / Android 12 (`233b4bd2865177ed`, Board `rk30sdk`)
**Frage:** Kann die ONE ihren Kamera-Feed live ins Netz streamen? Machbarkeit + Latenz.

---

## TL;DR — Verdikt

**JA, W3c ist mit vertretbarem Aufwand machbar.** Der Spike streamt den V4L2-Feed
der ONE live als RTSP/H.264 und ist mit Standard-Tools (ffplay/VLC) am PC dekodierbar —
stabil, ohne Bild-Fehler, bei 1280×720. Der Stream nutzt den **Rockchip-Hardware-Encoder**
(`c2.rk.avc.encoder`), läuft also energie-/CPU-schonend.

**Empfehlung:** RTSP/H.264 weiterverfolgen. Für die Produktion den Wegwerf-RTSP-Server
durch eine gehärtete eingebettete Variante ersetzen und den CPU-Farbkonvertierungs-Pfad
durch einen **Zero-Copy-Pfad** (HW-MJPEG-Decode → Surface → HW-Encoder) tauschen — das
hebt FPS auf volle 30 und senkt die Latenz weiter. WebRTC nur, falls echte Sub-100-ms-Latenz
gefordert wird (deutlich höhere Komplexität, für die Krabbler-Live-Sicht **nicht** nötig).

---

## Was gebaut wurde (Minimalpfad)

```
/dev/video0 (MS2109, MJPEG 1280×720)
  → V4L2Camera               [vorhandener Reader, wiederverwendet]
  → Bitmap (RGB_565, BitmapFactory)
  → SpikeH264Encoder         MediaCodec AVC, getInputImage→YUV420 (BT.601), Low-Latency
  → SpikeRtspServer          RTSP + RTP/H.264 (RFC 6184, FU-A) über TCP-Interleaved, :8554/cam
  → adb forward tcp:8554     → ffplay/VLC am PC
```

Neue Dateien (alle nur auf dem Spike-Branch, Package `com.uip.oneapp.spike`):
- `SpikeRtspActivity.kt` — Standalone-Entry-Point (Vorschau + Pipeline-Start)
- `SpikeH264Encoder.kt` — Bitmap → H.264 (Lazy-Config aus echter Bitmap-Größe)
- `SpikeRtspServer.kt` — minimaler RTSP/RTP-TCP-Server (eine Session)
- `SpikeNalUtils.kt` — Annex-B-NAL-Splitter / SPS-PPS-Extraktion
- `AndroidManifest.xml` — Activity-Eintrag
- `app/build.gradle.kts` — `debug { applicationIdSuffix = ".spike" }` → Installation
  **neben** der Produktions-App (keine Daten-/Signatur-Kollision)

> **Designentscheidung RTP-over-TCP (interleaved):** bewusst gewählt, weil es der einzige
> Transport ist, der durch `adb forward` tunnelt (UDP nicht). Spart zugleich eine fragile
> externe RTSP-Lib — der ganze Server sind ~250 Zeilen ohne Fremd-Abhängigkeit.

---

## Ergebnisse (gemessen auf dem Gerät)

### Läuft der Stream stabil?
**Ja.** ffprobe/ffmpeg verbinden sauber über TCP; Dekodierung über mehrere Läufe
**0 Fehler / 0 Corruptions / 0 Concealment**. RTSP-Handshake (OPTIONS→DESCRIBE→SETUP→
PLAY→TEARDOWN) inkl. Reconnect mehrfach fehlerfrei.

| Lauf            | Ergebnis                                   |
|-----------------|--------------------------------------------|
| ffprobe         | h264 **Constrained Baseline**, 1280×720, **yuv420p**, 30 fps |
| ffmpeg 8 s      | 188 Frames dekodiert, keine Fehler         |
| ffmpeg 6 s      | 140 Frames dekodiert, keine Fehler         |
| HUD (Gerät)     | `cam frames=570 enc=522 fps≈27.4`          |

### Auflösung / FPS
- **Auflösung:** 1280×720 (native MS2109-Mode; vom Treiber 1:1 akzeptiert).
- **Encoder-FPS:** ~**27 fps** (HUD), am PC ankommend ~23–26 fps.
- **Kamera liefert** ~30 fps; der Encoder verarbeitet ~92 % davon (522/570) →
  ~8 % Drop. **Begrenzender Faktor ist nicht** die Kamera und **nicht** der HW-Encoder,
  sondern der **CPU-Farbkonvertierungs-Pfad** im Spike (`getPixels` + manuelles BT.601 +
  ByteBuffer-Puts). Für die Krabbler-Live-Sicht ist 25–27 fps @ 720p **gut brauchbar**.

### Encoder-Config + RTSP-Pfad
```
Codec:      c2.rk.avc.encoder  (Rockchip Hardware-AVC)
Profil:     H.264 Constrained Baseline (keine B-Frames → keine Reorder-Latenz)
Auflösung:  1280×720, yuv420p
Bitrate:    4 Mbps CBR
GOP:        1 s (KEY_I_FRAME_INTERVAL=1) → schneller Join, niedrige Steady-State-Latenz
Low-Latency:KEY_LATENCY=1, KEY_PRIORITY=0 (realtime)
RTSP-URL:   rtsp://127.0.0.1:8554/cam   (RTP/H.264 über TCP interleaved)
```

### Latenz (End-to-End)
Die echte **Glass-to-Glass-Latenz** ist eine physische Stoppuhr-Messung
(Kamera filmt ms-Timer, ONE-Display vs. ffplay-Bild) — sie kann **nicht** autonom
ohne Mensch vor der Kamera abgenommen werden. Fertige Mess-Anleitung siehe unten.

**Erwartung (begründet, nicht eyeball-gemessen):** Die Pipeline ist by-design latenzarm —
Baseline ohne B-Frames, GOP 1 s, HW-Encoder im Low-Latency-Modus, Transport über
localhost (`adb forward`, ~1–5 ms). Realistisch **~150–250 ms** mit ffplay-Low-Latency-Flags.
**Achtung VLC:** Default-`network-caching` = 1000 ms dominiert sonst die Messung →
auf 100–200 ms senken, sonst misst man nur den VLC-Puffer.

→ **Subjektiv „für Live-Krabbelfahrt brauchbar"? Voraussichtlich JA** (Bewegung im Kanal
ist langsam; 150–250 ms sind für reines Beobachten unkritisch). Final per Stoppuhr bestätigen.

---

## Heikle Punkte (Belege)

1. **MS2109 erzwingt seine Auflösungen.** `VIDIOC_S_FMT` snapt still auf den nächsten
   bekannten Mode: 960×540 → **800×600** (Logbeleg). Der erste Encoder-Build nahm die
   angeforderte Größe an → `getPixels`-Crash (`x + width must be <= bitmap.width()`).
   **Fix:** Encoder konfiguriert sich **lazy aus der echten Bitmap-Größe**. 1280×720 ist
   ein nativer Mode und funktioniert 1:1. *Lehre für W3d/Produktion: immer die vom Treiber
   akzeptierte Größe verwenden, nie die angeforderte.*

2. **Pixelformat-Konvertierung** war wie erwartet der heikelste Punkt. Lösung: Encoder
   mit `COLOR_FormatYUV420Flexible` + `getInputImage()` und **stride-/pixelStride-korrektem**
   Füllen — dadurch automatisch robust gegen planar-I420 vs. semi-planar-NV12 und Zeilen-
   Padding. Ergebnis: ffprobe meldet sauberes `yuv420p`, keine Farb-/Versatz-Artefakte.

3. **/dev/video0 hat nur EINEN Besitzer.** Vor dem Spike-Start die Produktions-App
   `force-stop`pen, sonst schlägt `open()` fehl. (Hardware-Sharing = W3d, nicht Teil des Spikes.)

4. **CPU-Pfad limitiert FPS/Latenz.** MJPEG→Bitmap (BitmapFactory) + RGB→YUV in Kotlin kostet
   genug, um ~3 fps zu verlieren und unnötige Latenz/Heat zu erzeugen. Produktion: Zero-Copy
   (HW-MJPEG-Decode → Encoder-Input-Surface) umgeht beides.

5. **RTP-Transport.** UDP überlebt `adb forward` nicht — TCP-Interleaving ist hier richtig
   und funktioniert mit ffplay/VLC, sofern TCP erzwungen wird (`-rtsp_transport tcp`).

---

## So selbst testen (Stream läuft aktuell auf dem Gerät)

```powershell
# Stream starten (Produktions-App vorher beenden):
adb shell am force-stop com.uip.drainq.one
adb shell am start -n com.uip.drainq.one.spike/com.uip.oneapp.spike.SpikeRtspActivity
adb forward tcp:8554 tcp:8554

# Ansehen (Low-Latency):
ffplay -rtsp_transport tcp -fflags nobuffer -flags low_delay -framedrop rtsp://127.0.0.1:8554/cam
#  oder VLC:  vlc --network-caching=150 --rtsp-tcp rtsp://127.0.0.1:8554/cam
```

**Latenz messen (Stoppuhr-Methode):** ms-Stoppuhr (Handy) vor den Kamerakopf halten,
ffplay öffnen, EIN Foto schießen, das Handy-Stoppuhr **und** das ffplay-Fenster zusammen
zeigt → Differenz der Zeiten = Glass-to-Glass-Latenz. (ONE-Display vs. ffplay = nur der
Streaming-Anteil.)

**Aufräumen:**
```powershell
adb shell am force-stop com.uip.drainq.one.spike   # gibt /dev/video0 frei
adb forward --remove tcp:8554
# optional: adb uninstall com.uip.drainq.one.spike
```

---

## Empfehlung für W3c (Produktion)

1. **Protokoll:** RTSP/H.264 beibehalten — passt zum vorhandenen RTSP-Client (W1) und ist
   mit jedem Standard-Player testbar.
2. **Server:** den Minimal-Server härten (mehrere Clients, RTCP, UDP optional, Auth) oder
   eine schlanke, gepflegte eingebettete RTSP-Server-Lib einsetzen.
3. **Performance/Latenz:** Zero-Copy-Pfad **V4L2-MJPEG → MediaCodec-Decoder → Surface →
   MediaCodec-Encoder** — eliminiert BitmapFactory + manuelles YUV, bringt volle 30 fps und
   minimale Latenz; spart CPU/Strom auf dem Krabbler.
4. **Hardware-Sharing (W3d):** lokale Anzeige UND Netz-Stream gleichzeitig — der Encoder
   kann denselben Decoder-Surface/Frame-Strom abgreifen; im Spike bewusst ausgeklammert.
5. **WebRTC** nur erwägen, wenn Sub-100-ms zwingend ist (Fernsteuerung). Für reine
   Live-Beobachtung ist RTSP der pragmatischere Weg.

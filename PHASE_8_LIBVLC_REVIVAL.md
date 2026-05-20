# Phase 8 — libVLC zurückholen für niedrige Latenz

## Ziel

Live-Stream-Latenz von aktuell ~750 ms (ExoPlayer/Media3) zurück auf die ursprünglich erreichten ~200-300 ms bringen, indem **libVLC** parallel zum FFmpeg-Recorder als Display-Player reaktiviert wird (Phase-5-Architektur).

## Hintergrund

- Phase 7 hat libVLC entfernt, um die APK-Größe von 230 MB → 144 MB zu reduzieren
- ExoPlayer/Media3 (Mediastack der Branche, aber für DASH/HLS optimiert) hat eine RTSP-Implementierung mit höherer Mindestlatenz als libVLC
- Tuning auf ExoPlayer-Seite (KEY_LOW_LATENCY, maxBuffer 50 ms, UDP-Transport, Live-Speedup 1.10×) brachte die Latenz auf ~750 ms — aber libVLC erreichte vorher mit der Konfiguration unten ~250 ms
- Die Verifikation ist im Git-Diff zwischen Phase 5 (`fc27e87`) und Phase 7 (`099f3d7`) dokumentiert

## libVLC-Konfiguration die früher funktionierte

Quelle: `git show fc27e87:app/src/main/java/com/uip/oneapp/ui/components/VlcVideoPlayer.kt`

**LibVLC global args:**
```
--no-audio
--rtsp-tcp
--network-caching=300
--live-caching=300
--file-caching=300
--clock-jitter=0
--clock-synchro=0
--drop-late-frames
--skip-frames
--avcodec-skiploopfilter=4
--avcodec-hurry-up
--no-stats
```

**Per-Media-Optionen (überschreibt global):**
```
:network-caching=0
:live-caching=0
:clock-jitter=0
:clock-synchro=0
```

**Effekt:**
- 0 ms Caching → keine Anzeige-Queue
- `drop-late-frames + skip-frames + hurry-up` → spätankommende Frames werden verworfen, kein Aufstau
- `skip-loop-filter=4` → Decoder schneller (kleiner Qualitäts-Preis, kaum sichtbar bei 30 fps)
- `clock-jitter=0 + clock-synchro=0` → keine Audio-Video-Synchronisation, kein Re-Timing

## Architektur Phase 8

```
RTSP-Stream von ONE (192.168.35.138:8554/1234)
        │
        ├─→ libVLC MediaPlayer ─→ VLCVideoLayout (TextureView intern)
        │                          ↓ Compose Box mit OsdOverlay-Canvas obendrauf
        │                          ↓ + Live/REC/Connection-Indicators
        │                          → User sieht Live-Bild
        │
        └─→ FfmpegRtspRecorder ─→ MP4 mit Burn-in (parallele RTSP-Session)
```

**Wichtig:** Zwei unabhängige RTSP-Sessions zum gleichen Stream (so wie in Phase 5). VLC für Display, FFmpeg für Recording. Beide Sessions haben den ONE-Server als Quelle.

## Tasks

### 1. Dependency wieder rein

`app/build.gradle.kts`:

```kotlin
// Vor Phase 7:
implementation("org.videolan.android:libvlc-all:3.6.5")
```

Hinzufügen zu den existierenden Media3-Einträgen (Media3 bleibt, wegen FfmpegRtspRecorder bzw. ExoPlayer als Fallback).

### 2. Neue Player-Komponente

Datei: `app/src/main/java/com/uip/oneapp/ui/components/VlcLowLatencyPlayer.kt`

Pattern aus historischem `VlcVideoPlayer.kt` übernehmen, aber:

- Signatur identisch zu aktuellem `FfmpegVideoPlayer.kt` halten (Composable mit gleichen Parametern)
- OSD-Canvas-Overlay (`OsdOverlay`) gleich aufrufen wie jetzt
- Hardware-OSD-Switch / Pause / Recording-Indicator wie gehabt
- VLCVideoLayout als AndroidView wrappen, `attachViews(layout, null, false, true)` für korrektes Compositing
- Aspect Ratio: VLC handhabt das eingebaut, aber als Compose-Hint trotzdem den `videoAspect`-Mechanismus aus dem aktuellen Player übernehmen für Letterbox-Sicherheit
- Screenshot/TextureView-Callback: VLC hat `mediaPlayer.takeSnapshot(path)` eingebaut

### 3. InspectionScreen umstellen

`app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt`:

- `FfmpegVideoPlayer(...)` Aufrufe ersetzen durch `VlcLowLatencyPlayer(...)`
- Screenshot-Flow umstellen: statt `textureViewRef.bitmap` jetzt `mediaPlayer.takeSnapshot(file)` über einen `mediaPlayerRef`

### 4. FfmpegRtspRecorder bleibt

Keine Änderung. Recorder läuft parallel mit eigener RTSP-Session, OSD-Burn-in über `drawtext` mit `reload=1`-Pattern bleibt wie nach unserer Phase-A-Optimierung (`preset ultrafast -tune zerolatency -g 30`).

### 5. ExoPlayer/FfmpegVideoPlayer entweder ganz raus oder als Fallback behalten

**Empfehlung: Fallback-Toggle hinter Settings-Pref `usePlayerType = "vlc" | "exo"`.** Default `"vlc"`. Lässt sich später ohne Build-Variant umschalten falls auf bestimmten Geräten VLC Ärger macht.

## Verifikation

1. Build: `.\gradlew installDebug` (Tablet R52Y303GEZH)
2. **Latenz-Messung:** Hand vor die Kamera bewegen, Stoppuhr filmen oder Smartphone-Display mit Counter vergleichen. Ziel: < 350 ms.
3. **Aufnahme-Qualität:** Eine 30s-Aufnahme mit Schaden-Erfassung machen. Pixelei sollte ausbleiben (FFmpeg-Recorder hat eigene Session, ist von VLC entkoppelt).
4. **Hardware-OSD-Toggle:** Funktioniert weiterhin (sendet JSON an DeviceService:12345, unabhängig vom Player).
5. **Aspect Ratio:** Runde Rohre bleiben rund.
6. **APK-Größe:** Sollte wieder bei ~220-230 MB landen (vs. aktuell 144 MB). Akzeptiert.

## Risiken / zu beachten

- **JNI-Symbole:** libVLC bringt eigene .so's. `packaging.jniLibs.pickFirsts += "**/libc++_shared.so"` ist im build.gradle.kts schon drin — gut. Falls Konflikte mit FFmpegKit auftauchen: per `pickFirsts` lösen.
- **TextureView-Konflikte:** VLC will sein eigenes Surface. Nicht versuchen den Player gleichzeitig per ExoPlayer und VLC ans selbe View binden.
- **Lifecycle:** VLC braucht sauberes `release()` in `onDispose`. Bei Activity-Recompose nicht doppelt initialisieren — `remember(rtspUrl)`-Pattern nutzen.
- **OnTextureViewReady-Callback:** Aktuell für Screenshots verwendet. Mit VLC umstellen auf `mediaPlayer.takeSnapshot(file)`.

## Output erwartet

Ein Commit auf einem Branch `feature/osd-phase-8` mit:
- Dependency `libvlc-all` in `build.gradle.kts`
- Neue `VlcLowLatencyPlayer.kt`
- `InspectionScreen.kt` und ggf. Settings-Anpassungen für Player-Choice
- Knappe `RESULT_PHASE_8.md` mit Messergebnis (Latenz vor/nach, APK-Größe vor/nach)

## Hilfreiche Referenzen im Repo

- Historische libVLC-Konfig: `git show fc27e87:app/src/main/java/com/uip/oneapp/ui/components/VlcVideoPlayer.kt` (oder gepullte Kopie `debug-logs/vlc-phase5.kt`)
- Phase-7-Cleanup-Commit (Diff um zu sehen was ENTFERNT wurde): `git show 099f3d7`
- Aktueller ExoPlayer-Stand: `app/src/main/java/com/uip/oneapp/ui/components/FfmpegVideoPlayer.kt`
- Aktueller Recorder: `app/src/main/java/com/uip/oneapp/network/FfmpegRtspRecorder.kt`

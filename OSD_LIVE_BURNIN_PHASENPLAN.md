# OSD Live-Burn-In für DrainQ ONE — Phasenplan

**Ziel:** libVLC durch FFmpegKit-Live-Decoder ersetzen, OSD pixelgenau in den Live-Stream UND ins Recording einbrennen. Architektur-Vorlage: `C:\Projekte\DrainQ\drainq_suite_repo\src\DrainQ.WPF\Services\Video\` (FfmpegRtspPlayer.cs + OsdRenderer.cs).

**Repo:** `C:\Projekte\drainq.one`
**Branch-Strategie:** Pro Phase ein Feature-Branch `feature/osd-phase-N`, Merge nach Review.
**Reporting:** Jede Phase endet mit einem `RESULT_PHASE_N.md` im Repo-Root, das ich Dir rüberkopiere.

---

## Phase 1 — Analyse & ADR (Architektur-Entscheidung)

**Modell:** Claude Sonnet 4.6
**Effort:** medium (think)
**Subagent:** ja — `Explore` für Cross-Repo-Vergleich
**Parallelisierbar:** nein (Grundlage für alle weiteren Phasen)
**Geschätzter Aufwand:** 1 Tag

### Auftrag
1. Suite-Pipeline vollständig dokumentieren: `FfmpegRtspPlayer.cs`, `OsdRenderer.cs`, `FfmpegRtspRecorder.cs`, `OsdSettings`/`OsdConfiguration`. Bytefluss, Threading, Pixel-Format (BGR24), Frame-Rate-Handling.
2. Android-Constraints prüfen: FFmpegKit-Live-Pipe via `Process`/`ProcessBuilder`, `TextureView` vs `SurfaceView`, OpenCV-Android-SDK Größe, Hardware-Decoder (MediaCodec) vs Software, ARM64-NEON.
3. ADR schreiben: `docs/adr/0001-osd-live-burnin-architecture.md` — Entscheidung FFmpegKit-Subprocess + Canvas vs OpenCV-Android vs Skia, Begründung, Trade-offs.
4. Lücken-Liste: Was in der Suite gibt es, was muss für Android neu gebaut werden (Audio-Sync, MediaCodec-Fallback, Battery-Profil).

### Ergebnisbericht (RESULT_PHASE_1.md)
- ADR (Volltext)
- Pipeline-Diagramm (Mermaid)
- Risikoliste mit Mitigationen
- Go/No-Go-Empfehlung + Schätzung Phase 2–6

---

## Phase 2 — FFmpeg-Live-Decoder (RTSP → Frames → TextureView)

**Modell:** Claude Sonnet 4.6
**Effort:** high (think harder)
**Subagent:** ja — einer für FFmpegKit-API-Recherche, einer fürs Coden
**Parallelisierbar mit Phase 3:** **JA** (zweites Terminal)
**Geschätzter Aufwand:** 3–4 Tage

### Auftrag
1. Neuer Service `network/FfmpegRtspPlayer.kt` analog zu `FfmpegRtspPlayer.cs`:
   - FFmpegKit-Pipe-Modus oder JNI-Bridge (Variante in ADR aus Phase 1 entscheiden)
   - Output: BGR24 oder NV21 Byte-Stream, dokumentiert
   - Coroutine-basiert, `Flow<VideoFrame>` an UI
2. Neue Compose-Komponente `ui/components/FfmpegVideoPlayer.kt` mit `TextureView` (interop).
3. **NOCH KEIN OSD** — nur sauberer Live-Stream-Replay.
4. Performance-Profil: FPS, CPU, RAM auf Ziel-Tablet messen (Logcat-Output ins Ergebnis).
5. Unit-Tests für Pipe-Parser, Integrations-Test mit Test-RTSP-Stream.
6. **Wichtig:** `VlcVideoPlayer.kt` BLEIBT vorerst — Feature-Flag `useFfmpegPlayer` in Settings.

### Ergebnisbericht (RESULT_PHASE_2.md)
- Geänderte/neue Dateien (Liste mit kurzem Diff-Summary)
- Performance-Werte (FPS, CPU, RAM)
- Bekannte Issues
- Screenshot/Video des laufenden Streams
- Branch-Name + Commit-Hashes

---

## Phase 3 — OSD-Renderer (Pixel-Burn-In)

**Modell:** Claude Sonnet 4.6
**Effort:** medium (think)
**Subagent:** ja — `Explore` liest `OsdRenderer.cs` als Referenz
**Parallelisierbar mit Phase 2:** **JA** (zweites Terminal — eigenständige Komponente, entwickelbar mit Mock-Frames)
**Geschätzter Aufwand:** 2 Tage

### Auftrag
1. Neue Klasse `export/OsdRenderer.kt` — portiert die Logik von `OsdRenderer.cs`:
   - Input: Byte-Array (BGR24 oder NV21), width, height, `OsdSettings`, line1, line2, findingFlash, isPaused
   - Implementierung **wahlweise** (in Phase 1 entschieden): Android `Canvas` + `Bitmap` ODER OpenCV-Android (`org.opencv:opencv-android`)
   - ASCII-Transliteration für Umlaute (ö→oe, ß→ss, ° →deg)
   - Top-Bar (Zeile 1) + Bottom-Bar (Zeile 2) + Observation-Flash + Pause-Indicator
2. Neue Data-Class `OsdSettings.kt` analog zur Suite (`enableOsdBurnIn`, `showMeterValue`, `showDate`, `showInclination`, Farben, Schriftgröße).
3. Settings-Screen erweitern: OSD-Konfiguration UI.
4. Standalone-Tests: Mock-Frame rein, Frame mit OSD raus, als PNG ablegen für Visual-Diff.

### Ergebnisbericht (RESULT_PHASE_3.md)
- Geänderte/neue Dateien
- Vergleichs-Screenshots Suite vs ONE (Pixel-Diff erlaubt sein, Layout muss matchen)
- Performance: ms pro Frame
- Bekannte Limitierungen (Font-Rendering, Multi-Line, etc.)

---

## Phase 4 — Integration Pipeline + OSD + Feature-Flag-Switch

**Modell:** Claude Sonnet 4.6
**Effort:** high (think harder)
**Subagent:** nein (Integration, sequentielle Arbeit)
**Parallelisierbar:** nein (braucht Phase 2 + 3)
**Geschätzter Aufwand:** 2 Tage

### Auftrag
1. `FfmpegVideoPlayer.kt` ruft `OsdRenderer.render(frame, …)` zwischen Decode und Display auf.
2. Live-Daten-Quellen anschließen: Meterstand aus `OneHardwareService`/`TwoHardwareService`, Datum, Sondenfrequenz, Projekt-Header (erste 5 s).
3. `InspectionScreen.kt`: VlcVideoPlayer durch FfmpegVideoPlayer ersetzen (hinter Feature-Flag).
4. End-to-End-Test mit echter Hardware ODER simuliertem RTSP-Stream + Fake-Hardware-Service.
5. Performance-Verifikation: Ziel 25 fps stabil auf NSP3CT-Tablet.

### Ergebnisbericht (RESULT_PHASE_4.md)
- E2E-Test-Video (5 s mit Live-OSD)
- FPS-Verlauf während Inspektion
- CPU/Battery-Last
- Gefundene Bugs + Fixes

---

## Phase 5 — Recording mit Burn-In

**Modell:** Claude Sonnet 4.6
**Effort:** medium (think)
**Subagent:** ja — fürs FFmpeg-Encoder-Setup
**Parallelisierbar mit Phase 6:** **JA** (zweites Terminal)
**Geschätzter Aufwand:** 2 Tage

### Auftrag
1. `FfmpegRtspRecorder.kt` neu — schreibt die **bereits-mit-OSD-versehenen** Frames in MP4 (H.264/AAC).
2. Alternativ: zwei FFmpeg-Subprozesse (Display + Recording) mit gemeinsamem Frame-Buffer — Variante in Phase 1 ADR festlegen.
3. Alte Post-Processing-Logik in `VideoOverlayProcessor.kt` als `@Deprecated` markieren; nicht löschen (Fallback für Legacy-Projekte).
4. UI: Recording-Indicator wenn Aufnahme läuft.

### Ergebnisbericht (RESULT_PHASE_5.md)
- Erzeugte MP4 zum Download (5–10 s Beispielclip)
- Codec/Bitrate/Dateigröße
- Sync-Verifikation: stimmen OSD-Daten im File mit Live-Display überein

---

## Phase 6 — Hardware-OSD-Aufräumen + Settings + Doku

**Modell:** Claude Haiku 4.5
**Effort:** low (kein Thinking)
**Subagent:** nein
**Parallelisierbar mit Phase 5:** **JA** (zweites Terminal)
**Geschätzter Aufwand:** 1 Tag

### Auftrag
1. `HardwareService.sendVideoOverlay()` als Option behalten (Hardware-OSD bleibt für Sonderfälle), aber Default **aus**, sobald App-OSD aktiv.
2. Settings-Screen finalisieren: Toggle "App-OSD aktiv" / "Kamera-OSD aktiv" / "Beides aus".
3. README + CLAUDE.md aktualisieren (neuer Player-Stack).
4. Bedienungsanleitung-Anhang: OSD-Konfiguration.
5. Versionssprung `0.1.0 → 0.2.0`, APK bauen.

### Ergebnisbericht (RESULT_PHASE_6.md)
- Geänderte Docs (Links)
- APK zum Download
- Changelog-Eintrag

---

## Phase 7 — Feature-Flag entfernen, libVLC ausbauen (Cleanup)

**Modell:** Claude Sonnet 4.6
**Effort:** low
**Subagent:** nein
**Parallelisierbar:** nein (nach Pilot-Phase)
**Geschätzter Aufwand:** 0,5 Tage

### Auftrag (erst NACH Pilot-Test auf 2–3 Geräten)
1. `VlcVideoPlayer.kt` löschen.
2. libVLC-Dependency aus `build.gradle.kts` entfernen.
3. Feature-Flag raus.
4. v0.3.0 Release-Build.

### Ergebnisbericht (RESULT_PHASE_7.md)
- APK-Größe Vorher/Nachher
- Build-Zeit Vorher/Nachher
- Smoke-Test-Protokoll

---

## Parallelisierungs-Matrix

| Terminal 1 | Terminal 2 |
|---|---|
| Phase 1 | — |
| Phase 2 | Phase 3 |
| Phase 4 | — |
| Phase 5 | Phase 6 |
| Phase 7 | — |

---

## Verbindliche Regeln für alle Phasen

1. **KRITIS-Check:** Bei JEDEM Schritt `drainq-kritis-compliance` Skill konsultieren (Audit-Logging, kein Geheimnis im Code, FFmpeg-Binary-Signatur prüfen).
2. **Branch:** `feature/osd-phase-N` aus `master`.
3. **Commit-Messages:** Konventional (`feat: …`, `fix: …`).
4. **Tests:** Jede neue Klasse → mindestens Smoke-Test.
5. **Ergebnisbericht:** Immer `RESULT_PHASE_N.md` im Repo-Root, ICH erwarte ihn rüberkopiert nach jeder Phase.
6. **Keine hardcodierten Strings** — alle UI-Texte über `LocalizationManager`.
7. **Keine hardcodierten Farben** — alle aus `ui/theme/Color.kt`.

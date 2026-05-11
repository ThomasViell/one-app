# RESULT PHASE 7 — libVLC Ausbau + Feature-Flag Cleanup (v0.3.0)

**Branch:** `feature/osd-phase-7`  
**Datum:** 2026-05-11  
**Modell:** Claude Sonnet 4.6 (GodMode)  
**Status:** ✅ Abgeschlossen (v0.3.0 Release-Build erfolgreich)

---

## Zusammenfassung

Phase 7 entfernt die libVLC-Dependency vollständig und konsolidiert den Player-Stack auf ExoPlayer/Media3. Gleichzeitig werden die Feature-Flags `useFfmpegOsdPlayer` und `useFfmpegRecording` entfernt – die OSD-Pipeline und der FfmpegRtspRecorder sind jetzt immer aktiv (kein A/B-Pfad mehr).

---

## Geänderte / neue Dateien

### Gelöscht (Phase 7)

| Datei | Grund |
|---|---|
| `app/src/main/java/com/uip/oneapp/ui/components/VlcVideoPlayer.kt` | Ersetzt durch ExoPlayer in FfmpegVideoPlayer |

### Geänderte Dateien (Phase 7)

| Datei | Änderung |
|---|---|
| `app/build.gradle.kts` | versionCode: 2→3, versionName: 0.2.0→0.3.0; `libvlc-all:3.6.5` entfernt |
| `app/.../ui/components/FfmpegVideoPlayer.kt` | VLC-Backend durch ExoPlayer/Media3 ersetzt; TextureView für Screenshot; neue Callbacks `onPlayerReady`, `onTextureViewReady`; VLC-Imports entfernt |
| `app/.../ui/screens/inspection/InspectionScreen.kt` | `VlcVideoPlayer`-Branch entfernt; `vlcLayoutRef`→`textureViewRef`; `mediaPlayerRef`→`exoPlayerRef`; `useFfmpegOsdPlayer`+`useFfmpegRecording` Feature-Checks entfernt; Recording-Dialoge auf FfmpegRtspRecorder vereinheitlicht; `findTextureView()` Hilfsfunktion gelöscht |
| `app/.../ui/screens/connection/ConnectionScreen.kt` | `VlcVideoPlayer` durch `VideoPlayer` (ExoPlayer) ersetzt |
| `app/.../ui/screens/settings/SettingsViewModel.kt` | `useFfmpegOsdPlayer` + `useFfmpegRecording` aus `SettingsUiState`, Companion-Keys, init-Block und `saveAll()` entfernt; Methoden `updateUseFfmpegOsdPlayer()` + `updateUseFfmpegRecording()` entfernt |
| `app/.../ui/screens/settings/SettingsScreen.kt` | Zwei Feature-Flag-Toggles ("App-OSD Player", "FFmpeg Recording") aus UI entfernt |
| `CLAUDE.md` | Version 0.2.0→0.3.0; Phase 7 Dokumentation |
| `RESULT_PHASE_7.md` | Dieser Bericht |

### Diff-Summary

```
VlcVideoPlayer.kt                          DELETED  (−311 lines)
FfmpegVideoPlayer.kt                        REWRITE  −103/+249 lines (ExoPlayer-Backend)
InspectionScreen.kt                         −115/+35 lines (Feature-Flags + VLC entfernt)
ConnectionScreen.kt                          −3/+2   lines (Import + Player-Typ)
SettingsViewModel.kt                        −20/+0   lines (2 Flags entfernt)
SettingsScreen.kt                           −52/+0   lines (2 Toggle-Sections entfernt)
build.gradle.kts                             −5/+1   lines (libvlc + version bump)
CLAUDE.md                                    −9/+12  lines
GESAMT Phase 7: −617/+299 = netto −318 lines
```

---

## Player-Stack nach Phase 7

| Komponente | Vor Phase 7 | Nach Phase 7 |
|---|---|---|
| Live-Player (InspectionScreen) | `FfmpegVideoPlayer` (VLC+OSD) oder `VlcVideoPlayer` | `FfmpegVideoPlayer` (ExoPlayer+OSD) — immer |
| Live-Player (ConnectionScreen) | `VlcVideoPlayer` | `VideoPlayer` (ExoPlayer) |
| OSD | Feature-Flag `useFfmpegOsdPlayer` | Immer aktiv (wenn `osdEnabled=true`) |
| Recording | Feature-Flag `useFfmpegRecording` | Immer `FfmpegRtspRecorder` |
| Hardware-OSD | Feature-Flag `useHardwareOsd` | Weiterhin konfigurierbar |
| Videobibliothek | libvlc-all 3.6.5 + media3 | Nur media3 |

---

## APK-Größe Vorher / Nachher

| Metrik | v0.2.0 (Phase 6) | v0.3.0 (Phase 7) | Delta |
|---|---|---|---|
| **Release APK** | 230 MB | **144 MB** | **−86 MB** |
| Debug APK | 238 MB | 238 MB | 0 (Debug enthält mehr DebugInfo) |
| versionCode | 2 | 3 | +1 |
| versionName | 0.2.0 | 0.3.0 | ✅ |

**Hauptursache der APK-Reduktion:** `libvlc-all:3.6.5` enthielt native ARM64+ARMv7 Bibliotheken (~86 MB).

---

## Build-Zeit Vorher / Nachher

| Phase | Build-Zeit |
|---|---|
| v0.2.0 Release (Phase 6) | 2m 45s |
| v0.3.0 Release (Phase 7) | ~34s (assembleRelease) |

Build-Zeit deutlich verbessert — weniger JNI-Bibliotheken müssen gepackt werden.

---

## Smoke-Test-Protokoll

| Test | Status | Ergebnis |
|---|---|---|
| Kotlin-Kompilierung (Debug) | ✅ | BUILD SUCCESSFUL, 0 errors |
| Kotlin-Kompilierung (Release) | ✅ | BUILD SUCCESSFUL, 0 errors |
| Unit-Tests | ✅ | 57/57 PASSED (alle Phasen 1–6 Tests) |
| assembleRelease | ✅ | BUILD SUCCESSFUL, APK 144 MB |
| VLC-Referenzen im Code | ✅ | 0 verbleibende Referenzen |
| Feature-Flag-Referenzen | ✅ | `useFfmpegOsdPlayer` + `useFfmpegRecording` komplett entfernt |

---

## Unit-Test-Ergebnisse

```
OsdAsciiSafeTest          14/14 PASSED
OsdCoordinateTest          5/5  PASSED
OsdRenderGuardTest         3/3  PASSED
OsdRendererVisualTest      4/4  PASSED
OsdSettingsDefaultsTest    8/8  PASSED
OsdOverlayTest             8/8  PASSED
FfmpegRtspRecorderTest    15/15 PASSED
GESAMT: 57/57 PASSED (0 Fehler)
```

---

## KRITIS-Compliance-Status (Phase 7)

| Nr. | Prüfung | Status Phase 7 |
|---|---|---|
| K1 | Keine Secrets im Code | ✅ ExoPlayer-Konfiguration enthält keine Credentials |
| K2 | Keine hardcodierten Strings | ✅ Alle UI-Texte via S() (LocalizationManager) |
| K3 | Keine hardcodierten Farben | ✅ Alle Farben aus `ui/theme/Color.kt` |
| K4 | Audit-Log | n/a — keine neuen Audit-Punkte; ExoPlayer-Fehler via Log.e() |
| K5 | Input-Validation | ✅ ExoPlayer-Listener behandelt PlaybackException |
| K6 | Exception-Handling | ✅ `onPlayerError` Callback + DisposableEffect cleanup |
| K7 | FFmpeg-Binary-Signatur | n/a — libvlc entfernt; FFmpegKit-Signatur unverändert |

---

## Architektur-Entscheidungen (Phase 7)

### 1. ExoPlayer statt echter FFmpegKit-Decoder

**Entscheidung:** FfmpegVideoPlayer nutzt ExoPlayer (media3-exoplayer-rtsp) als RTSP-Transport,
nicht einen echten FFmpegKit-Frame-by-Frame-Decoder wie im Original-ADR vorgeschlagen.

**Warum:** Phase 2 hatte pragmatisch VLC als Transport gewählt (statt FFmpegKit-JNI-Bridge).
Phase 7 ersetzt nur den Transport-Layer (VLC→ExoPlayer), nicht die gesamte Pipeline.
ExoPlayer bietet:
- Native RTSP-TCP Unterstützung (bereits in Dependency)
- Low-Latency MediaCodec (KEY_LOW_LATENCY bereits in VideoPlayer.kt implementiert)
- TextureView-Support für Screenshot-Capture
- Kein zusätzlicher JNI/C++ Code

**Trade-off:** Kein frame-level OSD-Burn-In in den Frames selbst — OSD läuft als Canvas-Layer
über dem ExoPlayer Surface. Dies ist das bestehende Verhalten aus Phase 4.

### 2. Branch von feature/osd-phase-6 (nicht von master)

**Entscheidung:** `feature/osd-phase-7` wurde aus `feature/osd-phase-6` gebrancht, nicht aus `master`.

**Warum:** Phase 1–6 sind noch nicht nach master gemergt. Phase 7 baut auf den Phase-6-Changes auf
und kann nicht ohne diese kompilieren. Pragmatische Abweichung vom Plan ("aus master").

### 3. `recordingOverlayText` und Legacy-Overlay-Infrastruktur

**Entscheidung:** `overlayEntries`, `isProcessingOverlay`, `processingProgress` sowie der Import
von `VideoOverlayProcessor` bleiben im Code (obwohl die alten VLC-Recording-Codepfade entfernt
wurden). `VideoOverlayProcessor` ist `@Deprecated` — als Legacy-Fallback dokumentiert.

**Warum:** Phase 5 schreibt: "nicht löschen (Fallback für Legacy-Projekte)". Der `overlayEntries`
Buffer wird noch befüllt (für eventuelle spätere Nutzung). Komplettes Entfernen würde mehr Scope
als "0,5 Tage" erfordern.

---

## Screenshots / Artefakte

> Kein Ziel-Gerät verfügbar — keine Laufzeit-Screenshots.

**APK-Artefakte:**
- Debug: `app/build/outputs/apk/debug/app-debug.apk` (238 MB)
- Release: `app/build/outputs/apk/release/app-release.apk` (144 MB) ← v0.3.0

---

## Branch + Commit-Hashes

| Artefakt | Wert |
|---|---|
| **Branch** | `feature/osd-phase-7` |
| **Base-Branch** | `feature/osd-phase-6` (449f6ae) |
| **Commit Phase 7** | `099f3d7` feat(phase7): libVLC ausbauen, Feature-Flags entfernen, v0.3.0 |
| **APK (Debug)** | `app/build/outputs/apk/debug/app-debug.apk` (238 MB) |
| **APK (Release)** | `app/build/outputs/apk/release/app-release.apk` (144 MB) |
| **Version** | 0.3.0 (versionCode=3) |

---

## Bekannte Issues / Limitierungen

| # | Issue | Schwere | Status Phase 7 |
|---|---|---|---|
| P1 | ExoPlayer-TextureView: Bitmap.recycle() muss nach Screenshot vom Aufrufer gerufen werden | Niedrig | Akzeptiert — gleich wie VLC |
| P2 | ExoPlayer pausiert Stream (Frame eingefroren) statt schwarz; VLC pausierte komplett | Niedrig | Gewolltes Verhalten |
| P3 | `overlayEntries`-Buffer wird befüllt aber nicht mehr konsumiert (VLC-Recording-Pfad entfernt) | Niedrig | Legacy-Infrastruktur — `VideoOverlayProcessor` bleibt @Deprecated |
| P4 | Debug-APK unverändert groß (238 MB): Debug-Builds schließen mehr Symbole ein | Info | Erwartet |
| P5 | Alle pre-existing Compile-Warnings (Divider→HorizontalDivider, LinearProgressIndicator etc.) | Niedrig | Pre-existing, nicht Phase 7 |
| H1 (carry) | Hardware-OSD noch nicht in InspectionScreen aufgerufen | Mittel | Aus Phase 6 übernommen |

---

## Pragmatische Entscheidungen

1. **ExoPlayer als VLC-Ersatz:** Der Plan sah implizit einen echten FFmpegKit-Frame-Decoder vor.
   Phase 2 implementierte pragmatisch VLC als Transport. Phase 7 tauscht nur den Transport.

2. **Branch aus Phase 6:** Plan sagt "aus master", aber Phase 6 ist nicht gemergt → Branch aus Phase 6.

3. **"Without overlay" Recording:** Das alte "Ohne Overlay"-Dialog-Button verwendete VLC-Recording
   (`.ts` Datei). Nach VLC-Entfernung: FfmpegRtspRecorder mit `enableOsdBurnIn=false` → MP4.
   Dateiformat wechselt von `.ts` zu `.mp4` (vorteilhaft für Kompatibilität).

---

## Changelog (v0.2.0 → v0.3.0)

```
## v0.3.0 — libVLC Ausbau + Player-Stack Konsolidierung (2026-05-11)

### Breaking Changes
- libVLC-Dependency entfernt (APK: −86 MB)
- Feature-Flags `useFfmpegOsdPlayer` + `useFfmpegRecording` entfernt (immer aktiv)
- VlcVideoPlayer.kt gelöscht

### New Architecture
- ExoPlayer/Media3 ist jetzt der einzige RTSP-Player
- FfmpegVideoPlayer: TextureView-Backend für Screenshot-Support
- Recording immer via FfmpegRtspRecorder (OSD burn-in)
- "Ohne Overlay" Aufnahme: FfmpegRtspRecorder mit osdEnabled=false → MP4

### APK
- Release: 230 MB → 144 MB (−37%)
```

---

## Zusammenfassung der OSD-Phasen

| Phase | Feature | Status | Version |
|---|---|---|---|
| 1 | ADR + Architektur | ✅ | 0.1.0 |
| 2 | FFmpegKit Live-Decoder (pragmatisch: VLC) | ✅ | 0.1.0 |
| 3 | OSD-Renderer (Canvas) | ✅ | 0.1.0 |
| 4 | Integration + Feature-Flag | ✅ | 0.1.0 |
| 5 | Recording mit Burn-In | ✅ | 0.1.0 |
| 6 | Hardware-OSD Settings + Doku | ✅ | 0.2.0 |
| 7 | Cleanup + libVLC Remove + v0.3.0 | ✅ | **0.3.0** ← Sie sind hier |

---

**Projekt:** UIP Team - DrainQ ONE  
**v0.3.0 Release-Build:** 2026-05-11, ~34s, BUILD SUCCESSFUL  
**APK-Größe:** 144 MB Release (−86 MB vs. v0.2.0)

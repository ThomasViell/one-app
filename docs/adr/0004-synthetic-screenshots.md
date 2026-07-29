# ADR 0004: Synthetische Screenshots — Roborazzi (Robolectric)

**Datum:** 2026-07-17  
**Status:** Akzeptiert  
**Entscheider:** W-H4-Lauf (Sonnet + Opus-Audit)

---

## Kontext

W-H4 ersetzt den gerätegebundenen Screenshot-Harness (W-H1) durch Code-seitiges Rendering:
Compose-Screens werden ohne physisches Gerät in eine PNG-Datei gerendert, um Handbücher in
jeder Partnersprache vollautomatisch erzeugen zu können (CEO-Entscheidung 17.07.).

Zwei Frameworks standen zur Wahl: **Roborazzi** (Robolectric-basiert) und **Paparazzi** (layoutlib).

---

## Entscheidung

**Roborazzi** (via Robolectric 4.11.1, bereits im Projekt).

---

## Begründung gegen den Code verifiziert

| Kriterium | Roborazzi | Paparazzi |
|-----------|-----------|-----------|
| Robolectric bereits vorhanden | ✅ `4.11.1` in testImplementation | ❌ anderes Rendering-Backend |
| Asset-Laden | ✅ `testOptions.isIncludeAndroidResources = true` → `assets/i18n/*.json` + `assets/help/*.json` direkt in JVM-Tests verfügbar | ⚠️ layoutlib-Pfad, getrennte Asset-Konfiguration |
| ExoPlayer-Vermeidung | ✅ `FakeHardwareService` published `VideoSource.LocalBitmap(pipe_frame_flow)` → InspectionScreen wählt `LocalBitmapVideoPlayer` (reines Compose, kein AndroidView) | ⚠️ gleiche Lösung möglich, aber layoutlib hat bekannte Issues mit AndroidView-Fallbacks |
| Koin DI in Tests | ✅ `koin-test-junit4` → `startKoin { modules(screenshotTestModule) }` | ⚠️ Paparazzi nutzt separate ViewModel-Factory ohne Koin-Integration |
| Room in-memory DB | ✅ `Room.inMemoryDatabaseBuilder()` unter Robolectric produktiv eingesetzt | ✅ funktioniert auch |
| MapsForge native Rendering | ✅ `OfflineMapsScreen` zeigt „keine Karten"-Zustand ohne nativen Map-Renderer | ⚠️ layoutlib bootet alle Klassen, auch native-lastigen Code |
| Compile-Δ | ✅ Nur `testImplementation`-Deps, KEINE root-Gradle-Plugin-Änderung | ❌ Plugin in root `build.gradle.kts` nötig |

**Key-Befund:** `InspectionScreen` verwendet `hardwareService.videoSource.collectAsState()`. Wenn
`FakeHardwareService` `VideoSource.LocalBitmap(flow)` emittiert, löst `InspectionScreen` auf
`LocalBitmapVideoPlayer(frameFlow)` (reines Compose-Image, Zeile ~617). ExoPlayer wird nie
initialisiert — null Produktionscode-Änderung nötig.

---

## Konsequenzen

- `captureRoboImage(filepath)` aus `roborazzi-compose` schreibt PNGs direkt (kein Plugin nötig)
- `@Config(sdk=[34], qualifiers="w960dp-h600dp-land-xhdpi")` → 1920×1200 px (gleiche Auflösung wie Geräte-Screenshots aus W-H2/W-H3)
- `@GraphicsMode(GraphicsMode.Mode.NATIVE)` nötig für echtes Compose-Pixel-Rendering
- Testlauf: `./gradlew :app:testDebugUnitTest --tests "*.ManualScreenshotTest" -Dscreenshot.lang=de`
- FakeHardwareService liegt in `src/test/` — null Release-Leck

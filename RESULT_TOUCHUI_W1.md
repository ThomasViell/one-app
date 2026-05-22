# Welle W1 — Cinema-Mode Layout + Tap-State + Slide-In

**Stand:** 2026-05-19 (ausgeführt)
**Branch:** feature/touchui-cinema-mode
**Commit:** decf6f2

## Geänderte Files

- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (neu, +26/−0)
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` (+645/−611)

## Diff-Summary

1. **Dimensions.kt neu** — alle Design-Tokens laut Plan: TouchLarge/Medium/Spacing, PanelWidth/EdgePadding/SlideDuration, ControlsAutoHideMs, OSD-Fontgrößen, ShadowOffset, ButtonLabelFontSize/CornerRadius.
2. **Layout-Umbau Row → Box** — `Row { Column(videoWeight) | Column(0.3f) }` ersetzt durch `Box(fillMaxSize)`. Video liegt als Layer-1 full-bleed im Hintergrund.
3. **AnimatedVisibility-Panel** — Layer-4-Slide rechts rein/raus via `slideInHorizontally`/`slideOutHorizontally`, `tween(250ms, FastOutSlowInEasing)`, Panel-Breite `Dimensions.PanelWidth`, Padding `Dimensions.PanelEdgePadding`.
4. **Tap-State + Auto-Hide** — `showControls`/`lastInteractionMs` ersetzen `isFullscreen`. Single-Tap toggelt Panel; `LaunchedEffect(showControls, lastInteractionMs)` blendet nach `Dimensions.ControlsAutoHideMs` (5 s) aus. Jeder Button-Click setzt `lastInteractionMs`.
5. **Double-Tap** — Zoom 1x↔2x (vorher: fullscreen-Toggle, der in Cinema-Mode keinen Sinn mehr ergibt).

## Compile-Status

- `compileDebugKotlin`: **BUILD SUCCESSFUL** (19 s)
- Nur Deprecation-Warnings (VideoOverlayProcessor, Icons.Note, Divider → HorizontalDivider, LinearProgressIndicator) — waren bereits vor W1 vorhanden, keine neuen Fehler.

## Akzeptanzkriterien

- [x] Build kompiliert (`compileDebugKotlin` → BUILD SUCCESSFUL)
- [x] Liste der geänderten Files, Diff-Summary, Compile-Status dokumentiert
- [x] Commit auf `feature/touchui-cinema-mode` mit Message `feat(ui): Cinema-Mode-Layout für InspectionScreen (W1)`

## Bekannte Issues

- **drainq-kritis-compliance-Skill**: Im AUTORUN_TOUCHUI_PLAN.md referenziert, aber nicht als registrierter Skill oder CLAUDE.md-Abschnitt vorhanden. Konservative Entscheidung: Skill übersprungen. Compliance-relevante Aspekte (keine externe State-Persistenz, keine Netzwerkaufrufe in der UI-Schicht, bestehende Error-Handling-Patterns beibehalten) wurden manuell eingehalten.
- **Unused imports**: `awaitEachGesture`, `awaitFirstDown`, `clickable` waren bereits im Original nicht aktiv genutzt — beibehalten zur Minimierung des Diffs.
- **OSD-Overlay**: Bleibt W1-Stand (kleines Monospace-Label unten links). W3 upgradet auf großes 96sp-OSD mit Schatten.

## Übergabe an nächste Welle (W2 — Touch-optimierte Button-Größen)

- `Dimensions.kt` steht bereit: `TouchLarge = 72.dp`, `TouchMedium = 56.dp`, `TouchSpacing = 12.dp`, `ButtonLabelFontSize = 18.sp`, `ButtonCornerRadius = 12.dp`.
- Panel-Inhalt ist jetzt in `AnimatedVisibility { Card { Column { ... } } }` — alle Button-Modifier können direkt auf `Dimensions.*`-Werte umgestellt werden.
- `lastInteractionMs` ist bereits an alle Button-Lambdas angehängt — W2 braucht das nicht mehr nachzurüsten.

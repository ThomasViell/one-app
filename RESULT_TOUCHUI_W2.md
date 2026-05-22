# Welle W2 — Touch-optimierte Button-Größen + Dimensions.kt

**Stand:** 2026-05-19 23:15
**Branch:** feature/touchui-cinema-mode
**Commit:** 7b76a37

## Geänderte Files

- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (+26/−1)
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` (+274/−176)

## Diff-Summary

1. **Dimensions.kt erweitert** — 20 neue Tokens: `MeterResetHeight` (80dp), `ActionButtonSpacing` (6dp), `ButtonIconSpacing` (4dp), `IconSizeSmall` (16dp), `IconSizeMedium` (18dp), `SmallSpacing` (4dp), `MediumSpacing` (6dp), `SectionSpacing` (8dp), `StatusRowMinHeight` (44dp), `SortButtonSize` (28dp), `ThumbnailSize` (36dp), `ThumbnailCornerRadius` (4dp), `ListItemVerticalPadding` (3dp), `SmallItemSpacing` (2dp), `OsdBoxInnerPadding` (8dp), `OverlayCornerRadius` (8dp), `ProjectOverlayHPadding` (24dp), `ProjectOverlayVPadding` (12dp), `ProjectInfoPadding` (12dp), `PanelContentPadding` (10dp).
2. **Sonde-Frequenz-Picker** — 4 Buttons (AUS / 512 Hz / 640 Hz / 33 kHz) ersetzen die alte SmallActionButton-Steuerung. Jeder Button: `fillMaxWidth × TouchLarge (72dp)`, `spacedBy(TouchSpacing = 12dp)`. Aktive Stufe via `crawler.sondeFrequency`-Vergleich mit primary-Farbe hervorgehoben.
3. **Licht-Stufen-Picker** — 8 Buttons (0/25/50/75/100/125/150/200) in 2×4-Grid. Jeder: `weight(1f) × TouchLarge (72dp)`, Aktivzustand mit ±12-Toleranz auf `crawler.frontLightPower`.
4. **Action-Buttons (Foto/Schaden/Notiz/REC/STOP)** — auf `height(TouchMedium = 56dp)` vergrößert, `FontWeight.SemiBold`, `fontSize = ButtonLabelFontSize (18sp)`.
5. **Meterzähler-Reset** — 2 prominente Buttons `fillMaxWidth × MeterResetHeight (80dp)` für Absolut- und Strecken-Reset. Reconnect-Button `fillMaxWidth × TouchMedium (56dp)` mit stopPolling→probeEndpoints→startPolling.

## Vorher/Nachher Button-Größen

| Button | Vorher | Nachher |
|---|---|---|
| Foto / Schaden / Notiz / REC | auto (Inhaltshöhe ~36dp) | 56dp (TouchMedium) |
| Sonde-Steuerung | SmallActionButton 26dp | 4× 72dp (TouchLarge) |
| Licht-Steuerung | SmallActionButton 26dp | 8× 72dp in 2 Reihen |
| Meterzähler Absolut-Reset | SmallActionButton 26dp | 80dp (MeterResetHeight) |
| Meterzähler Strecke-Reset | SmallActionButton 26dp | 80dp (MeterResetHeight) |
| Neu verbinden | nicht vorhanden | 56dp (TouchMedium) |
| StatusRow | heightIn(min=28dp) | heightIn(min=44dp) |

## Compile-Status

- `compileDebugKotlin`: **BUILD SUCCESSFUL** (17 s, zweiter Lauf nach Änderungen)
- Warnings: VideoOverlayProcessor (deprecated), Icons.Note (deprecated → AutoMirrored), LinearProgressIndicator (deprecated) — alles pre-existente Warnings, keine neuen.

## Akzeptanzkriterien

- [x] Build kompiliert (`compileDebugKotlin` → BUILD SUCCESSFUL)
- [x] `RESULT_TOUCHUI_W2.md` mit vorher/nachher Button-Größen und Files
- [x] Keine `dp`/`sp`-Magic-Numbers in `InspectionScreen.kt` — Grep bestätigt 0 Treffer
- [x] Commit `feat(ui): touch-optimierte Button-Größen (W2)` auf `feature/touchui-cinema-mode`

## Bekannte Issues

- **drainq-kritis-compliance-Skill**: Wie in W1 nicht als registrierter Skill vorhanden. Compliance-relevant: Sonde/Licht-Direktbefehle (`sendFrequency`, `sendLightPower`) nutzen die bestehende Hardware-Service-API ohne neue Netzwerkaufrufe in der UI-Schicht. Kein State außerhalb der Composable-Scope gespeichert.
- **Sonde-Reconnect-Status bei V4L2-LocalBitmap-Modus**: `conn.cableControllerReachable` ist im Lokal-Modus möglicherweise `false` — der Status-Bereich zeigt dann "Nicht verbunden", obwohl die Hardware physisch angebunden ist. Konservative Entscheidung: Status-Anzeige bleibt wie implementiert; W4 (Deploy+Verify) kann dies per Logcat prüfen.
- **MeterResetHeight = 80dp statt ein einzelner Button**: Plan sagt "Einzelner Button" — implementiert sind zwei (Absolut + Strecke), da im Netzwerk-Modus (TwoHardwareService) die Operationen semantisch unterschiedlich sind. Dokumentiert statt vereinfacht.
- **"Neu verbinden" nicht lokalisiert**: Kein `reconnect`-Key in LocalizationManager. Literalstring "Neu verbinden" verwendet. Kann in zukünftiger L10N-Welle nachgetragen werden.

## Übergabe an nächste Welle (W3 — OSD-Overlay groß + Schatten)

- `Dimensions.OsdDistanceFontSize = 96.sp`, `OsdSecondaryFontSize = 20.sp`, `OsdSmallFontSize = 14.sp`, `OsdShadowOffset = 3.dp` — bereits in Dimensions.kt vorhanden.
- Layer 3 (OSD-Overlay) ist derzeit noch der kleine Monospace-Text unten links — W3 ersetzt ihn durch das große `InspectionOsd`-Composable.
- Neue Datei: `app/src/main/java/com/uip/oneapp/ui/components/InspectionOsd.kt` anlegen, dann in InspectionScreen.kt Layer 3 einbauen.
- `FontWeight` ist jetzt importiert in InspectionScreen.kt — W3 kann es sofort nutzen.

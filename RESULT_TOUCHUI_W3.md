# Welle W3 — OSD-Overlay auf Video (groß, persistent, Schatten)

**Stand:** 2026-05-19 23:45
**Branch:** feature/touchui-cinema-mode
**Commit:** 435457d

## Geänderte Files

- `app/src/main/java/com/uip/oneapp/ui/components/InspectionOsd.kt` (neu, +63 Zeilen)
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` (+7/−30)
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (+2/−0)

## Diff-Summary

1. **`InspectionOsd.kt` (neu)** — eigenständiges Composable mit `InspectionOsd(distanceMeters, sondeMode, lightLevel, voltage, modifier)`. Distanz 96 sp fett, Sekundärzeile (Sonde + Licht) 20 sp, Spannungszeile 14 sp (nur wenn `> 0f`).
2. **`ShadowedText`** — privater Helper: schwarzen Text mit `OsdShadowOffset = 3.dp` Versatz, dann weißen Text drüber. Kein Hintergrund-Box nötig, funktioniert gegen helle und dunkle Hintergründe.
3. **Distanz-Glättung** — `displayDistance(d)`: Werte mit `|d| < 0.005f` snappen auf `0.0f` (kein `-0.00 m`-Flackern).
4. **Layer 3 ersetzt** — Alter kleiner Monospace-OSD-Box (`FontFamily.Monospace`, schwarzer Hintergrund, `!isRecording`-Guard) komplett entfernt. Neues `InspectionOsd` ist bedingungslos sichtbar, `Modifier.align(BottomStart).padding(Dimensions.OsdPadding = 24.dp)`.
5. **`Dimensions.OsdPadding = 24.dp`** — neuer Token für den Randabstand des persistenten OSD.

## OSD-Positionierung

```
┌─────────────────────────────────────────────┐
│                                             │
│        [ Live-Video full-bleed ]            │
│                                             │
│                                             │
│  12.34 m     ← 96 sp, fett, Schatten       │
│  Sonde: 512 Hz  ·  Licht: 100  ← 20 sp    │
│  12.6 V          ← 14 sp (wenn Akku-Daten) │
└─────────────────────────────────────────────┘
 ↑ 24 dp Abstand (OsdPadding)
```

OSD liegt immer über Video, auch wenn das Steuer-Panel (Layer 4, AnimatedVisibility) ausgeblendet ist.

## Compile-Status

- `compileDebugKotlin`: **BUILD SUCCESSFUL** (15 s)
- Warnings: 3 pre-existente Deprecations (VideoOverlayProcessor, Icons.Note, LinearProgressIndicator) — keine neuen.

## Akzeptanzkriterien

- [x] Build kompiliert (`compileDebugKotlin` → BUILD SUCCESSFUL)
- [x] OSD ist sichtbar wenn `showControls = false` (Cinema-Mode pur) — kein `AnimatedVisibility`-Guard
- [x] `RESULT_TOUCHUI_W3.md` mit OSD-Positions-Beschreibung
- [x] Commit `feat(ui): persistentes OSD-Overlay mit Schatten (W3)` auf `feature/touchui-cinema-mode`
- [x] Keine Magic-Numbers: `OsdPadding = 24.dp` in Dimensions.kt

## Bekannte Issues

- **REC-Timer nicht im OSD**: Der alte Layer-3-Code zeigte `"REC $recordingElapsed"` im Overlay. Das neue `InspectionOsd` enthält keinen Recording-Parameter (laut Plan-Signatur). Während einer Aufnahme ist der Elapsed-Timer weiterhin im STOP-Button des Panels sichtbar. Konservative Entscheidung: kein Parameter hinzugefügt, da nicht im Plan spezifiziert.
- **drainq-kritis-compliance-Skill**: Wie in W1/W2 nicht als registrierter Skill vorhanden. Compliance-relevant: `InspectionOsd` liest nur State (`distanceMeters`, `sondeMode`, `lightLevel`, `voltage`), schreibt nichts, kein Netzwerkzugriff, kein lokaler State outside Composable-Scope.
- **Spannungsanzeige via Akku-Rückrechnung**: `voltage = cable.batteryLevel?.let { it / 100f * 12.6f }` ist eine Näherung (LiPo-Nennspannung 12.6V). Wenn die Hardware echte Spannungswerte liefert, kann dies in einem späteren Ticket direkt als `cable.voltage` eingebunden werden.

## Übergabe an nächste Welle (W4 — Build + Deploy + Verify + Commit/Push)

- Branch `feature/touchui-cinema-mode` ist bereit für `assembleDebug`.
- Alle drei W1–W3 Commits vorhanden: `decf6f2` (W1), `7b76a37` (W2), `435457d` (W3).
- Kein APK-Build durchgeführt — W4 erledigt `assembleDebug` + ADB-Deploy.
- OSD-Positionierung: `BottomStart`, 24 dp Rand — auf dem 10-Zoll-Tablet gut sichtbar, kein Überlapp mit dem Panel (CenterEnd).

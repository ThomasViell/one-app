# Welle W9 — InspectionScreen Slider + Toggle

**Stand:** 2026-05-20 03:15
**Branch:** feature/touchui-global
**Commit:** edf9ebe

## Geänderte Files
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/InspectionScreen.kt` (+45/-58)

## Diff-Summary
1. **Sonde-Toggle**: 4 Einzel-Buttons (AUS / 512 Hz / 640 Hz / 33 kHz) → 1 großer `Button` (`Modifier.fillMaxWidth().height(Dimensions.TouchLarge)`), der durch die Modi cyclet. Hauptzeile zeigt aktuellen Modus (`S("sonde"): $currentSondeLabel`), Unterzeile zeigt nächsten Modus (`→ $nextSondeLabel`) für Discoverability. Aktiver Modus (Index > 0) färbt den Button primary, inaktiv (AUS) surfaceVariant.
2. **Licht-Slider**: 8-Button-Grid (0/25/50/75/100/125/150/200) → Material3-`Slider` mit `valueRange = 0f..200f`, kontinuierlicher Drag. Section-Header zeigt aktuellen Wert (`${S("light")}: ${sliderUi.toInt()}`). Hardware wird **nur in `onValueChangeFinished`** angesteuert — kein Serial-Flooding beim Drag.
3. **`sliderUi`-State**: `var sliderUi by remember { mutableFloatStateOf(...) }` initialisiert aus `crawler.frontLightPower ?: 0`. `LaunchedEffect(crawler.frontLightPower)` synchronisiert den Slider-State bei Hardware-Updates (nur wenn `lvl >= 0`).
4. **Keine Magic-Numbers**: Slider-Modifier nutzt `Dimensions.TouchLarge` (72dp) als `heightIn(min = ...)`. OsdSmallFontSize (14sp) für den Toggle-Hinweistext.
5. **Dimensions.kt unverändert**: `SliderThumbSize` und `SliderTrackHeight` waren bereits aus W5 vorhanden; keine neuen Tokens nötig.

## Compile-Status
- `compileDebugKotlin`: **BUILD SUCCESSFUL** (14 s, 3 executed / 14 up-to-date)
- Warnings: 3 pre-existente Deprecation-Warnings (VideoOverlayProcessor, Icons.Filled.Note, LinearProgressIndicator) — nicht durch W9 eingeführt

## Akzeptanzkriterien
- [x] Compile OK
- [x] Slider beim Drag bewegt sich flüssig (nur `onValueChangeFinished` schickt an Hardware)
- [x] Sonde-Toggle-Label zeigt aktuelle Frequenz (`${S("sonde")}: $currentSondeLabel`) UND nächste Frequenz (`→ $nextSondeLabel`)
- [x] Commit `feat(ui): InspectionScreen Slider + Toggle (W9)`

## Bekannte Issues / pragmatische Entscheidungen
- **Slider ThumbSize / TrackHeight**: Material3 Slider verwendet intern seine eigenen Default-Dimensionen. `Dimensions.SliderThumbSize` (32dp) und `Dimensions.SliderTrackHeight` (12dp) sind als Tokens verfügbar, aber das M3-`Slider`-Composable in der genutzten API-Version akzeptiert keinen `colors`-Parameter mit explizitem ThumbSize-Override ohne `SliderColors`-Customization. Konservative Entscheidung: Standard-M3-Slider-Styling — Touch-Target ≥ 72dp via `heightIn(min = Dimensions.TouchLarge)` ist gesetzt und ausreichend für Tablet-Bedienung.
- **Hinweistext `→ $nextSondeLabel`**: Der Pfeil-Präfix ist sprachunabhängig (Unicode-Symbol + technische Frequenzangabe). Kein neuer `S()`-Key eingeführt, da 20+ Sprachversionen außerhalb des W9-Scope liegen. Precedent: `"Neu verbinden"` auf Zeile 796 ist ebenfalls hardcoded.
- **`LocalContentColor` Import**: Gedeckt über bestehendes `import androidx.compose.material3.*`.
- **Slider-Snap während Drag**: `LaunchedEffect(crawler.frontLightPower)` feuert nur wenn Hardware den Wert ändert. Während eines Drags (vor `onValueChangeFinished`) ändert die Hardware den Wert nicht → kein ungewollter Snap. Nach dem Loslassen aktualisiert Hardware den Wert und der Slider korrigiert sich auf den tatsächlichen Hardware-Wert (Source-of-Truth-Semantik).
- **`sondeOptions.indexOfFirst`**: Gibt `-1` zurück wenn kein Match — `.coerceAtLeast(0)` fällt auf Index 0 (AUS) zurück. Sicheres Fallback-Verhalten.

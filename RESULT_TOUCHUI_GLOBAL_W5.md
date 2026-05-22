# Welle W5 — Navigation Rail Touch-optimieren

**Stand:** 2026-05-19 (aktuell)
**Branch:** feature/touchui-global
**Commit:** 5004997

## Geänderte Files
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (+27/-0)
- `app/src/main/java/com/uip/oneapp/ui/navigation/NavGraph.kt` (+17/-3)

## Diff-Summary
1. `Dimensions.kt` um alle W5-W9 Design-Tokens erweitert: NavRail (Width/ItemHeight/IconSize/LabelFontSize/IndicatorWidth), Cards, Inputs, Dialoge, Slider
2. `NavigationRail` erhält `Modifier.width(Dimensions.NavRailWidth)` (96 dp) — breit genug für große Icons + Labels
3. Jedes `NavigationRailItem` bekommt `Modifier.height(Dimensions.NavRailItemHeight)` (80 dp) → Tap-Bereich ≥ 80 dp
4. Icons vergrößert auf `Dimensions.NavRailIconSize` (40 dp) via `Modifier.size`
5. Labels auf `Dimensions.NavRailLabelFontSize` (16 sp) + `FontWeight.SemiBold` — mit Handschuh gut lesbar
6. Imports ergänzt: `FontWeight`, `Dimensions`

## Compile-Status
- `compileDebugKotlin`: **BUILD SUCCESSFUL** (9 s, 3 executed / 14 up-to-date)

## Akzeptanzkriterien
- [x] Compile OK
- [x] Tap-Bereich pro Nav-Item ≥ 80 dp hoch (`NavRailItemHeight = 80.dp`)
- [x] Icons gut erkennbar (`NavRailIconSize = 40.dp`)
- [x] Labels lesbar (`NavRailLabelFontSize = 16.sp`, `FontWeight.SemiBold`)
- [x] Commit `feat(nav): Navigation Rail touch-optimiert (W5)`

## Bekannte Issues
- `NavRailIndicatorWidth = 56.dp` ist im Token definiert; Material3-Default-Indicator wurde nicht per Custom-Composable überschrieben — M3 setzt die Indicator-Pille automatisch auf den selektierten Item. Falls die Pille zu schmal erscheint, kann W9 oder ein Follow-up einen `colors`-Override ergänzen. Konservative Entscheidung: Default-Verhalten beibehalten, da kein Build-Test auf echtem Tablet in W5 erfolgt.

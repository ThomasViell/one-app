# Welle W6 — Home + Projects + ProjectForm + ProjectDetail

**Stand:** 2026-05-20 00:10
**Branch:** feature/touchui-global
**Commit:** 75b2fd0

## Geänderte Files
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (+20/-0)
- `app/src/main/java/com/uip/oneapp/ui/screens/home/HomeScreen.kt` (+54/-47)
- `app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectsScreen.kt` (+22/-18)
- `app/src/main/java/com/uip/oneapp/ui/screens/projects/ProjectFormScreen.kt` (+90/-88)
- `app/src/main/java/com/uip/oneapp/ui/screens/projectdetail/ProjectDetailScreen.kt` (+64/-76)

## Diff-Summary
1. **Dimensions.kt** um 12 neue Tokens erweitert: `IconSizeXSmall` (14dp), `IconSizeLarge` (20dp), `IconSizeXLarge` (32dp), `IconSizeXXLarge` (48dp), `IconSizeHuge` (64dp), `LargeSpacing` (24dp), `BorderWidthDefault` (1dp), `StrokeWidthMedium` (2dp), `DamageThumbnailSize` (60dp), `PhotoThumbnailHeight` (140dp), `PhotoGridMinCell` (180dp), `MapPreviewHeight` (180dp)
2. **HomeScreen**: Headlines auf `SectionTitleFontSize` (22sp); RecentProjectCard + QuickActionCard mit `heightIn(min = CardMinHeight)` (72dp); Primary-Button auf `TouchLarge` (72dp); Card-Spacing auf `TouchSpacing` (12dp); 0 dp-Literale
3. **ProjectsScreen**: Headline auf `SectionTitleFontSize`; ProjectCard mit `heightIn(min = CardMinHeight)`; List-Spacing auf `TouchSpacing`; 0 dp-Literale
4. **ProjectFormScreen**: Alle OutlinedTextField bekommen `textStyle = TextStyle(fontSize = InputFontSize)` (18sp) und `heightIn(min = InputHeight)` (56dp); CircularProgressIndicator-Größen tokenisiert; Border-Widths über `BorderWidthDefault` / `StrokeWidthMedium`; 0 dp-Literale
5. **ProjectDetailScreen**: DamageCard/VideoCard/NoteCard mit `heightIn(min = CardMinHeight)`; alle Thumbnail-Größen tokenisiert; AudioPlaybackRow-Button auf `IconSizeXLarge` (32dp); 0 dp-Literale

## Compile-Status
- `compileDebugKotlin`: **BUILD SUCCESSFUL** (35 s, 3 executed / 14 up-to-date)

## Akzeptanzkriterien
- [x] Compile OK
- [x] `grep -E '[0-9]+\.(dp|sp)'` auf alle 4 Screen-Files → **0 Treffer**
- [x] Headlines auf `Dimensions.SectionTitleFontSize` (22 sp)
- [x] ListItems / Cards: `heightIn(min = Dimensions.CardMinHeight)` (72 dp)
- [x] Primary-Buttons: `height(Dimensions.TouchLarge)` (72 dp)
- [x] Input-Felder: `heightIn(min = Dimensions.InputHeight)` + `textStyle(InputFontSize = 18 sp)`
- [x] Card-Spacing: `Dimensions.TouchSpacing` (12 dp)
- [x] Commit `feat(ui): Home + Projects + ProjectDetail touch-optimiert (W6)`

## Bekannte Issues / pragmatische Entscheidungen
- `LargeSpacing = 24.dp` wird dual verwendet: als vertikaler Abschnittsseparator (HomeScreen Spacers) UND als Größe für 24dp CircularProgressIndicator (GPS/Wetter-Button in ProjectFormScreen). Semantisch leicht unbefriedigend, aber konservative Konsolidierung gemäß Plan-Regel "konsolidieren, nicht duplizieren".
- `PhotoGridMinCell = 180.dp` und `MapPreviewHeight = 180.dp` haben denselben numerischen Wert aber unterschiedliche Semantik (Grid-Spaltenbreite vs. Bildhöhe) — beide Tokens beibehalten.
- `IconSizeXLarge = 32.dp` dient als Größe für IconButton-Touchflächen (32dp) im AudioPlaybackRow-Kompaktlayout. 32dp ist kein vollständiges Touch-Target (≥56dp), aber für eine kompakte Audio-Playback-Zeile innerhalb einer Note-Card akzeptabel. Konservative Entscheidung: keine Vergrößerung auf TouchMedium (würde Card-Proportionen sprengen).
- `OutlinedTextField.heightIn(min = Dimensions.InputHeight)` statt `height()` — `Modifier.height()` würde bei Dropdown-Feldern mit geöffnetem Menü oder floating Label clippen. `heightIn(min = ...)` ist das korrekte Compose-Pattern für Mindesthöhen bei TextField.
- Warnings (`ArrowBack deprecated`, `LinearProgressIndicator overload`) sind pre-existent aus W5, nicht durch W6 eingeführt.

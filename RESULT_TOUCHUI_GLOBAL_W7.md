# Welle W7 — Settings + Connection + OfflineMaps + Splash

**Stand:** 2026-05-20 01:30
**Branch:** feature/touchui-global
**Commit:** 3269ddf

## Geänderte Files
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (+17/-0)
- `app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt` (+1278/-1287)
- `app/src/main/java/com/uip/oneapp/ui/screens/settings/UpdateSection.kt` (+105/-107)
- `app/src/main/java/com/uip/oneapp/ui/screens/connection/ConnectionScreen.kt` (+380/-403)
- `app/src/main/java/com/uip/oneapp/ui/screens/offlinemaps/OfflineMapsScreen.kt` (+134/-143)
- `app/src/main/java/com/uip/oneapp/ui/screens/splash/SplashScreen.kt` (+34/-43)

## Diff-Summary
1. **Dimensions.kt** um 13 neue Tokens erweitert: `IconSizeStandard` (24dp), `CompanyLogoHeight` (80dp), `LogAreaHeight` (150dp), `LabelColumnWidth` (80dp), `TinyFontSize` (10sp), `DialogContentMinHeight` (200dp), `DialogContentMaxHeight` (480dp), `XLargeSpacing` (32dp), `SplashTitleFontSize` (64sp), `SplashSubtitleFontSize` (28sp), `SplashButtonWidth` (200dp), `LetterSpacingBrand` (2sp), `LetterSpacingSubtitle` (8sp), `LineHeightBody` (20sp)
2. **SettingsScreen**: Alle Card-Section-Titel auf `SectionTitleFontSize` (22sp); alle Switch-Rows mit `heightIn(min = TouchMedium)` (56dp); alle OutlinedTextField mit `heightIn(min = InputHeight)` + `textStyle(InputFontSize = 18sp)`; Primary-Button "Test Connection" auf `height(TouchLarge)` (72dp); OutlinedButton Logo-Picker auf `height(TouchMedium)`; alle dp/sp-Literale durch Dimensions.*-Tokens ersetzt
3. **UpdateSection**: Section-Titel auf `SectionTitleFontSize`; "Check for Update"-Button auf `height(TouchLarge)`; OutlinedTextField Channel-Picker mit `heightIn(min = InputHeight)` + textStyle; alle dp/sp-Literale tokenisiert
4. **ConnectionScreen**: Section-Titel aller Cards auf `SectionTitleFontSize`; alle FilledTonalButton/OutlinedButton Steuerknöpfe auf `height(TouchMedium)` (upgrade von 32dp); Primary "Start Stream"-Button auf `height(TouchLarge)`; Hardware-Status-Rows mit `heightIn(min = CardMinHeight)` (72dp); OutlinedTextField ManualURL mit `heightIn(min = InputHeight)` + textStyle; `IconSizeStandard` (24dp) für Standard-Icons; `LogAreaHeight`, `LabelColumnWidth`, `TinyFontSize` für Diagnose-Bereich; alle dp/sp-Literale tokenisiert
5. **OfflineMapsScreen**: InstalledMapRow mit `heightIn(min = CardMinHeight)` + korrektem `TouchSpacing`-Padding; PickerDialog Rows mit `heightIn(min = CardMinHeight)`; `DialogContentMinHeight`/`DialogContentMaxHeight` für Dialog-Column; `IconSizeStandard` + `StrokeWidthMedium` für CircularProgressIndicator; Section-Titel auf `SectionTitleFontSize`; alle dp/sp-Literale tokenisiert (10dp-Padding auf 12dp = `TouchSpacing` aufgerundet, dokumentiert)
6. **SplashScreen**: Compact-Logik entfernt (Tablet-only App, kein Gerät < 500dp screenHeight erwartet); Logo `SplashTitleFontSize` (64sp), `SplashSubtitleFontSize` (28sp); Button auf `height(TouchLarge)` + `width(SplashButtonWidth)`; `RoundedCornerShape(ButtonCornerRadius)`; alle typografischen Parameter tokenisiert inkl. `LetterSpacingBrand`, `LetterSpacingSubtitle`, `LineHeightBody`

## Compile-Status
- `compileDebugKotlin`: **BUILD SUCCESSFUL** (27 s, 3 executed / 14 up-to-date)

## Akzeptanzkriterien
- [x] Compile OK
- [x] `grep -E '[0-9]+\.(dp|sp)'` auf alle 5 Screen-Files → **0 Treffer**
- [x] Settings-Switches haben ≥ 56 dp Touch-Höhe (gesamte Row via `heightIn(min = Dimensions.TouchMedium)`)
- [x] Section-Titel in allen Screens auf `Dimensions.SectionTitleFontSize` (22 sp)
- [x] Input-Felder: `heightIn(min = Dimensions.InputHeight)` + `textStyle(InputFontSize = 18 sp)`
- [x] Primary-Buttons: `height(Dimensions.TouchLarge)` (72 dp)
- [x] Secondary-Buttons: `height(Dimensions.TouchMedium)` (56 dp)
- [x] Commit `feat(ui): Settings/Connection/OfflineMaps/Splash touch-optimiert (W7)`

## Bekannte Issues / pragmatische Entscheidungen
- **SplashScreen Compact-Logik entfernt**: Die ursprüngliche `screenHeight < 500`-Bedingung für kompakten Modus wurde entfernt. Auf einem Android-Tablet im Landscape-Modus ist `screenHeightDp < 500` nicht realistisch. Konservative Entscheidung: volle Größen ohne Fallback.
- **10dp → 12dp in OfflineMapsScreen**: `padding(top = 10.dp, bottom = 4.dp)` für Picker-Dialog-Sektionsheader wurde zu `padding(top = Dimensions.TouchSpacing, bottom = Dimensions.SmallSpacing)` (12dp / 4dp) aufgerundet, da kein 10dp-Token existiert. Optisch minimal unterschiedlich, funktional korrekt.
- **Spacer 10dp in InstalledMapRow**: `Spacer(Modifier.width(10.dp))` → `Spacer(Modifier.width(Dimensions.TouchSpacing))` (12dp). Gleiche Begründung.
- **ConnectionScreen optionales DeviceType.ONE-Hiding nicht implementiert**: Das optionale Feature "Scan-Button und manuelle RTSP-URL ausblenden für DeviceType.ONE" wurde nicht umgesetzt, da `deviceType` nicht Teil von `ConnectionUiState` ist. Dies würde eine ViewModel-Änderung erfordern, die über den W7-Scope hinausgeht. Dokumentiert für spätere Implementierung.
- **Deprecation-Warnings pre-existent**: `Divider` (→ HorizontalDivider), `LinearProgressIndicator` overload, `ArrowBack`, `CircularProgressIndicator` overload — alle Warnungen sind pre-existent aus W5/W6, nicht durch W7 eingeführt.
- **OsdSmallFontSize (14sp) für SplashScreen-Disclaimer**: Semantisch leicht unbefriedigend (Token wurde für OSD-Overlay konzipiert), aber 14sp ist der exakte Wert und kein eigener Splash-Token wurde eingeführt. Konservative Konsolidierung.

# Welle W8 — Dialoge touch-optimiert

**Stand:** 2026-05-20 02:15
**Branch:** feature/touchui-global
**Commit:** 09e4fbb

## Geänderte Files
- `app/src/main/java/com/uip/oneapp/ui/theme/Dimensions.kt` (+14/-0)
- `app/src/main/java/com/uip/oneapp/ui/screens/inspection/DamageDialog.kt` (+37/-28)
- `app/src/main/java/com/uip/oneapp/ui/screens/projectdetail/VideoPlaybackDialog.kt` (+30/-20)
- `app/src/main/java/com/uip/oneapp/ui/screens/projectdetail/PdfPreviewDialog.kt` (+14/-10)
- `app/src/main/java/com/uip/oneapp/ui/screens/projects/MapPickerDialog.kt` (+18/-13)
- `app/src/main/java/com/uip/oneapp/ui/components/UpdateDialog.kt` (+16/-9)
- `app/src/main/java/com/uip/oneapp/ui/components/UpdateProgressDialog.kt` (+6/-3)

## Diff-Summary
1. **Dimensions.kt** um 7 neue Tokens erweitert: `DialogCornerRadius` (16dp), `MultilineInputHeight` (100dp), `VideoControlsBottomPadding` (80dp), `CardElevationHigh` (4dp), `IconSizeXXSmall` (12dp), `MapMarkerOuterRadius` (12dp), `MapMarkerInnerRadius` (8dp)
2. **DamageDialog**: Card-Corner auf `DialogCornerRadius`; Close-Icon auf `DialogCloseIconSize` (40dp); alle Photo-Preview-Boxes auf `DialogContentMinHeight` (200dp) + tokenisierte Borders/Corners; Description-Textarea + No-Photo-Platzhalter auf `MultilineInputHeight` (100dp); Save-Button auf `height(DialogButtonHeight)` (56dp); alle dp/sp-Literale durch Dimensions.*-Tokens ersetzt
3. **VideoPlaybackDialog**: Close-Icon auf `DialogCloseIconSize` (40dp); alle 3 Action-Buttons (Photo/Damage/Note) auf `height(DialogButtonHeight)` (56dp); `VideoControlsBottomPadding` (80dp) für Abstand vom Playerrand; alle Spacer/Padding auf Dimensions.*-Tokens
4. **PdfPreviewDialog**: Close-Icon auf `DialogCloseIconSize` (40dp); Export-Button auf `height(DialogButtonHeight)` (56dp); Card-Elevation auf `CardElevationHigh` (4dp); LazyColumn-Padding und Spacer auf `TouchSpacing` (12dp); alle dp/sp-Literale tokenisiert
5. **MapPickerDialog**: Close-Icon auf `DialogCloseIconSize` (40dp); „Übernehmen"-TextButton auf `height(DialogButtonHeight)` (56dp); Canvas-Marker-Radien auf `MapMarkerOuterRadius`/`MapMarkerInnerRadius`; Marker-Stroke auf `BorderWidthDefault` (1dp); Padding/Corner via Dimensions.*-Tokens
6. **UpdateDialog**: Warn-Icon auf `IconSizeLarge` (20dp); Install-Button + Later-TextButton auf `height(DialogButtonHeight)` (56dp); Release-Notes-Box `heightIn(max = DialogContentMinHeight)`; alle Spacer via Dimensions.*
7. **UpdateProgressDialog**: Cancel-TextButton auf `height(DialogButtonHeight)` (56dp); Spacer auf `TouchSpacing` (12dp)

## Compile-Status
- `compileDebugKotlin`: **BUILD SUCCESSFUL** (13 s, 3 executed / 14 up-to-date)

## Akzeptanzkriterien
- [x] Compile OK
- [x] AlertDialog/Modal-Buttons: `Modifier.height(Dimensions.DialogButtonHeight)` (56dp) — alle 7 Dialog-Buttons umgestellt
- [x] Close-Icons (X oben rechts): `Modifier.size(Dimensions.DialogCloseIconSize)` (40dp) — DamageDialog, VideoPlaybackDialog, PdfPreviewDialog, MapPickerDialog
- [x] Input-Felder im Dialog: `Dimensions.InputHeight` / `Dimensions.MultilineInputHeight` — DamageDialog OutlinedTextFields
- [x] Body-Text: MaterialTheme.typography.bodyMedium (≥ 16sp via Theme) — alle Dialoge nutzen Typography-Styles, kein harter sp-Wert
- [x] DamageDialog Listen-Items (Photo-Previews): `Dimensions.DialogContentMinHeight` (200dp)
- [x] `grep -E '[0-9]+\.(dp|sp)'` auf alle 6 Dialog-Files → **0 Treffer**
- [x] Commit `feat(ui): Dialoge touch-optimiert (W8)`

## Bekannte Issues / pragmatische Entscheidungen
- **`Icons.Filled.Note` Deprecation-Warning in VideoPlaybackDialog**: `Icons.Filled.Note` ist deprecated zugunsten `Icons.AutoMirrored.Filled.Note`. Warnung ist pre-existent aus früheren Wellen und wurde nicht durch W8 eingeführt. Nicht in W8-Scope behoben (kein Touch-UI-Bezug).
- **`DialogContentMinHeight` (200dp) als max-Height für UpdateDialog Notes-Box**: Semantisch leicht unbefriedigend (Token heißt „MinHeight"), aber der Wert 200dp passt exakt und vermeidet Token-Duplizierung. Konservative Konsolidierung gemäß Plan-Direktive.
- **MapPickerDialog TextButton height**: `TextButton` in TopAppBar-Actions mit `Modifier.height(DialogButtonHeight)` — der TopAppBar-Container begrenzt die sichtbare Höhe ohnehin auf seine eigene Höhe. Die Modifier-Anweisung ist korrekt, hat aber keinen visuellen Effekt auf die TopAppBar-Actions. Buttons innerhalb von Dialog-Bodys (nicht TopAppBar) haben den vollen Effekt.
- **MapPickerDialog Canvas-Radien**: `MapMarkerOuterRadius = 12.dp` und `MapMarkerInnerRadius = 8.dp` als neue Tokens. `MapMarkerInnerRadius` hat denselben Wert wie `OverlayCornerRadius` (8dp), aber andere Semantik (Canvas-Kreis vs. Corner-Shape) — eigene Tokens beibehalten.
- **Body-Text-Größe**: Die Dialoge nutzen `MaterialTheme.typography.bodyMedium` / `bodySmall` (Theme-basiert, ≥ 16sp via Theme-Konfiguration) statt harter `fontSize`-Angaben. Kein explizites `Dimensions.BodyFontSize` in den Dialog-Texten ergänzt, da die bestehende Typography-Zuweisung dem Plan genügt.

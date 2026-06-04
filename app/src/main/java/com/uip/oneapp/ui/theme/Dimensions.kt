package com.uip.oneapp.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Dimensions {
    // Touch-Targets
    val TouchLarge = 72.dp        // ≈ 15 mm — Primary (Sonde, Licht, Reset)
    val TouchMedium = 56.dp       // ≈ 12 mm — Sekundär
    val TouchSpacing = 12.dp      // Mindestabstand zwischen Touch-Targets

    // Cinema-Mode Panel
    val PanelWidth = 320.dp       // Schiebbares rechtes Panel
    val PanelEdgePadding = 16.dp
    val PanelSlideDuration = 250  // ms, FastOutSlowInEasing
    val PanelContentPadding = 10.dp

    // Auto-Hide
    val ControlsAutoHideMs = 5000L

    // OSD
    val OsdDistanceFontSize = 72.sp    // 25% kleiner als ehemals 96sp — dezenter im Cinema-Mode
    val OsdDistanceAlpha = 0.7f        // 30% transparent — verdeckt das Bild nicht so stark
    val OsdSecondaryFontSize = 20.sp   // (aktuell ungenutzt — Sonde/Licht-Zeile aus OSD entfernt)
    val OsdSmallFontSize = 14.sp
    val OsdShadowOffset = 3.dp
    val OsdBoxInnerPadding = 8.dp
    val OsdPadding = 24.dp             // Abstand des persistent OSD vom Bildschirmrand

    // Buttons
    val ButtonLabelFontSize = 18.sp
    val ButtonCornerRadius = 12.dp
    val MeterResetHeight = 80.dp   // Meterzähler-Reset — extra prominent
    val ActionButtonSpacing = 6.dp // 2×2-Grid-Abstand
    val ButtonIconSpacing = 4.dp   // Icon-Text-Abstand in Buttons

    // Icons
    val IconSizeSmall = 16.dp
    val IconSizeMedium = 18.dp

    // General panel spacing
    val SmallSpacing = 4.dp
    val MediumSpacing = 6.dp
    val SectionSpacing = 8.dp

    // Status row
    val StatusRowMinHeight = 44.dp
    val SortButtonSize = 28.dp

    // Lists / thumbnails
    val ThumbnailSize = 36.dp
    val ThumbnailCornerRadius = 4.dp
    val ListItemVerticalPadding = 3.dp
    val SmallItemSpacing = 2.dp

    // Overlays
    val OverlayCornerRadius = 8.dp
    val ProjectOverlayHPadding = 24.dp
    val ProjectOverlayVPadding = 12.dp
    val ProjectInfoPadding = 12.dp

    // Navigation Rail
    val NavRailWidth = 120.dp      // SA-Vorgabe Abschnitt 3 (zuvor 96)
    val NavRailItemHeight = 80.dp
    val NavRailIconSize = 40.dp
    val NavRailLabelFontSize = 16.sp
    val NavRailIndicatorWidth = 56.dp

    // List & Cards
    val CardMinHeight = 72.dp
    val CardElevation = 2.dp
    val SectionTitleFontSize = 22.sp
    val BodyFontSize = 16.sp

    // Inputs
    val InputHeight = 56.dp
    val InputFontSize = 18.sp

    // Dialoge
    val DialogCloseIconSize = 40.dp
    val DialogButtonHeight = 56.dp

    // Slider (Licht im InspectionScreen)
    val SliderThumbSize = 32.dp
    val SliderTrackHeight = 12.dp

    // Additional icon sizes
    val IconSizeXSmall = 14.dp     // tiny inline icons (InfoChip, lock icon)
    val IconSizeLarge = 20.dp      // medium-large icons (battery, form indicators)
    val IconSizeXLarge = 32.dp     // card/action icons, compact button heights
    val IconSizeXXLarge = 48.dp    // feature/empty-state icons
    val IconSizeHuge = 64.dp       // large empty-state icons

    // General layout spacing
    val LargeSpacing = 24.dp       // major section separators; also 24 dp indicator sizes

    // Borders / strokes
    val BorderWidthDefault = 1.dp
    val StrokeWidthMedium = 2.dp

    // Media / photo dimensions
    val DamageThumbnailSize = 60.dp
    val PhotoThumbnailHeight = 140.dp
    val PhotoGridMinCell = 180.dp  // min cell width in photo grid
    val MapPreviewHeight = 180.dp  // map preview image height in ProjectForm

    // Standard Material icon size
    val IconSizeStandard = 24.dp

    // Settings
    val CompanyLogoHeight = 80.dp   // company logo preview height

    // Connection diagnostic
    val LogAreaHeight = 150.dp
    val LabelColumnWidth = 80.dp
    val TinyFontSize = 10.sp

    // Dialogs (W7 / W8 shared)
    val DialogContentMinHeight = 200.dp
    val DialogContentMaxHeight = 480.dp

    // Extra spacing
    val XLargeSpacing = 32.dp

    // Splash screen
    val SplashTitleFontSize = 64.sp
    val SplashSubtitleFontSize = 28.sp
    val SplashButtonWidth = 200.dp
    val LetterSpacingBrand = 2.sp
    val LetterSpacingSubtitle = 8.sp
    val LineHeightBody = 20.sp

    // W8 — Dialog-specific
    val DialogCornerRadius = 16.dp         // Card-Ecken in fullscreen-Dialog-Cards
    val MultilineInputHeight = 100.dp      // Mehrzeilige Textarea + No-Photo-Platzhalter
    val VideoControlsBottomPadding = 80.dp // Abstand der Video-Action-Buttons vom unteren Rand
    val CardElevationHigh = 4.dp           // Erhöhte Card-Elevation (PDF-Seiten)
    val IconSizeXXSmall = 12.dp            // Winzige Label-Icons in Annotationen

    // Map marker (canvas drawing)
    val MapMarkerOuterRadius = 12.dp       // Weißer Außenkreis des Kartenmarkers
    val MapMarkerInnerRadius = 8.dp        // Roter Innenkreis des Kartenmarkers

    // =================================================================================
    // DrainQ SA-Design — Maße (Vorgabe Abschnitt 3). Für neue Dq*-Composables.
    // =================================================================================
    val ButtonHeight       = 56.dp   // Standard-CTA
    val ButtonHeightLarge  = 72.dp   // zentrale CTA / Schnellaufnahme
    val IconButtonSize     = 56.dp   // Touch-Icon-Button (min 48)
    val BackButtonSize     = 60.dp   // Inspektion Zurück-Affordanz (~25 % größer als 48-dp-Default)
    val SoftButtonHeight   = 90.dp   // Inspektions-Leiste (~80 % — weniger Videoverdeckung; Icon/Label unverändert)
    val NavItemHeight      = 84.dp   // Nav-Eintrag (SA)
    val HeaderHeight       = 64.dp   // App-Header
    val CardPadding        = 16.dp
    val TouchMin           = 48.dp   // absolutes Minimum

    // Icons (SA): inline / std / toolbar / large
    val DqIconInline   = 24.dp
    val DqIconStd      = 28.dp
    val DqIconToolbar  = 32.dp
    val DqIconLarge    = 36.dp

    // Spacing-Skala (SA): 4/8/12/16/20/24 dp
    val Space4  = 4.dp
    val Space8  = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp

    // Komponenten-Maße (SA)
    val PillHeight     = 32.dp   // DqPill/DqStatusChip
    val ToggleWidth    = 64.dp   // DqToggle
    val ToggleHeight   = 36.dp
    val ToggleKnob     = 28.dp
    val SettingRowHeight = 56.dp // DqSettingRow/DqDropdownRow
    val FocusRingWidth = 2.dp    // Focus 2 dp Amber-Outline
}

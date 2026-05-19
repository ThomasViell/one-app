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
    val OsdDistanceFontSize = 96.sp
    val OsdSecondaryFontSize = 20.sp
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
}

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

    // Auto-Hide
    val ControlsAutoHideMs = 5000L

    // OSD
    val OsdDistanceFontSize = 96.sp
    val OsdSecondaryFontSize = 20.sp
    val OsdSmallFontSize = 14.sp
    val OsdShadowOffset = 3.dp

    // Buttons
    val ButtonLabelFontSize = 18.sp
    val ButtonCornerRadius = 12.dp
}

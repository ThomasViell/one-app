package com.uip.oneapp.ui.utils

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.compositionLocalOf

val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass not provided")
}

val WindowSizeClass.isExpanded: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded

val WindowSizeClass.isCompact: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Compact

/** True for Medium + Expanded: use NavigationRail instead of BottomBar */
val WindowSizeClass.usesRail: Boolean
    get() = widthSizeClass != WindowWidthSizeClass.Compact

/** Weight of the video player area (left side) in InspectionScreen */
val WindowSizeClass.videoWeight: Float
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 0.62f
        WindowWidthSizeClass.Medium  -> 0.68f
        else                         -> 0.72f  // Expanded
    }

package com.uip.oneapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary                = DarkPrimary,
    onPrimary              = DarkOnPrimary,
    primaryContainer       = DarkPrimaryVariant,
    onPrimaryContainer     = DarkOnPrimary,
    secondary              = DarkSecondary,
    onSecondary            = DarkOnSecondary,
    secondaryContainer     = Dt3ctBlue.copy(alpha = 0.25f),
    onSecondaryContainer   = DarkOnBackground,
    tertiary               = DarkTertiary,
    onTertiary             = DarkOnPrimary,
    background             = DarkBackground,
    onBackground           = DarkOnBackground,
    surface                = DarkSurface,
    onSurface              = DarkOnSurface,
    surfaceVariant         = DarkSurfaceVariant,
    onSurfaceVariant       = DarkOnSurface,
    error                  = DarkError,
    onError                = DarkOnError
)

@Composable
fun OneAppTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars     = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        shapes      = AppShapes,
        content     = content
    )
}

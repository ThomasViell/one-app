package com.uip.oneapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =====================================================================================
// DrainQ SA-Design — Theme (Vorgabe Abschnitt 1, Material3-Mapping)
// Zwei gleichwertige Schemata (Dark Standard), zur Laufzeit umschaltbar.
// =====================================================================================

/** Zusätzliche SA-Rollen, die nicht im Material3-ColorScheme abgebildet sind. */
@Immutable
data class DqColors(
    val amber: Color,
    val amberHover: Color,
    val onAmber: Color,
    val bgWindow: Color,
    val bgPanel: Color,
    val bgSidebar: Color,
    val bgElevated: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val videoBg: Color,
    val osdBg: Color,
    // Code-Familienfarben (themenunabhängig)
    val codeLeitung: Color,
    val codeSchacht: Color,
    val codeAnschluss: Color,
    val codeStrecke: Color,
    val codeBetrieb: Color,
    val isDark: Boolean,
)

private val DqDarkColors = DqColors(
    amber = Amber, amberHover = AmberHoverDark, onAmber = OnAmber,
    bgWindow = BgWindowDark, bgPanel = BgPanelDark, bgSidebar = BgSidebarDark, bgElevated = BgElevatedDark,
    borderSubtle = BorderSubtleDark,
    textPrimary = TextPrimaryDark, textSecondary = TextSecondaryDark, textTertiary = TextTertiaryDark,
    success = SuccessDark, warning = WarningDark, error = ErrorDark, info = InfoDark,
    videoBg = VideoBgColor, osdBg = OsdBgDark,
    codeLeitung = CodeLeitung, codeSchacht = CodeSchacht, codeAnschluss = CodeAnschluss,
    codeStrecke = CodeStrecke, codeBetrieb = CodeBetrieb,
    isDark = true,
)

private val DqLightColors = DqColors(
    amber = Amber, amberHover = AmberHoverLight, onAmber = OnAmber,
    bgWindow = BgWindowLight, bgPanel = BgPanelLight, bgSidebar = BgSidebarLight, bgElevated = BgElevatedLight,
    borderSubtle = BorderSubtleLight,
    textPrimary = TextPrimaryLight, textSecondary = TextSecondaryLight, textTertiary = TextTertiaryLight,
    success = SuccessLight, warning = WarningLight, error = ErrorLight, info = InfoLight,
    videoBg = VideoBgColor, osdBg = OsdBgLight,
    codeLeitung = CodeLeitung, codeSchacht = CodeSchacht, codeAnschluss = CodeAnschluss,
    codeStrecke = CodeStrecke, codeBetrieb = CodeBetrieb,
    isDark = false,
)

val LocalDqColors = staticCompositionLocalOf { DqDarkColors }

/** Zugriff auf SA-Tokens: `DrainQTheme.colors.amber` etc. */
object DrainQTheme {
    val colors: DqColors
        @Composable get() = LocalDqColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary              = Amber,
    onPrimary            = OnAmber,                 // dunkel auf Amber!
    primaryContainer     = Amber.copy(alpha = 0.16f),
    onPrimaryContainer   = Amber,
    secondary            = InfoDark,
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = InfoDark.copy(alpha = 0.16f),
    onSecondaryContainer = InfoDark,
    tertiary             = SuccessDark,
    onTertiary           = OnAmber,
    background           = BgWindowDark,
    onBackground         = TextPrimaryDark,
    surface              = BgPanelDark,
    onSurface            = TextPrimaryDark,
    surfaceVariant       = BgSidebarDark,
    onSurfaceVariant     = TextSecondaryDark,
    error                = ErrorDark,
    onError              = Color(0xFFFFFFFF),
    outline              = BorderSubtleDark,
    outlineVariant       = BgElevatedDark,
)

private val LightColorScheme = lightColorScheme(
    primary              = Amber,
    onPrimary            = OnAmber,                 // dunkel auf Amber!
    primaryContainer     = Amber.copy(alpha = 0.16f),
    onPrimaryContainer   = AmberHoverLight,
    secondary            = InfoLight,
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = InfoLight.copy(alpha = 0.16f),
    onSecondaryContainer = InfoLight,
    tertiary             = SuccessLight,
    onTertiary           = Color(0xFFFFFFFF),
    background           = BgWindowLight,
    onBackground         = TextPrimaryLight,
    surface              = BgPanelLight,
    onSurface            = TextPrimaryLight,
    surfaceVariant       = BgSidebarLight,
    onSurfaceVariant     = TextSecondaryLight,
    error                = ErrorLight,
    onError              = Color(0xFFFFFFFF),
    outline              = BorderSubtleLight,
    outlineVariant       = BgElevatedLight,
)

/**
 * Wurzel-Theme. `darkTheme` wird vom Aufrufer aus der `themeMode`-Pref abgeleitet
 * (System/Dunkel/Hell — siehe [ThemeMode] / [rememberDarkTheme]).
 * System-/Navigationsleiste + `isAppearanceLight*Bars` werden je Theme dynamisch gesetzt.
 */
@Composable
fun DrainQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val dqColors = if (darkTheme) DqDarkColors else DqLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars     = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalDqColors provides dqColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography  = Typography,
            shapes      = AppShapes,
            content     = content
        )
    }
}

// Backwards-compatibility-Alias (folgt Systemeinstellung, bis der Aufrufer themeMode liefert)
@Composable
fun OneAppTheme(content: @Composable () -> Unit) = DrainQTheme(content = content)

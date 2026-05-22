package com.uip.oneapp.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.uip.oneapp.MainActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uip.oneapp.R
import com.uip.oneapp.ui.theme.Dimensions
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.screens.connection.ConnectionScreen
import com.uip.oneapp.ui.screens.home.HomeScreen
import com.uip.oneapp.ui.screens.inspection.InspectionScreen
import com.uip.oneapp.ui.screens.projects.ProjectFormScreen
import com.uip.oneapp.ui.screens.projectdetail.ProjectDetailScreen
import com.uip.oneapp.ui.screens.projects.ProjectsScreen
import com.uip.oneapp.ui.screens.reports.ReportsScreen
import com.uip.oneapp.ui.screens.settings.SettingsScreen
import com.uip.oneapp.ui.screens.offlinemaps.OfflineMapsScreen
import com.uip.oneapp.ui.utils.LocalWindowSizeClass
import com.uip.oneapp.ui.utils.usesRail

/**
 * Steuert die Sichtbarkeit der NavigationRail aus den Routen heraus.
 *
 * Standard: true (Rail immer sichtbar).
 * Der InspectionScreen schaltet den Wert auf false (versteckt), wenn die Controls
 * im Cinema-Mode ausgeblendet sind, und wieder auf true beim Verlassen der Route.
 * Auf BottomBar-Devices (Compact) wird der Wert ignoriert.
 */
val LocalNavRailVisible = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(true)
}

sealed class Screen(
    val route: String,
    val titleKey: String,
    val icon: ImageVector
) {
    object Connection : Screen("connection", "nav_connection", Icons.Default.Link)
    object Home : Screen("home", "nav_home", Icons.Default.Home)
    object Projects : Screen("projects", "nav_projects", Icons.Default.Folder)
    object Inspection : Screen("inspection", "nav_inspection", Icons.Default.Videocam)
    object Reports : Screen("reports", "nav_reports", Icons.Default.Assessment)
    object Settings : Screen("settings", "nav_settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Inspection,
    Screen.Projects
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph() {
    val currentLang by LocalizationManager.currentLanguage.collectAsState()
    val windowSizeClass = LocalWindowSizeClass.current
    val usesRail = windowSizeClass.usesRail

    key(currentLang) {
        val navController = rememberNavController()

        if (usesRail) {
            NavGraphRail(navController)
        } else {
            NavGraphBottomBar(navController)
        }
    }
}

@Composable
private fun NavGraphRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activity = LocalContext.current as? MainActivity

    // Bei jedem Routen-Wechsel ScreenDecorOverlayBottom erneut ausblenden.
    // onWindowFocusChanged() greift nicht für Compose-Navigation (gleiche Activity),
    // aber der InspectionScreen-Video-Player triggert beim Abbau eine SystemUI-Neuberechnung
    // die den Balken wieder zeigt.
    LaunchedEffect(currentDestination) {
        activity?.requestHideDecorBar()
    }

    val railVisibleState = remember { mutableStateOf(true) }

    // Rail-Breite animiert zwischen 0 und NavRailWidth — Content-Bereich füllt den Rest.
    // Row-Layout statt Overlay: kein Clipping von TopAppBars oder Formularfeldern mehr.
    val railWidth by animateDpAsState(
        targetValue = if (railVisibleState.value) Dimensions.NavRailWidth else 0.dp,
        animationSpec = tween(
            durationMillis = Dimensions.PanelSlideDuration,
            easing = FastOutSlowInEasing
        ),
        label = "navRailWidth"
    )

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .clipToBounds(),
            windowInsets = WindowInsets(0)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            bottomNavItems.forEach { screen ->
                val label = S(screen.titleKey)
                val oneIconRes: Int? = when (screen) {
                    Screen.Inspection -> R.drawable.ic_one_recording
                    Screen.Settings -> R.drawable.ic_one_settings
                    else -> null
                }
                NavigationRailItem(
                    icon = {
                        if (oneIconRes != null) {
                            Icon(
                                painter = painterResource(id = oneIconRes),
                                contentDescription = label,
                                modifier = Modifier.size(Dimensions.NavRailIconSize),
                                tint = Color.Unspecified
                            )
                        } else {
                            Icon(
                                screen.icon,
                                contentDescription = label,
                                modifier = Modifier.size(Dimensions.NavRailIconSize)
                            )
                        }
                    },
                    label = {
                        Text(
                            label,
                            fontSize = Dimensions.NavRailLabelFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                    onClick = { navigateTo(navController, screen) },
                    modifier = Modifier.height(Dimensions.NavRailItemHeight)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        CompositionLocalProvider(LocalNavRailVisible provides railVisibleState) {
            NavGraphRoutes(
                navController = navController,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun NavGraphBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activity = LocalContext.current as? MainActivity

    LaunchedEffect(currentDestination) {
        activity?.requestHideDecorBar()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val label = S(screen.titleKey)
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = { navigateTo(navController, screen) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavGraphRoutes(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

private fun navigateTo(navController: NavController, screen: Screen) {
    navController.navigate(screen.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

@Composable
private fun NavGraphRoutes(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Connection.route) { ConnectionScreen() }
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Projects.route) { ProjectsScreen(navController) }
        composable(Screen.Inspection.route) { InspectionScreen(navController) }
        composable(
            "inspection/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId")
            InspectionScreen(navController, projectId = projectId)
        }
        composable(Screen.Reports.route) { ReportsScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(
            "project_detail/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            ProjectDetailScreen(navController, projectId = projectId)
        }
        composable("offline_maps") { OfflineMapsScreen(navController) }
        composable("project_form") { ProjectFormScreen(navController) }
        composable(
            "project_form/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            ProjectFormScreen(navController, editProjectId = projectId)
        }
    }
}

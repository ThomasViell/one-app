package com.uip.oneapp.ui.navigation

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.uip.oneapp.R
import com.uip.oneapp.ui.components.DqNavItem
import com.uip.oneapp.ui.components.DqNavRail
import com.uip.oneapp.ui.theme.DrainQTheme
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
import androidx.compose.runtime.LaunchedEffect
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.debugrig.ScreenshotRigBus
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
import com.uip.oneapp.ui.screens.network.NetworkScreen
import com.uip.oneapp.ui.screens.network.CloudLoginScreen
import com.uip.oneapp.ui.screens.pairing.PairingScreen
import com.uip.oneapp.ui.utils.LocalWindowSizeClass
import com.uip.oneapp.ui.utils.usesRail

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

// Hauptnavigation verschlankt (Feedback Louis #3): Home / Inspektion / Einstellungen.
// "Projekte" entfällt aus Leiste + Rail, bleibt aber über die Home-Kachel erreichbar
// (Route + andere Aufrufer unverändert). Inspektion bleibt direkt erreichbar (meistgenutzt).
val bottomNavItems = listOf(
    Screen.Home,
    Screen.Inspection,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph() {
    val currentLang by LocalizationManager.currentLanguage.collectAsState()
    val windowSizeClass = LocalWindowSizeClass.current
    val usesRail = windowSizeClass.usesRail

    key(currentLang) {
        val navController = rememberNavController()

        if (BuildConfig.DEBUG) {
            LaunchedEffect(navController) {
                ScreenshotRigBus.navigate.collect { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
        }

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

    // Inspektion = Vollbild/Kino: linke Leiste ausblenden, damit Video + die feste
    // Softbutton-Leiste die GANZE Breite nutzen und exakt über den Hardbuttons sitzen
    // (sonst schiebt die Rail das Video nach rechts → Icons nicht über den Tasten).
    val immersive = currentDestination?.route?.startsWith("inspection") == true

    Row(modifier = Modifier.fillMaxSize()) {
        if (!immersive) {
            // SA-Design: DqNavRail (Tokens, aktiv Amber). Navigation-Logik unverändert.
            val items = bottomNavItems.map {
                DqNavItem(iconKey = it.dqIconKey(), label = S(it.titleKey), route = it.route)
            }
            val selectedRoute = bottomNavItems.firstOrNull { screen ->
                currentDestination?.hierarchy?.any { it.route == screen.route } == true
            }?.route ?: ""
            DqNavRail(
                items = items,
                selectedRoute = selectedRoute,
                onSelect = { item ->
                    bottomNavItems.firstOrNull { it.route == item.route }
                        ?.let { navigateTo(navController, it) }
                },
                modifier = Modifier.statusBarsPadding(),
                header = {
                    // DrainQ-Bildmarke statt Amber-"ONE"-Box, theme-abhängig (gleiche
                    // Theme-Quelle wie der Rest der App). Quadratisch, ContentScale.Fit.
                    val context = LocalContext.current
                    val svgLoader = remember(context) {
                        ImageLoader.Builder(context)
                            .components { add(SvgDecoder.Factory()) }
                            .build()
                    }
                    val logoRes = if (DrainQTheme.colors.isDark)
                        R.raw.logo_drainq_icon_on_dark else R.raw.logo_drainq_icon_on_light
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(logoRes).build(),
                        imageLoader = svgLoader,
                        contentDescription = "DrainQ",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.dp)
                    )
                }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()      // avoid status bar at top
                .navigationBarsPadding()  // avoid Samsung nav buttons at bottom
        ) {
            NavGraphRoutes(navController = navController, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NavGraphBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                bottomNavItems.forEach { screen ->
                    val label = S(screen.titleKey)
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = { navigateTo(navController, screen) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavGraphRoutes(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

/** SA-Design: DqIcon-Key je Nav-Eintrag (Tabler-Outline). */
private fun Screen.dqIconKey(): String = when (this) {
    Screen.Home -> "home"
    Screen.Inspection -> "inspection"
    Screen.Projects -> "projects"
    Screen.Settings -> "settings"
    else -> "home"
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
        composable("network") { NetworkScreen(navController) }
        composable("pairing") { PairingScreen(navController) }
        composable("cloud_login") { CloudLoginScreen(navController) }
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

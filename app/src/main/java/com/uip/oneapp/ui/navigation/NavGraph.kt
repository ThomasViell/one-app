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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    Screen.Projects,
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

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            windowInsets = NavigationRailDefaults.windowInsets  // handles status bar insets
        ) {
            Spacer(modifier = Modifier.weight(1f))
            bottomNavItems.forEach { screen ->
                val label = S(screen.titleKey)
                NavigationRailItem(
                    icon = { Icon(screen.icon, contentDescription = label) },
                    label = { Text(label) },
                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                    onClick = { navigateTo(navController, screen) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
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

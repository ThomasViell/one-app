package com.uip.oneapp.ui.navigation

import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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

    key(currentLang) {
    val navController = rememberNavController()
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
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
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
    } // key(currentLang)
}

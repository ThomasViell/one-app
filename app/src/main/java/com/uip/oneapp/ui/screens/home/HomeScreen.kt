package com.uip.oneapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.OneHardwareState
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.*
import com.uip.oneapp.ui.utils.LocalWindowSizeClass
import com.uip.oneapp.ui.utils.usesRail
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val projectRepository: ProjectRepository = koinInject()
    val hardwareService: HardwareService = koinInject()
    val projects by projectRepository.getAllProjects().collectAsState(initial = emptyList())
    val hwState by hardwareService.hardwareState.collectAsState()
    val windowSizeClass = LocalWindowSizeClass.current

    val isConnected = hwState.connectionStatus.tcpConnected
    val recentProjects = projects.take(5)

    if (windowSizeClass.usesRail) {
        // Two-column layout for large tablets
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left column: status + quick actions
            Column(modifier = Modifier.weight(0.42f)) {
                Text(
                    text = S("dashboard_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(24.dp))
                HomeConnectionCard(hwState, isConnected)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = S("quick_access"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                HomeQuickActions(navController)
            }
            // Right column: project list
            Column(modifier = Modifier.weight(0.58f)) {
                HomeProjectsSection(navController, projects, recentProjects)
            }
        }
    } else {
        // Single-column layout for compact/medium
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = S("dashboard_title"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            HomeConnectionCard(hwState, isConnected)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = S("quick_access"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeQuickActions(navController)
            Spacer(modifier = Modifier.height(24.dp))
            HomeProjectsSection(navController, projects, recentProjects)
        }
    }
}

@Composable
private fun HomeConnectionCard(
    hwState: OneHardwareState,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Connected else Disconnected)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = S("one_controller"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isConnected) "${S("status_connected")} (${hwState.connectionStatus.discoveredIp})" else S("status_not_connected"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BatteryStd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${hwState.cableController.batteryLevel ?: 0}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActions(navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Add,
            title = S("new_project"),
            onClick = { navController.navigate("project_form") }
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Videocam,
            title = S("inspection"),
            onClick = { navController.navigate("inspection") }
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Assessment,
            title = S("reports"),
            onClick = { navController.navigate("reports") }
        )
    }
}

@Composable
private fun HomeProjectsSection(
    navController: NavController,
    projects: List<ProjectEntity>,
    recentProjects: List<ProjectEntity>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = S("current_projects"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (projects.isNotEmpty()) {
            TextButton(onClick = { navController.navigate("projects") }) {
                Text(S("show_all"))
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (recentProjects.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = S("no_projects"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate("project_form") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S("create_project"))
                }
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recentProjects) { project ->
                RecentProjectCard(
                    project = project,
                    onClick = { navController.navigate("project_detail/${project.id}") }
                )
            }
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: ProjectEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.projectNumber.ifEmpty { "---" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (project.auftraggeber.isNotEmpty()) {
                    Text(
                        text = project.auftraggeber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = project.inspektionsdatum,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (project.durchmesser.isNotEmpty()) {
                    Text(
                        text = "DN ${project.durchmesser}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

package com.uip.oneapp.ui.screens.home

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqButtonStyle
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqPager
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import org.koin.compose.koinInject

@Composable
fun HomeScreen(navController: NavController) {
    val projectRepository: ProjectRepository = koinInject()
    val projects by projectRepository.getAllProjects().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val c = DrainQTheme.colors

    // Projektliste paginiert: GENAU 6 pro Seite, clientseitig geteilt, currentPage als State.
    val pageSize = 6
    val totalPages = if (projects.isEmpty()) 1 else (projects.size + pageSize - 1) / pageSize
    var currentPage by remember { mutableIntStateOf(0) }
    val page = currentPage.coerceIn(0, totalPages - 1)
    val pageProjects = remember(projects, page) {
        projects.drop(page * pageSize).take(pageSize)
    }

    // Echte KPIs (keine Platzhalter): Anzahl, heute angelegt, freier Speicher.
    val todayCount = remember(projects) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        projects.count { it.createdAt >= startOfDay }
    }
    val freeGb = remember(context) {
        runCatching { StatFs(context.filesDir.absolutePath).availableBytes / 1_000_000_000L }
            .getOrDefault(0L)
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bgWindow)) {
        // Verbindungs-Status-Chip im Header ersatzlos entfernt (kein actions-Slot).
        DqHeader(
            title = S("nav_home")
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space16),
        ) {
            // CTAs: Amber Schnellaufnahme (Large) + Secondary Neues Projekt
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.Space16)) {
                DqButton(
                    text = S("quick_capture"),
                    iconKey = "camera",
                    style = DqButtonStyle.Primary,
                    large = true,
                    onClick = { navController.navigate("inspection") },
                    modifier = Modifier.weight(1f),
                )
                DqButton(
                    text = S("new_project"),
                    iconKey = "new_project",
                    style = DqButtonStyle.Secondary,
                    large = true,
                    onClick = { navController.navigate("project_form") },
                    modifier = Modifier.weight(1f),
                )
            }

            // Stat-Karten (KPI 40 sp)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.Space16)) {
                StatCard(Modifier.weight(1f), S("nav_projects"), projects.size.toString())
                StatCard(Modifier.weight(1f), S("stat_today"), todayCount.toString())
                StatCard(Modifier.weight(1f), S("storage_free"), "$freeGb GB")
            }

            // Projekte — volle Liste durchblätterbar (Pager statt "Alle anzeigen")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Projektliste ist seit der Navi-Verschlankung (Feedback Louis #3) nur noch
                // über Home erreichbar → Überschrift als Sprung in die volle Projektliste.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { navController.navigate("projects") }
                        .padding(vertical = Dimensions.Space4, horizontal = Dimensions.Space4),
                ) {
                    Text(S("nav_projects"), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Spacer(Modifier.width(Dimensions.Space8))
                    DqIcon("chevron_right", size = Dimensions.DqIconInline, tint = c.textSecondary)
                }
                if (totalPages > 1) {
                    DqPager(
                        currentPage = page,
                        pageCount = totalPages,
                        onPageSelected = { currentPage = it },
                    )
                }
            }

            if (pageProjects.isEmpty()) {
                DqCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Dimensions.Space24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DqIcon("projects", size = Dimensions.IconSizeXXLarge, tint = c.textSecondary)
                        Spacer(Modifier.height(Dimensions.Space12))
                        Text(S("no_projects"), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        Spacer(Modifier.height(Dimensions.Space16))
                        DqButton(
                            text = S("create_project"),
                            iconKey = "new_project",
                            onClick = { navController.navigate("project_form") },
                        )
                    }
                }
            } else {
                pageProjects.forEach { project ->
                    RecentProjectRow(project) { navController.navigate("project_detail/${project.id}") }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String) {
    val c = DrainQTheme.colors
    DqCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(Dimensions.Space8))
        Text(value, style = MaterialTheme.typography.displaySmall, color = c.textPrimary, maxLines = 1)
    }
}

@Composable
private fun RecentProjectRow(project: ProjectEntity, onClick: () -> Unit) {
    val c = DrainQTheme.colors
    DqCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.bgElevated),
                contentAlignment = Alignment.Center,
            ) {
                DqIcon("camera", tint = c.textSecondary)
            }
            Spacer(Modifier.width(Dimensions.Space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projectTitle(project),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(
                    project.durchmesser.takeIf { it.isNotBlank() }?.let { "DN $it" },
                    project.material.takeIf { it.isNotBlank() },
                    project.inspektionsdatum.takeIf { it.isNotBlank() },
                ).filterNotNull().joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(Dimensions.Space12))
            ProjectStatusChip(project.status)
            Spacer(Modifier.width(Dimensions.Space12))
            DqIcon("chevron_right", tint = c.textTertiary)
        }
    }
}

private fun projectTitle(project: ProjectEntity): String =
    listOf(project.standortAdresse, project.projectNumber)
        .filter { it.isNotBlank() }
        .joinToString(" — ")
        .ifEmpty { "---" }

@Composable
internal fun ProjectStatusChip(status: String) {
    val c = DrainQTheme.colors
    val done = status.uppercase().let { it.contains("DONE") || it.contains("FERTIG") || it.contains("COMPLET") }
    DqStatusChip(
        text = if (done) S("status_done") else S("status_open"),
        color = if (done) c.success else c.warning,
    )
}

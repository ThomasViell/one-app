package com.uip.oneapp.ui.screens.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqHeader
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.DqPager
import com.uip.oneapp.ui.components.DqStatusChip
import com.uip.oneapp.ui.components.KeyboardHideButton
import com.uip.oneapp.ui.components.appHintLocales
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    navController: NavController,
    viewModel: ProjectsViewModel = koinViewModel()
) {
    val projects by viewModel.projects.collectAsState(initial = emptyList())
    val c = DrainQTheme.colors
    var query by remember { mutableStateOf("") }

    // UI-seitiger Filter (kein Repository-Eingriff).
    val filtered = remember(projects, query) {
        if (query.isBlank()) projects
        else projects.filter {
            (it.projectNumber + " " + it.standortAdresse + " " + it.auftraggeber)
                .contains(query, ignoreCase = true)
        }
    }

    // Paginierung: GENAU 6 pro Seite. currentPage bei Filteränderung auf Seite 1 (Index 0).
    val pageSize = 6
    val totalPages = if (filtered.isEmpty()) 1 else (filtered.size + pageSize - 1) / pageSize
    var currentPage by remember(query) { mutableStateOf(0) }
    val page = currentPage.coerceIn(0, totalPages - 1)
    val pageItems = remember(filtered, page) { filtered.drop(page * pageSize).take(pageSize) }

    Scaffold(
        containerColor = c.bgWindow,
        topBar = {
            DqHeader(
                title = S("projects_title"),
                actions = {
                    if (totalPages > 1) {
                        DqPager(
                            currentPage = page,
                            pageCount = totalPages,
                            onPageSelected = { currentPage = it },
                        )
                        Spacer(Modifier.width(Dimensions.Space16))
                    }
                    KeyboardHideButton()
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("project_form") },
                containerColor = c.amber,
                contentColor = c.onAmber,
                icon = { DqIcon("new_project", size = Dimensions.DqIconInline, tint = c.onAmber) },
                text = { Text(S("new_project"), style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.heightIn(min = Dimensions.ButtonHeightLarge)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimensions.Space16),
        ) {
            // Such-Row (56 dp)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                singleLine = true,
                leadingIcon = { DqIcon("search", size = Dimensions.DqIconInline, tint = c.textSecondary) },
                placeholder = { Text(S("search_project")) },
                textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, hintLocales = appHintLocales()),
                keyboardActions = KeyboardActions(onSearch = {}),
            )

            Spacer(modifier = Modifier.height(Dimensions.Space16))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DqIcon("projects", size = Dimensions.IconSizeHuge, tint = c.textSecondary)
                        Spacer(modifier = Modifier.height(Dimensions.Space16))
                        Text(S("no_projects"), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        Text(S("no_projects_hint"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimensions.Space12)) {
                    items(pageItems) { project ->
                        ProjectCard(project) { navController.navigate("project_detail/${project.id}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit) {
    val c = DrainQTheme.colors
    DqCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = listOf(project.standortAdresse, project.projectNumber)
                    .filter { it.isNotBlank() }.joinToString(" — ").ifEmpty { "---" },
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Dimensions.Space12))
            val done = project.status.uppercase().let {
                it.contains("DONE") || it.contains("FERTIG") || it.contains("COMPLET")
            }
            DqStatusChip(
                text = if (done) S("status_done") else S("status_open"),
                color = if (done) c.success else c.warning,
            )
        }
        Spacer(modifier = Modifier.height(Dimensions.Space8))
        val meta = listOf(
            project.durchmesser.takeIf { it.isNotBlank() }?.let { "DN $it" },
            project.material.takeIf { it.isNotBlank() },
            project.inspektionslaenge.takeIf { it.isNotBlank() }?.let { "$it m" },
            project.inspektionsdatum.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

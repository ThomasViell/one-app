package com.uip.oneapp.ui.screens.projects

import androidx.lifecycle.ViewModel
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

class ProjectsViewModel(repository: ProjectRepository) : ViewModel() {
    val projects: Flow<List<ProjectEntity>> = repository.getAllProjects()
}

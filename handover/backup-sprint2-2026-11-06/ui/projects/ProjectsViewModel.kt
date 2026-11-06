package com.icoordinator.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.domain.ProgressCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectSummary(val project: ProjectEntity, val progress: Int)

class ProjectsViewModel(private val repo: ProjectRepository) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>> =
        combine(repo.observeProjects(), repo.observeAllGoals()) { projects, goals ->
            val byProject = goals.groupBy { it.projectId }
            projects.map { p ->
                ProjectSummary(p, ProgressCalculator.progress(byProject[p.id] ?: emptyList()))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createProject(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.createProject(title) }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch { repo.deleteProject(project) }
    }
}

class ProjectDetailViewModel(
    private val repo: ProjectRepository,
    private val projectId: Long,
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> =
        repo.observeProject(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val goals: StateFlow<List<GoalEntity>> =
        repo.observeGoals(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress: StateFlow<Int> =
        goals.map { ProgressCalculator.progress(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val remaining: StateFlow<Int> =
        goals.map { ProgressCalculator.remaining(it.sumOf { g -> g.weightPercent }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    /** خطای آخرین عملیات افزودن هدف (برای نمایش به کاربر). */
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun addGoal(title: String, weight: Int) {
        if (title.isBlank()) {
            _error.value = "عنوان هدف را وارد کن"
            return
        }
        viewModelScope.launch { _error.value = repo.addGoal(projectId, title, weight) }
    }

    fun clearError() {
        _error.value = null
    }

    fun toggleGoal(goal: GoalEntity) {
        viewModelScope.launch { repo.toggleGoal(goal) }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch { repo.deleteGoal(goal) }
    }

    fun saveManifesto(text: String) {
        val current = project.value ?: return
        viewModelScope.launch { repo.updateManifesto(current, text) }
    }
}

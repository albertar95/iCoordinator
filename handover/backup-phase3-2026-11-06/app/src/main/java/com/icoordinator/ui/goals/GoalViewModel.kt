package com.icoordinator.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.TaskRepository
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.ProgressCalculator
import com.icoordinator.ui.components.TaskFormResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalViewModel(
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val goalId: Long,
) : ViewModel() {

    val goal: StateFlow<GoalEntity?> =
        projects.observeGoal(goalId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val goalTasks: StateFlow<List<TaskEntity>> =
        tasks.observeByGoal(goalId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** پیشرفت هدف: محاسبه‌شده از تسک‌های انجام‌شده (ذخیره نمی‌شود). */
    val progress: StateFlow<Int> =
        combine(goal, goalTasks) { g, list ->
            if (g == null) 0 else ProgressCalculator.goalProgress(g, list)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val projectList: StateFlow<List<ProjectEntity>> =
        projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /** onSuccess فقط بعد از ذخیره‌ی موفق صدا زده می‌شود. */
    fun save(title: String, weight: Int, onSuccess: () -> Unit = {}) {
        val current = goal.value ?: return
        viewModelScope.launch {
            val message = projects.updateGoal(current, title, weight)
            _error.value = message
            if (message == null) onSuccess()
        }
    }

    fun addTask(r: TaskFormResult) {
        viewModelScope.launch {
            tasks.addTasks(
                r.title,
                r.date,
                r.time,
                r.projectId,
                r.repeat,
                r.until,
                r.alarmOffset,
                r.goalId,
                r.weekDays,
            )
        }
    }

    fun setDone(task: TaskEntity, done: Boolean, contribution: Int?) {
        viewModelScope.launch { tasks.setDone(task, done, contribution) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { tasks.deleteTask(task) }
    }
}

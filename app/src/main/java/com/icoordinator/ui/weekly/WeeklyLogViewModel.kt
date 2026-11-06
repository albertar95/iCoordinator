package com.icoordinator.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.TaskRepository
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.ProgressCalculator
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.TaskFormResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeeklyLogViewModel(
    private val tasks: TaskRepository,
    projects: ProjectRepository,
) : ViewModel() {

    private val _weekStart = MutableStateFlow(WeekUtils.weekStartOf(LocalDate.now()))
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksOfWeek: StateFlow<List<TaskEntity>> = _weekStart
        .flatMapLatest { tasks.observeWeek(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projectList: StateFlow<List<ProjectEntity>> = projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = projects.observeAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** پیشرفت هر هدف (شناسه‌ی هدف به درصد)، محاسبه‌شده از تسک‌های انجام‌شده. */
    val goalProgress: StateFlow<Map<Long, Int>> =
        combine(projects.observeAllGoals(), tasks.observeWithGoal()) { goalList, goalTasks ->
            goalList.associate { it.id to ProgressCalculator.goalProgress(it, goalTasks) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun shiftWeek(weeks: Long) {
        _weekStart.value = _weekStart.value.plusWeeks(weeks)
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

    fun updateTask(task: TaskEntity, r: TaskFormResult) {
        viewModelScope.launch {
            tasks.updateTask(task, r.title, r.date, r.time, r.projectId, r.goalId, r.alarmOffset)
        }
    }

    fun setDone(task: TaskEntity, done: Boolean, contribution: Int?) {
        viewModelScope.launch { tasks.setDone(task, done, contribution) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { tasks.deleteTask(task) }
    }
}

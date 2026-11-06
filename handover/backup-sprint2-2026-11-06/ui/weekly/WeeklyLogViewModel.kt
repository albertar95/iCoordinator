package com.icoordinator.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.TaskRepository
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.RepeatType
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun shiftWeek(weeks: Long) {
        _weekStart.value = _weekStart.value.plusWeeks(weeks)
    }

    /** برمی‌گرداند: true اگر تسک (یا تکرارهایش) ثبت شد. */
    fun addTask(
        title: String,
        day: LocalDate,
        timeText: String,
        projectId: Long?,
        repeat: RepeatType,
        untilText: String,
        alarmOffset: Int?,
    ): Boolean {
        if (title.isBlank()) {
            _error.value = "عنوان تسک را وارد کن"
            return false
        }
        val time = WeekUtils.parseTime(timeText)
        if (time == null) {
            _error.value = "ساعت را به‌صورت HH:mm وارد کن، مثلاً 09:30"
            return false
        }
        val until: LocalDate? = if (repeat == RepeatType.NONE) {
            null
        } else {
            val parsed = PersianCalendar.parse(untilText)
            if (parsed == null || parsed.isBefore(day)) {
                _error.value = "تاریخ پایان تکرار را به شکل ۱۴۰۵/۰۸/۱۰ و بعد از تاریخ شروع وارد کن"
                return false
            }
            parsed
        }
        _error.value = null
        viewModelScope.launch {
            tasks.addTasks(title, day, time, projectId, repeat, until, alarmOffset)
        }
        return true
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch { tasks.toggleTask(task) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { tasks.deleteTask(task) }
    }

    fun clearError() {
        _error.value = null
    }
}

package com.icoordinator.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.RoutineRepository
import com.icoordinator.data.TaskRepository
import com.icoordinator.data.entity.RoutineCompletionEntity
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.WeekUtils
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
import java.time.YearMonth

/** یک آیتم در تقویم؛ یا تسک است یا نمونه‌ای از یک روتین در یک روز. */
data class DayEntry(
    val key: String,
    val title: String,
    val timeText: String,
    val isDone: Boolean,
    val date: LocalDate,
    val task: TaskEntity? = null,
    val routineId: Long? = null,
)

class CalendarViewModel(
    private val tasks: TaskRepository,
    private val routines: RoutineRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _selected = MutableStateFlow(LocalDate.now())
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    /** همه‌ی آیتم‌های ماه (تسک‌ها + روتین‌ها) گروه‌بندی‌شده بر اساس روز */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesByDay: StateFlow<Map<LocalDate, List<DayEntry>>> = _month
        .flatMapLatest { m ->
            val from = m.atDay(1)
            val to = m.plusMonths(1).atDay(1)
            combine(
                tasks.observeRange(from, to),
                routines.observeRoutines(),
                routines.observeCompletions(from, to),
            ) { taskList, routineList, completions ->
                buildEntries(from, to, taskList, routineList, completions)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun shiftMonth(months: Long) {
        _month.value = _month.value.plusMonths(months)
    }

    fun select(date: LocalDate) {
        _selected.value = date
        if (YearMonth.from(date) != _month.value) _month.value = YearMonth.from(date)
    }

    fun toggle(entry: DayEntry) {
        viewModelScope.launch {
            val task = entry.task
            val routineId = entry.routineId
            when {
                task != null -> tasks.toggleTask(task)
                routineId != null -> routines.toggleCompletion(routineId, entry.date, entry.isDone)
            }
        }
    }

    private fun buildEntries(
        from: LocalDate,
        to: LocalDate,
        taskList: List<TaskEntity>,
        routineList: List<RoutineEntity>,
        completions: List<RoutineCompletionEntity>,
    ): Map<LocalDate, List<DayEntry>> {
        val result = mutableMapOf<LocalDate, MutableList<DayEntry>>()
        fun add(date: LocalDate, entry: DayEntry) {
            result.getOrPut(date) { mutableListOf() }.add(entry)
        }

        taskList.forEach { t ->
            val due = WeekUtils.toLocalDateTime(t.dueAt)
            add(
                due.toLocalDate(),
                DayEntry(
                    key = "t${t.id}",
                    title = t.title,
                    timeText = due.format(WeekUtils.TIME_FMT),
                    isDone = t.isDone,
                    date = due.toLocalDate(),
                    task = t,
                ),
            )
        }

        val doneSet = completions.map { it.routineId to it.date }.toSet()
        var day = from
        while (day < to) {
            val bit = 1 shl (day.dayOfWeek.value - 1)
            routineList.filter { it.daysMask and bit != 0 }.forEach { r ->
                add(
                    day,
                    DayEntry(
                        key = "r${r.id}-${day.toEpochDay()}",
                        title = r.title,
                        timeText = r.timeOfDay,
                        isDone = (r.id to day.toEpochDay()) in doneSet,
                        date = day,
                        routineId = r.id,
                    ),
                )
            }
            day = day.plusDays(1)
        }

        return result.mapValues { (_, list) -> list.sortedBy { it.timeText } }
    }
}

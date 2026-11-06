package com.icoordinator.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.RoutineRepository
import com.icoordinator.data.TaskRepository
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.RoutineCompletionEntity
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.ProgressCalculator
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** نمای تقویم: ماهانه، هفتگی یا روزانه */
enum class CalendarView { MONTH, WEEK, DAY }

/** ماه شمسی */
data class JMonth(val year: Int, val month: Int)

/** یک آیتم در تقویم؛ یا تسک است یا نمونه‌ای از روتین در یک روز. */
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
    projects: ProjectRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(
        PersianCalendar.toJalali(LocalDate.now()).let { JMonth(it.year, it.month) },
    )
    val month: StateFlow<JMonth> = _month.asStateFlow()

    private val _selected = MutableStateFlow(LocalDate.now())
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    private val _view = MutableStateFlow(CalendarView.MONTH)
    val view: StateFlow<CalendarView> = _view.asStateFlow()

    val goals: StateFlow<List<GoalEntity>> = projects.observeAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** پیشرفت هر هدف (شناسه‌ی هدف به درصد)، محاسبه‌شده از تسک‌های انجام‌شده. */
    val goalProgress: StateFlow<Map<Long, Int>> =
        combine(projects.observeAllGoals(), tasks.observeWithGoal()) { goalList, goalTasks ->
            goalList.associate { it.id to ProgressCalculator.goalProgress(it, goalTasks) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * آیتم‌های بازه‌ی دیده‌شده، گروه‌بندی‌شده بر اساس روز میلادی.
     * بازه از زودترین شروعِ (ماه شمسی، هفته‌ی روز انتخاب‌شده) تا دیرترین پایان آن‌ها است،
     * تا هفته‌ای که دو ماه شمسی را پوشش می‌دهد هم کامل بارگذاری شود.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesByDay: StateFlow<Map<LocalDate, List<DayEntry>>> =
        combine(_month, _selected) { m, sel ->
            val monthFrom = PersianCalendar.monthStart(m.year, m.month)
            val monthTo = if (m.month == 12) PersianCalendar.monthStart(m.year + 1, 1)
            else PersianCalendar.monthStart(m.year, m.month + 1)
            val weekFrom = WeekUtils.weekStartOf(sel)
            val weekTo = weekFrom.plusDays(7)
            val from = if (monthFrom.isBefore(weekFrom)) monthFrom else weekFrom
            val to = if (monthTo.isAfter(weekTo)) monthTo else weekTo
            Pair(from, to)
        }
            .distinctUntilChanged()
            .flatMapLatest { (from, to) ->
                combine(
                    tasks.observeRange(from, to),
                    routines.observeRoutines(),
                    routines.observeCompletions(from, to),
                ) { taskList, routineList, completions ->
                    buildEntries(from, to, taskList, routineList, completions)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setView(v: CalendarView) {
        _view.value = v
    }

    /** هفته‌ی قبل یا بعد: روز انتخاب‌شده را ۷ روز جابه‌جا می‌کند و ماه را با آن هماهنگ نگه می‌دارد. */
    fun shiftWeek(delta: Int) {
        select(_selected.value.plusWeeks(delta.toLong()))
    }

    /** روز قبل یا بعد: روز انتخاب‌شده را جابه‌جا می‌کند و اگر از ماه خارج شد ماه هم عوض می‌شود. */
    fun shiftDay(delta: Int) {
        select(_selected.value.plusDays(delta.toLong()))
    }

    fun shiftMonth(delta: Int) {
        var y = _month.value.year
        var m = _month.value.month + delta
        while (m > 12) { m -= 12; y++ }
        while (m < 1) { m += 12; y-- }
        _month.value = JMonth(y, m)
    }

    fun select(date: LocalDate) {
        _selected.value = date
        val j = PersianCalendar.toJalali(date)
        _month.value = JMonth(j.year, j.month)
    }

    /** انجام یا برگشت یک نمونه از روتین (تسک‌ها با setDone). */
    fun toggleRoutine(entry: DayEntry) {
        val routineId = entry.routineId ?: return
        viewModelScope.launch { routines.toggleCompletion(routineId, entry.date, entry.isDone) }
    }

    fun setDone(task: TaskEntity, done: Boolean, contribution: Int?) {
        viewModelScope.launch { tasks.setDone(task, done, contribution) }
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
            val date = due.toLocalDate()
            add(
                date,
                DayEntry(
                    key = "t${t.id}",
                    title = t.title,
                    timeText = due.format(WeekUtils.TIME_FMT),
                    isDone = t.isDone,
                    date = date,
                    task = t,
                ),
            )
        }

        val doneSet = completions.map { it.routineId to it.date }.toSet()
        var day = from
        while (day < to) {
            val bit = 1 shl (day.dayOfWeek.value - 1)
            routineList.forEach { r ->
                val start = LocalDate.ofEpochDay(r.startDay)
                val end = r.endDay?.let { LocalDate.ofEpochDay(it) }
                val inRange = !day.isBefore(start) && (end == null || !day.isAfter(end))
                if (inRange && (r.daysMask and bit) != 0) {
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
            }
            day = day.plusDays(1)
        }

        return result.mapValues { (_, list) -> list.sortedBy { it.timeText } }
    }
}

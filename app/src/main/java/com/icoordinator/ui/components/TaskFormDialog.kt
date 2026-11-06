package com.icoordinator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.ALARM_OPTIONS
import com.icoordinator.domain.AlarmOption
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.RepeatType
import com.icoordinator.domain.WeekUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class TaskFormResult(
    val title: String,
    val repeat: RepeatType,
    val date: LocalDate,
    val weekDays: Set<DayOfWeek>,
    val until: LocalDate?,
    val time: LocalTime,
    val alarmOffset: Int?,
    val projectId: Long?,
    val goalId: Long?,
)

private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

/**
 * Task form for creating (initial == null) or editing one task row.
 * Repeat table: NONE -> single day, no end date; DAILY -> no day chips, end date;
 * WEEKLY -> several days, end date.
 */
@Composable
fun TaskFormDialog(
    weekStart: LocalDate,
    projects: List<ProjectEntity>,
    goals: List<GoalEntity>,
    onDismiss: () -> Unit,
    onSave: (TaskFormResult) -> Unit,
    initial: TaskEntity? = null,
    fixedGoal: GoalEntity? = null,
) {
    val editing = initial != null
    val initialDateTime = remember(initial) { initial?.let { WeekUtils.toLocalDateTime(it.dueAt) } }
    val shownWeekStart = remember(initial, weekStart) {
        if (initialDateTime != null) WeekUtils.weekStartOf(initialDateTime.toLocalDate()) else weekStart
    }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var repeat by remember { mutableStateOf(RepeatType.NONE) }
    var singleDay by remember {
        mutableStateOf<LocalDate?>(
            initialDateTime?.toLocalDate()
                ?: LocalDate.now().takeIf { !it.isBefore(shownWeekStart) && !it.isAfter(shownWeekStart.plusDays(6)) },
        )
    }
    var weekDaysChosen by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }
    var until by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(initialDateTime?.toLocalTime() ?: DEFAULT_TIME) }
    var alarm: AlarmOption by remember {
        mutableStateOf(ALARM_OPTIONS.firstOrNull { it.offsetMinutes == initial?.alarmOffsetMinutes } ?: ALARM_OPTIONS[0])
    }
    var projectId by remember { mutableStateOf<Long?>(fixedGoal?.projectId ?: initial?.projectId) }
    var goalId by remember { mutableStateOf<Long?>(fixedGoal?.id ?: initial?.goalId) }
    var error by remember { mutableStateOf<String?>(null) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val todayInWeek = !today.isBefore(shownWeekStart) && !today.isAfter(shownWeekStart.plusDays(6))
    val startOfRange = if (todayInWeek) today else shownWeekStart

    val daysEnabled = repeat != RepeatType.DAILY
    val untilEnabled = repeat != RepeatType.NONE

    fun validate(): TaskFormResult? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            error = "عنوان تسک را وارد کن"
            return null
        }
        val chosenTime = time
        val date: LocalDate = when (repeat) {
            RepeatType.NONE -> {
                val d = singleDay
                if (d == null) {
                    error = "یک روز انتخاب کن"
                    return null
                }
                d
            }
            RepeatType.WEEKLY -> {
                if (weekDaysChosen.isEmpty()) {
                    error = "حداقل یک روز انتخاب کن"
                    return null
                }
                (0L..6L).map { startOfRange.plusDays(it) }.first { it.dayOfWeek in weekDaysChosen }
            }
            RepeatType.DAILY -> startOfRange
        }
        var untilDate: LocalDate? = null
        if (repeat != RepeatType.NONE) {
            val u = until
            if (u == null) {
                error = "تاریخ پایان تکرار را انتخاب کن"
                return null
            }
            if (u.isBefore(date)) {
                error = "تاریخ پایان نباید قبل از شروع تکرار باشد"
                return null
            }
            untilDate = u
        }
        if (chosenTime == null) {
            error = "ساعت را انتخاب کن"
            return null
        }
        return TaskFormResult(
            title = cleanTitle,
            repeat = repeat,
            date = date,
            weekDays = if (repeat == RepeatType.WEEKLY) weekDaysChosen else emptySet(),
            until = untilDate,
            time = chosenTime,
            alarmOffset = alarm.offsetMinutes,
            projectId = fixedGoal?.projectId ?: projectId,
            goalId = fixedGoal?.id ?: goalId,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (editing) "ویرایش تسک" else "تسک جدید") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = "عنوان تسک",
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!editing) {
                    Text("تکرار", style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RepeatType.entries.toList()) { type ->
                            FilterChip(
                                selected = repeat == type,
                                onClick = {
                                    repeat = type
                                    error = null
                                },
                                label = { Text(type.label) },
                            )
                        }
                    }
                }

                Text("روز", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WeekUtils.weekDays(shownWeekStart)) { d ->
                        val selected = when (repeat) {
                            RepeatType.NONE -> d == singleDay
                            RepeatType.WEEKLY -> d.dayOfWeek in weekDaysChosen
                            RepeatType.DAILY -> false
                        }
                        FilterChip(
                            selected = selected,
                            enabled = daysEnabled,
                            onClick = {
                                if (repeat == RepeatType.NONE) {
                                    singleDay = d
                                } else if (repeat == RepeatType.WEEKLY) {
                                    weekDaysChosen = if (d.dayOfWeek in weekDaysChosen) {
                                        weekDaysChosen - d.dayOfWeek
                                    } else {
                                        weekDaysChosen + d.dayOfWeek
                                    }
                                }
                                error = null
                            },
                            label = { Text("${WeekUtils.persianDayName(d.dayOfWeek)} ${PersianCalendar.toJalali(d).day}") },
                        )
                    }
                }

                DateField(
                    label = "پایان تکرار",
                    date = if (untilEnabled) until else null,
                    onClick = { showUntilPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = untilEnabled,
                )

                TimeField(
                    label = "ساعت",
                    time = time,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("آلارم", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ALARM_OPTIONS) { option ->
                        FilterChip(
                            selected = alarm == option,
                            onClick = {
                                alarm = option
                                error = null
                            },
                            label = { Text(option.label) },
                        )
                    }
                }

                if (fixedGoal == null) {
                    Text("پروژه (اختیاری)", style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = projectId == null,
                                onClick = {
                                    projectId = null
                                    goalId = null
                                    error = null
                                },
                                label = { Text("بدون پروژه") },
                            )
                        }
                        items(projects) { p ->
                            FilterChip(
                                selected = projectId == p.id,
                                onClick = {
                                    if (projectId != p.id) goalId = null
                                    projectId = p.id
                                    error = null
                                },
                                label = { Text(p.title) },
                            )
                        }
                    }

                    val chosenProject = projectId
                    if (chosenProject != null) {
                        val projectGoals = goals.filter { it.projectId == chosenProject }
                        Text("هدف (اختیاری)", style = MaterialTheme.typography.bodySmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = goalId == null,
                                    onClick = {
                                        goalId = null
                                        error = null
                                    },
                                    label = { Text("بدون هدف") },
                                )
                            }
                            items(projectGoals) { g ->
                                FilterChip(
                                    selected = goalId == g.id,
                                    onClick = {
                                        goalId = g.id
                                        error = null
                                    },
                                    label = { Text(g.title) },
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            PillButton(
                text = "ذخیره",
                onClick = {
                    val result = validate()
                    if (result != null) onSave(result)
                },
                modifier = Modifier.height(52.dp),
            )
        },
        dismissButton = {
            PillButton(
                text = "انصراف",
                onClick = onDismiss,
                modifier = Modifier.height(52.dp),
                outlined = true,
            )
        },
    )

    if (showUntilPicker) {
        ShamsiDatePickerDialog(
            initial = until ?: startOfRange,
            onDismiss = { showUntilPicker = false },
            onPick = {
                until = it
                error = null
                showUntilPicker = false
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialog24(
            initial = time ?: DEFAULT_TIME,
            onDismiss = { showTimePicker = false },
            onPick = {
                time = it
                error = null
                showTimePicker = false
            },
        )
    }
}

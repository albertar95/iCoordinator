package com.icoordinator.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.CheckCircle
import com.icoordinator.ui.components.ContributionDialog
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TbIcon
import java.time.LocalDate

private val WEEKDAY_HEADERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

@Composable
fun CalendarScreen() {
    val vm: CalendarViewModel = viewModel(key = "calendar") {
        val app = ICoordinatorApp.instance
        CalendarViewModel(app.taskRepository, app.routineRepository, app.projectRepository)
    }
    val month by vm.month.collectAsStateWithLifecycle()
    val byDay by vm.entriesByDay.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val goalProgress by vm.goalProgress.collectAsStateWithLifecycle()
    var taskToTick by remember { mutableStateOf<TaskEntity?>(null) }
    val scheme = MaterialTheme.colorScheme

    val goalsById = goals.associateBy { it.id }
    val firstDay = PersianCalendar.monthStart(month.year, month.month)
    val length = PersianCalendar.monthLength(month.year, month.month)
    val cells = monthCells(firstDay, length)
    val selectedEntries = byDay[selected].orEmpty()
    val weekDays = WeekUtils.weekDays(WeekUtils.weekStartOf(selected))
    val today = LocalDate.now()

    // TICK RULE for tasks; routine entries keep toggleCompletion
    val onToggleEntry: (DayEntry) -> Unit = { entry ->
        val task = entry.task
        if (task == null) {
            vm.toggleRoutine(entry)
        } else if (task.isDone) {
            vm.setDone(task, false, null)
        } else if (task.goalId?.let { goalsById[it] } != null) {
            taskToTick = task
        } else {
            vm.setDone(task, true, null)
        }
    }

    val headerTitle: String
    val prevDescription: String
    val nextDescription: String
    val onPrev: () -> Unit
    val onNext: () -> Unit
    when (view) {
        CalendarView.MONTH -> {
            headerTitle = "${PersianCalendar.monthName(month.month)} ${month.year}"
            prevDescription = "ماه قبل"
            nextDescription = "ماه بعد"
            onPrev = { vm.shiftMonth(-1) }
            onNext = { vm.shiftMonth(1) }
        }
        CalendarView.WEEK -> {
            headerTitle = weekRangeText(weekDays.first(), weekDays.last())
            prevDescription = "هفته قبل"
            nextDescription = "هفته بعد"
            onPrev = { vm.shiftWeek(-1) }
            onNext = { vm.shiftWeek(1) }
        }
        CalendarView.DAY -> {
            val j = PersianCalendar.toJalali(selected)
            headerTitle = "${WeekUtils.persianDayName(selected.dayOfWeek)} ${j.day} ${PersianCalendar.monthName(j.month)} ${j.year}"
            prevDescription = "روز قبل"
            nextDescription = "روز بعد"
            onPrev = { vm.shiftDay(-1) }
            onNext = { vm.shiftDay(1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        PageHeader(title = "تقویم")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "view-switch") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ViewChip("ماهانه", view == CalendarView.MONTH, { vm.setView(CalendarView.MONTH) }, Modifier.weight(1f))
                    ViewChip("هفتگی", view == CalendarView.WEEK, { vm.setView(CalendarView.WEEK) }, Modifier.weight(1f))
                    ViewChip("روزانه", view == CalendarView.DAY, { vm.setView(CalendarView.DAY) }, Modifier.weight(1f))
                }
            }

            item(key = "header-card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onPrev) {
                                TbIcon(R.drawable.ic_tb_chevron_left, prevDescription, tint = scheme.onSurface)
                            }
                            Text(
                                text = headerTitle,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            IconButton(onClick = onNext) {
                                TbIcon(R.drawable.ic_tb_chevron_right, nextDescription, tint = scheme.onSurface)
                            }
                        }
                        if (view == CalendarView.MONTH) {
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                WEEKDAY_HEADERS.forEach { h ->
                                    Text(
                                        text = h,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            cells.chunked(7).forEach { week ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    week.forEach { date ->
                                        DayCell(
                                            date = date,
                                            hasItems = date != null && byDay[date].orEmpty().isNotEmpty(),
                                            isSelected = date == selected,
                                            isToday = date == today,
                                            onClick = { date?.let { vm.select(it) } },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }

            if (view == CalendarView.WEEK) {
                weekDays.forEach { day ->
                    val dayEntries = byDay[day].orEmpty()
                    item(key = "day-${day.toEpochDay()}") {
                        SectionLabel(
                            "${WeekUtils.persianDayName(day.dayOfWeek)} ${WeekUtils.dateText(day)}" +
                                if (day == today) " (امروز)" else "",
                        )
                    }
                    if (dayEntries.isEmpty()) {
                        item(key = "empty-${day.toEpochDay()}") {
                            Text(
                                text = "چیزی برای این روز نیست",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    items(dayEntries, key = { it.key }) { entry ->
                        EntryRow(entry = entry, onToggle = { onToggleEntry(entry) })
                    }
                }
            } else {
                item(key = "selected-label") {
                    SectionLabel(
                        "برنامه‌ی ${WeekUtils.dateText(selected)} (${WeekUtils.persianDayName(selected.dayOfWeek)})",
                    )
                }

                if (selectedEntries.isEmpty()) {
                    item(key = "selected-empty") {
                        EmptyState(
                            icon = R.drawable.ic_tb_calendar,
                            title = "چیزی برای این روز نیست",
                            message = "از «تسک‌ها» یا «روتین‌ها» برای این روز برنامه اضافه کن",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                items(selectedEntries, key = { it.key }) { entry ->
                    EntryRow(entry = entry, onToggle = { onToggleEntry(entry) })
                }
            }
        }
    }

    val pendingTick = taskToTick
    if (pendingTick != null) {
        val tickGoal = pendingTick.goalId?.let { goalsById[it] }
        if (tickGoal != null) {
            ContributionDialog(
                goalTitle = tickGoal.title,
                currentProgress = goalProgress[tickGoal.id] ?: 0,
                onDismiss = { taskToTick = null },
                onConfirm = { value ->
                    vm.setDone(pendingTick, true, value)
                    taskToTick = null
                },
            )
        }
    }
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        modifier = modifier,
    )
}

/** مثل «۱۰ تا ۱۶ آبان» یا، وقتی هفته دو ماه را می‌پوشاند، «۲۵ مهر تا ۱ آبان». */
private fun weekRangeText(start: LocalDate, end: LocalDate): String {
    val js = PersianCalendar.toJalali(start)
    val je = PersianCalendar.toJalali(end)
    return if (js.year == je.year && js.month == je.month) {
        "${js.day} تا ${je.day} ${PersianCalendar.monthName(je.month)}"
    } else {
        "${js.day} ${PersianCalendar.monthName(js.month)} تا ${je.day} ${PersianCalendar.monthName(je.month)}"
    }
}

@Composable
private fun EntryRow(entry: DayEntry, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    PillRow(
        modifier = Modifier.fillMaxWidth(),
        leading = {
            CheckCircle(checked = entry.isDone, onCheckedChange = { onToggle() })
        },
        trailing = {
            Box(modifier = Modifier.padding(end = 12.dp)) {
                TbIcon(
                    icon = if (entry.routineId != null) R.drawable.ic_tb_repeat else R.drawable.ic_tb_checklist,
                    contentDescription = null,
                    size = 24.dp,
                    tint = scheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (entry.isDone) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (entry.isDone) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
        )
        Text(
            text = (if (entry.routineId != null) "روتین" else "تسک") + " - ${entry.timeText}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** خانه‌های تقویم: خانه‌های خالی تا شنبه، سپس روزهای ماه. */
private fun monthCells(firstDay: LocalDate, length: Int): List<LocalDate?> {
    // شنبه = ۰ ... جمعه = ۶
    val leading = (firstDay.dayOfWeek.value - 6 + 7) % 7
    return List<LocalDate?>(leading) { null } + (0 until length).map { firstDay.plusDays(it.toLong()) }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    hasItems: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isSelected) scheme.primary else Color.Transparent
    val textColor = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        else -> scheme.onSurface
    }
    val borderModifier = if (isToday && !isSelected) {
        Modifier.border(2.dp, scheme.primary, shape)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(shape)
            .background(bg)
            .then(borderModifier)
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = PersianCalendar.toJalali(date).day.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                )
                if (hasItems) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                if (isSelected) scheme.onPrimary else scheme.primary,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

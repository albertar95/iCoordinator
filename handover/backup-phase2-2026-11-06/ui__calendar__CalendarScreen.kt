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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.CheckCircle
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
        CalendarViewModel(app.taskRepository, app.routineRepository)
    }
    val month by vm.month.collectAsStateWithLifecycle()
    val byDay by vm.entriesByDay.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    val firstDay = PersianCalendar.monthStart(month.year, month.month)
    val length = PersianCalendar.monthLength(month.year, month.month)
    val cells = monthCells(firstDay, length)
    val selectedEntries = byDay[selected].orEmpty()
    val today = LocalDate.now()

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
            item {
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
                            IconButton(onClick = { vm.shiftMonth(-1) }) {
                                TbIcon(R.drawable.ic_tb_chevron_left, "ماه قبل", tint = scheme.onSurface)
                            }
                            Text(
                                text = "${PersianCalendar.monthName(month.month)} ${month.year}",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            IconButton(onClick = { vm.shiftMonth(1) }) {
                                TbIcon(R.drawable.ic_tb_chevron_right, "ماه بعد", tint = scheme.onSurface)
                            }
                        }
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

            item {
                SectionLabel(
                    "برنامه‌ی ${WeekUtils.dateText(selected)} (${WeekUtils.persianDayName(selected.dayOfWeek)})",
                )
            }

            if (selectedEntries.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_tb_calendar,
                        title = "چیزی برای این روز نیست",
                        message = "از «تسک‌ها» یا «روتین‌ها» برای این روز برنامه اضافه کن",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(selectedEntries, key = { it.key }) { entry ->
                PillRow(
                    modifier = Modifier.fillMaxWidth(),
                    leading = {
                        CheckCircle(checked = entry.isDone, onCheckedChange = { vm.toggle(entry) })
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
        }
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

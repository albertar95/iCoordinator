package com.icoordinator.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.*
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

    val firstDay = PersianCalendar.monthStart(month.year, month.month)
    val length = PersianCalendar.monthLength(month.year, month.month)
    val cells = monthCells(firstDay, length)
    val selectedEntries = byDay[selected].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { vm.shiftMonth(-1) }) { Text("ماه قبل") }
                    Text(
                        "${PersianCalendar.monthName(month.month)} ${month.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { vm.shiftMonth(1) }) { Text("ماه بعد") }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { h ->
                    Text(
                        h,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(cells.chunked(7)) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        hasItems = date != null && byDay[date].orEmpty().isNotEmpty(),
                        isSelected = date == selected,
                        onClick = { date?.let { vm.select(it) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        item {
            SectionTitle(
                "برنامه‌ی ${WeekUtils.dateText(selected)} (${WeekUtils.persianDayName(selected.dayOfWeek)})",
            )
        }

        if (selectedEntries.isEmpty()) {
            item {
                EmptyState(
                    title = "چیزی برای این روز نیست",
                    message = "از «لاگ هفتگی» یا «روتین‌ها» برای این روز برنامه اضافه کن",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(selectedEntries, key = { it.key }) { entry ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = entry.isDone, onCheckedChange = { vm.toggle(entry) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (entry.isDone) TextDecoration.LineThrough else null,
                        )
                        Text(
                            (if (entry.routineId != null) "روتین" else "تسک") + " - ${entry.timeText}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    PersianCalendar.toJalali(date).day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                if (hasItems) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

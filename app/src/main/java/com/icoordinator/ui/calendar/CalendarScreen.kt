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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.domain.WeekUtils
import java.time.LocalDate
import java.time.YearMonth

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

    val cells = monthCells(month)
    val selectedEntries = byDay[selected].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            MonthHeader(month = month, onPrev = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) })
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { h ->
                    Text(
                        h,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
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

        item { HorizontalDivider() }

        item {
            Text(
                "برنامه‌ی ${selected.format(WeekUtils.DATE_FMT)} (${WeekUtils.persianDayName(selected.dayOfWeek)})",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        if (selectedEntries.isEmpty()) {
            item { Text("چیزی برای این روز نیست") }
        }

        items(selectedEntries, key = { it.key }) { entry ->
            EntryRow(entry = entry, onToggle = { vm.toggle(entry) })
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev) { Text("ماه قبل") }
        Text(
            "${month.year}/${"%02d".format(month.monthValue)}",
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onNext) { Text("ماه بعد") }
    }
}

/** خانه‌های تقویم: اول خانه‌های خالی تا شنبه، بعد روزهای ماه. */
private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    // شنبه = ۰، جمعه = ۶
    val leading = (first.dayOfWeek.value - 6 + 7) % 7
    return List<LocalDate?>(leading) { null } +
        (1..month.lengthOfMonth()).map { month.atDay(it) }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    hasItems: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                if (hasItems) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DayEntry, onToggle: () -> Unit) {
    val label = if (entry.routineId != null) "روتین" else "تسک"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (entry.isDone) TextDecoration.LineThrough else null,
            )
            Text("$label - ${entry.timeText}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

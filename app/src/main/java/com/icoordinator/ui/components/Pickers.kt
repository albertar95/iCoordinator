package com.icoordinator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.WeekUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val WEEK_ORDER = listOf(
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private const val NOT_SELECTED_TEXT = "انتخاب نشده"

/** Shamsi month grid (week starts on Saturday). Tap a day to select it, then confirm. */
@Composable
fun ShamsiDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val initialJalali = remember(initial) { PersianCalendar.toJalali(initial) }
    var year by remember(initial) { mutableIntStateOf(initialJalali.year) }
    var month by remember(initial) { mutableIntStateOf(initialJalali.month) }
    var selected by remember(initial) { mutableStateOf(initial) }
    val today = remember { LocalDate.now() }

    val monthFirst = remember(year, month) { PersianCalendar.monthStart(year, month) }
    val monthDays = remember(year, month) { PersianCalendar.monthLength(year, month) }
    // Saturday = column 0 ... Friday = column 6
    val offset = (monthFirst.dayOfWeek.value + 1) % 7
    val rowCount = (offset + monthDays + 6) / 7

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = scheme.surface,
        title = { Text("انتخاب تاریخ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (month == 1) {
                                month = 12
                                year -= 1
                            } else {
                                month -= 1
                            }
                        },
                    ) { Text("ماه قبل") }
                    Text(
                        text = "${PersianCalendar.monthName(month)} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (month == 12) {
                                month = 1
                                year += 1
                            } else {
                                month += 1
                            }
                        },
                    ) { Text("ماه بعد") }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    WEEK_ORDER.forEach { d ->
                        Text(
                            text = WeekUtils.persianDayName(d).take(1),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                for (r in 0 until rowCount) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val dayNumber = r * 7 + c - offset + 1
                            if (dayNumber in 1..monthDays) {
                                val date = monthFirst.plusDays((dayNumber - 1).toLong())
                                val isSelected = date == selected
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) scheme.primary else scheme.surface)
                                            .border(
                                                width = if (isToday) 1.5.dp else 0.dp,
                                                color = if (isToday) scheme.primary else scheme.surface,
                                                shape = CircleShape,
                                            )
                                            .clickable(role = Role.Button) { selected = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) scheme.onPrimary else scheme.onSurface,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PillButton(
                text = "تأیید",
                onClick = { onPick(selected) },
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
}

/** 24-hour Material3 time picker in a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog24(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("انتخاب ساعت") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            PillButton(
                text = "تأیید",
                onClick = { onPick(LocalTime.of(state.hour, state.minute)) },
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
}

/** Read-only clickable date field in the AppTextField style. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PickerField(
        label = label,
        valueText = if (date == null) NOT_SELECTED_TEXT else PersianCalendar.format(date),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

/** Read-only clickable time field in the AppTextField style. */
@Composable
fun TimeField(
    label: String,
    time: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PickerField(
        label = label,
        valueText = if (time == null) NOT_SELECTED_TEXT else time.format(WeekUtils.TIME_FMT),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun PickerField(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = scheme.outlineVariant,
                focusedBorderColor = scheme.primary,
                errorBorderColor = scheme.error,
            ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        )
    }
}

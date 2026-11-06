package com.icoordinator.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.TagText
import java.time.DayOfWeek
import java.time.LocalDate

/** روزهای هفته به ترتیب شنبه تا جمعه */
private val WEEK_ORDER = listOf(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

@Composable
fun RoutinesScreen() {
    val vm: RoutinesViewModel = viewModel(key = "routines") {
        RoutinesViewModel(ICoordinatorApp.instance.routineRepository)
    }
    val routines by vm.routines.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<RoutineEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (routines.isEmpty()) {
            PageHeader(title = "روتین‌ها")
            EmptyState(
                icon = R.drawable.ic_tb_repeat,
                title = "هنوز روتینی نداری",
                message = "با دکمه‌ی «روتین جدید» یک عادت تکرارشونده بساز",
                action = {
                    PillButton(
                        text = "روتین جدید",
                        onClick = { showAdd = true },
                        icon = R.drawable.ic_tb_plus,
                    )
                },
            )
        } else {
            PageHeader(
                title = "روتین‌ها",
                actionIcon = R.drawable.ic_tb_plus,
                actionDescription = "روتین جدید",
                onAction = { showAdd = true },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(routines, key = { it.id }) { routine ->
                    PillRow(
                        modifier = Modifier.fillMaxWidth(),
                        leading = { IconCircle(icon = R.drawable.ic_tb_repeat) },
                        trailing = { DeleteIconButton(onClick = { routineToDelete = routine }) },
                    ) {
                        Text(
                            text = routine.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "${RoutineUtils.daysLabel(routine.daysMask)} - ${routine.timeOfDay}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        TagText(
                            text = "از ${WeekUtils.dateText(LocalDate.ofEpochDay(routine.startDay))}" +
                                (routine.endDay?.let { " تا ${WeekUtils.dateText(LocalDate.ofEpochDay(it))}" }
                                    ?: " (بدون پایان)"),
                            icon = R.drawable.ic_tb_calendar_event,
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRoutineDialog(
            errorText = error,
            onEdit = { vm.clearError() },
            onDismiss = {
                vm.clearError()
                showAdd = false
            },
            onSave = { title, days, time, start, end ->
                if (vm.addRoutine(title, days, time, start, end)) showAdd = false
            },
        )
    }

    val pendingDelete = routineToDelete
    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            itemName = pendingDelete.title,
            onConfirm = {
                vm.deleteRoutine(pendingDelete)
                routineToDelete = null
            },
            onDismiss = { routineToDelete = null },
        )
    }
}

@Composable
private fun AddRoutineDialog(
    errorText: String?,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Set<DayOfWeek>, String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var time by remember { mutableStateOf("07:00") }
    var startText by remember { mutableStateOf(PersianCalendar.format(LocalDate.now())) }
    var endText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("روتین جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onEdit()
                    },
                    label = "عنوان روتین",
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("روزها", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WEEK_ORDER) { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = {
                                days = if (day in days) days - day else days + day
                                onEdit()
                            },
                            label = { Text(WeekUtils.persianDayName(day)) },
                        )
                    }
                }

                AppTextField(
                    value = time,
                    onValueChange = {
                        time = it
                        onEdit()
                    },
                    label = "ساعت (HH:mm)",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = startText,
                    onValueChange = {
                        startText = it
                        onEdit()
                    },
                    label = "تاریخ شروع (مثل ۱۴۰۵/۰۸/۱۰)",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = endText,
                    onValueChange = {
                        endText = it
                        onEdit()
                    },
                    label = "تاریخ پایان (خالی = بدون پایان)",
                    modifier = Modifier.fillMaxWidth(),
                )

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            PillButton(
                text = "ذخیره",
                onClick = { onSave(title, days, time, startText, endText) },
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

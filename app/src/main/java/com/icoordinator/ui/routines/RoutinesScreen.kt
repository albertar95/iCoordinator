package com.icoordinator.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.icoordinator.domain.ALARM_OPTIONS
import com.icoordinator.domain.AlarmOption
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DateField
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ShamsiDatePickerDialog
import com.icoordinator.ui.components.TagText
import com.icoordinator.ui.components.TimeField
import com.icoordinator.ui.components.TimePickerDialog24
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

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
    var routineToEdit by remember { mutableStateOf<RoutineEntity?>(null) }
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
                        onClick = {
                            vm.clearError()
                            routineToEdit = routine
                        },
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

    val editing = routineToEdit
    if (showAdd || editing != null) {
        RoutineFormDialog(
            initial = editing,
            errorText = error,
            onEdit = { vm.clearError() },
            onDismiss = {
                vm.clearError()
                showAdd = false
                routineToEdit = null
            },
            onSave = { title, days, time, start, end, alarmOffset ->
                val saved = if (editing == null) {
                    vm.addRoutine(title, days, time, start, end, alarmOffset)
                } else {
                    vm.updateRoutine(editing, title, days, time, start, end, alarmOffset)
                }
                if (saved) {
                    showAdd = false
                    routineToEdit = null
                }
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
private fun RoutineFormDialog(
    initial: RoutineEntity?,
    errorText: String?,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Set<DayOfWeek>, LocalTime, LocalDate, LocalDate?, Int?) -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var days by remember(initial?.id) {
        mutableStateOf(initial?.let { RoutineUtils.daysOf(it.daysMask).toSet() } ?: setOf<DayOfWeek>())
    }
    var time by remember(initial?.id) {
        mutableStateOf(initial?.let { WeekUtils.parseTime(it.timeOfDay) } ?: DEFAULT_TIME)
    }
    var startDate by remember(initial?.id) {
        mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.startDay) } ?: LocalDate.now())
    }
    var endDate by remember(initial?.id) {
        mutableStateOf<LocalDate?>(initial?.endDay?.let { LocalDate.ofEpochDay(it) })
    }
    // new routine: "at time" (offset 0) if it exists, else the first option; edit: the stored offset
    var alarm: AlarmOption by remember(initial?.id) {
        val default = ALARM_OPTIONS.firstOrNull { it.offsetMinutes == 0 } ?: ALARM_OPTIONS[0]
        mutableStateOf(
            if (initial == null) default
            else ALARM_OPTIONS.firstOrNull { it.offsetMinutes == initial.alarmOffsetMinutes } ?: default,
        )
    }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "روتین جدید" else "ویرایش روتین") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

                TimeField(
                    label = "ساعت",
                    time = time,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "تاریخ شروع",
                    date = startDate,
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "تاریخ پایان (اختیاری)",
                    date = endDate,
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = endDate == null,
                    onClick = {
                        endDate = null
                        onEdit()
                    },
                    label = { Text("بدون پایان") },
                )

                Text("آلارم", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ALARM_OPTIONS) { option ->
                        FilterChip(
                            selected = alarm == option,
                            onClick = {
                                alarm = option
                                onEdit()
                            },
                            label = { Text(option.label) },
                        )
                    }
                }

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            PillButton(
                text = "ذخیره",
                onClick = { onSave(title, days, time, startDate, endDate, alarm.offsetMinutes) },
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

    if (showTimePicker) {
        TimePickerDialog24(
            initial = time,
            onDismiss = { showTimePicker = false },
            onPick = {
                time = it
                showTimePicker = false
                onEdit()
            },
        )
    }
    if (showStartPicker) {
        ShamsiDatePickerDialog(
            initial = startDate,
            onDismiss = { showStartPicker = false },
            onPick = {
                startDate = it
                showStartPicker = false
                onEdit()
            },
        )
    }
    if (showEndPicker) {
        ShamsiDatePickerDialog(
            initial = endDate ?: startDate,
            onDismiss = { showEndPicker = false },
            onPick = {
                endDate = it
                showEndPicker = false
                onEdit()
            },
        )
    }
}

package com.icoordinator.ui.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.ALARM_OPTIONS
import com.icoordinator.domain.AlarmOption
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.RepeatType
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.CheckCircle
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ProgressLine
import com.icoordinator.ui.components.QuickAction
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TagText
import com.icoordinator.ui.openExactAlarmSettings
import com.icoordinator.ui.rememberExactAlarmAllowed
import java.time.LocalDate

@Composable
fun WeeklyLogScreen() {
    val vm: WeeklyLogViewModel = viewModel(key = "weekly") {
        WeeklyLogViewModel(
            ICoordinatorApp.instance.taskRepository,
            ICoordinatorApp.instance.projectRepository,
        )
    }
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val tasks by vm.tasksOfWeek.collectAsStateWithLifecycle()
    val projects by vm.projectList.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }

    val context = LocalContext.current
    val alarmAllowed = rememberExactAlarmAllowed()
    val scheme = MaterialTheme.colorScheme

    val projectNames = projects.associate { it.id to it.title }
    val weekEnd = weekStart.plusDays(6)
    val doneCount = tasks.count { it.isDone }
    val donePercent = if (tasks.isEmpty()) 0 else doneCount * 100 / tasks.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(scheme.primaryContainer.copy(alpha = 0.6f), scheme.background),
                        ),
                    ),
            ) {
                PageHeader(title = "تسک‌ها")
                Text(
                    text = "سلام 👋",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${WeekUtils.dateText(weekStart)} تا ${WeekUtils.dateText(weekEnd)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "انجام‌شده: $doneCount از ${tasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ProgressLine(
                    percent = donePercent,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        QuickAction(
                            icon = R.drawable.ic_tb_plus,
                            label = "تسک جدید",
                            onClick = { showAdd = true },
                            accent = true,
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        QuickAction(
                            icon = R.drawable.ic_tb_chevron_left,
                            label = "هفته قبل",
                            onClick = { vm.shiftWeek(-1) },
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        QuickAction(
                            icon = R.drawable.ic_tb_chevron_right,
                            label = "هفته بعد",
                            onClick = { vm.shiftWeek(1) },
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (!alarmAllowed) {
                            QuickAction(
                                icon = R.drawable.ic_tb_bell,
                                label = "فعال‌سازی آلارم",
                                onClick = { openExactAlarmSettings(context) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionLabel(
                text = "تسک‌ها",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (tasks.isEmpty()) {
            item {
                EmptyState(
                    icon = R.drawable.ic_tb_checklist,
                    title = "برای این هفته تسکی نداری",
                    message = "با دکمه‌ی «تسک جدید» اولین تسک را اضافه کن",
                    modifier = Modifier.fillMaxWidth(),
                    action = {
                        PillButton(
                            text = "تسک جدید",
                            onClick = { showAdd = true },
                            icon = R.drawable.ic_tb_plus,
                        )
                    },
                )
            }
        }

        items(tasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                projectName = task.projectId?.let { projectNames[it] },
                onToggle = { vm.toggleTask(task) },
                onDelete = { taskToDelete = task },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (showAdd) {
        AddTaskDialog(
            weekStart = weekStart,
            projects = projects,
            errorText = error,
            onEdit = { vm.clearError() },
            onDismiss = {
                vm.clearError()
                showAdd = false
            },
            onSave = { title, day, time, projectId, repeat, until, alarm ->
                if (vm.addTask(title, day, time, projectId, repeat, until, alarm)) showAdd = false
            },
        )
    }

    val pendingDelete = taskToDelete
    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            itemName = pendingDelete.title,
            onConfirm = {
                vm.deleteTask(pendingDelete)
                taskToDelete = null
            },
            onDismiss = { taskToDelete = null },
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    projectName: String?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val due = WeekUtils.toLocalDateTime(task.dueAt)
    val hasAlarm = task.alarmOffsetMinutes != null
    val dueLabel = "${WeekUtils.persianDayName(due.dayOfWeek)} ${WeekUtils.dateText(due.toLocalDate())} - " +
        due.format(WeekUtils.TIME_FMT)

    PillRow(
        modifier = modifier,
        leading = { CheckCircle(checked = task.isDone, onCheckedChange = { onToggle() }) },
        trailing = { DeleteIconButton(onClick = onDelete) },
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (task.isDone) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
        )
        Text(
            text = dueLabel,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (projectName != null || hasAlarm) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (projectName != null) {
                    TagText(text = projectName, icon = R.drawable.ic_tb_folder)
                }
                if (hasAlarm) {
                    TagText(text = "آلارم", icon = R.drawable.ic_tb_bell, color = scheme.primary)
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    weekStart: LocalDate,
    projects: List<ProjectEntity>,
    errorText: String?,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, Long?, RepeatType, String, Int?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(weekStart) }
    var time by remember { mutableStateOf("09:00") }
    var projectId by remember { mutableStateOf<Long?>(null) }
    var repeat by remember { mutableStateOf(RepeatType.NONE) }
    var untilText by remember { mutableStateOf("") }
    var alarm: AlarmOption by remember { mutableStateOf(ALARM_OPTIONS[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("تسک جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onEdit()
                    },
                    label = "عنوان تسک",
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("روز", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WeekUtils.weekDays(weekStart)) { d ->
                        FilterChip(
                            selected = d == day,
                            onClick = {
                                day = d
                                onEdit()
                            },
                            label = { Text("${WeekUtils.persianDayName(d.dayOfWeek)} ${PersianCalendar.toJalali(d).day}") },
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

                Text("تکرار", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RepeatType.values().toList()) { type ->
                        FilterChip(
                            selected = repeat == type,
                            onClick = {
                                repeat = type
                                onEdit()
                            },
                            label = { Text(type.label) },
                        )
                    }
                }
                if (repeat != RepeatType.NONE) {
                    AppTextField(
                        value = untilText,
                        onValueChange = {
                            untilText = it
                            onEdit()
                        },
                        label = "پایان تکرار (مثل ۱۴۰۵/۰۸/۱۰)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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

                Text("پروژه (اختیاری)", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = projectId == null,
                            onClick = {
                                projectId = null
                                onEdit()
                            },
                            label = { Text("بدون پروژه") },
                        )
                    }
                    items(projects) { p ->
                        FilterChip(
                            selected = projectId == p.id,
                            onClick = {
                                projectId = p.id
                                onEdit()
                            },
                            label = { Text(p.title) },
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
                onClick = { onSave(title, day, time, projectId, repeat, untilText, alarm.offsetMinutes) },
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

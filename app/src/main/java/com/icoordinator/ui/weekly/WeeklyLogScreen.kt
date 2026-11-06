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
import androidx.compose.foundation.lazy.items
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
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.CheckCircle
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.ContributionDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ProgressLine
import com.icoordinator.ui.components.QuickAction
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TagText
import com.icoordinator.ui.components.TaskFormDialog
import com.icoordinator.ui.openExactAlarmSettings
import com.icoordinator.ui.rememberExactAlarmAllowed

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
    val goals by vm.goals.collectAsStateWithLifecycle()
    val goalProgress by vm.goalProgress.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToTick by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }

    val context = LocalContext.current
    val alarmAllowed = rememberExactAlarmAllowed()
    val scheme = MaterialTheme.colorScheme

    val projectNames = projects.associate { it.id to it.title }
    val goalsById = goals.associateBy { it.id }
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
                goalName = task.goalId?.let { goalsById[it]?.title },
                onClick = { taskToEdit = task },
                onToggle = {
                    if (task.isDone) {
                        vm.setDone(task, false, null)
                    } else if (task.goalId?.let { goalsById[it] } != null) {
                        taskToTick = task
                    } else {
                        vm.setDone(task, true, null)
                    }
                },
                onDelete = { taskToDelete = task },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (showAdd) {
        TaskFormDialog(
            weekStart = weekStart,
            projects = projects,
            goals = goals,
            onDismiss = { showAdd = false },
            onSave = { r ->
                vm.addTask(r)
                showAdd = false
            },
        )
    }

    val editing = taskToEdit
    if (editing != null) {
        TaskFormDialog(
            weekStart = weekStart,
            projects = projects,
            goals = goals,
            onDismiss = { taskToEdit = null },
            onSave = { r ->
                vm.updateTask(editing, r)
                taskToEdit = null
            },
            initial = editing,
        )
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
    goalName: String?,
    onClick: () -> Unit,
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
        onClick = onClick,
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
        if (projectName != null || goalName != null || hasAlarm) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (projectName != null) {
                    TagText(text = projectName, icon = R.drawable.ic_tb_folder)
                }
                if (goalName != null) {
                    TagText(text = goalName, icon = R.drawable.ic_tb_check, color = scheme.primary)
                }
                if (hasAlarm) {
                    TagText(text = "آلارم", icon = R.drawable.ic_tb_bell, color = scheme.primary)
                }
            }
        }
    }
}

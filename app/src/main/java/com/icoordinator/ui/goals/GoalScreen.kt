package com.icoordinator.ui.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.WeekUtils
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.CheckCircle
import com.icoordinator.ui.components.ContributionDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.DeleteTaskDialog
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ProgressLine
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TaskFormDialog
import java.time.LocalDate

private const val PAGE_SIZE = 10

@Composable
fun GoalScreen(goalId: Long, onBack: () -> Unit) {
    val vm: GoalViewModel = viewModel(key = "goal-$goalId") {
        GoalViewModel(
            ICoordinatorApp.instance.projectRepository,
            ICoordinatorApp.instance.taskRepository,
            goalId,
        )
    }
    val goal by vm.goal.collectAsStateWithLifecycle()
    val goalTasks by vm.goalTasks.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val projects by vm.projectList.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val current = goal
    if (current == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            PageHeader(title = "هدف", onBack = onBack)
            Text(
                "",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var showAddTask by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToTick by remember { mutableStateOf<TaskEntity?>(null) }
    var shownCount by rememberSaveable(current.id) { mutableIntStateOf(PAGE_SIZE) }
    val sortedTasks = goalTasks.sortedByDescending { it.dueAt }
    val visibleTasks = sortedTasks.take(shownCount)
    var titleText by remember(current.id) { mutableStateOf(current.title) }
    var weightText by remember(current.id) { mutableStateOf(current.weightPercent.toString()) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        PageHeader(title = current.title, onBack = onBack)

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
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "پیشرفت هدف",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "$progress٪",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressLine(percent = progress, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item {
                Column {
                    SectionLabel("ویرایش هدف")
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        value = titleText,
                        onValueChange = {
                            titleText = it
                            vm.clearError()
                        },
                        label = "عنوان هدف",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        value = weightText,
                        onValueChange = {
                            weightText = it.filter { c -> c.isDigit() }.take(3)
                            vm.clearError()
                        },
                        label = "درصد از کل پروژه",
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null,
                        supportingText = error,
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton(
                        text = "ذخیره",
                        onClick = { vm.save(titleText, weightText.toIntOrNull() ?: 0) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                }
            }

            item { SectionLabel("تسک‌های این هدف (${goalTasks.size})") }

            if (goalTasks.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_tb_checklist,
                        title = "هنوز تسکی برای این هدف نیست",
                        message = "با دکمه‌ی «افزودن تسک» یکی اضافه کن",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(visibleTasks, key = { it.id }) { task ->
                GoalTaskRow(
                    task = task,
                    onToggle = {
                        if (task.isDone) {
                            vm.setDone(task, false, null)
                        } else {
                            taskToTick = task
                        }
                    },
                    onDelete = { taskToDelete = task },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (sortedTasks.size > shownCount) {
                item {
                    PillButton(
                        text = "بیشتر",
                        onClick = { shownCount += PAGE_SIZE },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        outlined = true,
                    )
                }
            }

            item {
                PillButton(
                    text = "افزودن تسک",
                    onClick = { showAddTask = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    icon = R.drawable.ic_tb_plus,
                    outlined = true,
                )
            }
        }
    }

    if (showAddTask) {
        TaskFormDialog(
            weekStart = WeekUtils.weekStartOf(LocalDate.now()),
            projects = projects,
            goals = listOf(current),
            onDismiss = { showAddTask = false },
            onSave = { r ->
                vm.addTask(r)
                showAddTask = false
            },
            fixedGoal = current,
        )
    }

    val pendingTick = taskToTick
    if (pendingTick != null) {
        ContributionDialog(
            goalTitle = current.title,
            currentProgress = progress,
            onDismiss = { taskToTick = null },
            onConfirm = { value ->
                vm.setDone(pendingTick, true, value)
                taskToTick = null
            },
        )
    }

    val pendingDelete = taskToDelete
    if (pendingDelete != null) {
        DeleteTaskDialog(
            task = pendingDelete,
            onDismiss = { taskToDelete = null },
            onDeleteOne = {
                vm.deleteTask(pendingDelete)
                taskToDelete = null
            },
            onDeleteSeries = {
                vm.deleteSeriesFrom(pendingDelete)
                taskToDelete = null
            },
        )
    }
}

@Composable
private fun GoalTaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val due = WeekUtils.toLocalDateTime(task.dueAt)
    val dueLabel = "${WeekUtils.dateText(due.toLocalDate())} - ${due.format(WeekUtils.TIME_FMT)}"
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
    }
}

package com.icoordinator.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.WeekUtils
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
    var showAdd by remember { mutableStateOf(false) }

    val projectNames = projects.associate { it.id to it.title }
    val weekEnd = weekStart.plusDays(6)
    val doneCount = tasks.count { it.isDone }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { vm.shiftWeek(-1) }) { Text("هفته قبل") }
                    Text(
                        "${weekStart.format(WeekUtils.DATE_FMT)} تا ${weekEnd.format(WeekUtils.DATE_FMT)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { vm.shiftWeek(1) }) { Text("هفته بعد") }
                }
            }

            item {
                Text(
                    "انجام‌شده: $doneCount از ${tasks.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (tasks.isEmpty()) {
                item { Text("برای این هفته تسکی نداری") }
            }

            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    projectName = task.projectId?.let { projectNames[it] },
                    onToggle = { vm.toggleTask(task) },
                    onDelete = { vm.deleteTask(task) },
                )
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text("تسک جدید")
        }
    }

    if (showAdd) {
        AddTaskDialog(
            weekStart = weekStart,
            projects = projects,
            onDismiss = { showAdd = false },
            onSave = { title, day, time, projectId ->
                if (vm.addTask(title, day, time, projectId)) showAdd = false
            },
            errorText = vm.error.collectAsStateWithLifecycle().value,
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    projectName: String?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val due = WeekUtils.toLocalDateTime(task.dueAt)
    val dueLabel = "${WeekUtils.persianDayName(due.dayOfWeek)} " +
        "${due.toLocalDate().format(WeekUtils.DATE_FMT)} - ${due.format(WeekUtils.TIME_FMT)}"
    val suffix = projectName?.let { " | $it" } ?: ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            )
            Text(dueLabel + suffix, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text("حذف") }
    }
}

@Composable
private fun AddTaskDialog(
    weekStart: LocalDate,
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, Long?) -> Unit,
    errorText: String?,
) {
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(weekStart) }
    var time by remember { mutableStateOf("09:00") }
    var projectId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسک جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان تسک") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("روز", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WeekUtils.weekDays(weekStart)) { d ->
                        FilterChip(
                            selected = d == day,
                            onClick = { day = d },
                            label = { Text(WeekUtils.persianDayName(d.dayOfWeek)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("ساعت (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("پروژه (اختیاری)", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = projectId == null,
                            onClick = { projectId = null },
                            label = { Text("بدون پروژه") },
                        )
                    }
                    items(projects) { p ->
                        FilterChip(
                            selected = projectId == p.id,
                            onClick = { projectId = p.id },
                            label = { Text(p.title) },
                        )
                    }
                }
                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, day, time, projectId) }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

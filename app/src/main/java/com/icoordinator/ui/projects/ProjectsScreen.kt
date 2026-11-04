package com.icoordinator.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.ui.components.*

@Composable
fun ProjectsScreen(onOpenProject: (Long) -> Unit) {
    val vm: ProjectsViewModel = viewModel(key = "projects") {
        ProjectsViewModel(ICoordinatorApp.instance.projectRepository)
    }
    val projects by vm.projects.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (projects.isEmpty()) {
            EmptyState(
                title = "هنوز پروژه‌ای نداری",
                message = "با دکمه‌ی «پروژه جدید» شروع کن",
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects, key = { it.project.id }) { summary ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(summary.project.id) },
                    ) {
                        Text(summary.project.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        ProgressLine(
                            percent = summary.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("پیشرفت: ${summary.progress}٪", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { projectToDelete = summary.project }) {
                                Text("حذف")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Text("پروژه جدید")
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { title ->
                vm.createProject(title)
                showCreate = false
            },
        )
    }

    val pendingDelete = projectToDelete
    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            itemName = pendingDelete.title,
            onConfirm = {
                vm.deleteProject(pendingDelete)
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null },
        )
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پروژه جدید") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان پروژه") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(title) }, enabled = title.isNotBlank()) { Text("ساختن") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

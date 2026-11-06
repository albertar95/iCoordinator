package com.icoordinator.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.LetterCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ProgressLine

@Composable
fun ProjectsScreen(onOpenProject: (Long) -> Unit) {
    val vm: ProjectsViewModel = viewModel(key = "projects") {
        ProjectsViewModel(ICoordinatorApp.instance.projectRepository)
    }
    val projects by vm.projects.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PageHeader(
            title = "پروژه‌ها",
            actionIcon = R.drawable.ic_tb_plus,
            actionDescription = "پروژه جدید",
            onAction = { showCreate = true },
        )
        if (projects.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_tb_home,
                title = "هنوز پروژه‌ای نداری",
                message = "با دکمه‌ی «پروژه جدید» شروع کن",
                action = {
                    PillButton(
                        text = "پروژه جدید",
                        onClick = { showCreate = true },
                        icon = R.drawable.ic_tb_plus,
                    )
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects, key = { it.project.id }) { summary ->
                    PillRow(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenProject(summary.project.id) },
                        leading = { LetterCircle(summary.project.title) },
                        trailing = {
                            DeleteIconButton(onClick = { projectToDelete = summary.project })
                        },
                    ) {
                        Text(
                            text = summary.project.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "پیشرفت: ${summary.progress}٪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressLine(
                            percent = summary.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("پروژه جدید") },
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = "عنوان پروژه",
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PillButton(
                text = "ساختن",
                onClick = { onCreate(title) },
                modifier = Modifier.height(52.dp),
                enabled = title.isNotBlank(),
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

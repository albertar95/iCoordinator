package com.icoordinator.ui.projects

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.ui.components.*

@Composable
fun ProjectDetailScreen(projectId: Long, onBack: () -> Unit) {
    val vm: ProjectDetailViewModel = viewModel(key = "detail-$projectId") {
        ProjectDetailViewModel(ICoordinatorApp.instance.projectRepository, projectId)
    }
    val project by vm.project.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val current = project
    if (current == null) {
        Text(
            "پروژه پیدا نشد",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    var goalTitle by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var manifesto by remember(current.id) { mutableStateOf(current.manifesto) }
    var goalToDelete by remember { mutableStateOf<GoalEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("بازگشت") }
            }
            Text(current.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("پیشرفت کل: $progress٪", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            ProgressLine(
                percent = progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SectionTitle("اهداف")
            Text("باقی‌مانده برای تخصیص: $remaining٪", style = MaterialTheme.typography.bodySmall)
        }

        if (goals.isEmpty()) {
            item {
                EmptyState(
                    title = "هنوز هدفی اضافه نشده",
                    message = "از بخش «هدف جدید» پایین صفحه یک هدف اضافه کن",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(goals, key = { it.id }) { goal ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = goal.isDone, onCheckedChange = { vm.toggleGoal(goal) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(goal.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${goal.weightPercent}٪ از پروژه", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { goalToDelete = goal }) { Text("حذف") }
                }
            }
        }

        item {
            SectionTitle("هدف جدید")
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = goalTitle,
                onValueChange = {
                    goalTitle = it
                    vm.clearError()
                },
                label = { Text("عنوان هدف") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = goalWeight,
                onValueChange = {
                    goalWeight = it.filter { c -> c.isDigit() }.take(3)
                    vm.clearError()
                },
                label = { Text("درصد از کل پروژه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.addGoal(goalTitle, goalWeight.toIntOrNull() ?: 0)
                    if (error == null && goalTitle.isNotBlank()) {
                        goalTitle = ""
                        goalWeight = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("افزودن هدف") }
        }

        item {
            SectionTitle("مانیفست پروژه")
            OutlinedTextField(
                value = manifesto,
                onValueChange = { manifesto = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                minLines = 5,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.saveManifesto(manifesto) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره مانیفست") }
        }
    }

    val pendingGoal = goalToDelete
    if (pendingGoal != null) {
        ConfirmDeleteDialog(
            itemName = pendingGoal.title,
            onConfirm = {
                vm.deleteGoal(pendingGoal)
                goalToDelete = null
            },
            onDismiss = { goalToDelete = null },
        )
    }
}

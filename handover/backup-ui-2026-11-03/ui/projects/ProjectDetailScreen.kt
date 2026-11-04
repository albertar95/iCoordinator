package com.icoordinator.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp

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

    val current = project ?: run {
        Text("پروژه پیدا نشد")
        return
    }

    var goalTitle by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var manifesto by remember(current.id) { mutableStateOf(current.manifesto) }

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
            Text("پیشرفت کل: $progress٪", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { HorizontalDivider() }

        item {
            Text("اهداف", style = MaterialTheme.typography.titleMedium)
            Text("باقی‌مانده برای تخصیص: $remaining٪", style = MaterialTheme.typography.bodySmall)
        }

        if (goals.isEmpty()) {
            item { Text("هنوز هدفی اضافه نشده", style = MaterialTheme.typography.bodyMedium) }
        }

        items(goals, key = { it.id }) { goal ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = goal.isDone, onCheckedChange = { vm.toggleGoal(goal) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.bodyLarge)
                    Text("${goal.weightPercent}٪ از پروژه", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.deleteGoal(goal) }) { Text("حذف") }
            }
        }

        item {
            HorizontalDivider()
            Text("هدف جدید", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = goalTitle,
                onValueChange = { goalTitle = it },
                label = { Text("عنوان هدف") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = goalWeight,
                onValueChange = { goalWeight = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("درصد از کل پروژه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
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
            HorizontalDivider()
            Text("مانیفست پروژه", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = manifesto,
                onValueChange = { manifesto = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                minLines = 5,
            )
            OutlinedButton(
                onClick = { vm.saveManifesto(manifesto) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره مانیفست") }
        }
    }
}

package com.icoordinator.ui.projects

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.CheckCircle
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ProgressLine
import com.icoordinator.ui.components.SectionLabel

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            PageHeader(title = "پروژه", onBack = onBack)
            Text(
                "پروژه پیدا نشد",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        ProjectDetailContent(
            vm = vm,
            current = current,
            goals = goals,
            progress = progress,
            remaining = remaining,
            error = error,
            onBack = onBack,
        )
    }
}

@Composable
private fun ProjectDetailContent(
    vm: ProjectDetailViewModel,
    current: ProjectEntity,
    goals: List<GoalEntity>,
    progress: Int,
    remaining: Int,
    error: String?,
    onBack: () -> Unit,
) {
    var goalTitle by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var manifesto by remember(current.id) { mutableStateOf(current.manifesto) }
    var goalToDelete by remember { mutableStateOf<GoalEntity?>(null) }
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
                                text = "پیشرفت کل: $progress٪",
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
                        ProgressLine(
                            percent = progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("اهداف", modifier = Modifier.weight(1f))
                    Text(
                        text = "باقی‌مانده برای تخصیص: $remaining٪",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                    )
                }
            }

            if (goals.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_tb_check,
                        title = "هنوز هدفی اضافه نشده",
                        message = "از بخش «هدف جدید» پایین صفحه یک هدف اضافه کن",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(goals, key = { it.id }) { goal ->
                PillRow(
                    modifier = Modifier.fillMaxWidth(),
                    leading = {
                        CheckCircle(
                            checked = goal.isDone,
                            onCheckedChange = { vm.toggleGoal(goal) },
                        )
                    },
                    trailing = { DeleteIconButton(onClick = { goalToDelete = goal }) },
                ) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (goal.isDone) scheme.onSurfaceVariant else scheme.onSurface,
                        textDecoration = if (goal.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                    )
                    Text(
                        text = "${goal.weightPercent}٪ از پروژه",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            item {
                Column {
                    SectionLabel("هدف جدید")
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        value = goalTitle,
                        onValueChange = {
                            goalTitle = it
                            vm.clearError()
                        },
                        label = "عنوان هدف",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        value = goalWeight,
                        onValueChange = {
                            goalWeight = it.filter { c -> c.isDigit() }.take(3)
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
                        text = "افزودن هدف",
                        onClick = {
                            vm.addGoal(goalTitle, goalWeight.toIntOrNull() ?: 0)
                            if (error == null && goalTitle.isNotBlank()) {
                                goalTitle = ""
                                goalWeight = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        icon = R.drawable.ic_tb_plus,
                    )
                }
            }

            item {
                Column {
                    SectionLabel("مانیفست پروژه")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manifesto,
                        onValueChange = { manifesto = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = scheme.outlineVariant,
                            focusedBorderColor = scheme.primary,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton(
                        text = "ذخیره مانیفست",
                        onClick = { vm.saveManifesto(manifesto) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        outlined = true,
                    )
                }
            }
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

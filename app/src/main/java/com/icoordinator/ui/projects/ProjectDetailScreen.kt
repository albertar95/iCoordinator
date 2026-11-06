package com.icoordinator.ui.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.icoordinator.ui.components.ProgressLine
import com.icoordinator.ui.components.SectionLabel

@Composable
fun ProjectDetailScreen(projectId: Long, onBack: () -> Unit, onOpenGoal: (Long) -> Unit) {
    val vm: ProjectDetailViewModel = viewModel(key = "detail-$projectId") {
        ProjectDetailViewModel(
            ICoordinatorApp.instance.projectRepository,
            ICoordinatorApp.instance.taskRepository,
            projectId,
        )
    }
    val project by vm.project.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val goalProgress by vm.goalProgress.collectAsStateWithLifecycle()
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
                "",
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
            goalProgress = goalProgress,
            progress = progress,
            remaining = remaining,
            error = error,
            onBack = onBack,
            onOpenGoal = onOpenGoal,
        )
    }
}

@Composable
private fun ProjectDetailContent(
    vm: ProjectDetailViewModel,
    current: ProjectEntity,
    goals: List<GoalEntity>,
    goalProgress: Map<Long, Int>,
    progress: Int,
    remaining: Int,
    error: String?,
    onBack: () -> Unit,
    onOpenGoal: (Long) -> Unit,
) {
    var manifestoOpen by remember { mutableStateOf(false) }
    var manifestoEditing by remember { mutableStateOf(false) }
    var manifestoDraft by remember(current.manifesto) { mutableStateOf(current.manifesto) }
    var goalsOpen by remember { mutableStateOf(true) }
    var showAddGoal by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<GoalEntity?>(null) }

    // Local order used while dragging; it is replaced whenever the stored goals change.
    var order by remember { mutableStateOf(goals) }
    val storedGoals by rememberUpdatedState(goals)
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(goals) { if (draggingId == null) order = goals }
    val rowHeights = remember { mutableStateMapOf<Long, Int>() }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        PageHeader(title = current.title, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

            // C1a: manifesto first, collapsed by default
            SectionHeader(
                title = "مانیفست پروژه",
                open = manifestoOpen,
                onToggle = { manifestoOpen = !manifestoOpen },
            )
            if (manifestoOpen) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        if (manifestoEditing) {
                            AppTextField(
                                value = manifestoDraft,
                                onValueChange = { manifestoDraft = it },
                                label = "مانیفست",
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                minLines = 5,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PillButton(
                                    text = "ذخیره",
                                    onClick = {
                                        vm.saveManifesto(manifestoDraft)
                                        manifestoEditing = false
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                )
                                PillButton(
                                    text = "انصراف",
                                    onClick = {
                                        manifestoDraft = current.manifesto
                                        manifestoEditing = false
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    outlined = true,
                                )
                            }
                        } else {
                            Text(
                                text = current.manifesto.ifBlank { "مانیفستی نوشته نشده" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (current.manifesto.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            PillButton(
                                text = "ویرایش",
                                onClick = {
                                    manifestoDraft = current.manifesto
                                    manifestoEditing = true
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                outlined = true,
                            )
                        }
                    }
                }
            }

            // C1b: goals with count, collapsible, expanded by default
            SectionHeader(
                title = "اهداف (${goals.size})",
                open = goalsOpen,
                onToggle = { goalsOpen = !goalsOpen },
                hint = "باقی‌مانده برای تخصیص: $remaining٪",
            )
            if (goalsOpen) {
                if (goals.isEmpty()) {
                    EmptyState(
                        icon = R.drawable.ic_tb_check,
                        title = "هنوز هدفی اضافه نشده",
                        message = "با دکمه‌ی «افزودن» یک هدف اضافه کن",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    order.forEach { goal ->
                        key(goal.id) {
                            val dragging = draggingId == goal.id
                            GoalRow(
                                goal = goal,
                                percent = goalProgress[goal.id] ?: 0,
                                onToggle = { vm.toggleGoal(goal) },
                                onDelete = { goalToDelete = goal },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { rowHeights[goal.id] = it.size.height }
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                    // C1d: double-tap opens the goal
                                    .pointerInput(goal.id) {
                                        detectTapGestures(onDoubleTap = { onOpenGoal(goal.id) })
                                    }
                                    // C1c: long-press then drag reorders
                                    .pointerInput(goal.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingId = goal.id
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val index = order.indexOfFirst { it.id == goal.id }
                                                if (index >= 0) {
                                                    if (dragOffset > 0f && index < order.lastIndex) {
                                                        val step = (rowHeights[order[index + 1].id] ?: 0) + gapPx
                                                        if (step > 0f && dragOffset > step / 2f) {
                                                            val moved = order.toMutableList()
                                                            moved.add(index + 1, moved.removeAt(index))
                                                            order = moved
                                                            dragOffset -= step
                                                        }
                                                    } else if (dragOffset < 0f && index > 0) {
                                                        val step = (rowHeights[order[index - 1].id] ?: 0) + gapPx
                                                        if (step > 0f && -dragOffset > step / 2f) {
                                                            val moved = order.toMutableList()
                                                            moved.add(index - 1, moved.removeAt(index))
                                                            order = moved
                                                            dragOffset += step
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                dragOffset = 0f
                                                vm.reorderGoals(order)
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffset = 0f
                                                order = storedGoals
                                            },
                                        )
                                    },
                            )
                        }
                    }
                }
                // C1e: only an add button under the list
                PillButton(
                    text = "افزودن",
                    onClick = {
                        vm.clearError()
                        showAddGoal = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    icon = R.drawable.ic_tb_plus,
                    outlined = true,
                )
            }
        }
    }

    if (showAddGoal) {
        AddGoalDialog(
            remaining = remaining,
            error = error,
            onClearError = { vm.clearError() },
            onDismiss = {
                vm.clearError()
                showAddGoal = false
            },
            onSave = { title, weight ->
                vm.addGoal(title, weight) { showAddGoal = false }
            },
        )
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

/** Section title row with an expand/collapse arrow; the whole row toggles. */
@Composable
private fun SectionHeader(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    hint: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, modifier = Modifier.weight(1f))
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            )
        }
        Text(
            text = if (open) "▲" else "▼",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, end = 8.dp),
        )
    }
}

/** Goal row whose background fills from the start side up to [percent]. */
@Composable
private fun GoalRow(
    goal: GoalEntity,
    percent: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = percent.coerceIn(0, 100) / 100f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Box {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(scheme.primaryContainer),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckCircle(checked = goal.isDone, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (goal.isDone) scheme.onSurfaceVariant else scheme.onSurface,
                        textDecoration = if (goal.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                    )
                    Text(
                        text = "وزن ${goal.weightPercent}٪",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "$percent٪",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                DeleteIconButton(onClick = onDelete)
            }
        }
    }
}

@Composable
private fun AddGoalDialog(
    remaining: Int,
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("هدف جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onClearError()
                    },
                    label = "عنوان هدف",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = weight,
                    onValueChange = {
                        weight = it.filter { c -> c.isDigit() }.take(3)
                        onClearError()
                    },
                    label = "درصد از کل پروژه",
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error ?: "باقی‌مانده برای تخصیص: $remaining٪",
                    keyboardType = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            PillButton(
                text = "افزودن",
                onClick = { onSave(title, weight.toIntOrNull() ?: 0) },
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

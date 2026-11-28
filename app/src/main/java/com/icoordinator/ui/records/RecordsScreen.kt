package com.icoordinator.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.R
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.personal.RecordEntity
import com.icoordinator.personal.RecordMath
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.ProgressLine
import com.icoordinator.ui.components.TbIcon
import java.time.Instant
import java.time.ZoneId

@Composable
fun RecordsScreen(onExit: () -> Unit) {
    val vm: RecordsViewModel = viewModel(key = "records")
    val records by vm.records.collectAsStateWithLifecycle()

    var showNew by remember { mutableStateOf(false) }
    var valueFor by remember { mutableStateOf<RecordEntity?>(null) }
    var editing by remember { mutableStateOf<RecordEntity?>(null) }
    var deleting by remember { mutableStateOf<RecordEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader(
            title = "رکوردها",
            onBack = onExit,
            actionIcon = R.drawable.ic_tb_plus,
            actionDescription = "رکورد جدید",
            onAction = { showNew = true },
        )
        val list = records
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                icon = R.drawable.ic_tb_target,
                title = "رکوردی نیست",
                message = "هدفی تعریف کن و پیشرفتت را ثبت کن",
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onClick = { valueFor = record },
                        onEdit = { editing = record },
                    )
                }
            }
        }
    }

    if (showNew) {
        RecordDialog(
            title = "رکورد جدید",
            confirmText = "افزودن",
            initialTitle = "",
            initialTarget = "",
            initialCurrent = "",
            onSubmit = { t, target, current, report ->
                vm.addRecord(t, target, current) { errors ->
                    if (errors.isEmpty) showNew = false else report(errors)
                }
            },
            onDismiss = { showNew = false },
        )
    }

    val valueRecord = valueFor
    if (valueRecord != null) {
        RecordDialog(
            title = "ثبت مقدار جدید",
            confirmText = "ثبت",
            initialTitle = null,
            initialTarget = null,
            initialCurrent = RecordMath.format(valueRecord.current),
            onSubmit = { _, _, current, report ->
                vm.updateValue(valueRecord.id, current) { errors ->
                    if (errors.isEmpty) valueFor = null else report(errors)
                }
            },
            onDismiss = { valueFor = null },
        )
    }

    val editRecord = editing
    if (editRecord != null) {
        RecordDialog(
            title = "ویرایش رکورد",
            confirmText = "ذخیره",
            initialTitle = editRecord.title,
            initialTarget = RecordMath.format(editRecord.target),
            initialCurrent = null,
            onSubmit = { t, target, _, report ->
                vm.editRecord(editRecord.id, t, target) { errors ->
                    if (errors.isEmpty) editing = null else report(errors)
                }
            },
            onDismiss = { editing = null },
            onDelete = {
                deleting = editRecord
                editing = null
            },
        )
    }

    val deleteRecord = deleting
    if (deleteRecord != null) {
        ConfirmDeleteDialog(
            itemName = deleteRecord.title,
            onConfirm = {
                deleting = null
                vm.deleteRecord(deleteRecord.id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordCard(record: RecordEntity, onClick: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val percent = RecordMath.percent(record.current, record.target)
    val previous = record.previous
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = AccountingFormat.digits("$percent") + "٪",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                IconButton(onClick = onEdit) {
                    TbIcon(R.drawable.ic_tb_pencil, "ویرایش رکورد", size = 22.dp, tint = scheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.padding(end = 10.dp)) {
                ProgressLine(percent = percent)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "فعلی: " + RecordMath.format(record.current) + " از " + RecordMath.format(record.target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                if (previous != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "قبلی: " + RecordMath.format(previous) + " (آخرین تغییر: " + changeDateText(record.updatedAt) + ")",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** تاریخ شمسی (با ارقام فارسی) از زمان ذخیره‌شده؛ updatedAt فقط با تغییر مقدار فعلی عوض می‌شود. */
private fun changeDateText(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return AccountingFormat.digits(PersianCalendar.format(date))
}

/**
 * One dialog for the three record forms: a field is shown when its initial text is not null.
 * [onSubmit] receives (title, target, current, reportErrors); the caller closes the dialog on success.
 */
@Composable
private fun RecordDialog(
    title: String,
    confirmText: String,
    initialTitle: String?,
    initialTarget: String?,
    initialCurrent: String?,
    onSubmit: (String, String, String, (RecordFormErrors) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var titleText by remember { mutableStateOf(initialTitle ?: "") }
    var targetText by remember { mutableStateOf(initialTarget ?: "") }
    var currentText by remember { mutableStateOf(initialCurrent ?: "") }
    var errors by remember { mutableStateOf(RecordFormErrors()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialTitle != null) {
                    AppTextField(
                        value = titleText,
                        onValueChange = {
                            titleText = it
                            errors = errors.copy(title = null, general = null)
                        },
                        label = "عنوان",
                        isError = errors.title != null,
                        supportingText = errors.title,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (initialTarget != null) {
                    AppTextField(
                        value = targetText,
                        onValueChange = {
                            targetText = it
                            errors = errors.copy(target = null, general = null)
                        },
                        label = "مقدار هدف",
                        keyboardType = KeyboardType.Decimal,
                        isError = errors.target != null,
                        supportingText = errors.target,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (initialCurrent != null) {
                    AppTextField(
                        value = currentText,
                        onValueChange = {
                            currentText = it
                            errors = errors.copy(current = null, general = null)
                        },
                        label = "مقدار فعلی",
                        keyboardType = KeyboardType.Decimal,
                        isError = errors.current != null,
                        supportingText = errors.current,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val general = errors.general
                if (general != null) {
                    Text(text = general, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("حذف رکورد", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(titleText, targetText, currentText) { errors = it } }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

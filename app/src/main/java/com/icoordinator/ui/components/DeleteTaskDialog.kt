package com.icoordinator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.icoordinator.R
import com.icoordinator.data.entity.TaskEntity

/**
 * تأیید حذف تسک. اگر تسک عضو یک سری تکراری باشد (seriesId != null) بین «فقط همین تسک» و
 * «این و بعدی‌های سری» انتخاب می‌شود؛ تسک‌های انجام‌شده با حذف سری پاک نمی‌شوند.
 */
@Composable
fun DeleteTaskDialog(task: TaskEntity, onDismiss: () -> Unit, onDeleteOne: () -> Unit, onDeleteSeries: () -> Unit) {
    val isSeries = task.seriesId != null
    Dialog(onDismissRequest = onDismiss) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0.32f)
        }
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.width(330.dp),
            shape = RoundedCornerShape(32.dp),
            color = scheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    TbIcon(R.drawable.ic_tb_trash, null, size = 32.dp, tint = scheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isSeries) "حذف تسک تکراری" else "حذف تسک",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isSeries) "تسک‌های انجام‌شده حذف نمی‌شوند." else "حذف «${task.title}»؟ این کار قابل برگشت نیست.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                if (isSeries) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PillButton(
                            text = "فقط همین تسک",
                            onClick = onDeleteOne,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            destructive = true,
                        )
                        PillButton(
                            text = "این و بعدی‌های سری",
                            onClick = onDeleteSeries,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            destructive = true,
                        )
                        PillButton(
                            text = "انصراف",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            outlined = true,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PillButton(
                            text = "انصراف",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(52.dp),
                            outlined = true,
                        )
                        PillButton(
                            text = "حذف",
                            onClick = onDeleteOne,
                            modifier = Modifier.weight(1f).height(52.dp),
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

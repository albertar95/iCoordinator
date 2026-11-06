package com.icoordinator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val QUICK_VALUES = listOf(5, 10, 25, 50)

/** Asks how many percent of the goal a finished task advanced (0..100). */
@Composable
fun ContributionDialog(
    goalTitle: String,
    currentProgress: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val value: Int? = text.toIntOrNull()?.takeIf { it in 0..100 }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("پیشرفت هدف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goalTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "پیشرفت فعلی: ${currentProgress}٪",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                    label = "درصد پیشرفت (۰ تا ۱۰۰)",
                    modifier = Modifier.fillMaxWidth(),
                    isError = text.isNotEmpty() && value == null,
                    keyboardType = KeyboardType.Number,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QUICK_VALUES) { q ->
                        FilterChip(
                            selected = value == q,
                            onClick = { text = q.toString() },
                            label = { Text("${q}٪") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            PillButton(
                text = "تأیید",
                onClick = { value?.let(onConfirm) },
                modifier = Modifier.height(52.dp),
                enabled = value != null,
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

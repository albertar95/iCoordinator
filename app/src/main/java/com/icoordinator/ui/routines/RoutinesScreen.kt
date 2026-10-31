package com.icoordinator.ui.routines

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import java.time.DayOfWeek
import java.time.LocalDate

/** روزهای هفته به ترتیب شنبه تا جمعه */
private val WEEK_ORDER = listOf(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

@Composable
fun RoutinesScreen() {
    val vm: RoutinesViewModel = viewModel(key = "routines") {
        RoutinesViewModel(ICoordinatorApp.instance.routineRepository)
    }
    val routines by vm.routines.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (routines.isEmpty()) {
                item { Text("هنوز روتینی نداری") }
            }

            items(routines, key = { it.id }) { routine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${RoutineUtils.daysLabel(routine.daysMask)} - ${routine.timeOfDay}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "از ${WeekUtils.dateText(LocalDate.ofEpochDay(routine.startDay))}" +
                                    (routine.endDay?.let { " تا ${WeekUtils.dateText(LocalDate.ofEpochDay(it))}" }
                                        ?: " (بدون پایان)"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { vm.deleteRoutine(routine) }) { Text("حذف") }
                    }
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Text("روتین جدید")
        }
    }

    if (showAdd) {
        AddRoutineDialog(
            errorText = error,
            onDismiss = {
                vm.clearError()
                showAdd = false
            },
            onSave = { title, days, time, start, end ->
                if (vm.addRoutine(title, days, time, start, end)) showAdd = false
            },
        )
    }
}

@Composable
private fun AddRoutineDialog(
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (String, Set<DayOfWeek>, String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var time by remember { mutableStateOf("07:00") }
    var startText by remember { mutableStateOf(PersianCalendar.format(LocalDate.now())) }
    var endText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("روتین جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان روتین") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("روزها", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WEEK_ORDER) { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(WeekUtils.persianDayName(day)) },
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
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("تاریخ شروع (مثل ۱۴۰۵/۰۸/۱۰)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("تاریخ پایان (خالی = بدون پایان)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, days, time, startText, endText) }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

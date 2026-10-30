package com.icoordinator.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.data.entity.WeeklyReportEntity
import com.icoordinator.domain.ReportScore
import com.icoordinator.domain.WeeklyReport
import java.time.LocalDate

@Composable
fun ReportScreen() {
    val vm: ReportViewModel = viewModel(key = "report") {
        ReportViewModel(ICoordinatorApp.instance.reportRepository)
    }
    val reports by vm.reports.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(onClick = { vm.generateCurrentWeek() }, modifier = Modifier.fillMaxWidth()) {
                Text("ساخت گزارش هفته‌ی جاری")
            }
        }

        selected?.let { report ->
            item { ReportCard(report) }
        }

        item {
            Text("گزارش‌های ذخیره‌شده", style = MaterialTheme.typography.titleSmall)
        }

        if (reports.isEmpty()) {
            item { Text("هنوز گزارشی ساخته نشده") }
        }

        items(reports, key = { it.weekStart }) { report ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.select(report) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(WeeklyReport.rangeLabel(LocalDate.ofEpochDay(report.weekStart)))
                Text(
                    scoreLabel(report),
                    color = scoreColor(report.scoreName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ReportCard(report: WeeklyReportEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "نمره: ${scoreLabel(report)}",
                style = MaterialTheme.typography.titleMedium,
                color = scoreColor(report.scoreName),
            )
            Text(report.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun scoreLabel(report: WeeklyReportEntity): String =
    report.scoreName?.let { name -> ReportScore.valueOf(name).label } ?: "بدون داده"

private fun scoreColor(scoreName: String?): Color = when (scoreName) {
    ReportScore.GOOD.name -> Color(0xFF2E7D32)
    ReportScore.MEDIUM.name -> Color(0xFFF9A825)
    ReportScore.BAD.name -> Color(0xFFC62828)
    else -> Color.Unspecified
}

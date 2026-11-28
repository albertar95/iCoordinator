package com.icoordinator.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.entity.WeeklyReportEntity
import com.icoordinator.domain.ReportScore
import com.icoordinator.domain.WeekUtils
import com.icoordinator.domain.WeeklyReport
import android.widget.Toast
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.ShamsiDatePickerDialog
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.ScorePill
import com.icoordinator.ui.components.ScoreRing
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.theme.scoreColor
import java.time.LocalDate

@Composable
fun ReportScreen() {
    val vm: ReportViewModel = viewModel(key = "report") {
        ReportViewModel(ICoordinatorApp.instance.reportRepository)
    }
    val reports by vm.reports.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    // Hero shows the selected report, or the latest saved one (first in the list) when none is selected.
    val heroReport = selected ?: reports.firstOrNull()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WeeklyReportEntity?>(null) }

    if (showPicker) {
        // هر روزی از هفته‌ی مورد نظر را انتخاب کن؛ گزارش کل آن هفته (شنبه تا جمعه) ساخته می‌شود
        ShamsiDatePickerDialog(
            initial = LocalDate.now().minusWeeks(1),
            onDismiss = { showPicker = false },
            onPick = { date ->
                showPicker = false
                if (WeekUtils.weekStartOf(date).isAfter(WeekUtils.weekStartOf(LocalDate.now()))) {
                    Toast.makeText(context, "برای هفته‌ی آینده نمی‌شود گزارش ساخت", Toast.LENGTH_SHORT).show()
                } else {
                    vm.generateForDate(date)
                }
            },
        )
    }

    deleting?.let { report ->
        ConfirmDeleteDialog(
            itemName = "گزارش " + WeeklyReport.rangeLabel(LocalDate.ofEpochDay(report.weekStart)),
            onConfirm = {
                deleting = null
                vm.delete(report)
            },
            onDismiss = { deleting = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PageHeader(title = "گزارش هفته")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PillButton(
                    text = "ساخت گزارش هفته‌ی جاری",
                    onClick = { vm.generateCurrentWeek() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_tb_plus,
                )
            }
            item {
                PillButton(
                    text = "ساخت گزارش برای بازه‌ی دیگر",
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_tb_calendar,
                    outlined = true,
                )
            }

            heroReport?.let { report ->
                item {
                    ReportCard(
                        report = report,
                        onRegenerate = { vm.regenerate(report) },
                        onDelete = { deleting = report },
                    )
                }
            }

            item {
                SectionLabel("گزارش‌های ذخیره‌شده")
            }

            if (reports.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_tb_report_analytics,
                        title = "هنوز گزارشی ساخته نشده",
                        message = "با دکمه‌ی بالا گزارش هفته‌ی جاری را بساز",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(reports, key = { it.weekStart }) { report ->
                PillRow(
                    modifier = Modifier.fillMaxWidth(),
                    selected = heroReport != null && heroReport.weekStart == report.weekStart,
                    onClick = { vm.select(report) },
                    leading = { IconCircle(icon = R.drawable.ic_tb_report_analytics) },
                    trailing = {
                        Box(modifier = Modifier.padding(end = 4.dp)) {
                            // DRAFT: the source of the percent is an open question (see return.md).
                            ScorePill(percent = report.percent)
                        }
                    },
                ) {
                    Text(
                        text = WeeklyReport.rangeLabel(LocalDate.ofEpochDay(report.weekStart)),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = report.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: WeeklyReportEntity, onRegenerate: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScoreRing(percent = report.percent, size = 128.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = WeeklyReport.rangeLabel(LocalDate.ofEpochDay(report.weekStart)),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ScoreLabelPill(label = "نمره: ${scoreLabel(report)}", percent = report.percent)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = report.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    text = "ساخت مجدد",
                    onClick = onRegenerate,
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_tb_refresh,
                    outlined = true,
                )
                DeleteIconButton(onClick = onDelete, contentDescription = "حذف گزارش")
            }
        }
    }
}

/** Label chip ("نمره: بد", 34 dp high) in the score colour of the report's percent. */
@Composable
private fun ScoreLabelPill(label: String, percent: Int?) {
    val dark = isSystemInDarkTheme()
    val onColor = when {
        percent == null -> MaterialTheme.colorScheme.surface
        percent > 80 -> if (dark) Color(0xFF00210A) else Color(0xFFFFFFFF)
        percent >= 50 -> Color(0xFF1A1A1A)
        else -> if (dark) Color(0xFF410002) else Color(0xFFFFFFFF)
    }
    Surface(
        shape = CircleShape,
        color = scoreColor(percent),
        contentColor = onColor,
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = onColor,
            )
        }
    }
}

private fun scoreLabel(report: WeeklyReportEntity): String =
    report.scoreName?.let { name -> ReportScore.valueOf(name).label } ?: "بدون داده"

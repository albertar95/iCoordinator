package com.icoordinator.data

import com.icoordinator.data.dao.RoutineDao
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.data.dao.WeeklyReportDao
import com.icoordinator.data.entity.WeeklyReportEntity
import com.icoordinator.domain.ReportScore
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import com.icoordinator.domain.WeeklyReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ReportRepository(
    private val reports: WeeklyReportDao,
    private val tasks: TaskDao,
    private val routines: RoutineDao,
) {

    fun observeReports(): Flow<List<WeeklyReportEntity>> = reports.observeAll()

    suspend fun delete(weekStart: Long) = reports.delete(weekStart)

    /** گزارش هفته‌ی داده‌شده را از روی تسک‌ها و روتین‌های آن هفته می‌سازد و ذخیره می‌کند. */
    suspend fun generate(weekStart: LocalDate): WeeklyReportEntity {
        val startDay = weekStart.toEpochDay()
        val endDay = startDay + 7

        val taskList = tasks.observeWeek(startDay).first()
        val routineList = routines.observeRoutines().first()
        val completions = routines.observeCompletions(startDay, endDay).first()

        val days = WeekUtils.weekDays(weekStart)
        val routinesTotal = routineList.sumOf { r ->
            val routineDays = RoutineUtils.daysOf(r.daysMask).toSet()
            days.count { it.dayOfWeek in routineDays }
        }
        val routineIds = routineList.map { it.id }.toSet()
        val routinesDone = completions.count { it.routineId in routineIds }

        val done = taskList.count { it.isDone } + routinesDone
        val total = taskList.size + routinesTotal
        val percent = if (total == 0) null else done * 100 / total
        val score = ReportScore.of(percent)

        val text = WeeklyReport.buildText(
            rangeLabel = WeeklyReport.rangeLabel(weekStart),
            tasks = taskList.map { it.title to it.isDone },
            routinesDone = routinesDone,
            routinesTotal = routinesTotal,
            percent = percent,
            score = score,
        )

        val report = WeeklyReportEntity(
            weekStart = startDay,
            generatedAt = System.currentTimeMillis(),
            percent = percent,
            scoreName = score?.name,
            text = text,
        )
        reports.upsert(report)
        return report
    }
}

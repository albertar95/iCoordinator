package com.icoordinator.data

import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.RepeatType
import com.icoordinator.domain.TaskRules
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskRepository(
    private val dao: TaskDao,
    private val alarms: TaskAlarmScheduler,
) {

    fun observeWeek(weekStart: LocalDate): Flow<List<TaskEntity>> =
        dao.observeWeek(weekStart.toEpochDay())

    /** تسک‌های بازه‌ی [from, to) */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>> =
        dao.observeRange(WeekUtils.toMillis(from.atStartOfDay()), WeekUtils.toMillis(to.atStartOfDay()))

    /** یک تسک یا تکرارهای آن را می‌سازد و برای هرکدام آلارم می‌گذارد. تعداد ساخته‌شده را برمی‌گرداند. */
    suspend fun addTasks(
        title: String,
        startDate: LocalDate,
        time: LocalTime,
        projectId: Long?,
        repeat: RepeatType,
        until: LocalDate?,
        alarmOffset: Int?,
    ): Int {
        val clean = title.trim()
        val dates = TaskRules.occurrenceDates(startDate, repeat, until)
        dates.forEach { date ->
            val dueAt = WeekUtils.toMillis(LocalDateTime.of(date, time))
            val task = TaskEntity(
                weekStart = WeekUtils.weekStartOf(date).toEpochDay(),
                dueAt = dueAt,
                title = clean,
                projectId = projectId,
                alarmOffsetMinutes = alarmOffset,
            )
            val id = dao.insert(task)
            alarms.schedule(id, clean, dueAt, alarmOffset)
        }
        return dates.size
    }

    suspend fun toggleTask(task: TaskEntity) {
        val updated = task.copy(isDone = !task.isDone)
        dao.update(updated)
        if (updated.isDone) {
            alarms.cancel(task.id)
        } else {
            alarms.schedule(task.id, updated.title, updated.dueAt, updated.alarmOffsetMinutes)
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        alarms.cancel(task.id)
        dao.delete(task)
    }
}

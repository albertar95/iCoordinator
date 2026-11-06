package com.icoordinator.data

import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.RepeatType
import com.icoordinator.domain.TaskRules
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
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
        goalId: Long? = null,
        weekDays: Set<DayOfWeek> = emptySet(),
    ): Int {
        val clean = title.trim()
        val dates = TaskRules.occurrenceDates(startDate, repeat, until, weekDays)
        dates.forEach { date ->
            val dueAt = WeekUtils.toMillis(LocalDateTime.of(date, time))
            val task = TaskEntity(
                weekStart = WeekUtils.weekStartOf(date).toEpochDay(),
                dueAt = dueAt,
                title = clean,
                projectId = projectId,
                goalId = goalId,
                alarmOffsetMinutes = alarmOffset,
            )
            val id = dao.insert(task)
            alarms.schedule(id, clean, dueAt, alarmOffset)
        }
        return dates.size
    }

    fun observeByGoal(goalId: Long): Flow<List<TaskEntity>> = dao.observeByGoal(goalId)

    fun observeWithGoal(): Flow<List<TaskEntity>> = dao.observeWithGoal()

    /** فقط همین یک ردیف (یک تکرار) را ویرایش می‌کند. */
    suspend fun updateTask(
        task: TaskEntity,
        title: String,
        date: LocalDate,
        time: LocalTime,
        projectId: Long?,
        goalId: Long?,
        alarmOffset: Int?,
    ) {
        val clean = title.trim()
        val dueAt = WeekUtils.toMillis(LocalDateTime.of(date, time))
        val updated = task.copy(
            title = clean,
            dueAt = dueAt,
            weekStart = WeekUtils.weekStartOf(date).toEpochDay(),
            projectId = projectId,
            goalId = goalId,
            alarmOffsetMinutes = alarmOffset,
        )
        dao.update(updated)
        alarms.cancel(task.id)
        if (!updated.isDone) {
            alarms.schedule(updated.id, clean, dueAt, alarmOffset)
        }
    }

    suspend fun setDone(task: TaskEntity, done: Boolean, contribution: Int?) {
        val updated = if (done) {
            task.copy(isDone = true, goalContribution = contribution?.coerceIn(0, 100))
        } else {
            task.copy(isDone = false, goalContribution = null)
        }
        dao.update(updated)
        if (done) {
            alarms.cancel(task.id)
        } else {
            alarms.schedule(task.id, updated.title, updated.dueAt, updated.alarmOffsetMinutes)
        }
    }

    suspend fun toggleTask(task: TaskEntity) = setDone(task, !task.isDone, null)

    suspend fun deleteTask(task: TaskEntity) {
        alarms.cancel(task.id)
        dao.delete(task)
    }
}

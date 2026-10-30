package com.icoordinator.data

import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

class TaskRepository(
    private val dao: TaskDao,
    private val alarms: TaskAlarmScheduler,
) {

    fun observeWeek(weekStart: LocalDate): Flow<List<TaskEntity>> =
        dao.observeWeek(weekStart.toEpochDay())

    /** تسک‌های بازه‌ی [from, to) را برمی‌گرداند (to معمولاً اولین روز ماه بعد است). */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>> =
        dao.observeRange(WeekUtils.toMillis(from.atStartOfDay()), WeekUtils.toMillis(to.atStartOfDay()))

    suspend fun addTask(title: String, dueAt: LocalDateTime, projectId: Long?) {
        val draft = TaskEntity(
            weekStart = WeekUtils.weekStartOf(dueAt.toLocalDate()).toEpochDay(),
            dueAt = WeekUtils.toMillis(dueAt),
            title = title.trim(),
            projectId = projectId,
        )
        val id = dao.insert(draft)
        alarms.schedule(id, draft.title, draft.dueAt)
    }

    suspend fun toggleTask(task: TaskEntity) {
        val updated = task.copy(isDone = !task.isDone)
        dao.update(updated)
        if (updated.isDone) {
            alarms.cancel(task.id)
        } else {
            alarms.schedule(task.id, updated.title, updated.dueAt)
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        alarms.cancel(task.id)
        dao.delete(task)
    }
}

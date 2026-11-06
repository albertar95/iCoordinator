package com.icoordinator.data

import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.dao.RoutineDao
import com.icoordinator.data.entity.RoutineCompletionEntity
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RoutineRepository(
    private val dao: RoutineDao,
    private val alarms: TaskAlarmScheduler,
) {

    fun observeRoutines(): Flow<List<RoutineEntity>> = dao.observeRoutines()

    /** انجام‌های روتین در بازه‌ی [from, to) */
    fun observeCompletions(from: LocalDate, to: LocalDate): Flow<List<RoutineCompletionEntity>> =
        dao.observeCompletions(from.toEpochDay(), to.toEpochDay())

    suspend fun addRoutine(
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        val cleanTitle = title.trim()
        val id = dao.insertRoutine(
            RoutineEntity(
                title = cleanTitle,
                daysMask = RoutineUtils.maskOf(days),
                timeOfDay = time.format(WeekUtils.TIME_FMT),
                startDay = startDate.toEpochDay(),
                endDay = endDate?.toEpochDay(),
            ),
        )
        scheduleAlarms(id, cleanTitle, days, time, startDate, endDate)
    }

    /** ویرایش روتین: آلارم‌های قبلی لغو و با مقادیر جدید دوباره ثبت می‌شوند. انجام‌ها حفظ می‌شوند. */
    suspend fun updateRoutine(
        routine: RoutineEntity,
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        val cleanTitle = title.trim()
        alarms.cancelRoutine(routine.id)
        dao.updateRoutine(
            routine.copy(
                title = cleanTitle,
                daysMask = RoutineUtils.maskOf(days),
                timeOfDay = time.format(WeekUtils.TIME_FMT),
                startDay = startDate.toEpochDay(),
                endDay = endDate?.toEpochDay(),
            ),
        )
        scheduleAlarms(routine.id, cleanTitle, days, time, startDate, endDate)
    }

    suspend fun deleteRoutine(routine: RoutineEntity) {
        alarms.cancelRoutine(routine.id)
        dao.deleteRoutine(routine)
    }

    suspend fun toggleCompletion(routineId: Long, date: LocalDate, isDone: Boolean) {
        if (isDone) {
            dao.deleteCompletion(routineId, date.toEpochDay())
        } else {
            dao.insertCompletion(RoutineCompletionEntity(routineId, date.toEpochDay()))
        }
    }

    /** آلارم هر روز را از اولین نوبت بعد از الان یا شروع روتین ثبت می‌کند، تا پایان روتین. */
    private fun scheduleAlarms(
        id: Long,
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        val endAt = endDate?.let { WeekUtils.toMillis(it.plusDays(1).atStartOfDay()) } ?: 0L
        val from: LocalDateTime = maxOf(LocalDateTime.now(), startDate.atStartOfDay())
        days.forEach { day ->
            val next = RoutineUtils.nextOccurrence(day, time, from)
            val nextDate = WeekUtils.toLocalDateTime(next).toLocalDate()
            if (endDate != null && nextDate > endDate) return@forEach
            alarms.scheduleRoutine(id, day, title, next, endAt)
        }
    }
}

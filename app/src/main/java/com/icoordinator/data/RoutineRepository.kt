package com.icoordinator.data

import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.dao.RoutineDao
import com.icoordinator.data.entity.RoutineCompletionEntity
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.domain.RoutineUtils
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RoutineRepository(
    private val dao: RoutineDao,
    private val alarms: TaskAlarmScheduler,
) {

    fun observeRoutines(): Flow<List<RoutineEntity>> = dao.observeRoutines()

    /** انجام‌های روتین در بازه‌ی [from, to) */
    fun observeCompletions(from: LocalDate, to: LocalDate): Flow<List<RoutineCompletionEntity>> =
        dao.observeCompletions(from.toEpochDay(), to.toEpochDay())

    suspend fun addRoutine(title: String, days: Set<DayOfWeek>, time: LocalTime) {
        val cleanTitle = title.trim()
        val id = dao.insertRoutine(
            RoutineEntity(
                title = cleanTitle,
                daysMask = RoutineUtils.maskOf(days),
                timeOfDay = time.format(com.icoordinator.domain.WeekUtils.TIME_FMT),
            ),
        )
        days.forEach { day ->
            alarms.scheduleRoutine(id, day, cleanTitle, RoutineUtils.nextOccurrence(day, time))
        }
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
}

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
        alarmOffset: Int? = 0,
    ) {
        val cleanTitle = title.trim()
        val id = dao.insertRoutine(
            RoutineEntity(
                title = cleanTitle,
                daysMask = RoutineUtils.maskOf(days),
                timeOfDay = time.format(WeekUtils.TIME_FMT),
                startDay = startDate.toEpochDay(),
                endDay = endDate?.toEpochDay(),
                alarmOffsetMinutes = alarmOffset,
            ),
        )
        scheduleAlarms(id, cleanTitle, days, time, startDate, endDate, alarmOffset)
    }

    /** ویرایش روتین: آلارم‌های قبلی لغو و با مقادیر جدید دوباره ثبت می‌شوند. انجام‌ها حفظ می‌شوند. */
    suspend fun updateRoutine(
        routine: RoutineEntity,
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
        alarmOffset: Int? = routine.alarmOffsetMinutes,
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
                alarmOffsetMinutes = alarmOffset,
            ),
        )
        scheduleAlarms(routine.id, cleanTitle, days, time, startDate, endDate, alarmOffset)
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

    /** آلارم‌های یک روتین ذخیره‌شده را (بدون لغو قبلی‌ها) دوباره ثبت می‌کند؛ برای بازتنظیم بعد از ری‌استارت. */
    fun scheduleAlarmsFor(routine: RoutineEntity) {
        val time = WeekUtils.parseTime(routine.timeOfDay) ?: return
        scheduleAlarms(
            id = routine.id,
            title = routine.title,
            days = RoutineUtils.daysOf(routine.daysMask).toSet(),
            time = time,
            startDate = LocalDate.ofEpochDay(routine.startDay),
            endDate = routine.endDay?.let { LocalDate.ofEpochDay(it) },
            alarmOffset = routine.alarmOffsetMinutes,
        )
    }

    /**
     * آلارم هر روز را از اولین نوبتِ بعد از الان (در/بعد از شروع روتین) ثبت می‌کند، تا پایان روتین.
     * alarmOffset: null یعنی بدون آلارم، ۰ همان ساعت، و N یعنی N دقیقه قبل.
     */
    private fun scheduleAlarms(
        id: Long,
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
        alarmOffset: Int?,
    ) {
        if (alarmOffset == null) return
        val endAt = endDate?.let { WeekUtils.toMillis(it.plusDays(1).atStartOfDay()) } ?: 0L
        val now = LocalDateTime.now()
        days.forEach { day ->
            val trigger = RoutineUtils.nextAlarm(day, time, alarmOffset, now, startDate) ?: return@forEach
            val occurrenceDate = WeekUtils.toLocalDateTime(trigger + alarmOffset * 60_000L).toLocalDate()
            if (endDate != null && occurrenceDate > endDate) return@forEach
            alarms.scheduleRoutine(id, day, title, trigger, endAt, alarmOffset)
        }
    }
}

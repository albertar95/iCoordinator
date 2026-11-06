package com.icoordinator.alarm

import com.icoordinator.data.dao.RoutineDao
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** بعد از ری‌استارت گوشی یا آپدیت اپ، همه‌ی آلارم‌های آینده را دوباره ثبت می‌کند. */
class AlarmRescheduler(
    private val tasks: TaskDao,
    private val routines: RoutineDao,
    private val scheduler: TaskAlarmScheduler,
) {

    /** همه‌ی آلارم‌های تسک‌های انجام‌نشده‌ی آینده و روتین‌ها را لغو می‌کند (قبل از بازیابی). */
    suspend fun cancelAll() {
        val now = System.currentTimeMillis()
        tasks.upcomingUndone(now).forEach { scheduler.cancel(it.id) }
        routines.observeRoutines().first().forEach { scheduler.cancelRoutine(it.id) }
    }

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()

        tasks.upcomingUndone(now).forEach { task ->
            scheduler.schedule(task.id, task.title, task.dueAt, task.alarmOffsetMinutes)
        }

        routines.observeRoutines().first().forEach { routine ->
            val time = LocalTime.parse(routine.timeOfDay, WeekUtils.TIME_FMT)
            val start = LocalDate.ofEpochDay(routine.startDay)
            val end = routine.endDay?.let { LocalDate.ofEpochDay(it) }
            val endAt = end?.let { WeekUtils.toMillis(it.plusDays(1).atStartOfDay()) } ?: 0L
            val offset = routine.alarmOffsetMinutes ?: return@forEach
            val offsetMs = offset * 60_000L
            val nowDt = LocalDateTime.now()

            RoutineUtils.daysOf(routine.daysMask).forEach { day ->
                val trigger = RoutineUtils.nextAlarm(day, time, offset, nowDt, start) ?: return@forEach
                val occurrenceDate = WeekUtils.toLocalDateTime(trigger + offsetMs).toLocalDate()
                if (end != null && occurrenceDate > end) return@forEach
                scheduler.scheduleRoutine(routine.id, day, routine.title, trigger, endAt, offset)
            }
        }
    }
}

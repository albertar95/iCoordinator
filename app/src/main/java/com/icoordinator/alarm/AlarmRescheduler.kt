package com.icoordinator.alarm

import com.icoordinator.data.dao.RoutineDao
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.domain.RoutineUtils
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/** بعد از ری‌استارت گوشی یا به‌روزرسانی اپ، همه‌ی آلارم‌های آینده را دوباره ثبت می‌کند. */
class AlarmRescheduler(
    private val tasks: TaskDao,
    private val routines: RoutineDao,
    private val scheduler: TaskAlarmScheduler,
) {

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()

        tasks.upcomingUndone(now).forEach { task ->
            scheduler.schedule(task.id, task.title, task.dueAt)
        }

        routines.observeRoutines().first().forEach { routine ->
            val time = LocalTime.parse(routine.timeOfDay, WeekUtils.TIME_FMT)
            RoutineUtils.daysOf(routine.daysMask).forEach { day ->
                scheduler.scheduleRoutine(
                    routine.id,
                    day,
                    routine.title,
                    RoutineUtils.nextOccurrence(day, time),
                )
            }
        }
    }
}

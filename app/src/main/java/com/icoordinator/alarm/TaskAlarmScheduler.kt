package com.icoordinator.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.DayOfWeek

/** زمان‌بندی و لغو آلارم تسک‌ها و روتین‌ها با AlarmManager. */
class TaskAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    /** آلارم یک‌باره‌ی تسک */
    fun schedule(taskId: Long, title: String, dueAt: Long) {
        if (dueAt <= System.currentTimeMillis()) return
        val code = taskCode(taskId)
        setAlarm(code, dueAt, alarmIntent(code, title, repeatMs = 0L, triggerAt = dueAt))
    }

    fun cancel(taskId: Long) = cancelCode(taskCode(taskId))

    /** آلارم هفتگی روتین برای یک روز هفته؛ بعد از هر بار اجرا، هفته‌ی بعد دوباره ثبت می‌شود. */
    fun scheduleRoutine(routineId: Long, day: DayOfWeek, title: String, triggerAt: Long) {
        val code = routineCode(routineId, day)
        setAlarm(code, triggerAt, alarmIntent(code, title, WEEK_MS, triggerAt))
    }

    fun cancelRoutine(routineId: Long) {
        DayOfWeek.values().forEach { cancelCode(routineCode(routineId, it)) }
    }

    /** فراخوانی از AlarmReceiver برای ثبت نوبت بعدی یک آلارم تکرارشونده */
    fun rescheduleRepeating(code: Int, title: String, triggerAt: Long, repeatMs: Long) {
        setAlarm(code, triggerAt, alarmIntent(code, title, repeatMs, triggerAt))
    }

    private fun setAlarm(code: Int, triggerAt: Long, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // کاربر اجازه‌ی آلارم دقیق نداده؛ آلارم تقریبی زمان‌بندی می‌شود
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm not permitted, falling back", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelCode(code: Int) {
        val existing = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun alarmIntent(code: Int, title: String, repeatMs: Long, triggerAt: Long): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, code)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_REPEAT_MS, repeatMs)
            putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
        }

    companion object {
        private const val TAG = "TaskAlarmScheduler"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        private const val ROUTINE_FLAG = 0x40000000

        /** کد درخواست تسک‌ها (بیت ۳۰ همیشه صفر است تا با روتین‌ها تداخل نکند) */
        fun taskCode(taskId: Long): Int = (taskId and 0x3FFFFFFF).toInt()

        /** کد درخواست روتین: بیت ۳۰ یک، و هر روز هفته سه بیت پایین */
        fun routineCode(routineId: Long, day: DayOfWeek): Int =
            ROUTINE_FLAG or (((routineId and 0x3FFFF).toInt()) shl 3) or day.value
    }
}

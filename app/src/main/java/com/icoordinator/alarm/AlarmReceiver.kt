package com.icoordinator.alarm

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.icoordinator.ICoordinatorApp
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** وقتی آلارم می‌رسد، نوتیفیکیشن نشان می‌دهد و اگر روتین هنوز پایان نگرفته، نوبت بعد را ثبت می‌کند. */
class AlarmReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val repeatMs = intent.getLongExtra(EXTRA_REPEAT_MS, 0L)
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
        val endAt = intent.getLongExtra(EXTRA_END_AT, 0L)
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)
        val offsetMs = intent.getLongExtra(EXTRA_OFFSET_MS, 0L)

        val appContext = context.applicationContext
        createChannel(appContext)

        if (routineId <= 0L) {
            notifyIfAllowed(appContext, code, title)
            rearm(appContext, code, title, triggerAt, repeatMs, endAt, routineId, offsetMs)
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val occurrenceDate = WeekUtils.toLocalDateTime(triggerAt + offsetMs).toLocalDate()
                val d = occurrenceDate.toEpochDay()
                val doneToday = try {
                    ICoordinatorApp.instance.database.routineDao()
                        .observeCompletions(d, d + 1).first()
                        .any { it.routineId == routineId }
                } catch (e: Exception) {
                    false
                }
                if (!doneToday) notifyIfAllowed(appContext, code, title)
            } finally {
                try {
                    rearm(appContext, code, title, triggerAt, repeatMs, endAt, routineId, offsetMs)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(context: Context, code: Int, title: String) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("یادآوری")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(code, notification)
    }

    private fun rearm(
        context: Context,
        code: Int,
        title: String,
        triggerAt: Long,
        repeatMs: Long,
        endAt: Long,
        routineId: Long,
        offsetMs: Long,
    ) {
        if (repeatMs <= 0) return
        val next = triggerAt + repeatMs
        if (endAt == 0L || next + offsetMs < endAt) {
            TaskAlarmScheduler(context).rescheduleRepeating(code, title, next, repeatMs, endAt, routineId, offsetMs)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_TASKS,
            "یادآور تسک‌ها و روتین‌ها",
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_REPEAT_MS = "repeat_ms"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_END_AT = "end_at"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_OFFSET_MS = "offset_ms"
        const val CHANNEL_TASKS = "tasks"
    }
}

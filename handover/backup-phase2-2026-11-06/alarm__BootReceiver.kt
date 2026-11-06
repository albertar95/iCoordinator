package com.icoordinator.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.icoordinator.ICoordinatorApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** بعد از روشن شدن گوشی یا آپدیت اپ، آلارم‌ها دوباره ثبت می‌شوند (AlarmManager بعد از ری‌استارت پاک می‌شود). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ICoordinatorApp.instance.alarmRescheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

package com.icoordinator.accounting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.icoordinator.ICoordinatorApp
import com.icoordinator.alarm.AlarmReceiver
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * One reminder per open loan (D67): 09:00 local time on the due date of its next unpaid installment.
 * Reuses [AlarmReceiver] (one-shot: repeat 0, routine 0) and never touches task or routine request codes.
 */
object InstallmentReminders {
    private const val TAG = "InstallmentReminders"
    private const val PREFS_NAME = "accounting_reminders"
    private const val KEY_CODES = "codes"
    private const val REMIND_HOUR = 9

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    /** Called once from ICoordinatorApp.onCreate; a second call does nothing. */
    fun start(app: ICoordinatorApp) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                val repo = app.accountingRepository
                combine(repo.observeLoans(), repo.observeInstallments()) { loans, installments ->
                    // M4: per open loan, the first unpaid installment whose reminder time is still ahead (overdue ones are skipped)
                    val now = System.currentTimeMillis()
                    val byLoan = installments.filter { !it.isPaid }.groupBy { it.loanId }
                    loans.filter { !it.isClosed }.mapNotNull { loan ->
                        val upcoming = byLoan[loan.id].orEmpty()
                            .sortedWith(compareBy({ it.dueEpochDay }, { it.seq }))
                            .firstOrNull { triggerMillis(it.dueEpochDay) > now }
                        upcoming?.let { loan to it }
                    }
                }
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) {
                            false
                        } else {
                            Log.w(TAG, "installment flow failed, retrying", cause)
                            delay(minOf(60_000L, 5_000L * (attempt + 1)))
                            true
                        }
                    }
                    .collect { targets ->
                        try {
                            applyTargets(app.applicationContext, targets)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "updating installment reminders failed", e)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "installment reminders stopped", e)
            }
        }
    }

    /** Request code of an installment alarm: bit 31 set, disjoint from task codes and routine codes. */
    fun requestCode(installmentId: Long): Int = Int.MIN_VALUE or (installmentId and 0x3FFFFFFF).toInt()

    private fun applyTargets(context: Context, targets: List<Pair<LoanEntity, InstallmentEntity>>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val wanted = HashSet<Int>()
        for ((loan, installment) in targets) {
            val triggerAt = triggerMillis(installment.dueEpochDay)
            if (triggerAt <= now) continue
            val code = requestCode(installment.id)
            try {
                val title = "موعد قسط " + loan.title + " - " + MoneyFormat.format(installment.amount) + " تومان"
                setAlarm(context, alarmManager, code, triggerAt, alarmIntent(context, code, title, triggerAt))
                wanted.add(code)
            } catch (e: Exception) {
                Log.w(TAG, "could not schedule reminder for installment " + installment.id, e)
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        for (text in previous) {
            val code = text.toIntOrNull() ?: continue
            if (code in wanted) continue
            try {
                cancelCode(context, alarmManager, code)
            } catch (e: Exception) {
                Log.w(TAG, "could not cancel reminder $code", e)
            }
        }
        prefs.edit().putStringSet(KEY_CODES, wanted.map { it.toString() }.toSet()).apply()
    }

    /** 09:00 local time on the due day, in epoch millis. */
    private fun triggerMillis(dueEpochDay: Long): Long =
        LocalDate.ofEpochDay(dueEpochDay)
            .atTime(REMIND_HOUR, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun alarmIntent(context: Context, code: Int, title: String, triggerAt: Long): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, code)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_REPEAT_MS, 0L)
            putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
            putExtra(AlarmReceiver.EXTRA_END_AT, 0L)
            putExtra(AlarmReceiver.EXTRA_ROUTINE_ID, 0L)
            putExtra(AlarmReceiver.EXTRA_OFFSET_MS, 0L)
        }

    /** Same exact / inexact choice as TaskAlarmScheduler.setAlarm. */
    private fun setAlarm(context: Context, alarmManager: AlarmManager, code: Int, triggerAt: Long, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm not permitted, falling back", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelCode(context: Context, alarmManager: AlarmManager, code: Int) {
        val existing = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }
}

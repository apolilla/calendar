package com.apolilla.calendar.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.apolilla.calendar.domain.TimeProvider
import com.apolilla.calendar.domain.repository.ReminderScheduler
import com.apolilla.calendar.domain.repository.TaskRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps exactly one alarm armed: the earliest pending reminder. When it fires,
 * [ReminderAlarmReceiver] shows what is due and calls [syncAlarms] again. Using a single
 * alarm avoids the per-app alarm limit and makes cancellation trivial: whenever tasks
 * change the alarm is simply recomputed from the database.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val repository: TaskRepository,
    private val notifier: TaskNotifier,
    private val time: TimeProvider,
) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    override suspend fun syncAlarms() = mutex.withLock {
        val next = repository.getPendingReminders().minOfOrNull { it.triggerAtMillis }
        val pendingIntent = alarmIntent()
        if (next == null) {
            alarmManager.cancel(pendingIntent)
            return@withLock
        }
        val at = maxOf(next, time.nowMillis() + 1_000)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                // Exact alarms not allowed: Android may deliver a few minutes late.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
    }

    override fun cancelShownNotifications(taskId: Long) = notifier.cancelForTask(taskId)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1001
    }
}

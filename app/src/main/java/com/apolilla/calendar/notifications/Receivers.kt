package com.apolilla.calendar.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apolilla.calendar.CalendarApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private fun BroadcastReceiver.runAsync(context: Context, block: suspend (CalendarApp) -> Unit) {
    val app = context.applicationContext as CalendarApp
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

/** Fired by the single scheduled alarm. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        runAsync(context) { it.container.reminderProcessor.run() }
    }

    companion object {
        const val ACTION_FIRE = "com.apolilla.calendar.action.FIRE_REMINDERS"
    }
}

/**
 * Alarms do not survive a reboot, an app update, or (semantically) a time zone change.
 * This receiver re-arms them; reminders missed while the device was off are shown if
 * their task has not started yet.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED ->
                runAsync(context) { it.container.reminderProcessor.recomputeAndRun() }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> runAsync(context) { it.container.reminderProcessor.run() }
        }
    }
}

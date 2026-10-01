package com.apolilla.calendar.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.apolilla.calendar.MainActivity
import com.apolilla.calendar.R
import com.apolilla.calendar.domain.reminder.PendingReminder
import com.apolilla.calendar.domain.reminder.ReminderOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Posts and cancels task reminder notifications. */
class TaskNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notification_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    fun show(reminders: List<PendingReminder>) {
        if (reminders.isEmpty() || !canPost()) return
        ensureChannel()
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        reminders.forEach { reminder ->
            val text = context.getString(
                R.string.notification_text,
                reminder.eventStart.toLocalDate().format(dateFormat),
                reminder.eventStart.toLocalTime().format(timeFormat),
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(reminder.taskName)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openTaskIntent(reminder.taskId))
                .build()
            try {
                manager.notify(notificationId(reminder.taskId, reminder.offset), notification)
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call.
            }
        }
    }

    fun cancelForTask(taskId: Long) {
        ReminderOffset.entries.forEach { manager.cancel(notificationId(taskId, it)) }
    }

    private fun openTaskIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TASK)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"

        /** Stable per (task, offset): re-posting replaces instead of duplicating. */
        fun notificationId(taskId: Long, offset: ReminderOffset): Int =
            (taskId * ReminderOffset.entries.size + offset.ordinal).toInt()
    }
}

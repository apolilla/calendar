package com.apolilla.calendar.notifications

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.apolilla.calendar.CalendarApp
import com.apolilla.calendar.domain.model.Reminder
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.reminder.ReminderOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Exercises the real notification path: due reminder -> processor -> posted notification. */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {

    @get:Rule
    val permission: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private val app = ApplicationProvider.getApplicationContext<CalendarApp>()
    private val container = app.container
    private val notificationManager = app.getSystemService(NotificationManager::class.java)

    private fun posted(taskId: Long, offset: ReminderOffset) =
        notificationManager.activeNotifications.any { it.id == TaskNotifier.notificationId(taskId, offset) }

    @Test
    fun dueReminder_isPostedOnce_andCancelledWhenTaskDeleted() = runBlocking {
        val start = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0)
        val id = container.repository.insertTasks(
            listOf(
                Task(
                    name = "Notification test",
                    date = start.toLocalDate(),
                    time = start.toLocalTime(),
                    reminders = listOf(
                        // Due one minute ago (e.g. the alarm just fired).
                        Reminder(offset = ReminderOffset.HOURS_5, triggerAtMillis = System.currentTimeMillis() - 60_000),
                        Reminder(offset = ReminderOffset.HOURS_1, triggerAtMillis = System.currentTimeMillis() + 3_600_000),
                    ),
                ),
            ),
            null,
        ).single()

        container.reminderProcessor.run()
        assertTrue(posted(id, ReminderOffset.HOURS_5))
        assertFalse(posted(id, ReminderOffset.HOURS_1))

        // Running again (e.g. a reboot) must not produce a duplicate.
        notificationManager.cancel(TaskNotifier.notificationId(id, ReminderOffset.HOURS_5))
        container.reminderProcessor.run()
        assertFalse(posted(id, ReminderOffset.HOURS_5))
        assertEquals(1, container.repository.getPendingReminders().count { it.taskId == id })

        // Re-post, then delete: the shown notification and pending reminders go away.
        container.notifier.show(listOf(container.repository.getPendingReminders().first { it.taskId == id }))
        assertTrue(posted(id, ReminderOffset.HOURS_1))
        container.taskService.delete(id)
        assertFalse(posted(id, ReminderOffset.HOURS_1))
        assertTrue(container.repository.getPendingReminders().none { it.taskId == id })
    }
}

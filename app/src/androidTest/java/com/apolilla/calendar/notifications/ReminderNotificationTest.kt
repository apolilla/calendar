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

    /** Notifications are enqueued asynchronously by the system: poll until [expected] or timeout. */
    private fun awaitPosted(taskId: Long, offset: ReminderOffset, expected: Boolean, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (posted(taskId, offset) == expected) return true
            Thread.sleep(100)
        }
        return posted(taskId, offset) == expected
    }

    private fun diagnostics() = "canPost=${container.notifier.canPost()} " +
        "enabled=${notificationManager.areNotificationsEnabled()} " +
        "active=${notificationManager.activeNotifications.map { it.id }}"

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
        assertTrue("due reminder not shown: ${diagnostics()}", awaitPosted(id, ReminderOffset.HOURS_5, true))
        assertFalse(posted(id, ReminderOffset.HOURS_1))

        // Running again (e.g. a reboot) must not produce a duplicate.
        notificationManager.cancel(TaskNotifier.notificationId(id, ReminderOffset.HOURS_5))
        assertTrue(awaitPosted(id, ReminderOffset.HOURS_5, false))
        container.reminderProcessor.run()
        Thread.sleep(1_000)
        assertFalse("reminder shown twice", posted(id, ReminderOffset.HOURS_5))
        assertEquals(1, container.repository.getPendingReminders().count { it.taskId == id })

        // Re-post, then delete: the shown notification and pending reminders go away.
        container.notifier.show(listOf(container.repository.getPendingReminders().first { it.taskId == id }))
        assertTrue("re-posted reminder not shown: ${diagnostics()}", awaitPosted(id, ReminderOffset.HOURS_1, true))
        container.taskService.delete(id)
        assertTrue("notification not cancelled on delete", awaitPosted(id, ReminderOffset.HOURS_1, false))
        assertTrue(container.repository.getPendingReminders().none { it.taskId == id })
    }
}

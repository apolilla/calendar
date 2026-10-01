package com.apolilla.calendar.notifications

import com.apolilla.calendar.domain.TaskService
import com.apolilla.calendar.domain.repository.ReminderScheduler

/** Shows due reminders and re-arms the alarm. Shared by every trigger (alarm, boot, app start). */
class ReminderProcessor(
    private val service: TaskService,
    private val notifier: TaskNotifier,
    private val scheduler: ReminderScheduler,
) {
    suspend fun run() {
        val plan = service.processDueReminders()
        notifier.show(plan.toNotify)
        scheduler.syncAlarms()
    }

    /** After a time zone change local task times map to new instants. */
    suspend fun recomputeAndRun() {
        service.recomputeReminders()
        run()
    }
}

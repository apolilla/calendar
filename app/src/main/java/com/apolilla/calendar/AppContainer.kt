package com.apolilla.calendar

import android.content.Context
import com.apolilla.calendar.data.RoomTaskRepository
import com.apolilla.calendar.data.db.AppDatabase
import com.apolilla.calendar.domain.SystemTimeProvider
import com.apolilla.calendar.domain.TaskService
import com.apolilla.calendar.domain.TimeProvider
import com.apolilla.calendar.domain.repository.TaskRepository
import com.apolilla.calendar.files.AttachmentManager
import com.apolilla.calendar.files.PhotoStore
import com.apolilla.calendar.notifications.AlarmReminderScheduler
import com.apolilla.calendar.notifications.ReminderProcessor
import com.apolilla.calendar.notifications.TaskNotifier

/** Manual dependency container: small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val time: TimeProvider = SystemTimeProvider
    val database: AppDatabase = AppDatabase.build(appContext)
    val repository: TaskRepository = RoomTaskRepository(database)
    val photoStore = PhotoStore(appContext)
    val attachmentManager = AttachmentManager(appContext, photoStore)
    val notifier = TaskNotifier(appContext)
    val scheduler = AlarmReminderScheduler(appContext, repository, notifier, time)
    val taskService = TaskService(repository, scheduler, attachmentManager, time)
    val reminderProcessor = ReminderProcessor(taskService, notifier, scheduler)
}

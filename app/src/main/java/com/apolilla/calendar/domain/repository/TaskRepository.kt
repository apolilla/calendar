package com.apolilla.calendar.domain.repository

import com.apolilla.calendar.domain.model.Reminder
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.reminder.PendingReminder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/** Definition of a recurrence series stored alongside its materialized instances. */
data class SeriesDefinition(
    val rule: RecurrenceRule,
    val startDateTime: LocalDateTime,
    val endDate: LocalDate?,
    val instanceCount: Int,
)

/** Persistence boundary of the app. All lists are returned in chronological order. */
interface TaskRepository {
    fun observeTasksBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Task>>
    fun observeTask(id: Long): Flow<Task?>
    suspend fun getTask(id: Long): Task?

    /**
     * Inserts tasks atomically. When [series] is not null a series row is created and every
     * task is linked to it; the task's [Task.series] supplies index and original date-time.
     */
    suspend fun insertTasks(tasks: List<Task>, series: SeriesDefinition?): List<Long>

    /** Replaces a task's fields, attachments and reminders. */
    suspend fun updateTask(task: Task)

    /** Deletes one task (instance) and returns it, or null if it did not exist. */
    suspend fun deleteTask(id: Long): Task?

    /** Deletes every instance of a series and returns them. */
    suspend fun deleteSeries(seriesId: Long): List<Task>

    suspend fun getPendingReminders(): List<PendingReminder>
    suspend fun markRemindersFired(reminderIds: List<Long>, firedAtMillis: Long)
    suspend fun getTasksWithPendingReminders(): List<Task>
    suspend fun replaceReminders(taskId: Long, reminders: List<Reminder>)

    /** Number of stored attachments pointing at [uri] (shared between series instances). */
    suspend fun countAttachmentsWithUri(uri: String): Int
}

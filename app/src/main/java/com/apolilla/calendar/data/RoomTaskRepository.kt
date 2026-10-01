package com.apolilla.calendar.data

import androidx.room.withTransaction
import com.apolilla.calendar.data.db.AppDatabase
import com.apolilla.calendar.domain.model.Reminder
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.reminder.PendingReminder
import com.apolilla.calendar.domain.repository.SeriesDefinition
import com.apolilla.calendar.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Room-backed [TaskRepository]. Multi-table writes run in a single transaction. */
class RoomTaskRepository(private val db: AppDatabase) : TaskRepository {
    private val dao = db.taskDao()

    override fun observeTasksBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Task>> =
        dao.observeBetween(start.toEpochDay(), endInclusive.toEpochDay())
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeTask(id: Long): Flow<Task?> =
        dao.observe(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getTask(id: Long): Task? = dao.get(id)?.toDomain()

    override suspend fun insertTasks(tasks: List<Task>, series: SeriesDefinition?): List<Long> =
        db.withTransaction {
            val seriesId = series?.let { dao.insertSeries(it.toEntity()) }
            tasks.map { task ->
                val id = dao.insertTask(task.copy(id = 0).toEntity(seriesId))
                if (task.attachments.isNotEmpty()) dao.insertAttachments(task.attachments.map { it.toEntity(id) })
                if (task.reminders.isNotEmpty()) dao.insertReminders(task.reminders.map { it.toEntity(id) })
                id
            }
        }

    override suspend fun updateTask(task: Task) {
        db.withTransaction {
            val current = dao.get(task.id) ?: throw NoSuchElementException("Task ${task.id} not found")
            dao.updateTask(task.toEntity(current.task.seriesId))
            dao.deleteAttachmentsOf(task.id)
            dao.deleteRemindersOf(task.id)
            if (task.attachments.isNotEmpty()) dao.insertAttachments(task.attachments.map { it.toEntity(task.id) })
            if (task.reminders.isNotEmpty()) dao.insertReminders(task.reminders.map { it.toEntity(task.id) })
        }
    }

    override suspend fun deleteTask(id: Long): Task? = db.withTransaction {
        val existing = dao.get(id)?.toDomain() ?: return@withTransaction null
        dao.deleteTask(id) // attachments and reminders cascade
        existing
    }

    override suspend fun deleteSeries(seriesId: Long): List<Task> = db.withTransaction {
        val instances = dao.getSeriesInstances(seriesId).map { it.toDomain() }
        dao.deleteTasksOfSeries(seriesId)
        dao.deleteSeries(seriesId)
        instances
    }

    override suspend fun getPendingReminders(): List<PendingReminder> =
        dao.getPendingReminders().mapNotNull { it.toDomain() }

    override suspend fun markRemindersFired(reminderIds: List<Long>, firedAtMillis: Long) {
        // SQLite limits bound parameters; chunk to stay well below it.
        db.withTransaction {
            reminderIds.chunked(500).forEach { dao.markFired(it, firedAtMillis) }
        }
    }

    override suspend fun getTasksWithPendingReminders(): List<Task> =
        dao.getWithPendingReminders().map { it.toDomain() }

    override suspend fun replaceReminders(taskId: Long, reminders: List<Reminder>) {
        db.withTransaction {
            dao.deleteRemindersOf(taskId)
            if (reminders.isNotEmpty()) dao.insertReminders(reminders.map { it.toEntity(taskId) })
        }
    }

    override suspend fun countAttachmentsWithUri(uri: String): Int = dao.countAttachmentsWithUri(uri)
}

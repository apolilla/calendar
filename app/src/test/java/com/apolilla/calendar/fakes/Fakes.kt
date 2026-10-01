package com.apolilla.calendar.fakes

import com.apolilla.calendar.domain.TimeProvider
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.Reminder
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.model.TaskOrdering
import com.apolilla.calendar.domain.reminder.PendingReminder
import com.apolilla.calendar.domain.repository.AttachmentCleaner
import com.apolilla.calendar.domain.repository.ReminderScheduler
import com.apolilla.calendar.domain.repository.SeriesDefinition
import com.apolilla.calendar.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** In-memory repository mirroring the Room implementation's contract. */
class FakeTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    private var nextTaskId = 1L
    private var nextSeriesId = 1L
    private var nextChildId = 1L

    val all: List<Task> get() = tasks.value.values.sortedWith(TaskOrdering.chronological)

    override fun observeTasksBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Task>> =
        tasks.map { map ->
            map.values.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }
                .sortedWith(TaskOrdering.chronological)
        }

    override fun observeTask(id: Long): Flow<Task?> = tasks.map { it[id] }

    override suspend fun getTask(id: Long): Task? = tasks.value[id]

    override suspend fun insertTasks(tasks: List<Task>, series: SeriesDefinition?): List<Long> {
        val seriesId = series?.let { nextSeriesId++ }
        val ids = ArrayList<Long>()
        val updated = this.tasks.value.toMutableMap()
        tasks.forEach { task ->
            val id = nextTaskId++
            ids += id
            updated[id] = withChildIds(
                task.copy(id = id, series = task.series?.copy(seriesId = seriesId ?: 0)),
            )
        }
        this.tasks.value = updated
        return ids
    }

    override suspend fun updateTask(task: Task) {
        require(tasks.value.containsKey(task.id))
        tasks.value = tasks.value + (task.id to withChildIds(task))
    }

    override suspend fun deleteTask(id: Long): Task? {
        val existing = tasks.value[id] ?: return null
        tasks.value = tasks.value - id
        return existing
    }

    override suspend fun deleteSeries(seriesId: Long): List<Task> {
        val removed = tasks.value.values.filter { it.series?.seriesId == seriesId }
        tasks.value = tasks.value - removed.map { it.id }.toSet()
        return removed
    }

    override suspend fun getPendingReminders(): List<PendingReminder> =
        tasks.value.values.flatMap { task ->
            task.reminders.filter { it.firedAtMillis == null }.map {
                PendingReminder(it.id, task.id, task.name, task.dateTime, it.offset, it.triggerAtMillis)
            }
        }

    override suspend fun markRemindersFired(reminderIds: List<Long>, firedAtMillis: Long) {
        val ids = reminderIds.toSet()
        tasks.value = tasks.value.mapValues { (_, task) ->
            task.copy(reminders = task.reminders.map {
                if (it.id in ids) it.copy(firedAtMillis = firedAtMillis) else it
            })
        }
    }

    override suspend fun getTasksWithPendingReminders(): List<Task> =
        all.filter { task -> task.reminders.any { it.firedAtMillis == null } }

    override suspend fun replaceReminders(taskId: Long, reminders: List<Reminder>) {
        val task = tasks.value.getValue(taskId)
        tasks.value = tasks.value + (taskId to withChildIds(task.copy(reminders = reminders)))
    }

    override suspend fun countAttachmentsWithUri(uri: String): Int =
        tasks.value.values.sumOf { task -> task.attachments.count { it.uri == uri } }

    private fun withChildIds(task: Task) = task.copy(
        attachments = task.attachments.map { if (it.id == 0L) it.copy(id = nextChildId++) else it },
        reminders = task.reminders.map { it.copy(id = nextChildId++) },
    )
}

class RecordingScheduler : ReminderScheduler {
    var syncCount = 0
    val cancelled = mutableListOf<Long>()
    override suspend fun syncAlarms() { syncCount++ }
    override fun cancelShownNotifications(taskId: Long) { cancelled += taskId }
}

class RecordingCleaner : AttachmentCleaner {
    val released = mutableListOf<Attachment>()
    override suspend fun release(attachment: Attachment) { released += attachment }
}

class FixedTime(var now: Long, private val zoneId: ZoneId = ZoneId.of("Europe/Madrid")) : TimeProvider {
    override fun nowMillis(): Long = now
    override fun zone(): ZoneId = zoneId
}

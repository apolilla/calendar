package com.apolilla.calendar.domain

import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.SeriesRef
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrenceEngine
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.reminder.DispatchPlan
import com.apolilla.calendar.domain.reminder.ReminderOffset
import com.apolilla.calendar.domain.reminder.ReminderPlanner
import com.apolilla.calendar.domain.repository.AttachmentCleaner
import com.apolilla.calendar.domain.repository.ReminderScheduler
import com.apolilla.calendar.domain.repository.SeriesDefinition
import com.apolilla.calendar.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the task form submits. [recurrence] is only honoured when creating. */
data class TaskDraft(
    val name: String,
    val date: LocalDate,
    val time: LocalTime,
    val color: TaskColor = TaskColor.DEFAULT,
    val description: String = "",
    val attachments: List<Attachment> = emptyList(),
    val reminderOffsets: Set<ReminderOffset> = ReminderOffset.DEFAULTS,
    val recurrence: RecurrenceRule? = null,
    val recurrenceEnd: LocalDate? = null,
)

enum class ValidationError { NAME_REQUIRED, END_DATE_BEFORE_START }

class ValidationException(val errors: Set<ValidationError>) :
    IllegalArgumentException("Invalid task: $errors")

class TaskNotFoundException(id: Long) : NoSuchElementException("Task $id not found")

data class CreateResult(val taskIds: List<Long>, val truncated: Boolean)

/**
 * Business logic for tasks. UI and Android components call this; it coordinates the
 * repository, recurrence generation, reminder planning and side effects.
 */
class TaskService(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val cleaner: AttachmentCleaner,
    private val time: TimeProvider,
) {

    fun validate(draft: TaskDraft): Set<ValidationError> = buildSet {
        if (draft.name.isBlank()) add(ValidationError.NAME_REQUIRED)
        if (draft.recurrence != null && draft.recurrenceEnd != null &&
            draft.recurrenceEnd.isBefore(draft.date)
        ) {
            add(ValidationError.END_DATE_BEFORE_START)
        }
    }

    /** Number of instances a draft would create (shown to the user before saving). */
    fun previewGeneration(draft: TaskDraft): RecurrenceEngine.Result? {
        val rule = draft.recurrence ?: return null
        return RecurrenceEngine.generate(LocalDateTime.of(draft.date, draft.time), rule, draft.recurrenceEnd)
    }

    suspend fun create(draft: TaskDraft): CreateResult {
        val errors = validate(draft)
        if (errors.isNotEmpty()) throw ValidationException(errors)

        val start = LocalDateTime.of(draft.date, draft.time)
        val rule = draft.recurrence
        val generation = rule?.let { RecurrenceEngine.generate(start, it, draft.recurrenceEnd) }
        val occurrences = generation?.occurrences ?: listOf(start)
        val now = time.nowMillis()
        val zone = time.zone()
        val name = draft.name.trim()

        val tasks = occurrences.mapIndexed { index, dateTime ->
            Task(
                name = name,
                date = dateTime.toLocalDate(),
                time = dateTime.toLocalTime(),
                color = draft.color,
                description = draft.description.trim(),
                attachments = draft.attachments.map { it.copy(id = 0) },
                reminders = ReminderPlanner.plan(dateTime, draft.reminderOffsets, emptyList(), now, zone),
                series = rule?.let {
                    SeriesRef(
                        seriesId = 0,
                        occurrenceIndex = index,
                        originalDateTime = dateTime,
                        rule = it,
                        endDate = draft.recurrenceEnd,
                        instanceCount = occurrences.size,
                    )
                },
            )
        }
        val series = rule?.let { SeriesDefinition(it, start, draft.recurrenceEnd, occurrences.size) }
        val ids = repository.insertTasks(tasks, series)
        scheduler.syncAlarms()
        return CreateResult(ids, generation?.truncated ?: false)
    }

    /** Edits one task. For a recurring instance only that instance changes. */
    suspend fun update(id: Long, draft: TaskDraft): Task {
        val errors = validate(draft.copy(recurrence = null))
        if (errors.isNotEmpty()) throw ValidationException(errors)
        val existing = repository.getTask(id) ?: throw TaskNotFoundException(id)

        val start = LocalDateTime.of(draft.date, draft.time)
        val updated = existing.copy(
            name = draft.name.trim(),
            date = draft.date,
            time = draft.time,
            color = draft.color,
            description = draft.description.trim(),
            attachments = draft.attachments,
            reminders = ReminderPlanner.plan(
                start, draft.reminderOffsets, existing.reminders, time.nowMillis(), time.zone(),
            ),
        )
        repository.updateTask(updated)

        val keptIds = draft.attachments.map { it.id }.filter { it != 0L }.toSet()
        releaseUnreferenced(existing.attachments.filter { it.id !in keptIds })

        if (existing.dateTime != start || existing.reminders.map { it.offset }.toSet() != draft.reminderOffsets) {
            scheduler.cancelShownNotifications(id)
        }
        scheduler.syncAlarms()
        return updated
    }

    suspend fun delete(id: Long) {
        val deleted = repository.deleteTask(id) ?: return
        scheduler.cancelShownNotifications(id)
        releaseUnreferenced(deleted.attachments)
        scheduler.syncAlarms()
    }

    suspend fun deleteSeries(seriesId: Long) {
        val deleted = repository.deleteSeries(seriesId)
        deleted.forEach { scheduler.cancelShownNotifications(it.id) }
        releaseUnreferenced(deleted.flatMap { it.attachments })
        scheduler.syncAlarms()
    }

    /**
     * Releases an attachment added in the form but never saved (form cancelled or the
     * attachment removed before saving). Safe to call for attachments that are stored.
     */
    suspend fun discardDraftAttachment(attachment: Attachment) {
        releaseUnreferenced(listOf(attachment))
    }

    /** Recomputes trigger times, e.g. after a time zone change. */
    suspend fun recomputeReminders() {
        val now = time.nowMillis()
        val zone = time.zone()
        repository.getTasksWithPendingReminders().forEach { task ->
            val offsets = task.reminders.map { it.offset }.toSet()
            repository.replaceReminders(
                task.id,
                ReminderPlanner.plan(task.dateTime, offsets, task.reminders, now, zone),
            )
        }
        scheduler.syncAlarms()
    }

    /**
     * Marks due reminders as handled and returns what must be shown and when the next
     * alarm must fire. Called by the alarm receiver, on boot and on app start.
     */
    suspend fun processDueReminders(): DispatchPlan {
        val now = time.nowMillis()
        val plan = ReminderPlanner.dispatch(repository.getPendingReminders(), now, time.zone())
        val handled = (plan.toNotify + plan.toDiscard).map { it.reminderId }
        if (handled.isNotEmpty()) repository.markRemindersFired(handled, now)
        return plan
    }

    private suspend fun releaseUnreferenced(attachments: List<Attachment>) {
        attachments.distinctBy { it.uri }.forEach { attachment ->
            if (repository.countAttachmentsWithUri(attachment.uri) == 0) {
                runCatching { cleaner.release(attachment) }
            }
        }
    }
}

package com.apolilla.calendar.domain.model

import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.reminder.ReminderOffset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single calendar task. Recurring tasks are materialized: every occurrence is its own
 * [Task] with its own [id]; [series] links it back to the recurrence that generated it.
 * Names are intentionally not unique.
 */
data class Task(
    val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val time: LocalTime,
    val color: TaskColor = TaskColor.DEFAULT,
    val description: String = "",
    val attachments: List<Attachment> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val series: SeriesRef? = null,
) {
    val dateTime: LocalDateTime get() = LocalDateTime.of(date, time)
}

enum class AttachmentKind { FILE, PHOTO }

/** A file associated with a task. [uri] is a content:// URI (SAF document or app FileProvider). */
data class Attachment(
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val kind: AttachmentKind,
)

/** One notification of a task. [firedAtMillis] is set once it was shown (or skipped). */
data class Reminder(
    val id: Long = 0,
    val offset: ReminderOffset,
    val triggerAtMillis: Long,
    val firedAtMillis: Long? = null,
)

/**
 * Identifies a materialized recurring instance: which series it belongs to, its position
 * in that series and the date-time it was originally generated for (before any edit).
 */
data class SeriesRef(
    val seriesId: Long,
    val occurrenceIndex: Int,
    val originalDateTime: LocalDateTime,
    val rule: RecurrenceRule,
    val endDate: LocalDate?,
    val instanceCount: Int,
)

package com.apolilla.calendar.data

import com.apolilla.calendar.data.db.AttachmentEntity
import com.apolilla.calendar.data.db.PendingReminderRow
import com.apolilla.calendar.data.db.ReminderEntity
import com.apolilla.calendar.data.db.SeriesEntity
import com.apolilla.calendar.data.db.TaskEntity
import com.apolilla.calendar.data.db.TaskWithDetails
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.AttachmentKind
import com.apolilla.calendar.domain.model.Reminder
import com.apolilla.calendar.domain.model.SeriesRef
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrencePreset
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.recurrence.RecurrenceUnit
import com.apolilla.calendar.domain.reminder.PendingReminder
import com.apolilla.calendar.domain.reminder.ReminderOffset
import com.apolilla.calendar.domain.repository.SeriesDefinition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
internal fun minuteOfDayToTime(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)

internal fun Task.toEntity(seriesId: Long?): TaskEntity = TaskEntity(
    id = id,
    name = name,
    epochDay = date.toEpochDay(),
    minuteOfDay = time.toMinuteOfDay(),
    color = color.name,
    description = description,
    seriesId = seriesId,
    occurrenceIndex = series?.occurrenceIndex,
    originalDateTime = series?.originalDateTime?.toString(),
)

internal fun Attachment.toEntity(taskId: Long) = AttachmentEntity(
    taskId = taskId,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    kind = kind.name,
)

internal fun Reminder.toEntity(taskId: Long) = ReminderEntity(
    taskId = taskId,
    offsetName = offset.name,
    triggerAt = triggerAtMillis,
    firedAt = firedAtMillis,
)

internal fun SeriesDefinition.toEntity() = SeriesEntity(
    preset = rule.preset.name,
    repeatInterval = rule.interval,
    repeatUnit = rule.unit.name,
    startDateTime = startDateTime.toString(),
    endEpochDay = endDate?.toEpochDay(),
    instanceCount = instanceCount,
)

internal fun SeriesEntity.toRule(): RecurrenceRule = RecurrenceRule(
    preset = RecurrencePreset.entries.firstOrNull { it.name == preset } ?: RecurrencePreset.CUSTOM,
    interval = repeatInterval.coerceAtLeast(1),
    unit = RecurrenceUnit.entries.firstOrNull { it.name == repeatUnit } ?: RecurrenceUnit.DAY,
)

internal fun TaskWithDetails.toDomain(): Task {
    val date = LocalDate.ofEpochDay(task.epochDay)
    val time = minuteOfDayToTime(task.minuteOfDay)
    val seriesRef = series?.let { s ->
        SeriesRef(
            seriesId = s.id,
            occurrenceIndex = task.occurrenceIndex ?: 0,
            originalDateTime = task.originalDateTime?.let(LocalDateTime::parse) ?: LocalDateTime.of(date, time),
            rule = s.toRule(),
            endDate = s.endEpochDay?.let(LocalDate::ofEpochDay),
            instanceCount = s.instanceCount,
        )
    }
    return Task(
        id = task.id,
        name = task.name,
        date = date,
        time = time,
        color = TaskColor.fromName(task.color),
        description = task.description,
        attachments = attachments.sortedBy { it.id }.map {
            Attachment(
                id = it.id,
                uri = it.uri,
                displayName = it.displayName,
                mimeType = it.mimeType,
                kind = AttachmentKind.entries.firstOrNull { k -> k.name == it.kind } ?: AttachmentKind.FILE,
            )
        },
        reminders = reminders.mapNotNull { r ->
            ReminderOffset.fromName(r.offsetName)?.let {
                Reminder(id = r.id, offset = it, triggerAtMillis = r.triggerAt, firedAtMillis = r.firedAt)
            }
        }.sortedBy { it.offset.ordinal },
        series = seriesRef,
    )
}

internal fun PendingReminderRow.toDomain(): PendingReminder? {
    val offset = ReminderOffset.fromName(offsetName) ?: return null
    return PendingReminder(
        reminderId = reminderId,
        taskId = taskId,
        taskName = taskName,
        eventStart = LocalDateTime.of(LocalDate.ofEpochDay(epochDay), minuteOfDayToTime(minuteOfDay)),
        offset = offset,
        triggerAtMillis = triggerAt,
    )
}

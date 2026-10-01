package com.apolilla.calendar.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Definition of a recurrence; instances live in [TaskEntity] rows linked by seriesId. */
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preset: String,
    val repeatInterval: Int,
    val repeatUnit: String,
    /** ISO-8601 local date-time of the first occurrence. */
    val startDateTime: String,
    /** Inclusive end date as epoch day, or null when the default horizon was used. */
    val endEpochDay: Long?,
    val instanceCount: Int,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("epochDay", "minuteOfDay"), Index("seriesId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    val color: String,
    val description: String,
    val seriesId: Long?,
    val occurrenceIndex: Int?,
    /** ISO-8601 local date-time this instance was generated for (recurring only). */
    val originalDateTime: String?,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index("uri")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val kind: String,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["taskId", "offsetName"], unique = true), Index("firedAt")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val offsetName: String,
    val triggerAt: Long,
    val firedAt: Long?,
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val reminders: List<ReminderEntity>,
    @Relation(parentColumn = "seriesId", entityColumn = "id")
    val series: SeriesEntity?,
)

data class PendingReminderRow(
    val reminderId: Long,
    val taskId: Long,
    val taskName: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    val offsetName: String,
    val triggerAt: Long,
)

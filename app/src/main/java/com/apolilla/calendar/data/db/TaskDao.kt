package com.apolilla.calendar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Transaction
    @Query("SELECT * FROM tasks WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay, minuteOfDay, id")
    fun observeBetween(startDay: Long, endDay: Long): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskWithDetails?

    @Transaction
    @Query("SELECT * FROM tasks WHERE seriesId = :seriesId ORDER BY epochDay, minuteOfDay, id")
    suspend fun getSeriesInstances(seriesId: Long): List<TaskWithDetails>

    @Transaction
    @Query(
        "SELECT * FROM tasks WHERE id IN (SELECT DISTINCT taskId FROM reminders WHERE firedAt IS NULL) " +
            "ORDER BY epochDay, minuteOfDay, id",
    )
    suspend fun getWithPendingReminders(): List<TaskWithDetails>

    @Insert
    suspend fun insertSeries(series: SeriesEntity): Long

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity): Int

    @Insert
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Insert
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM attachments WHERE taskId = :taskId")
    suspend fun deleteAttachmentsOf(taskId: Long)

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun deleteRemindersOf(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long): Int

    @Query("DELETE FROM tasks WHERE seriesId = :seriesId")
    suspend fun deleteTasksOfSeries(seriesId: Long): Int

    @Query("DELETE FROM series WHERE id = :seriesId")
    suspend fun deleteSeries(seriesId: Long)

    @Query(
        "SELECT r.id AS reminderId, r.taskId AS taskId, t.name AS taskName, t.epochDay AS epochDay, " +
            "t.minuteOfDay AS minuteOfDay, r.offsetName AS offsetName, r.triggerAt AS triggerAt " +
            "FROM reminders r INNER JOIN tasks t ON t.id = r.taskId WHERE r.firedAt IS NULL",
    )
    suspend fun getPendingReminders(): List<PendingReminderRow>

    @Query("UPDATE reminders SET firedAt = :firedAt WHERE id IN (:ids)")
    suspend fun markFired(ids: List<Long>, firedAt: Long)

    @Query("SELECT COUNT(*) FROM attachments WHERE uri = :uri")
    suspend fun countAttachmentsWithUri(uri: String): Int
}

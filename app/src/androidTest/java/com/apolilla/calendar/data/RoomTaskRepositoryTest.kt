package com.apolilla.calendar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apolilla.calendar.data.db.AppDatabase
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.AttachmentKind
import com.apolilla.calendar.domain.model.Reminder
import com.apolilla.calendar.domain.model.SeriesRef
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.reminder.ReminderOffset
import com.apolilla.calendar.domain.repository.SeriesDefinition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomTaskRepository
    private val day = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = RoomTaskRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun task(name: String, time: LocalTime, date: LocalDate = day) = Task(
        name = name, date = date, time = time, color = TaskColor.GREEN, description = "d",
        attachments = listOf(Attachment(uri = "content://x/$name", displayName = name, mimeType = "text/plain", kind = AttachmentKind.FILE)),
        reminders = listOf(Reminder(offset = ReminderOffset.HOURS_1, triggerAtMillis = 1000L)),
    )

    @Test
    fun insertAndObserve_sortedChronologically_withDuplicatesAndSameTime() = runBlocking {
        repository.insertTasks(listOf(task("B", LocalTime.of(15, 0))), null)
        repository.insertTasks(listOf(task("A", LocalTime.of(9, 0))), null)
        repository.insertTasks(listOf(task("A", LocalTime.of(9, 0))), null)
        repository.insertTasks(listOf(task("Other day", LocalTime.of(1, 0), day.plusDays(1))), null)
        val tasks = repository.observeTasksBetween(day, day).first()
        assertEquals(listOf("A", "A", "B"), tasks.map { it.name })
        assertTrue(tasks[0].id < tasks[1].id)
        assertEquals(TaskColor.GREEN, tasks[0].color)
        assertEquals(1, tasks[0].attachments.size)
        assertEquals(ReminderOffset.HOURS_1, tasks[0].reminders.single().offset)
    }

    @Test
    fun update_replacesChildren() = runBlocking {
        val id = repository.insertTasks(listOf(task("A", LocalTime.of(9, 0))), null).single()
        val stored = repository.getTask(id)!!
        repository.updateTask(
            stored.copy(
                name = "A2", time = LocalTime.of(11, 30), attachments = emptyList(),
                reminders = listOf(
                    Reminder(offset = ReminderOffset.DAYS_1, triggerAtMillis = 5L),
                    Reminder(offset = ReminderOffset.WEEKS_1, triggerAtMillis = 6L, firedAtMillis = 7L),
                ),
            ),
        )
        val updated = repository.getTask(id)!!
        assertEquals("A2", updated.name)
        assertEquals(LocalTime.of(11, 30), updated.time)
        assertTrue(updated.attachments.isEmpty())
        assertEquals(listOf(ReminderOffset.DAYS_1, ReminderOffset.WEEKS_1), updated.reminders.map { it.offset })
        assertEquals(1, repository.getPendingReminders().size)
    }

    @Test
    fun delete_cascadesToAttachmentsAndReminders() = runBlocking {
        val id = repository.insertTasks(listOf(task("A", LocalTime.of(9, 0))), null).single()
        assertEquals(1, repository.countAttachmentsWithUri("content://x/A"))
        val deleted = repository.deleteTask(id)
        assertNotNull(deleted)
        assertNull(repository.getTask(id))
        assertEquals(0, repository.countAttachmentsWithUri("content://x/A"))
        assertTrue(repository.getPendingReminders().isEmpty())
        assertNull(repository.deleteTask(id))
    }

    @Test
    fun series_roundTripAndDelete() = runBlocking {
        val rule = RecurrenceRule.weekly()
        val start = LocalDateTime.of(2027, 1, 3, 10, 0)
        val instances = (0 until 3).map { i ->
            val dt = start.plusWeeks(i.toLong())
            task("Gym", dt.toLocalTime(), dt.toLocalDate()).copy(
                series = SeriesRef(0, i, dt, rule, LocalDate.of(2027, 1, 17), 3),
            )
        }
        val ids = repository.insertTasks(instances, SeriesDefinition(rule, start, LocalDate.of(2027, 1, 17), 3))
        val second = repository.getTask(ids[1])!!
        val series = second.series!!
        assertEquals(1, series.occurrenceIndex)
        assertEquals(rule, series.rule)
        assertEquals(LocalDate.of(2027, 1, 17), series.endDate)
        assertEquals(3, series.instanceCount)
        assertEquals(LocalDateTime.of(2027, 1, 10, 10, 0), series.originalDateTime)

        // Editing one instance keeps the series link and leaves the others untouched.
        repository.updateTask(second.copy(name = "Moved", date = second.date.plusDays(1)))
        assertEquals("Gym", repository.getTask(ids[0])!!.name)
        assertEquals(series.seriesId, repository.getTask(ids[1])!!.series!!.seriesId)

        val removed = repository.deleteSeries(series.seriesId)
        assertEquals(3, removed.size)
        assertTrue(ids.all { repository.getTask(it) == null })
    }

    @Test
    fun pendingReminders_joinTaskData_andMarkFired() = runBlocking {
        val id = repository.insertTasks(listOf(task("A", LocalTime.of(9, 0))), null).single()
        val pending = repository.getPendingReminders().single()
        assertEquals(id, pending.taskId)
        assertEquals("A", pending.taskName)
        assertEquals(LocalDateTime.of(day, LocalTime.of(9, 0)), pending.eventStart)
        repository.markRemindersFired(listOf(pending.reminderId), 42L)
        assertTrue(repository.getPendingReminders().isEmpty())
        assertEquals(42L, repository.getTask(id)!!.reminders.single().firedAtMillis)
    }

    @Test
    fun data_survivesClosingAndReopeningTheDatabaseFile() = runBlocking {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val first = AppDatabase.build(context, name)
        val id = RoomTaskRepository(first).insertTasks(listOf(task("Persisted", LocalTime.of(8, 15))), null).single()
        first.close()

        val reopened = AppDatabase.build(context, name)
        val restored = RoomTaskRepository(reopened).getTask(id)
        reopened.close()
        context.deleteDatabase(name)
        assertEquals("Persisted", restored!!.name)
        assertEquals(LocalTime.of(8, 15), restored.time)
    }
}

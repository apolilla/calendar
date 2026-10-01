package com.apolilla.calendar.domain

import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.AttachmentKind
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.recurrence.RecurrenceUnit
import com.apolilla.calendar.domain.reminder.ReminderOffset
import com.apolilla.calendar.fakes.FakeTaskRepository
import com.apolilla.calendar.fakes.FixedTime
import com.apolilla.calendar.fakes.RecordingCleaner
import com.apolilla.calendar.fakes.RecordingScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskServiceTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val repository = FakeTaskRepository()
    private val scheduler = RecordingScheduler()
    private val cleaner = RecordingCleaner()
    private val time = FixedTime(LocalDateTime.of(2026, 10, 1, 9, 0).atZone(zone).toInstant().toEpochMilli(), zone)
    private val service = TaskService(repository, scheduler, cleaner, time)

    private val day = LocalDate.of(2026, 10, 20)
    private fun draft(name: String = "Gym", date: LocalDate = day, time: LocalTime = LocalTime.of(10, 0)) =
        TaskDraft(name = name, date = date, time = time)

    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun create_singleTask_persistsWithDefaults() = runBlocking {
        val result = service.create(draft())
        val task = repository.getTask(result.taskIds.single())!!
        assertEquals("Gym", task.name)
        assertEquals(day, task.date)
        assertEquals(LocalTime.of(10, 0), task.time)
        assertEquals(TaskColor.BLUE, task.color)
        assertNull(task.series)
        assertEquals(listOf(ReminderOffset.DAYS_2), task.reminders.map { it.offset })
        assertEquals(1, scheduler.syncCount)
    }

    @Test
    fun create_blankName_isRejected() = runBlocking {
        try {
            service.create(draft(name = "   "))
            fail("Expected validation error")
        } catch (e: ValidationException) {
            assertEquals(setOf(ValidationError.NAME_REQUIRED), e.errors)
        }
        assertTrue(repository.all.isEmpty())
    }

    @Test
    fun create_endDateBeforeStart_isRejected() = runBlocking {
        try {
            service.create(draft().copy(recurrence = RecurrenceRule.daily(), recurrenceEnd = day.minusDays(1)))
            fail("Expected validation error")
        } catch (e: ValidationException) {
            assertEquals(setOf(ValidationError.END_DATE_BEFORE_START), e.errors)
        }
    }

    @Test
    fun duplicateNamesAndSimultaneousTasks_areAllKeptAndSorted() = runBlocking {
        service.create(draft(name = "Meeting", time = LocalTime.of(15, 0)))
        service.create(draft(name = "Meeting", time = LocalTime.of(9, 30)))
        service.create(draft(name = "Meeting", time = LocalTime.of(9, 30)))
        service.create(draft(name = "Call", time = LocalTime.of(9, 30)))
        val tasks = repository.observeTasksBetween(day, day).first()
        assertEquals(4, tasks.size)
        assertEquals(4, tasks.map { it.id }.toSet().size)
        assertEquals(listOf(9, 9, 9, 15), tasks.map { it.time.hour })
        assertEquals(3, tasks.count { it.name == "Meeting" })
        // Same time: creation order is preserved.
        assertTrue(tasks[0].id < tasks[1].id && tasks[1].id < tasks[2].id)
    }

    @Test
    fun pastTasks_remainStored() = runBlocking {
        service.create(draft(date = LocalDate.of(2020, 1, 1)))
        val past = repository.observeTasksBetween(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1)).first()
        assertEquals(1, past.size)
        // Its reminder lies in the past: it is skipped, not pending.
        assertTrue(repository.getPendingReminders().isEmpty())
    }

    @Test
    fun recurrence_materializesIndependentInstances() = runBlocking {
        val result = service.create(
            draft(date = LocalDate.of(2027, 1, 3)).copy(
                recurrence = RecurrenceRule.weekly(), recurrenceEnd = LocalDate.of(2027, 1, 24),
            ),
        )
        assertEquals(4, result.taskIds.size)
        assertFalse(result.truncated)
        val instances = result.taskIds.map { repository.getTask(it)!! }
        assertEquals(listOf(3, 10, 17, 24), instances.map { it.date.dayOfMonth })
        val seriesId = instances.first().series!!.seriesId
        assertNotEquals(0L, seriesId)
        assertTrue(instances.all { it.series!!.seriesId == seriesId && it.series!!.instanceCount == 4 })
        assertEquals(listOf(0, 1, 2, 3), instances.map { it.series!!.occurrenceIndex })

        // Editing the second instance changes only that one.
        val second = instances[1]
        service.update(
            second.id,
            TaskDraft(
                name = "Gym (moved)", date = second.date.plusDays(1), time = LocalTime.of(18, 0),
                color = TaskColor.RED, description = "legs", reminderOffsets = setOf(ReminderOffset.HOURS_1),
            ),
        )
        val after = result.taskIds.map { repository.getTask(it)!! }
        assertEquals("Gym (moved)", after[1].name)
        assertEquals(LocalDate.of(2027, 1, 11), after[1].date)
        assertEquals(TaskColor.RED, after[1].color)
        assertEquals(seriesId, after[1].series!!.seriesId)
        assertEquals(LocalDateTime.of(2027, 1, 10, 10, 0), after[1].series!!.originalDateTime)
        listOf(0, 2, 3).forEach { i ->
            assertEquals(instances[i].copy(reminders = after[i].reminders), after[i])
            assertEquals("Gym", after[i].name)
        }
    }

    @Test
    fun recurrence_endDateIsInclusive() = runBlocking {
        val result = service.create(
            draft(date = LocalDate.of(2026, 10, 1)).copy(
                recurrence = RecurrenceRule.custom(3, RecurrenceUnit.DAY), recurrenceEnd = LocalDate.of(2026, 10, 10),
            ),
        )
        val dates = result.taskIds.map { repository.getTask(it)!!.date.dayOfMonth }
        assertEquals(listOf(1, 4, 7, 10), dates)
    }

    @Test
    fun recurrence_attachmentsAreSharedByUriAndReleasedWithLastInstance() = runBlocking {
        val photo = Attachment(uri = "content://app/photos/p1.jpg", displayName = "p1.jpg", mimeType = "image/jpeg", kind = AttachmentKind.PHOTO)
        val result = service.create(
            draft().copy(attachments = listOf(photo), recurrence = RecurrenceRule.daily(), recurrenceEnd = day.plusDays(1)),
        )
        assertEquals(2, repository.countAttachmentsWithUri(photo.uri))
        service.delete(result.taskIds[0])
        assertTrue(cleaner.released.isEmpty())
        service.delete(result.taskIds[1])
        assertEquals(listOf(photo.uri), cleaner.released.map { it.uri })
    }

    @Test
    fun update_removingAttachmentReleasesIt() = runBlocking {
        val file = Attachment(uri = "content://docs/1", displayName = "a.pdf", mimeType = "application/pdf", kind = AttachmentKind.FILE)
        val id = service.create(draft().copy(attachments = listOf(file))).taskIds.single()
        val stored = repository.getTask(id)!!
        assertEquals(1, stored.attachments.size)
        service.update(id, draft().copy(attachments = emptyList()))
        assertTrue(repository.getTask(id)!!.attachments.isEmpty())
        assertEquals(listOf(file.uri), cleaner.released.map { it.uri })
    }

    @Test
    fun update_dateOrTimeChange_reschedulesReminders() = runBlocking {
        val id = service.create(
            draft().copy(reminderOffsets = setOf(ReminderOffset.HOURS_1, ReminderOffset.DAYS_1)),
        ).taskIds.single()
        val before = repository.getTask(id)!!.reminders.associate { it.offset to it.triggerAtMillis }
        assertEquals(millis(LocalDateTime.of(2026, 10, 20, 9, 0)), before[ReminderOffset.HOURS_1])

        service.update(id, draft(date = day.plusDays(2), time = LocalTime.of(16, 0)).copy(
            reminderOffsets = setOf(ReminderOffset.HOURS_1, ReminderOffset.DAYS_1),
        ))
        val after = repository.getTask(id)!!.reminders.associate { it.offset to it.triggerAtMillis }
        assertEquals(millis(LocalDateTime.of(2026, 10, 22, 15, 0)), after[ReminderOffset.HOURS_1])
        assertEquals(millis(LocalDateTime.of(2026, 10, 21, 16, 0)), after[ReminderOffset.DAYS_1])
        assertEquals(2, repository.getPendingReminders().size)
        assertEquals(listOf(id), scheduler.cancelled)
        assertEquals(2, scheduler.syncCount)
    }

    @Test
    fun update_unrelatedField_doesNotCancelShownNotification() = runBlocking {
        val id = service.create(draft()).taskIds.single()
        service.update(id, draft().copy(description = "new text"))
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun delete_removesTaskAndItsPendingReminders() = runBlocking {
        val id = service.create(draft()).taskIds.single()
        assertEquals(1, repository.getPendingReminders().size)
        service.delete(id)
        assertNull(repository.getTask(id))
        assertTrue(repository.getPendingReminders().isEmpty())
        assertEquals(listOf(id), scheduler.cancelled)
    }

    @Test
    fun deleteSeries_removesAllInstances() = runBlocking {
        val ids = service.create(draft().copy(recurrence = RecurrenceRule.daily(), recurrenceEnd = day.plusDays(4))).taskIds
        service.create(draft(name = "Other"))
        val seriesId = repository.getTask(ids.first())!!.series!!.seriesId
        service.deleteSeries(seriesId)
        assertEquals(listOf("Other"), repository.all.map { it.name })
        assertEquals(ids, scheduler.cancelled)
    }

    @Test
    fun processDueReminders_firesOnceAndNeverDuplicates() = runBlocking {
        val id = service.create(
            draft(date = LocalDate.of(2026, 10, 3)).copy(reminderOffsets = setOf(ReminderOffset.DAYS_1, ReminderOffset.HOURS_1)),
        ).taskIds.single()
        time.now = millis(LocalDateTime.of(2026, 10, 2, 10, 0))
        val first = service.processDueReminders()
        assertEquals(listOf(id), first.toNotify.map { it.taskId })
        assertEquals(ReminderOffset.DAYS_1, first.toNotify.single().offset)
        assertEquals(millis(LocalDateTime.of(2026, 10, 3, 9, 0)), first.nextTriggerAtMillis)

        val again = service.processDueReminders()
        assertTrue(again.toNotify.isEmpty())

        // Editing an unrelated field keeps the fired state: no second notification.
        service.update(id, draft(date = LocalDate.of(2026, 10, 3)).copy(
            description = "x", reminderOffsets = setOf(ReminderOffset.DAYS_1, ReminderOffset.HOURS_1),
        ))
        assertTrue(service.processDueReminders().toNotify.isEmpty())
        assertEquals(1, repository.getPendingReminders().size)
    }

    @Test
    fun recomputeReminders_followsTimeZoneChange() = runBlocking {
        val id = service.create(draft()).taskIds.single()
        val utcService = TaskService(repository, scheduler, cleaner, FixedTime(time.now, ZoneId.of("UTC")))
        utcService.recomputeReminders()
        val trigger = repository.getTask(id)!!.reminders.single().triggerAtMillis
        assertEquals(LocalDateTime.of(2026, 10, 18, 10, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(), trigger)
    }

    @Test
    fun manyTasks_onOneDay() = runBlocking {
        repeat(30) { i -> service.create(draft(name = "T$i", time = LocalTime.of(23 - (i % 24), 0))) }
        val tasks = repository.observeTasksBetween(day, day).first()
        assertEquals(30, tasks.size)
        assertEquals(tasks.sortedBy { it.time }.map { it.time }, tasks.map { it.time })
    }

    @Test
    fun previewGeneration_reportsCountAndTruncation() {
        val p = service.previewGeneration(draft().copy(recurrence = RecurrenceRule.custom(2, RecurrenceUnit.HOUR)))
        assertNotNull(p)
        assertTrue(p!!.truncated)
        assertNull(service.previewGeneration(draft()))
    }
}

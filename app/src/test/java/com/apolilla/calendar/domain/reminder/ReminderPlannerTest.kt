package com.apolilla.calendar.domain.reminder

import com.apolilla.calendar.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private val event = LocalDateTime.of(2026, 10, 20, 10, 0)
    private val now = millis(LocalDateTime.of(2026, 10, 1, 9, 0))

    @Test
    fun offsets_computeExpectedTriggerTimes() {
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), ReminderOffset.HOURS_1.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 10, 20, 8, 0), ReminderOffset.HOURS_2.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 10, 20, 5, 0), ReminderOffset.HOURS_5.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0), ReminderOffset.DAYS_1.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 10, 18, 10, 0), ReminderOffset.DAYS_2.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 0), ReminderOffset.WEEKS_1.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 10, 6, 10, 0), ReminderOffset.WEEKS_2.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 9, 20, 10, 0), ReminderOffset.MONTHS_1.triggerFor(event))
        assertEquals(LocalDateTime.of(2026, 2, 28, 8, 0), ReminderOffset.MONTHS_1.triggerFor(LocalDateTime.of(2026, 3, 31, 8, 0)))
    }

    @Test
    fun defaultIsTwoDaysBefore() {
        assertEquals(setOf(ReminderOffset.DAYS_2), ReminderOffset.DEFAULTS)
    }

    @Test
    fun plan_multipleReminders_futureOnesPending_pastOnesSkipped() {
        val reminders = ReminderPlanner.plan(
            event, setOf(ReminderOffset.HOURS_1, ReminderOffset.DAYS_2, ReminderOffset.MONTHS_1), emptyList(), now, zone,
        )
        assertEquals(3, reminders.size)
        val byOffset = reminders.associateBy { it.offset }
        assertNull(byOffset.getValue(ReminderOffset.HOURS_1).firedAtMillis)
        assertNull(byOffset.getValue(ReminderOffset.DAYS_2).firedAtMillis)
        // One month before (20 Sep) is already past on 1 Oct: skipped, never fires late.
        assertEquals(now, byOffset.getValue(ReminderOffset.MONTHS_1).firedAtMillis)
        assertEquals(millis(LocalDateTime.of(2026, 10, 18, 10, 0)), byOffset.getValue(ReminderOffset.DAYS_2).triggerAtMillis)
    }

    @Test
    fun plan_keepsFiredStateWhenTriggerUnchanged() {
        val trigger = millis(ReminderOffset.DAYS_2.triggerFor(event))
        val previous = listOf(Reminder(offset = ReminderOffset.DAYS_2, triggerAtMillis = trigger, firedAtMillis = 123L))
        val replanned = ReminderPlanner.plan(event, setOf(ReminderOffset.DAYS_2), previous, now, zone)
        assertEquals(123L, replanned.single().firedAtMillis)
    }

    @Test
    fun plan_rearmsWhenTimeChanged() {
        val trigger = millis(ReminderOffset.DAYS_2.triggerFor(event))
        val previous = listOf(Reminder(offset = ReminderOffset.DAYS_2, triggerAtMillis = trigger, firedAtMillis = 123L))
        val moved = event.plusHours(3)
        val replanned = ReminderPlanner.plan(moved, setOf(ReminderOffset.DAYS_2), previous, now, zone).single()
        assertNull(replanned.firedAtMillis)
        assertEquals(millis(moved.minusDays(2)), replanned.triggerAtMillis)
    }

    private fun pending(id: Long, taskId: Long, offset: ReminderOffset, start: LocalDateTime) =
        PendingReminder(id, taskId, "t$taskId", start, offset, millis(offset.triggerFor(start)))

    @Test
    fun dispatch_notifiesDueAndComputesNextAlarm() {
        val start = LocalDateTime.of(2026, 10, 3, 10, 0)
        val p1 = pending(1, 1, ReminderOffset.DAYS_2, start) // due 1 Oct 10:00
        val p2 = pending(2, 1, ReminderOffset.HOURS_1, start) // 3 Oct 09:00
        val p3 = pending(3, 2, ReminderOffset.DAYS_1, LocalDateTime.of(2026, 10, 5, 8, 0))
        val at = millis(LocalDateTime.of(2026, 10, 1, 10, 0))
        val plan = ReminderPlanner.dispatch(listOf(p1, p2, p3), at, zone)
        assertEquals(listOf(p1), plan.toNotify)
        assertTrue(plan.toDiscard.isEmpty())
        assertEquals(p2.triggerAtMillis, plan.nextTriggerAtMillis)
    }

    @Test
    fun dispatch_afterDowntime_showsOnlyLatestPerTask() {
        val start = LocalDateTime.of(2026, 10, 3, 10, 0)
        val twoDays = pending(1, 1, ReminderOffset.DAYS_2, start)
        val oneDay = pending(2, 1, ReminderOffset.DAYS_1, start)
        val plan = ReminderPlanner.dispatch(listOf(twoDays, oneDay), millis(LocalDateTime.of(2026, 10, 2, 18, 0)), zone)
        assertEquals(listOf(oneDay), plan.toNotify)
        assertEquals(listOf(twoDays), plan.toDiscard)
        assertNull(plan.nextTriggerAtMillis)
    }

    @Test
    fun dispatch_discardsRemindersOfTasksThatAlreadyStarted() {
        val start = LocalDateTime.of(2026, 10, 1, 8, 0)
        val stale = pending(1, 1, ReminderOffset.HOURS_1, start)
        val plan = ReminderPlanner.dispatch(listOf(stale), millis(LocalDateTime.of(2026, 10, 1, 9, 0)), zone)
        assertTrue(plan.toNotify.isEmpty())
        assertEquals(listOf(stale), plan.toDiscard)
    }

    @Test
    fun dispatch_nothingPending() {
        val plan = ReminderPlanner.dispatch(emptyList(), now, zone)
        assertTrue(plan.toNotify.isEmpty())
        assertNull(plan.nextTriggerAtMillis)
    }
}

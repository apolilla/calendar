package com.apolilla.calendar.domain.reminder

import com.apolilla.calendar.domain.model.Reminder
import java.time.LocalDateTime
import java.time.ZoneId

/** An unfired reminder joined with the task data needed to show it. */
data class PendingReminder(
    val reminderId: Long,
    val taskId: Long,
    val taskName: String,
    val eventStart: LocalDateTime,
    val offset: ReminderOffset,
    val triggerAtMillis: Long,
)

data class DispatchPlan(
    /** Reminders to show now (at most one per task: the most recent one). */
    val toNotify: List<PendingReminder>,
    /** Due reminders that must not be shown (task already started, or superseded). */
    val toDiscard: List<PendingReminder>,
    /** When the next alarm should fire, or null if nothing is pending. */
    val nextTriggerAtMillis: Long?,
)

/**
 * Pure scheduling logic. The Android layer only stores the result and sets one alarm
 * for [DispatchPlan.nextTriggerAtMillis]; this keeps the app far below the system's
 * per-app alarm limit no matter how many recurring instances exist.
 */
object ReminderPlanner {

    /**
     * Computes the reminders of a task starting at [eventStart].
     *
     * - A reminder that already fired keeps its fired state while its trigger time is
     *   unchanged, so editing other fields never re-notifies (no duplicates).
     * - A reminder whose trigger time is already in the past when planned is marked as
     *   fired immediately: it is skipped instead of firing late.
     */
    fun plan(
        eventStart: LocalDateTime,
        offsets: Set<ReminderOffset>,
        previous: List<Reminder>,
        nowMillis: Long,
        zone: ZoneId,
    ): List<Reminder> = offsets.sortedBy { it.ordinal }.map { offset ->
        val trigger = offset.triggerFor(eventStart).atZone(zone).toInstant().toEpochMilli()
        val old = previous.firstOrNull { it.offset == offset }
        val fired = when {
            old != null && old.triggerAtMillis == trigger && old.firedAtMillis != null -> old.firedAtMillis
            trigger <= nowMillis -> nowMillis
            else -> null
        }
        Reminder(offset = offset, triggerAtMillis = trigger, firedAtMillis = fired)
    }

    /**
     * Decides what to do with the unfired reminders at [nowMillis] (alarm fired, device
     * booted, app started...). Reminders missed while the device was off are still shown
     * if their task has not started yet; when several reminders of the same task are due
     * at once only the latest one is shown.
     */
    fun dispatch(pending: List<PendingReminder>, nowMillis: Long, zone: ZoneId): DispatchPlan {
        val (due, future) = pending.partition { it.triggerAtMillis <= nowMillis }
        val notify = ArrayList<PendingReminder>()
        val discard = ArrayList<PendingReminder>()
        due.groupBy { it.taskId }.values.forEach { forTask ->
            val stillUpcoming = forTask.filter {
                it.eventStart.atZone(zone).toInstant().toEpochMilli() > nowMillis
            }
            val latest = stillUpcoming.maxByOrNull { it.triggerAtMillis }
            if (latest != null) notify += latest
            discard += forTask.filter { it !== latest }
        }
        return DispatchPlan(
            toNotify = notify.sortedBy { it.triggerAtMillis },
            toDiscard = discard,
            nextTriggerAtMillis = future.minOfOrNull { it.triggerAtMillis },
        )
    }
}

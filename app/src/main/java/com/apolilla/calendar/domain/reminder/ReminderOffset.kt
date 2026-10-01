package com.apolilla.calendar.domain.reminder

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** How long before the task start a notification is shown. Persisted by name. */
enum class ReminderOffset(val amount: Long, val unit: ChronoUnit) {
    HOURS_1(1, ChronoUnit.HOURS),
    HOURS_2(2, ChronoUnit.HOURS),
    HOURS_5(5, ChronoUnit.HOURS),
    DAYS_1(1, ChronoUnit.DAYS),
    DAYS_2(2, ChronoUnit.DAYS),
    WEEKS_1(1, ChronoUnit.WEEKS),
    WEEKS_2(2, ChronoUnit.WEEKS),
    MONTHS_1(1, ChronoUnit.MONTHS);

    /** Local date-time at which the notification fires (calendar-aware for months). */
    fun triggerFor(eventStart: LocalDateTime): LocalDateTime = eventStart.minus(amount, unit)

    companion object {
        /** "2 days before" is enabled by default for new tasks. */
        val DEFAULTS: Set<ReminderOffset> = setOf(DAYS_2)

        fun fromName(name: String): ReminderOffset? = entries.firstOrNull { it.name == name }
    }
}

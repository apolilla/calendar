package com.apolilla.calendar.domain.recurrence

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Generates the occurrences of a recurrence.
 *
 * Rules (documented in the README):
 * - Occurrence `n` is computed from the start (`start + n * interval`), never from the
 *   previous occurrence, so month-end clamping never drifts.
 * - Monthly on a day the month lacks (e.g. the 31st) falls on that month's last day
 *   (Jan 31 -> Feb 28/29 -> Mar 31 -> Apr 30 ...). Every month gets exactly one occurrence.
 * - Yearly on February 29 falls on February 28 in non-leap years.
 * - Hourly recurrence uses wall-clock time and may cross into following days.
 * - The end date is inclusive: occurrences on the end date are generated, later ones are not.
 * - Without an end date a default horizon applies ([defaultHorizon]).
 * - At most [MAX_INSTANCES] instances are ever generated; the result says if it was capped.
 */
object RecurrenceEngine {
    const val MAX_INSTANCES = 1000
    const val DEFAULT_HORIZON_YEARS = 2L
    const val DEFAULT_HORIZON_YEARS_FOR_YEARLY = 20L

    data class Result(
        val occurrences: List<LocalDateTime>,
        /** True when generation stopped at [MAX_INSTANCES] before reaching the end date/horizon. */
        val truncated: Boolean,
        /** The last date (inclusive) that was considered. */
        val effectiveEndDate: LocalDate,
    )

    fun occurrenceAt(start: LocalDateTime, rule: RecurrenceRule, index: Int): LocalDateTime {
        val steps = index.toLong() * rule.interval
        return when (rule.unit) {
            RecurrenceUnit.HOUR -> start.plusHours(steps)
            RecurrenceUnit.DAY -> start.plusDays(steps)
            RecurrenceUnit.WEEK -> start.plusWeeks(steps)
            RecurrenceUnit.MONTH -> start.plusMonths(steps)
            RecurrenceUnit.YEAR -> start.plusYears(steps)
        }
    }

    fun defaultHorizon(start: LocalDate, rule: RecurrenceRule): LocalDate =
        if (rule.unit == RecurrenceUnit.YEAR) {
            start.plusYears(maxOf(DEFAULT_HORIZON_YEARS_FOR_YEARLY, rule.interval * 3L))
        } else {
            start.plusYears(DEFAULT_HORIZON_YEARS)
        }

    fun generate(
        start: LocalDateTime,
        rule: RecurrenceRule,
        endDate: LocalDate?,
        maxInstances: Int = MAX_INSTANCES,
    ): Result {
        require(maxInstances >= 1)
        val limit = endDate ?: defaultHorizon(start.toLocalDate(), rule)
        val result = ArrayList<LocalDateTime>()
        var truncated = false
        var index = 0
        while (true) {
            val occurrence = occurrenceAt(start, rule, index)
            if (occurrence.toLocalDate().isAfter(limit)) break
            if (result.size == maxInstances) {
                truncated = true
                break
            }
            result += occurrence
            index++
        }
        return Result(result, truncated, limit)
    }

    /** First [count] occurrences, used to let the user verify a rule while editing it. */
    fun preview(
        start: LocalDateTime,
        rule: RecurrenceRule,
        endDate: LocalDate?,
        count: Int = 3,
    ): List<LocalDateTime> = generate(start, rule, endDate, count).occurrences
}

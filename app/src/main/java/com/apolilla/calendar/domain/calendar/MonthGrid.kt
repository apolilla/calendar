package com.apolilla.calendar.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Builds the week rows of a month view. Weeks always start on [firstDayOfWeek] (Monday). */
object MonthGrid {

    fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<List<LocalDate>> {
        val (start, end) = visibleRange(month, firstDayOfWeek)
        val days = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .toList()
        return days.chunked(7)
    }

    /** First and last (inclusive) dates shown for [month], including adjacent-month padding. */
    fun visibleRange(
        month: YearMonth,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): Pair<LocalDate, LocalDate> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val lastDayOfWeek = firstDayOfWeek.plus(6)
        val trailing = (lastDayOfWeek.value - last.dayOfWeek.value + 7) % 7
        return first.minusDays(leading.toLong()) to last.plusDays(trailing.toLong())
    }

    fun weekDayHeaders(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<DayOfWeek> =
        (0L until 7L).map { firstDayOfWeek.plus(it) }
}

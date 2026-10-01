package com.apolilla.calendar.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceEngineTest {

    private fun dt(y: Int, m: Int, d: Int, h: Int = 10, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test
    fun daily_generatesEveryDayThroughInclusiveEndDate() {
        val result = RecurrenceEngine.generate(dt(2026, 1, 1), RecurrenceRule.daily(), LocalDate.of(2026, 1, 5))
        assertEquals((1..5).map { dt(2026, 1, it) }, result.occurrences)
        assertFalse(result.truncated)
    }

    @Test
    fun weekly_keepsSameDayOfWeek() {
        val start = dt(2026, 1, 3) // Saturday
        val result = RecurrenceEngine.generate(start, RecurrenceRule.weekly(), LocalDate.of(2026, 1, 31))
        assertEquals(listOf(3, 10, 17, 24, 31).map { dt(2026, 1, it) }, result.occurrences)
        assertTrue(result.occurrences.all { it.dayOfWeek == DayOfWeek.SATURDAY })
    }

    @Test
    fun monthly_onThe31st_clampsToLastDayWithoutDrifting() {
        val result = RecurrenceEngine.generate(dt(2026, 1, 31), RecurrenceRule.monthly(), LocalDate.of(2026, 6, 30))
        assertEquals(
            listOf(dt(2026, 1, 31), dt(2026, 2, 28), dt(2026, 3, 31), dt(2026, 4, 30), dt(2026, 5, 31), dt(2026, 6, 30)),
            result.occurrences,
        )
    }

    @Test
    fun monthly_keepsSameDayOfMonth() {
        val result = RecurrenceEngine.generate(dt(2026, 11, 15), RecurrenceRule.monthly(), LocalDate.of(2027, 2, 15))
        assertEquals(listOf(dt(2026, 11, 15), dt(2026, 12, 15), dt(2027, 1, 15), dt(2027, 2, 15)), result.occurrences)
    }

    @Test
    fun monthly_onThe30th_inLeapYearFebruaryUses29() {
        val result = RecurrenceEngine.generate(dt(2028, 1, 30), RecurrenceRule.monthly(), LocalDate.of(2028, 3, 31))
        assertEquals(listOf(dt(2028, 1, 30), dt(2028, 2, 29), dt(2028, 3, 30)), result.occurrences)
    }

    @Test
    fun yearly_onFebruary29_usesFebruary28InNonLeapYears() {
        val result = RecurrenceEngine.generate(dt(2024, 2, 29), RecurrenceRule.yearly(), LocalDate.of(2028, 12, 31))
        assertEquals(
            listOf(dt(2024, 2, 29), dt(2025, 2, 28), dt(2026, 2, 28), dt(2027, 2, 28), dt(2028, 2, 29)),
            result.occurrences,
        )
    }

    @Test
    fun yearly_keepsMonthAndDay() {
        val result = RecurrenceEngine.generate(dt(2026, 7, 14), RecurrenceRule.yearly(), LocalDate.of(2029, 7, 13))
        assertEquals(listOf(dt(2026, 7, 14), dt(2027, 7, 14), dt(2028, 7, 14)), result.occurrences)
    }

    @Test
    fun custom_everyTwoHours_crossesMidnight() {
        val result = RecurrenceEngine.generate(
            dt(2026, 3, 1, 20), RecurrenceRule.custom(2, RecurrenceUnit.HOUR), LocalDate.of(2026, 3, 2),
        )
        assertEquals(dt(2026, 3, 1, 20), result.occurrences.first())
        assertEquals(dt(2026, 3, 1, 22), result.occurrences[1])
        assertEquals(dt(2026, 3, 2, 0), result.occurrences[2])
        assertEquals(dt(2026, 3, 2, 22), result.occurrences.last())
        assertEquals(14, result.occurrences.size) // 20,22 on day 1 + 12 on day 2
    }

    @Test
    fun custom_multiHourInterval() {
        val preview = RecurrenceEngine.preview(dt(2026, 3, 1, 9), RecurrenceRule.custom(5, RecurrenceUnit.HOUR), null)
        assertEquals(listOf(dt(2026, 3, 1, 9), dt(2026, 3, 1, 14), dt(2026, 3, 1, 19)), preview)
    }

    @Test
    fun custom_everyThreeDays() {
        val preview = RecurrenceEngine.preview(dt(2026, 1, 30), RecurrenceRule.custom(3, RecurrenceUnit.DAY), null)
        assertEquals(listOf(dt(2026, 1, 30), dt(2026, 2, 2), dt(2026, 2, 5)), preview)
    }

    @Test
    fun custom_everyTwoWeeks() {
        val preview = RecurrenceEngine.preview(dt(2026, 1, 5), RecurrenceRule.custom(2, RecurrenceUnit.WEEK), null)
        assertEquals(listOf(dt(2026, 1, 5), dt(2026, 1, 19), dt(2026, 2, 2)), preview)
    }

    @Test
    fun custom_everyThreeMonths() {
        val preview = RecurrenceEngine.preview(dt(2026, 11, 30), RecurrenceRule.custom(3, RecurrenceUnit.MONTH), null)
        assertEquals(listOf(dt(2026, 11, 30), dt(2027, 2, 28), dt(2027, 5, 30)), preview)
    }

    @Test
    fun custom_everyTwoYears() {
        val preview = RecurrenceEngine.preview(dt(2026, 5, 1), RecurrenceRule.custom(2, RecurrenceUnit.YEAR), null)
        assertEquals(listOf(dt(2026, 5, 1), dt(2028, 5, 1), dt(2030, 5, 1)), preview)
    }

    @Test
    fun preview_isLimitedByEndDate() {
        val preview = RecurrenceEngine.preview(dt(2026, 1, 1), RecurrenceRule.weekly(), LocalDate.of(2026, 1, 8))
        assertEquals(listOf(dt(2026, 1, 1), dt(2026, 1, 8)), preview)
    }

    @Test
    fun endDate_noInstanceAfterIt() {
        val end = LocalDate.of(2026, 12, 31)
        val result = RecurrenceEngine.generate(dt(2026, 10, 2), RecurrenceRule.weekly(), end)
        assertTrue(result.occurrences.all { !it.toLocalDate().isAfter(end) })
        assertEquals(LocalDate.of(2026, 12, 25), result.occurrences.last().toLocalDate())
    }

    @Test
    fun endDate_equalToStart_givesSingleOccurrence() {
        val result = RecurrenceEngine.generate(dt(2026, 1, 1), RecurrenceRule.daily(), LocalDate.of(2026, 1, 1))
        assertEquals(listOf(dt(2026, 1, 1)), result.occurrences)
    }

    @Test
    fun endDate_beforeStart_givesNothing() {
        val result = RecurrenceEngine.generate(dt(2026, 1, 10), RecurrenceRule.daily(), LocalDate.of(2026, 1, 1))
        assertTrue(result.occurrences.isEmpty())
    }

    @Test
    fun noEndDate_usesDefaultHorizon() {
        val result = RecurrenceEngine.generate(dt(2026, 1, 1), RecurrenceRule.monthly(), null)
        assertEquals(LocalDate.of(2028, 1, 1), result.effectiveEndDate)
        assertEquals(25, result.occurrences.size)
        val yearly = RecurrenceEngine.generate(dt(2026, 1, 1), RecurrenceRule.yearly(), null)
        assertEquals(21, yearly.occurrences.size)
    }

    @Test
    fun generation_isCappedAndReportsTruncation() {
        val result = RecurrenceEngine.generate(dt(2026, 1, 1, 0), RecurrenceRule.custom(1, RecurrenceUnit.HOUR), null)
        assertEquals(RecurrenceEngine.MAX_INSTANCES, result.occurrences.size)
        assertTrue(result.truncated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveInterval_isRejected() {
        RecurrenceRule.custom(0, RecurrenceUnit.DAY)
    }
}

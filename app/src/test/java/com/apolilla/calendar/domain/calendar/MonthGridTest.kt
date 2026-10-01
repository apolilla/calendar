package com.apolilla.calendar.domain.calendar

import com.apolilla.calendar.domain.model.TaskColor.BLUE
import com.apolilla.calendar.domain.model.TaskColor.GREEN
import com.apolilla.calendar.domain.model.TaskColor.RED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun weeksStartOnMondayAndEndOnSunday() {
        listOf(YearMonth.of(2026, 10), YearMonth.of(2027, 2), YearMonth.of(2028, 2), YearMonth.of(1999, 12))
            .forEach { month ->
                val weeks = MonthGrid.weeks(month)
                weeks.forEach { week ->
                    assertEquals(7, week.size)
                    assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
                    assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
                }
                val days = weeks.flatten()
                assertTrue(days.contains(month.atDay(1)))
                assertTrue(days.contains(month.atEndOfMonth()))
            }
    }

    @Test
    fun october2026_startsWithPaddingFromSeptember() {
        // 1 Oct 2026 is a Thursday.
        val weeks = MonthGrid.weeks(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), weeks.last().last())
        assertEquals(5, weeks.size)
    }

    @Test
    fun february2027_fitsInFourWeeks() {
        // Starts on Monday, 28 days.
        val weeks = MonthGrid.weeks(YearMonth.of(2027, 2))
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2027, 2, 1), weeks.first().first())
    }

    @Test
    fun monthStartingOnSunday_needsSixWeeks() {
        // 1 Aug 2027 is a Sunday, 31 days.
        assertEquals(6, MonthGrid.weeks(YearMonth.of(2027, 8)).size)
    }

    @Test
    fun navigationAcrossYears() {
        assertEquals(YearMonth.of(2027, 1), YearMonth.of(2026, 12).plusMonths(1))
        assertEquals(YearMonth.of(2025, 12), YearMonth.of(2026, 1).minusMonths(1))
        val past = MonthGrid.weeks(YearMonth.of(1970, 1))
        assertEquals(LocalDate.of(1969, 12, 29), past.first().first())
    }

    @Test
    fun headersAreMondayFirst() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            MonthGrid.weekDayHeaders(),
        )
    }

    @Test
    fun indicators_oneDotPerTask() {
        assertEquals(listOf(BLUE, BLUE, BLUE), DayIndicators.of(listOf(BLUE, BLUE, BLUE)).dots)
        assertEquals(listOf(BLUE, RED), DayIndicators.of(listOf(BLUE, RED)).dots)
        val mixed = DayIndicators.of(listOf(BLUE, RED, BLUE))
        assertEquals(2, mixed.dots.count { it == BLUE })
        assertEquals(1, mixed.dots.count { it == RED })
        assertEquals(0, mixed.overflow)
    }

    @Test
    fun indicators_zeroTasks() {
        val none = DayIndicators.of(emptyList())
        assertTrue(none.dots.isEmpty())
        assertEquals(0, none.total)
    }

    @Test
    fun indicators_overflowKeepsTotal() {
        val colors = List(9) { if (it % 2 == 0) GREEN else RED }
        val indicators = DayIndicators.of(colors, maxDots = 5)
        assertEquals(4, indicators.dots.size)
        assertEquals(5, indicators.overflow)
        assertEquals(9, indicators.total)
        assertEquals(colors.take(4), indicators.dots)
        assertEquals(5, DayIndicators.of(List(5) { BLUE }, maxDots = 5).dots.size)
    }
}

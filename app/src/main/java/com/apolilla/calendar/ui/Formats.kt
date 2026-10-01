package com.apolilla.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.apolilla.calendar.R
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrencePreset
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.recurrence.RecurrenceUnit
import com.apolilla.calendar.domain.reminder.ReminderOffset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Locale-aware formatting helpers shared by screens. */
object Formats {
    private val locale: Locale get() = Locale.getDefault()

    fun monthTitle(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } +
            " " + month.year

    fun monthName(month: java.time.Month): String =
        month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    fun weekday(day: java.time.DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    fun longDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
            .replaceFirstChar { it.titlecase(locale) }

    fun mediumDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun time(time: LocalTime): String =
        time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    fun dateTime(dateTime: LocalDateTime): String =
        date(dateTime.toLocalDate()) + " · " + time(dateTime.toLocalTime())

    /** e.g. "Mon, 5 Jan 2026" — short but unambiguous. */
    fun date(date: LocalDate): String =
        weekday(date.dayOfWeek) + ", " + mediumDate(date)
}

@Composable
fun reminderLabel(offset: ReminderOffset): String = stringResource(
    when (offset) {
        ReminderOffset.HOURS_1 -> R.string.reminder_hours_1
        ReminderOffset.HOURS_2 -> R.string.reminder_hours_2
        ReminderOffset.HOURS_5 -> R.string.reminder_hours_5
        ReminderOffset.DAYS_1 -> R.string.reminder_days_1
        ReminderOffset.DAYS_2 -> R.string.reminder_days_2
        ReminderOffset.WEEKS_1 -> R.string.reminder_weeks_1
        ReminderOffset.WEEKS_2 -> R.string.reminder_weeks_2
        ReminderOffset.MONTHS_1 -> R.string.reminder_months_1
    },
)

@Composable
fun colorLabel(color: TaskColor): String = stringResource(
    when (color) {
        TaskColor.BLUE -> R.string.color_blue
        TaskColor.RED -> R.string.color_red
        TaskColor.GREEN -> R.string.color_green
        TaskColor.YELLOW -> R.string.color_yellow
        TaskColor.ORANGE -> R.string.color_orange
        TaskColor.PURPLE -> R.string.color_purple
        TaskColor.TEAL -> R.string.color_teal
        TaskColor.PINK -> R.string.color_pink
        TaskColor.GRAY -> R.string.color_gray
    },
)

@Composable
fun presetLabel(preset: RecurrencePreset?): String = stringResource(
    when (preset) {
        null -> R.string.recurrence_none
        RecurrencePreset.DAILY -> R.string.recurrence_daily
        RecurrencePreset.WEEKLY -> R.string.recurrence_weekly
        RecurrencePreset.MONTHLY -> R.string.recurrence_monthly
        RecurrencePreset.YEARLY -> R.string.recurrence_yearly
        RecurrencePreset.CUSTOM -> R.string.recurrence_custom
    },
)

@Composable
fun unitLabel(unit: RecurrenceUnit, count: Int): String {
    val res = when (unit) {
        RecurrenceUnit.HOUR -> R.plurals.unit_hour
        RecurrenceUnit.DAY -> R.plurals.unit_day
        RecurrenceUnit.WEEK -> R.plurals.unit_week
        RecurrenceUnit.MONTH -> R.plurals.unit_month
        RecurrenceUnit.YEAR -> R.plurals.unit_year
    }
    return androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(res, count)
}

/** "Every 2 weeks" style description of a rule. */
@Composable
fun ruleDescription(rule: RecurrenceRule): String =
    if (rule.preset != RecurrencePreset.CUSTOM) {
        presetLabel(rule.preset)
    } else {
        stringResource(R.string.recurrence_every_n, rule.interval, unitLabel(rule.unit, rule.interval))
    }

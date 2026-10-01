package com.apolilla.calendar.domain.recurrence

enum class RecurrenceUnit { HOUR, DAY, WEEK, MONTH, YEAR }

/** The option the user picked; presets are shortcuts for interval = 1. */
enum class RecurrencePreset { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** "Repeat every [interval] [unit]". A non-recurring task simply has no rule. */
data class RecurrenceRule(
    val preset: RecurrencePreset,
    val interval: Int,
    val unit: RecurrenceUnit,
) {
    init {
        require(interval >= 1) { "Recurrence interval must be a positive integer" }
    }

    companion object {
        fun daily() = RecurrenceRule(RecurrencePreset.DAILY, 1, RecurrenceUnit.DAY)
        fun weekly() = RecurrenceRule(RecurrencePreset.WEEKLY, 1, RecurrenceUnit.WEEK)
        fun monthly() = RecurrenceRule(RecurrencePreset.MONTHLY, 1, RecurrenceUnit.MONTH)
        fun yearly() = RecurrenceRule(RecurrencePreset.YEARLY, 1, RecurrenceUnit.YEAR)
        fun custom(interval: Int, unit: RecurrenceUnit) =
            RecurrenceRule(RecurrencePreset.CUSTOM, interval, unit)

        fun of(preset: RecurrencePreset, customInterval: Int, customUnit: RecurrenceUnit) = when (preset) {
            RecurrencePreset.DAILY -> daily()
            RecurrencePreset.WEEKLY -> weekly()
            RecurrencePreset.MONTHLY -> monthly()
            RecurrencePreset.YEARLY -> yearly()
            RecurrencePreset.CUSTOM -> custom(customInterval, customUnit)
        }
    }
}

package com.apolilla.calendar.domain.calendar

import com.apolilla.calendar.domain.model.TaskColor

/**
 * What a calendar cell shows for its tasks: one dot per task, in chronological order.
 * When there are more tasks than [maxDots], the last slot becomes a "+N" counter so the
 * cell never overflows while the total remains visible (dots + overflow == task count).
 */
data class DayIndicators(val dots: List<TaskColor>, val overflow: Int) {
    val total: Int get() = dots.size + overflow

    companion object {
        const val DEFAULT_MAX_DOTS = 5

        fun of(chronologicalColors: List<TaskColor>, maxDots: Int = DEFAULT_MAX_DOTS): DayIndicators {
            require(maxDots >= 2)
            return if (chronologicalColors.size <= maxDots) {
                DayIndicators(chronologicalColors, 0)
            } else {
                val shown = chronologicalColors.take(maxDots - 1)
                DayIndicators(shown, chronologicalColors.size - shown.size)
            }
        }
    }
}

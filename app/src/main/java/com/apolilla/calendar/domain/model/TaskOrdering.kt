package com.apolilla.calendar.domain.model

/** Canonical chronological order for tasks: date, then time, then creation order (id). */
object TaskOrdering {
    val chronological: Comparator<Task> =
        compareBy<Task> { it.date }.thenBy { it.time }.thenBy { it.id }
}

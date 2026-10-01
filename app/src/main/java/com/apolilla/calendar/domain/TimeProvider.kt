package com.apolilla.calendar.domain

import java.time.ZoneId

/** Abstraction over the clock so business logic can be tested deterministically. */
interface TimeProvider {
    fun nowMillis(): Long
    fun zone(): ZoneId
}

object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

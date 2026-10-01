package com.apolilla.calendar.domain.model

/**
 * Palette of task colors. Colors are persisted by [name] so the palette can be
 * re-tuned later without migrating stored data.
 */
enum class TaskColor(val argb: Long) {
    BLUE(0xFF5B9BFF),
    RED(0xFFFF6B6B),
    GREEN(0xFF5FD38D),
    YELLOW(0xFFFFD45C),
    ORANGE(0xFFFF9F43),
    PURPLE(0xFFB78CFF),
    TEAL(0xFF3CD3C8),
    PINK(0xFFFF7EB6),
    GRAY(0xFFA4ACB9);

    companion object {
        val DEFAULT = BLUE

        fun fromName(name: String?): TaskColor = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

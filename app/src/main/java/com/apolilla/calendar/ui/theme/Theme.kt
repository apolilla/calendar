package com.apolilla.calendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.apolilla.calendar.domain.model.TaskColor

private val Scheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1A33),
    primaryContainer = Color(0xFF243A5E),
    onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFFB9C6DC),
    onSecondary = Color(0xFF1A2230),
    secondaryContainer = Color(0xFF2B3546),
    onSecondaryContainer = Color(0xFFDDE5F3),
    tertiary = Color(0xFFFFB4A1),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE4E7EE),
    surfaceVariant = Color(0xFF262B34),
    onSurfaceVariant = Color(0xFFB8BFCC),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF161A20),
    surfaceContainer = Color(0xFF1A1F26),
    surfaceContainerHigh = Color(0xFF222832),
    surfaceContainerHighest = Color(0xFF2B323D),
    outline = Color(0xFF6E7787),
    outlineVariant = Color(0xFF3A414C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** The app is dark-only by design (requirement), regardless of the system setting. */
@Composable
fun CalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}

val TaskColor.color: Color get() = Color(argb)

package com.apolilla.calendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apolilla.calendar.R
import com.apolilla.calendar.domain.calendar.DayIndicators
import com.apolilla.calendar.domain.calendar.MonthGrid
import com.apolilla.calendar.ui.Formats
import com.apolilla.calendar.ui.theme.color
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("prev_month")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month))
        }
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onTitleClick)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Formats.monthTitle(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("month_title"),
            )
            Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.choose_month))
        }
        IconButton(onClick = onNext, modifier = Modifier.testTag("next_month")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
        }
        TextButton(onClick = onToday) { Text(stringResource(R.string.today)) }
    }
}

/** Monday-first month grid. Horizontal swipes change the month. */
@Composable
fun MonthGridView(
    month: YearMonth,
    weeks: List<List<LocalDate>>,
    selected: LocalDate,
    today: LocalDate,
    indicators: Map<LocalDate, DayIndicators>,
    onSelect: (LocalDate) -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next by rememberUpdatedState(onSwipeNext)
    val previous by rememberUpdatedState(onSwipePrevious)
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    val threshold = 80.dp.toPx()
                    if (drag < -threshold) next() else if (drag > threshold) previous()
                },
                onHorizontalDrag = { _, amount -> drag += amount },
            )
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            MonthGrid.weekDayHeaders().forEach { day ->
                Text(
                    Formats.weekday(day),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isSelected = date == selected,
                        isToday = date == today,
                        indicators = indicators[date],
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    indicators: DayIndicators?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val count = indicators?.total ?: 0
    val description = Formats.longDate(date) +
        if (count > 0) ", " + pluralStringResource(R.plurals.tasks_count, count, count) else ""
    Column(
        modifier
            .heightIn(min = 52.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                selected = isSelected
            }
            .testTag("day_$date")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .then(if (isToday) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> scheme.onPrimaryContainer
                    inMonth -> scheme.onSurface
                    else -> scheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        TaskDots(indicators, dimmed = !inMonth)
    }
}

@Composable
private fun TaskDots(indicators: DayIndicators?, dimmed: Boolean) {
    Row(
        Modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicators?.dots?.forEach { taskColor ->
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(taskColor.color.copy(alpha = if (dimmed) 0.45f else 1f)),
            )
        }
        if (indicators != null && indicators.overflow > 0) {
            Text(
                "+${indicators.overflow}",
                fontSize = 8.sp,
                lineHeight = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

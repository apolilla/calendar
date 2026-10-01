package com.apolilla.calendar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apolilla.calendar.R
import com.apolilla.calendar.ui.Formats
import java.time.Month
import java.time.YearMonth

/** Quick jump to any month of any year (year navigation). */
@Composable
fun MonthYearPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit,
) {
    var year by rememberSaveable { mutableIntStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_year))
                }
                Text(year.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { year++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_year))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Month.values().toList().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { month ->
                            val target = YearMonth.of(year, month)
                            val label = Formats.monthName(month)
                            val modifier = Modifier.weight(1f)
                            if (target == initial) {
                                FilledTonalButton(onClick = { onPick(target) }, modifier = modifier) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = { onPick(target) },
                                    modifier = modifier,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                ) { Text(label, modifier = Modifier.padding(0.dp)) }
                            }
                        }
                    }
                }
            }
        },
    )
}

package com.apolilla.calendar.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.apolilla.calendar.R
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.ui.Formats
import com.apolilla.calendar.ui.theme.color
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    notificationsEnabled: () -> Boolean,
    message: String?,
    onMessageShown: () -> Unit,
    onNewTask: (LocalDate) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Re-check on every resume: the user may change it in system settings.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { notificationsOn = notificationsEnabled() }
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewTask(state.selectedDate) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_task)) },
                modifier = Modifier.testTag("new_task"),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 600.dp
            val calendar: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.padding(horizontal = 8.dp)) {
                    if (!notificationsOn) {
                        NotificationsOffBanner(onEnable = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }
                        })
                    }
                    MonthHeader(
                        month = state.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        onTitleClick = { showPicker = true },
                        onToday = viewModel::goToToday,
                    )
                    MonthGridView(
                        month = state.month,
                        weeks = state.weeks,
                        selected = state.selectedDate,
                        today = state.today,
                        indicators = state.indicators,
                        onSelect = viewModel::selectDate,
                        onSwipeNext = viewModel::nextMonth,
                        onSwipePrevious = viewModel::previousMonth,
                    )
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    calendar(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    TaskList(
                        state.selectedDate, state.selectedTasks, state.loading,
                        onNewTask, onOpenTask, Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    calendar(Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                    TaskList(
                        state.selectedDate, state.selectedTasks, state.loading,
                        onNewTask, onOpenTask, Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showPicker) {
        MonthYearPickerDialog(
            initial = state.month,
            onDismiss = { showPicker = false },
            onPick = {
                viewModel.showMonth(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun NotificationsOffBanner(onEnable: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                stringResource(R.string.notifications_disabled),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onEnable) { Text(stringResource(R.string.enable)) }
        }
    }
}

@Composable
private fun TaskList(
    date: LocalDate,
    tasks: List<Task>,
    loading: Boolean,
    onNewTask: (LocalDate) -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            Formats.longDate(date),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("selected_date"),
        )
        if (!loading && tasks.isEmpty()) {
            EmptyDay(onCreate = { onNewTask(date) })
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("task_list"),
            ) {
                items(tasks, key = { it.id }) { task -> TaskRow(task, onClick = { onOpenTask(task.id) }) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(task.color.color))
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Formats.time(task.time),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    task.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.series != null) {
                    Icon(
                        Icons.Outlined.Repeat, stringResource(R.string.recurring),
                        Modifier.size(18.dp).padding(start = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.attachments.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Attachment, stringResource(R.string.has_attachments),
                        Modifier.size(18.dp).padding(start = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDay(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp).testTag("empty_day"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.EventAvailable, null,
            Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.no_tasks), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onCreate) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.create_task))
        }
    }
}

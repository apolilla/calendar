package com.apolilla.calendar.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apolilla.calendar.R
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.ui.Formats
import com.apolilla.calendar.ui.colorLabel
import com.apolilla.calendar.ui.components.AttachmentList
import com.apolilla.calendar.ui.reminderLabel
import com.apolilla.calendar.ui.ruleDescription
import com.apolilla.calendar.ui.theme.color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    message: String?,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deletedMsg = stringResource(R.string.task_deleted)
    val cannotOpen = stringResource(R.string.attachment_cannot_open)

    LaunchedEffect(deleted) { if (deleted) onDeleted(deletedMsg) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (state is DetailState.Loaded) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete")) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is DetailState.Loaded) {
                ExtendedFloatingActionButton(
                    onClick = { onEdit(viewModel.taskId) },
                    icon = { Icon(Icons.Filled.Edit, null) },
                    text = { Text(stringResource(R.string.edit)) },
                    modifier = Modifier.testTag("edit"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                DetailState.Loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                DetailState.Missing -> if (!deleted) {
                    Text(stringResource(R.string.task_not_found), Modifier.padding(32.dp))
                }
                is DetailState.Loaded -> TaskDetails(
                    task = s.task,
                    isAvailable = viewModel::isAvailable,
                    onOpen = { if (!viewModel.open(it)) scope.launch { snackbar.showSnackbar(cannotOpen) } },
                )
            }
        }
    }

    val loaded = state as? DetailState.Loaded
    if (confirmDelete && loaded != null) {
        val series = loaded.task.series
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = {
                Text(stringResource(if (series != null) R.string.delete_instance_message else R.string.delete_task_message))
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }, modifier = Modifier.testTag("confirm_delete")) {
                    Text(stringResource(if (series != null) R.string.delete_this_only else R.string.delete))
                }
            },
            dismissButton = {
                Row {
                    if (series != null) {
                        TextButton(onClick = { confirmDelete = false; viewModel.deleteSeries(series.seriesId) }) {
                            Text(stringResource(R.string.delete_whole_series))
                        }
                    }
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun TaskDetails(
    task: Task,
    isAvailable: suspend (com.apolilla.calendar.domain.model.Attachment) -> Boolean,
    onOpen: (com.apolilla.calendar.domain.model.Attachment) -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(task.color.color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(
                task.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("detail_name"),
            )
        }
        DetailRow(Icons.Outlined.CalendarMonth, Formats.longDate(task.date))
        DetailRow(Icons.Outlined.Schedule, Formats.time(task.time))
        DetailRow(Icons.Outlined.Palette, colorLabel(task.color))
        task.series?.let { series ->
            val until = series.endDate?.let { stringResource(R.string.until_date, Formats.mediumDate(it)) } ?: ""
            DetailRow(
                Icons.Outlined.Repeat,
                ruleDescription(series.rule) + until + "\n" +
                    stringResource(R.string.instance_position, series.occurrenceIndex + 1, series.instanceCount),
            )
        }

        if (task.description.isNotBlank()) {
            HorizontalDivider()
            Text(stringResource(R.string.description), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(task.description, style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider()
        Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (task.reminders.isEmpty()) {
            DetailRow(Icons.Outlined.NotificationsOff, stringResource(R.string.no_notifications))
        } else {
            task.reminders.forEach { reminder ->
                val label = reminderLabel(reminder.offset) +
                    if (reminder.firedAtMillis != null) " · " + stringResource(R.string.reminder_done) else ""
                DetailRow(Icons.Outlined.Notifications, label)
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.attachments), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (task.attachments.isEmpty()) {
            Text(stringResource(R.string.no_attachments), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AttachmentList(task.attachments, isAvailable, onOpen, onRemove = null)
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

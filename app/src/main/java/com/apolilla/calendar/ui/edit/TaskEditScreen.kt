package com.apolilla.calendar.ui.edit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apolilla.calendar.R
import com.apolilla.calendar.camera.CaptureError
import com.apolilla.calendar.camera.rememberPhotoCapture
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrencePreset
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.recurrence.RecurrenceUnit
import com.apolilla.calendar.domain.reminder.ReminderOffset
import com.apolilla.calendar.files.PhotoStore
import com.apolilla.calendar.ui.Formats
import com.apolilla.calendar.ui.colorLabel
import com.apolilla.calendar.ui.components.AttachmentList
import com.apolilla.calendar.ui.components.DatePickerModal
import com.apolilla.calendar.ui.components.TimePickerModal
import com.apolilla.calendar.ui.presetLabel
import com.apolilla.calendar.ui.reminderLabel
import com.apolilla.calendar.ui.ruleDescription
import com.apolilla.calendar.ui.theme.color
import com.apolilla.calendar.ui.unitLabel
import kotlinx.coroutines.launch
import java.time.LocalTime

private enum class PickerDialog { DATE, TIME, END_DATE }

private val QUICK_TIMES = listOf(8, 9, 10, 12, 14, 16, 18, 20).map { LocalTime.of(it, 0) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    photoStore: PhotoStore,
    isAvailable: suspend (Attachment) -> Boolean,
    onDone: (message: String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by rememberSaveable { mutableStateOf<PickerDialog?>(null) }

    val savedCreated = stringResource(R.string.task_created)
    val savedUpdated = stringResource(R.string.task_updated)
    val saveFailed = stringResource(R.string.save_failed)
    val notFound = stringResource(R.string.task_not_found)
    val attachmentFailed = stringResource(R.string.attachment_failed)
    val unavailableMsg = stringResource(R.string.attachment_cannot_open)
    val res = context.resources

    LaunchedEffect(event) {
        when (val e = event) {
            is EditEvent.Saved -> {
                val message = when {
                    !e.isNew -> savedUpdated
                    e.createdCount > 1 -> res.getQuantityString(R.plurals.tasks_created, e.createdCount, e.createdCount)
                    else -> savedCreated
                }
                onDone(message)
            }
            EditEvent.Cancelled -> onDone(null)
            EditEvent.NotFound -> onDone(notFound)
            EditEvent.SaveFailed -> snackbar.showSnackbar(saveFailed)
            EditEvent.AttachmentFailed -> snackbar.showSnackbar(attachmentFailed)
            null -> Unit
        }
        if (event != null) viewModel.consumeEvent()
    }

    BackHandler { viewModel.cancel() }

    // --- Permissions & launchers -------------------------------------------------------
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.save() // save regardless; a banner on the home screen explains denied permission
    }
    val onSave = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            state.reminders.isNotEmpty() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.save()
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPickedFiles(uris)
    }

    val cameraDenied = stringResource(R.string.camera_permission_denied)
    val cameraDeniedForever = stringResource(R.string.camera_permission_denied_settings)
    val noCamera = stringResource(R.string.no_camera)
    val cameraFailed = stringResource(R.string.camera_failed)
    val settingsLabel = stringResource(R.string.settings)
    val capturePhoto = rememberPhotoCapture(
        photoStore = photoStore,
        onCaptured = viewModel::addPhoto,
        onError = { error ->
            scope.launch {
                when (error) {
                    CaptureError.PERMISSION_DENIED -> snackbar.showSnackbar(cameraDenied)
                    CaptureError.PERMISSION_DENIED_PERMANENTLY -> {
                        val result = snackbar.showSnackbar(cameraDeniedForever, settingsLabel, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        }
                    }
                    CaptureError.NO_CAMERA -> snackbar.showSnackbar(noCamera)
                    CaptureError.CAMERA_FAILED -> snackbar.showSnackbar(cameraFailed)
                }
            }
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_task else R.string.edit_task)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::cancel, modifier = Modifier.testTag("cancel")) {
                        Icon(Icons.Filled.Close, stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    }
                    TextButton(onClick = onSave, enabled = !state.saving && !state.loading, modifier = Modifier.testTag("save")) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.name_required)) },
                    isError = state.nameError,
                    supportingText = if (state.nameError) {
                        { Text(stringResource(R.string.error_name_required)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("name_field"),
                )

                // Date & time
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { dialog = PickerDialog.DATE }, modifier = Modifier.weight(1.4f).testTag("date_button")) {
                        Icon(Icons.Outlined.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text(Formats.date(state.date), maxLines = 1)
                    }
                    OutlinedButton(onClick = { dialog = PickerDialog.TIME }, modifier = Modifier.weight(1f).testTag("time_button")) {
                        Icon(Icons.Outlined.Schedule, null)
                        Spacer(Modifier.width(8.dp))
                        Text(Formats.time(state.time), maxLines = 1)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_TIMES.forEach { t ->
                        FilterChip(selected = state.time == t, onClick = { viewModel.setTime(t) }, label = { Text(Formats.time(t)) })
                    }
                }

                Section(stringResource(R.string.color)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TaskColor.entries.forEach { c -> ColorSwatch(c, c == state.color) { viewModel.setColor(c) } }
                    }
                }

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text(stringResource(R.string.description)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("description_field"),
                )

                if (state.isNew) {
                    RecurrenceSection(state, viewModel, onPickEnd = { dialog = PickerDialog.END_DATE })
                } else if (state.series != null) {
                    val series = state.series!!
                    InfoCard(
                        stringResource(
                            R.string.instance_info,
                            ruleDescription(series.rule),
                            series.occurrenceIndex + 1,
                            series.instanceCount,
                        ),
                    )
                }

                Section(stringResource(R.string.notifications)) {
                    ReminderOffset.entries.forEach { offset ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Checkbox) { viewModel.toggleReminder(offset) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = offset in state.reminders, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(reminderLabel(offset))
                        }
                    }
                }

                Section(stringResource(R.string.attachments)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickFiles.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("attach_file")) {
                            Icon(Icons.Outlined.AttachFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.attach_file))
                        }
                        FilledTonalIconButton(
                            onClick = capturePhoto,
                            modifier = Modifier.size(48.dp).testTag("take_photo"),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, stringResource(R.string.take_photo))
                        }
                    }
                    if (state.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        AttachmentList(
                            attachments = state.attachments,
                            isAvailable = isAvailable,
                            onOpen = { if (!viewModel.openAttachment(it)) scope.launch { snackbar.showSnackbar(unavailableMsg) } },
                            onRemove = viewModel::removeAttachment,
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.save)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    when (dialog) {
        PickerDialog.DATE -> DatePickerModal(state.date, { dialog = null }) { viewModel.setDate(it); dialog = null }
        PickerDialog.TIME -> TimePickerModal(state.time, { dialog = null }) { viewModel.setTime(it); dialog = null }
        PickerDialog.END_DATE -> DatePickerModal(state.recurrenceEnd ?: state.date.plusMonths(1), { dialog = null }) {
            viewModel.setRecurrenceEnd(it); dialog = null
        }
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceSection(state: TaskEditUiState, viewModel: TaskEditViewModel, onPickEnd: () -> Unit) {
    Section(stringResource(R.string.repeat)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<RecurrencePreset?>(null) + RecurrencePreset.entries).forEach { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { viewModel.setPreset(preset) },
                    label = { Text(presetLabel(preset)) },
                    modifier = Modifier.testTag("preset_${preset?.name ?: "NONE"}"),
                )
            }
        }
        if (state.preset == RecurrencePreset.CUSTOM) {
            val interval = state.customInterval.toIntOrNull() ?: 1
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.repeat_every))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.customInterval,
                    onValueChange = viewModel::setCustomInterval,
                    singleLine = true,
                    isError = state.intervalError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(88.dp).testTag("interval_field"),
                )
            }
            if (state.intervalError) {
                Text(
                    stringResource(R.string.error_interval),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                RecurrenceUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = state.customUnit == unit,
                        onClick = { viewModel.setCustomUnit(unit) },
                        label = { Text(unitLabel(unit, interval)) },
                        modifier = Modifier.testTag("unit_${unit.name}"),
                    )
                }
            }
        }
        if (state.preset != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.until), modifier = Modifier.padding(end = 8.dp))
                AssistChip(
                    onClick = onPickEnd,
                    label = {
                        Text(state.recurrenceEnd?.let { Formats.date(it) } ?: stringResource(R.string.no_end_date))
                    },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("end_date"),
                )
                if (state.recurrenceEnd != null) {
                    IconButton(onClick = { viewModel.setRecurrenceEnd(null) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.clear_end_date))
                    }
                }
            }
            if (state.endDateError) {
                Text(stringResource(R.string.error_end_date), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            RecurrencePreview(state)
        }
    }
}

@Composable
private fun RecurrencePreview(state: TaskEditUiState) {
    val rule = state.preset?.let {
        RecurrenceRule.of(it, (state.customInterval.toIntOrNull() ?: 1).coerceAtLeast(1), state.customUnit)
    } ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
            .testTag("recurrence_preview"),
    ) {
        val summary = if (state.recurrenceEnd != null) {
            stringResource(R.string.repeat_summary_until, ruleDescription(rule), Formats.mediumDate(state.recurrenceEnd))
        } else {
            ruleDescription(rule)
        }
        Text(summary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        if (state.preview.isEmpty()) {
            Text(stringResource(R.string.no_occurrences), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(R.string.next_occurrences), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.preview.forEach { Text("• " + Formats.dateTime(it), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(6.dp))
            val countText = LocalContext.current.resources.getQuantityString(
                R.plurals.will_create, state.instanceCount, state.instanceCount,
            )
            Text(countText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.recurrenceEnd == null) {
                Text(stringResource(R.string.default_horizon_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.truncated) {
                Text(stringResource(R.string.truncated_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        HorizontalDivider(Modifier.padding(bottom = 12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoCard(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ColorSwatch(taskColor: TaskColor, selected: Boolean, onClick: () -> Unit) {
    val label = colorLabel(taskColor)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(taskColor.color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag("color_${taskColor.name}"),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color(0xFF101318))
    }
}

package com.apolilla.calendar.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apolilla.calendar.domain.TaskDraft
import com.apolilla.calendar.domain.TaskService
import com.apolilla.calendar.domain.ValidationError
import com.apolilla.calendar.domain.ValidationException
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.SeriesRef
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.domain.recurrence.RecurrenceEngine
import com.apolilla.calendar.domain.recurrence.RecurrencePreset
import com.apolilla.calendar.domain.recurrence.RecurrenceRule
import com.apolilla.calendar.domain.recurrence.RecurrenceUnit
import com.apolilla.calendar.domain.reminder.ReminderOffset
import com.apolilla.calendar.domain.repository.TaskRepository
import com.apolilla.calendar.files.AttachmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TaskEditUiState(
    val isNew: Boolean,
    val loading: Boolean = false,
    val name: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = DEFAULT_TIME,
    val color: TaskColor = TaskColor.DEFAULT,
    val description: String = "",
    val attachments: List<Attachment> = emptyList(),
    val reminders: Set<ReminderOffset> = ReminderOffset.DEFAULTS,
    /** null = does not repeat. */
    val preset: RecurrencePreset? = null,
    val customInterval: String = "1",
    val customUnit: RecurrenceUnit = RecurrenceUnit.DAY,
    val recurrenceEnd: LocalDate? = null,
    val preview: List<LocalDateTime> = emptyList(),
    val instanceCount: Int = 0,
    val truncated: Boolean = false,
    val series: SeriesRef? = null,
    val nameError: Boolean = false,
    val intervalError: Boolean = false,
    val endDateError: Boolean = false,
    val saving: Boolean = false,
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(10, 0)
    }
}

sealed interface EditEvent {
    data class Saved(val createdCount: Int, val truncated: Boolean, val isNew: Boolean) : EditEvent
    data object Cancelled : EditEvent
    data object NotFound : EditEvent
    data object SaveFailed : EditEvent
    data object AttachmentFailed : EditEvent
}

/**
 * Holds the form state for creating or editing a task. All persistence and recurrence
 * work is delegated to [TaskService]; this class only maps form fields to a [TaskDraft].
 */
class TaskEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val service: TaskService,
    private val attachments: AttachmentManager,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(ARG_TASK_ID) ?: NEW_TASK
    private val initialDate: LocalDate =
        savedStateHandle.get<Long>(ARG_DATE)?.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay) ?: LocalDate.now()

    private val _state = MutableStateFlow(TaskEditUiState(isNew = taskId == NEW_TASK, date = initialDate))
    val state: StateFlow<TaskEditUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<EditEvent?>(null)
    val events: StateFlow<EditEvent?> = _events.asStateFlow()

    /** Attachments stored in the DB before editing started (never discarded on cancel). */
    private var storedUris: Set<String> = emptySet()

    init {
        if (taskId != NEW_TASK) load() else refreshPreview()
    }

    private fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val task = repository.getTask(taskId)
            if (task == null) {
                _events.value = EditEvent.NotFound
                return@launch
            }
            storedUris = task.attachments.map { it.uri }.toSet()
            _state.update {
                it.copy(
                    loading = false,
                    name = task.name,
                    date = task.date,
                    time = task.time,
                    color = task.color,
                    description = task.description,
                    attachments = task.attachments,
                    reminders = task.reminders.map { r -> r.offset }.toSet(),
                    series = task.series,
                )
            }
        }
    }

    fun consumeEvent() { _events.value = null }

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun setDate(value: LocalDate) = mutate { it.copy(date = value) }
    fun setTime(value: LocalTime) = mutate { it.copy(time = value) }
    fun setColor(value: TaskColor) = _state.update { it.copy(color = value) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }

    fun toggleReminder(offset: ReminderOffset) = _state.update {
        it.copy(reminders = if (offset in it.reminders) it.reminders - offset else it.reminders + offset)
    }

    fun setPreset(value: RecurrencePreset?) = mutate { it.copy(preset = value) }
    fun setCustomInterval(value: String) = mutate { it.copy(customInterval = value.filter(Char::isDigit).take(4)) }
    fun setCustomUnit(value: RecurrenceUnit) = mutate { it.copy(customUnit = value) }
    fun setRecurrenceEnd(value: LocalDate?) = mutate { it.copy(recurrenceEnd = value) }

    fun addPickedFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val added = uris.mapNotNull { uri -> runCatching { attachments.importPickedDocument(uri) }.getOrNull() }
            if (added.size < uris.size) _events.value = EditEvent.AttachmentFailed
            _state.update { s -> s.copy(attachments = s.attachments + added.filter { a -> s.attachments.none { it.uri == a.uri } }) }
        }
    }

    fun addPhoto(uri: Uri) = _state.update { it.copy(attachments = it.attachments + attachments.photoAttachment(uri)) }

    fun removeAttachment(attachment: Attachment) {
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.uri == attachment.uri }) }
        if (attachment.uri !in storedUris) {
            viewModelScope.launch { service.discardDraftAttachment(attachment) }
        }
    }

    fun openAttachment(attachment: Attachment): Boolean = attachments.open(attachment)

    fun cancel() {
        val unsaved = _state.value.attachments.filter { it.uri !in storedUris }
        viewModelScope.launch {
            unsaved.forEach { service.discardDraftAttachment(it) }
            _events.value = EditEvent.Cancelled
        }
    }

    private fun currentRule(s: TaskEditUiState): RecurrenceRule? {
        val preset = s.preset ?: return null
        val interval = s.customInterval.toIntOrNull() ?: return null
        if (preset == RecurrencePreset.CUSTOM && interval < 1) return null
        return RecurrenceRule.of(preset, interval.coerceAtLeast(1), s.customUnit)
    }

    private fun mutate(change: (TaskEditUiState) -> TaskEditUiState) {
        _state.update { change(it).copy(intervalError = false, endDateError = false) }
        refreshPreview()
    }

    private fun refreshPreview() = _state.update { s ->
        val rule = if (s.isNew) currentRule(s) else null
        if (rule == null || (s.recurrenceEnd != null && s.recurrenceEnd.isBefore(s.date))) {
            s.copy(preview = emptyList(), instanceCount = 0, truncated = false)
        } else {
            val start = LocalDateTime.of(s.date, s.time)
            val generation = RecurrenceEngine.generate(start, rule, s.recurrenceEnd)
            s.copy(
                preview = generation.occurrences.take(3),
                instanceCount = generation.occurrences.size,
                truncated = generation.truncated,
            )
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val rule = if (s.isNew) currentRule(s) else null
        val intervalInvalid = s.isNew && s.preset == RecurrencePreset.CUSTOM && rule == null
        val draft = TaskDraft(
            name = s.name,
            date = s.date,
            time = s.time,
            color = s.color,
            description = s.description,
            attachments = s.attachments,
            reminderOffsets = s.reminders,
            recurrence = rule,
            recurrenceEnd = if (rule != null) s.recurrenceEnd else null,
        )
        val errors = service.validate(draft)
        if (errors.isNotEmpty() || intervalInvalid) {
            _state.update {
                it.copy(
                    nameError = ValidationError.NAME_REQUIRED in errors,
                    endDateError = ValidationError.END_DATE_BEFORE_START in errors,
                    intervalError = intervalInvalid,
                )
            }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (s.isNew) {
                    val result = service.create(draft)
                    _events.value = EditEvent.Saved(result.taskIds.size, result.truncated, isNew = true)
                } else {
                    service.update(taskId, draft)
                    _events.value = EditEvent.Saved(1, false, isNew = false)
                }
            } catch (e: ValidationException) {
                _state.update {
                    it.copy(
                        nameError = ValidationError.NAME_REQUIRED in e.errors,
                        endDateError = ValidationError.END_DATE_BEFORE_START in e.errors,
                    )
                }
            } catch (e: Exception) {
                _events.value = EditEvent.SaveFailed
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
        const val ARG_DATE = "date"
        const val NEW_TASK = -1L
        const val NO_DATE = Long.MIN_VALUE
    }
}

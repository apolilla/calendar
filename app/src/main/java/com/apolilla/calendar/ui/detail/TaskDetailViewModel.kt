package com.apolilla.calendar.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apolilla.calendar.domain.TaskService
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.repository.TaskRepository
import com.apolilla.calendar.files.AttachmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Loaded(val task: Task) : DetailState
}

class TaskDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: TaskRepository,
    private val service: TaskService,
    private val attachments: AttachmentManager,
) : ViewModel() {
    val taskId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_TASK_ID))

    val state: StateFlow<DetailState> = repository.observeTask(taskId)
        .map { task -> if (task == null) DetailState.Missing else DetailState.Loaded(task) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun delete() = viewModelScope.launch {
        service.delete(taskId)
        _deleted.value = true
    }

    fun deleteSeries(seriesId: Long) = viewModelScope.launch {
        service.deleteSeries(seriesId)
        _deleted.value = true
    }

    suspend fun isAvailable(attachment: Attachment) = attachments.isAvailable(attachment)

    fun open(attachment: Attachment): Boolean = attachments.open(attachment)

    companion object {
        const val ARG_TASK_ID = "taskId"
    }
}

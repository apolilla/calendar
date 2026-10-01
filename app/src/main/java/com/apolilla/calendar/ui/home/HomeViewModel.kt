package com.apolilla.calendar.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apolilla.calendar.domain.calendar.DayIndicators
import com.apolilla.calendar.domain.calendar.MonthGrid
import com.apolilla.calendar.domain.model.Task
import com.apolilla.calendar.domain.model.TaskOrdering
import com.apolilla.calendar.domain.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth,
    val weeks: List<List<LocalDate>>,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val indicators: Map<LocalDate, DayIndicators> = emptyMap(),
    val selectedTasks: List<Task> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: TaskRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val selected = MutableStateFlow(today())

    private val monthTasks = month.flatMapLatest { m ->
        val (start, end) = MonthGrid.visibleRange(m)
        repository.observeTasksBetween(start, end).map { tasks ->
            tasks.sortedWith(TaskOrdering.chronological)
                .groupBy { it.date }
                .mapValues { (_, dayTasks) -> DayIndicators.of(dayTasks.map { it.color }) }
        }
    }

    private val selectedTasks = selected.flatMapLatest { date ->
        repository.observeTasksBetween(date, date).map { it.sortedWith(TaskOrdering.chronological) }
    }

    val state: StateFlow<HomeUiState> =
        combine(month, selected, monthTasks, selectedTasks) { m, sel, indicators, tasks ->
            HomeUiState(
                month = m,
                weeks = MonthGrid.weeks(m),
                selectedDate = sel,
                today = today(),
                indicators = indicators,
                selectedTasks = tasks,
                loading = false,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(month.value, MonthGrid.weeks(month.value), selected.value, today()),
        )

    fun selectDate(date: LocalDate) {
        selected.value = date
        val dateMonth = YearMonth.from(date)
        if (dateMonth != month.value) month.value = dateMonth
    }

    fun showMonth(target: YearMonth) {
        if (target == month.value) return
        month.value = target
        // Keep the same day of month when possible (31st -> last day of shorter months).
        selected.update { target.atDay(minOf(it.dayOfMonth, target.lengthOfMonth())) }
        if (target == YearMonth.from(today())) selected.value = today()
    }

    fun nextMonth() = showMonth(month.value.plusMonths(1))
    fun previousMonth() = showMonth(month.value.minusMonths(1))
    fun goToToday() = selectDate(today())
}

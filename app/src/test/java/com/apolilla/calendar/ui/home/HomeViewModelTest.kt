package com.apolilla.calendar.ui.home

import com.apolilla.calendar.domain.TaskDraft
import com.apolilla.calendar.domain.TaskService
import com.apolilla.calendar.domain.model.TaskColor
import com.apolilla.calendar.fakes.FakeTaskRepository
import com.apolilla.calendar.fakes.FixedTime
import com.apolilla.calendar.fakes.RecordingCleaner
import com.apolilla.calendar.fakes.RecordingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val today = LocalDate.of(2026, 10, 1)
    private val repository = FakeTaskRepository()
    private val service = TaskService(repository, RecordingScheduler(), RecordingCleaner(), FixedTime(0))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun selectedDayAndIndicatorsFollowRepository() = runTest {
        val vm = HomeViewModel(repository) { today }
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertTrue(vm.state.value.selectedTasks.isEmpty())

        service.create(TaskDraft("B", today, LocalTime.of(12, 0), TaskColor.RED))
        service.create(TaskDraft("A", today, LocalTime.of(9, 0)))
        service.create(TaskDraft("A", today, LocalTime.of(9, 0)))

        val state = vm.state.first { it.selectedTasks.size == 3 }
        assertEquals(listOf("A", "A", "B"), state.selectedTasks.map { it.name })
        assertEquals(listOf(TaskColor.BLUE, TaskColor.BLUE, TaskColor.RED), state.indicators.getValue(today).dots)

        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        assertEquals(LocalDate.of(2026, 11, 1), vm.state.value.selectedDate)
        assertTrue(vm.state.value.selectedTasks.isEmpty())

        vm.selectDate(LocalDate.of(2027, 1, 31))
        assertEquals(YearMonth.of(2027, 1), vm.state.value.month)
        vm.nextMonth()
        assertEquals(LocalDate.of(2027, 2, 28), vm.state.value.selectedDate)

        vm.goToToday()
        assertEquals(3, vm.state.value.selectedTasks.size)
        job.cancel()
    }
}

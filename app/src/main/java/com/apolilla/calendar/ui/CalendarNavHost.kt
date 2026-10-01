package com.apolilla.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apolilla.calendar.AppContainer
import com.apolilla.calendar.ui.detail.TaskDetailScreen
import com.apolilla.calendar.ui.detail.TaskDetailViewModel
import com.apolilla.calendar.ui.edit.TaskEditScreen
import com.apolilla.calendar.ui.edit.TaskEditViewModel
import com.apolilla.calendar.ui.home.HomeScreen
import com.apolilla.calendar.ui.home.HomeViewModel
import kotlinx.coroutines.flow.StateFlow

private object Routes {
    const val HOME = "home"
    const val DETAIL = "task/{${TaskDetailViewModel.ARG_TASK_ID}}"
    const val EDIT = "edit?${TaskEditViewModel.ARG_TASK_ID}={${TaskEditViewModel.ARG_TASK_ID}}" +
        "&${TaskEditViewModel.ARG_DATE}={${TaskEditViewModel.ARG_DATE}}"

    fun detail(id: Long) = "task/$id"
    fun newTask(epochDay: Long) = "edit?${TaskEditViewModel.ARG_TASK_ID}=${TaskEditViewModel.NEW_TASK}&${TaskEditViewModel.ARG_DATE}=$epochDay"
    fun editTask(id: Long) = "edit?${TaskEditViewModel.ARG_TASK_ID}=$id"
}

private const val MESSAGE_KEY = "user_message"

@Composable
fun CalendarNavHost(
    container: AppContainer,
    notificationsEnabled: () -> Boolean,
    openTaskRequests: StateFlow<Long?>,
    onOpenTaskHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val openTaskId by openTaskRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openTaskId) {
        openTaskId?.let { id ->
            navController.navigate(Routes.detail(id)) { launchSingleTop = true }
            onOpenTaskHandled()
        }
    }

    /** Navigates back and hands a snackbar message to the previous screen. */
    fun popWithMessage(message: String?) {
        navController.previousBackStackEntry?.savedStateHandle?.set(MESSAGE_KEY, message)
        navController.popBackStack()
    }

    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { entry ->
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.repository) } })
            val message by entry.savedStateHandle.getStateFlow<String?>(MESSAGE_KEY, null).collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = vm,
                notificationsEnabled = notificationsEnabled,
                message = message,
                onMessageShown = { entry.savedStateHandle[MESSAGE_KEY] = null },
                onNewTask = { navController.navigate(Routes.newTask(it.toEpochDay())) },
                onOpenTask = { navController.navigate(Routes.detail(it)) },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument(TaskDetailViewModel.ARG_TASK_ID) { type = NavType.LongType }),
        ) { entry ->
            val message by entry.savedStateHandle.getStateFlow<String?>(MESSAGE_KEY, null).collectAsStateWithLifecycle()
            val vm: TaskDetailViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        TaskDetailViewModel(createSavedStateHandle(), container.repository, container.taskService, container.attachmentManager)
                    }
                },
            )
            TaskDetailScreen(
                viewModel = vm,
                message = message,
                onMessageShown = { entry.savedStateHandle[MESSAGE_KEY] = null },
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editTask(it)) },
                onDeleted = { popWithMessage(it) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument(TaskEditViewModel.ARG_TASK_ID) {
                    type = NavType.LongType
                    defaultValue = TaskEditViewModel.NEW_TASK
                },
                navArgument(TaskEditViewModel.ARG_DATE) {
                    type = NavType.LongType
                    defaultValue = TaskEditViewModel.NO_DATE
                },
            ),
        ) {
            val vm: TaskEditViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        TaskEditViewModel(createSavedStateHandle(), container.repository, container.taskService, container.attachmentManager)
                    }
                },
            )
            TaskEditScreen(
                viewModel = vm,
                photoStore = container.photoStore,
                isAvailable = { container.attachmentManager.isAvailable(it) },
                onDone = { popWithMessage(it) },
            )
        }
    }
}

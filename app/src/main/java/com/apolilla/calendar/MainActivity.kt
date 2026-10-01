package com.apolilla.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.apolilla.calendar.ui.CalendarNavHost
import com.apolilla.calendar.ui.theme.CalendarTheme

class MainActivity : ComponentActivity() {

    /** Task to open, requested by a notification tap. Consumed by the nav host. */
    private val openTaskRequests = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as CalendarApp).container
        setContent {
            CalendarTheme {
                CalendarNavHost(
                    container = container,
                    notificationsEnabled = { container.notifier.canPost() },
                    openTaskRequests = openTaskRequests.asStateFlow(),
                    onOpenTaskHandled = { openTaskRequests.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TASK) {
            val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            if (id > 0) openTaskRequests.value = id
        }
    }

    companion object {
        const val ACTION_OPEN_TASK = "com.apolilla.calendar.action.OPEN_TASK"
        const val EXTRA_TASK_ID = "taskId"
    }
}

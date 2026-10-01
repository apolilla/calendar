package com.apolilla.calendar

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CalendarApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()
        // Catch up on anything missed while the process was dead and re-arm the alarm.
        appScope.launch { runCatching { container.reminderProcessor.run() } }
    }
}

package com.apolilla.calendar.domain.repository

import com.apolilla.calendar.domain.model.Attachment

/** Android notification/alarm side effects, abstracted for testability. */
interface ReminderScheduler {
    /** Re-reads pending reminders and (re)arms the single next alarm. Idempotent. */
    suspend fun syncAlarms()

    /** Removes any notification of [taskId] that is currently shown. */
    fun cancelShownNotifications(taskId: Long)
}

/** Releases resources held by an attachment that no task references anymore. */
interface AttachmentCleaner {
    /** Photos: deletes the app-owned file. Files: releases the persisted URI permission. */
    suspend fun release(attachment: Attachment)
}

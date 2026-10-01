package com.apolilla.calendar.files

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.AttachmentKind
import com.apolilla.calendar.domain.repository.AttachmentCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything about attachment URIs: persisting SAF permissions, reading metadata,
 * checking availability and opening them with another app. Every operation is defensive:
 * files may have been deleted or moved outside the app at any time.
 */
class AttachmentManager(
    private val context: Context,
    private val photoStore: PhotoStore,
) : AttachmentCleaner {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Builds an attachment from a document picked with ACTION_OPEN_DOCUMENT. */
    suspend fun importPickedDocument(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Provider does not offer persistable permissions; access may end later,
            // which the app handles as "unavailable".
        }
        Attachment(
            uri = uri.toString(),
            displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file",
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
            kind = AttachmentKind.FILE,
        )
    }

    fun photoAttachment(uri: Uri): Attachment = Attachment(
        uri = uri.toString(),
        displayName = uri.lastPathSegment ?: "photo.jpg",
        mimeType = "image/jpeg",
        kind = AttachmentKind.PHOTO,
    )

    /** True if the content can currently be opened for reading. Never throws. */
    suspend fun isAvailable(attachment: Attachment): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(attachment.uri)
        photoStore.fileFor(uri)?.let { return@withContext it.exists() && it.length() > 0 }
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Opens the attachment in an external viewer. Returns false if that was impossible. */
    fun open(attachment: Attachment): Boolean {
        val uri = Uri.parse(attachment.uri)
        val type = attachment.mimeType ?: runCatching { resolver.getType(uri) }.getOrNull() ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun release(attachment: Attachment) {
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(attachment.uri)
            if (attachment.kind == AttachmentKind.PHOTO && photoStore.isOwnedUri(uri)) {
                photoStore.delete(uri)
            } else {
                try {
                    resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Permission was never persisted or already gone.
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}

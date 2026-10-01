package com.apolilla.calendar.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Photos taken from the app are stored in app-private storage (`files/photos`) and
 * exposed through a FileProvider URI. They therefore survive even if the user cleans
 * the gallery, and are removed when the last task referencing them is deleted.
 */
class PhotoStore(private val context: Context) {
    private val authority = "${context.packageName}.fileprovider"
    private val dir: File get() = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

    /** Creates an empty target file for the camera and returns its shareable URI. */
    fun createPhotoTarget(): Pair<File, Uri> {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(dir, "IMG_${stamp}_${UUID.randomUUID().toString().take(8)}.jpg")
        file.createNewFile()
        return file to uriFor(file)
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    fun isOwnedUri(uri: Uri): Boolean = uri.authority == authority

    /** The file behind an app-owned photo URI, or null if [uri] is not one of ours. */
    fun fileFor(uri: Uri): File? {
        if (!isOwnedUri(uri)) return null
        val name = uri.lastPathSegment ?: return null
        val file = File(dir, name)
        // Guard against path traversal: only files directly inside the photo dir.
        return file.takeIf { it.parentFile?.canonicalPath == dir.canonicalPath }
    }

    fun delete(uri: Uri): Boolean = fileFor(uri)?.delete() ?: false

    /** Deletes a target whose capture was cancelled or failed (empty file). */
    fun discardIfEmpty(file: File) {
        if (file.exists() && file.length() == 0L) file.delete()
    }

    companion object {
        const val PHOTO_DIR = "photos"
    }
}

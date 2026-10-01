package com.apolilla.calendar.camera

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apolilla.calendar.files.PhotoStore
import java.io.File

enum class CaptureError {
    /** The user denied the permission (the system may ask again). */
    PERMISSION_DENIED,
    PERMISSION_DENIED_PERMANENTLY,
    NO_CAMERA,
    CAMERA_FAILED,
}

/**
 * Wires camera permission + ACTION_IMAGE_CAPTURE (TakePicture) + file creation.
 * Returns a function that starts a capture. The target file path is kept in saveable
 * state so a capture survives the activity being recreated while the camera is open.
 * A cancelled capture deletes its empty file and reports nothing.
 */
@Composable
fun rememberPhotoCapture(
    photoStore: PhotoStore,
    onCaptured: (Uri) -> Unit,
    onError: (CaptureError) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (saved && file.exists() && file.length() > 0) {
            currentOnCaptured(photoStore.uriFor(file))
        } else {
            photoStore.discardIfEmpty(file) // cancelled by the user or camera error
            if (saved) currentOnError(CaptureError.CAMERA_FAILED)
        }
    }

    fun launchCamera() {
        val (file, uri) = try {
            photoStore.createPhotoTarget()
        } catch (_: Exception) {
            currentOnError(CaptureError.CAMERA_FAILED)
            return
        }
        pendingPath = file.absolutePath
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingPath = null
            photoStore.discardIfEmpty(file)
            currentOnError(CaptureError.NO_CAMERA)
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            currentOnError(
                if (canAskAgain) CaptureError.PERMISSION_DENIED else CaptureError.PERMISSION_DENIED_PERMANENTLY,
            )
        }
    }

    return remember(context) {
        {
            when {
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ->
                    currentOnError(CaptureError.NO_CAMERA)
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> launchCamera()
                else -> requestPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.example.checkinoutapp.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Saves bytes the user asked to export (a ticket, a CSV, a PDF). Platform code, behind this seam. */
interface FileSaver {
    /** True when the file was saved or handed to the system share sheet; false when the user cancelled. */
    suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean
}

/** A short haptic / audible confirmation after a scan, where the device supports one. */
interface ScanFeedback {
    fun success()
    fun failure()
}

/** Phones open on the organizer tools; the web and desktop open on the public site. */
expect val isMobilePlatform: Boolean

enum class CameraState { STARTING, READY, PERMISSION_DENIED, UNAVAILABLE }

/** False where there is no camera scanner at all (desktop): the screen offers code entry instead. */
expect val cameraScanningSupported: Boolean

/**
 * The live camera preview with QR detection. Reports every decoded payload through [onScanned];
 * de-duplication and submission are the ViewModel's job, not the camera's.
 */
@Composable
expect fun QrCameraView(modifier: Modifier, active: Boolean, torchOn: Boolean, onScanned: (String) -> Unit, onState: (CameraState) -> Unit)

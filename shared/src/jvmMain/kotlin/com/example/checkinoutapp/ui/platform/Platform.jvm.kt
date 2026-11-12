package com.example.checkinoutapp.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.io.File

actual val isMobilePlatform: Boolean = false

/** Desktop is the administration workstation: no camera. A USB QR reader types into the code field. */
actual val cameraScanningSupported: Boolean = false

@Composable
actual fun QrCameraView(modifier: Modifier, active: Boolean, torchOn: Boolean, onScanned: (String) -> Unit, onState: (CameraState) -> Unit) {
    LaunchedEffect(Unit) { onState(CameraState.UNAVAILABLE) }
}

class DesktopFileSaver : FileSaver {
    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val target = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply { file = fileName; isVisible = true }
            dialog.file?.let { File(dialog.directory, it) }
        } ?: return false
        withContext(Dispatchers.IO) { target.writeBytes(bytes) }
        return true
    }
}

class DesktopScanFeedback : ScanFeedback {
    override fun success() = Unit
    override fun failure() = Toolkit.getDefaultToolkit().beep()
}

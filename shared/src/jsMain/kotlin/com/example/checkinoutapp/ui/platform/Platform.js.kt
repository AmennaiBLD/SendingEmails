package com.example.checkinoutapp.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

actual val isMobilePlatform: Boolean = false

actual val cameraScanningSupported: Boolean = true

@JsModule("jsqr")
@JsNonModule
private external fun jsQR(data: dynamic, width: Int, height: Int): dynamic

private const val SCAN_INTERVAL_MS = 180
private const val MAX_ANALYSIS_WIDTH = 640

/**
 * Browser scanner. Compose draws on a canvas, so the camera is a plain <video> element laid
 * over the composable's bounds. Frames are decoded with the browser's BarcodeDetector where it
 * exists (Chrome, Edge, Android) and with jsQR everywhere else (Safari, Firefox).
 */
@Composable
actual fun QrCameraView(modifier: Modifier, active: Boolean, torchOn: Boolean, onScanned: (String) -> Unit, onState: (CameraState) -> Unit) {
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnState by rememberUpdatedState(onState)
    val density = LocalDensity.current.density
    val video = androidx.compose.runtime.remember {
        (document.createElement("video") as HTMLVideoElement).apply {
            setAttribute("playsinline", "true")
            muted = true
            style.cssText = "position:fixed;object-fit:cover;z-index:10;pointer-events:none;border-radius:16px;background:#000;display:none;"
        }
    }

    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        var stream: dynamic = null
        var timer = 0
        var disposed = false
        val devices = window.navigator.asDynamic().mediaDevices
        if (devices == null || devices.getUserMedia == undefined) {
            currentOnState(CameraState.UNAVAILABLE)
            return@DisposableEffect onDispose { }
        }
        currentOnState(CameraState.STARTING)
        document.body?.appendChild(video)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val detectorClass = window.asDynamic().BarcodeDetector
        val detector: dynamic = if (detectorClass != undefined) runCatching { js("new detectorClass({ formats: ['qr_code'] })") }.getOrNull() else null

        fun analyse() {
            if (disposed || video.videoWidth == 0) return
            if (detector != null) {
                detector.detect(video).then { codes: dynamic ->
                    val count = codes.length as Int
                    for (i in 0 until count) (codes[i].rawValue as? String)?.takeIf { it.isNotBlank() }?.let { currentOnScanned(it) }
                }.catch { _: dynamic -> }
                return
            }
            val scale = minOf(1.0, MAX_ANALYSIS_WIDTH.toDouble() / video.videoWidth)
            canvas.width = (video.videoWidth * scale).toInt()
            canvas.height = (video.videoHeight * scale).toInt()
            val context = canvas.getContext("2d", js("({ willReadFrequently: true })")) as CanvasRenderingContext2D
            context.drawImage(video, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
            val image = context.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
            val code = jsQR(image.data, image.width, image.height)
            if (code != null) (code.data as? String)?.takeIf { it.isNotBlank() }?.let { currentOnScanned(it) }
        }

        val constraints = js("({ video: { facingMode: { ideal: 'environment' } }, audio: false })")
        devices.getUserMedia(constraints).then { granted: dynamic ->
            if (disposed) {
                granted.getTracks().forEach { track: dynamic -> track.stop() }
            } else {
                stream = granted
                video.asDynamic().srcObject = granted
                video.style.display = "block"
                video.play()
                currentOnState(CameraState.READY)
                timer = window.setInterval({ analyse() }, SCAN_INTERVAL_MS)
            }
        }.catch { error: dynamic ->
            val name = error?.name as? String
            currentOnState(if (name == "NotAllowedError" || name == "SecurityError") CameraState.PERMISSION_DENIED else CameraState.UNAVAILABLE)
        }

        onDispose {
            disposed = true
            window.clearInterval(timer)
            if (stream != null) stream.getTracks().forEach { track: dynamic -> track.stop() }
            video.asDynamic().srcObject = null
            video.style.display = "none"
            video.remove()
        }
    }

    Box(
        modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            video.style.left = "${bounds.left / density}px"
            video.style.top = "${bounds.top / density}px"
            video.style.width = "${bounds.width / density}px"
            video.style.height = "${bounds.height / density}px"
        },
    )
}

class BrowserFileSaver : FileSaver {
    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val array = Uint8Array(bytes.size)
        for (i in bytes.indices) array[i] = bytes[i]
        val url = URL.createObjectURL(Blob(arrayOf(array), BlobPropertyBag(type = mimeType)))
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = fileName
        document.body?.appendChild(anchor)
        anchor.click()
        anchor.remove()
        window.setTimeout({ URL.revokeObjectURL(url) }, 10_000)
        return true
    }
}

class BrowserScanFeedback : ScanFeedback {
    private fun vibrate(millis: Int) {
        runCatching {
            val navigator = window.navigator.asDynamic()
            if (navigator.vibrate != undefined) navigator.vibrate(millis)
        }
    }

    override fun success() = vibrate(60)
    override fun failure() = vibrate(260)
}

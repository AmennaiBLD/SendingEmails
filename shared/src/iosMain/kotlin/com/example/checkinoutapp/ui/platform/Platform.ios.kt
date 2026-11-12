package com.example.checkinoutapp.ui.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.setTorchMode
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIView
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

actual val isMobilePlatform: Boolean = true

actual val cameraScanningSupported: Boolean = true

/** A UIView whose preview layer always fills it, including after rotation. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewContainer(private val preview: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectZero.readValue()) {
    init {
        layer.addSublayer(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        preview.setFrame(bounds)
        CATransaction.commit()
    }
}

private class QrDelegate(private val onCode: (String) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .mapNotNull { it.stringValue?.takeIf { value -> value.isNotBlank() } }
            .forEach(onCode)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrCameraView(modifier: Modifier, active: Boolean, torchOn: Boolean, onScanned: (String) -> Unit, onState: (CameraState) -> Unit) {
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnState by rememberUpdatedState(onState)
    var authorized by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized) }

    LaunchedEffect(active, authorized) {
        if (active && !authorized) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    authorized = granted
                    if (!granted) currentOnState(CameraState.PERMISSION_DENIED)
                }
            }
        }
    }
    if (!authorized) return

    val session = remember { AVCaptureSession() }
    val preview = remember { AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill } }
    val delegate = remember { QrDelegate { currentOnScanned(it) } }

    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        currentOnState(CameraState.STARTING)
        runCatching {
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: error("no camera")
            session.beginConfiguration()
            session.sessionPreset = AVCaptureSessionPresetHigh
            if (session.inputs.isEmpty()) {
                val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: error("camera input unavailable")
                if (session.canAddInput(input)) session.addInput(input)
            }
            if (session.outputs.isEmpty()) {
                val output = AVCaptureMetadataOutput()
                if (session.canAddOutput(output)) {
                    session.addOutput(output)
                    output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
                    val available = output.availableMetadataObjectTypes.filterIsInstance<String>()
                    output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode).filter { it in available }
                }
            }
            session.commitConfiguration()
            if (!session.isRunning()) session.startRunning()
            currentOnState(CameraState.READY)
        }.onFailure { currentOnState(CameraState.UNAVAILABLE) }
        onDispose { if (session.isRunning()) session.stopRunning() }
    }

    LaunchedEffect(active, torchOn) {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device != null && device.hasTorch) {
            runCatching {
                device.lockForConfiguration(null)
                device.setTorchMode(if (active && torchOn) AVCaptureTorchModeOn else AVCaptureTorchModeOff)
                device.unlockForConfiguration()
            }
        }
    }

    UIKitView(
        factory = { PreviewContainer(preview) },
        modifier = modifier.fillMaxSize(),
        update = { },
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

/** Hands the file to the iOS share sheet (Save to Files, AirDrop, Mail…). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileSaver : FileSaver {
    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean = withContext(Dispatchers.Main) {
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@withContext false
        while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
        val dir = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("exports") ?: return@withContext false
        NSFileManager.defaultManager.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val file = dir.URLByAppendingPathComponent(fileName) ?: return@withContext false
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        if (!data.writeToURL(file, atomically = true)) return@withContext false
        suspendCancellableCoroutine { continuation ->
            val sheet = UIActivityViewController(activityItems = listOf(file), applicationActivities = null)
            sheet.completionWithItemsHandler = { _, completed, _, _ -> if (continuation.isActive) continuation.resume(completed) }
            sheet.popoverPresentationController?.let { popover ->
                popover.sourceView = presenter.view
                popover.sourceRect = presenter.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
            }
            presenter.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

class IosScanFeedback : ScanFeedback {
    private val haptics = UINotificationFeedbackGenerator()

    override fun success() {
        haptics.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        AudioServicesPlaySystemSound(SOUND_SUCCESS)
    }

    override fun failure() {
        haptics.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        AudioServicesPlaySystemSound(SOUND_FAILURE)
    }

    private companion object {
        const val SOUND_SUCCESS = 1057u
        const val SOUND_FAILURE = 1053u
    }
}

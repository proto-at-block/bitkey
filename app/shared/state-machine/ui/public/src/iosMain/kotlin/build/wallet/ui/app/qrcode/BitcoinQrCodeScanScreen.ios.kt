package build.wallet.ui.app.qrcode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import bitkey.shared.ui_core_public.generated.resources.*
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.model.alert.ButtonAlertModel
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private sealed class PermissionState {
  data object Unset : PermissionState()

  data object Granted : PermissionState()

  data object Denied : PermissionState()
}

@Composable
internal actual fun NativeQrCodeScanner(model: QrCodeScanBodyModel) {
  val permissionState by produceState<PermissionState>(PermissionState.Unset) {
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
      AVAuthorizationStatusAuthorized -> value = PermissionState.Granted
      AVAuthorizationStatusDenied,
      AVAuthorizationStatusRestricted,
      -> value = PermissionState.Denied

      AVAuthorizationStatusNotDetermined -> {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
          value = if (granted) PermissionState.Granted else PermissionState.Denied
        }
      }
    }
  }
  when (permissionState) {
    PermissionState.Granted -> {
      val camera: AVCaptureDevice? = remember {
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
      }

      camera?.let {
        UIKitScannerView(
          camera = it,
          onDataScanned = model.onQrCodeScanned
        )
      }
    }

    PermissionState.Denied -> {
      PermissionDeniedDialog(onClose = model.onClose)
    }

    PermissionState.Unset -> {
      // waiting for permission result
    }
  }
}

@Composable
private fun PermissionDeniedDialog(onClose: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    AlertDialog(
      model = ButtonAlertModel(
        title = stringResource(Res.string.qr_scanner_no_access_alert_title),
        subline = stringResource(Res.string.qr_scanner_no_access_alert_subline),
        primaryButtonText = stringResource(Res.string.qr_scanner_no_access_alert_button_primary),
        secondaryButtonText = stringResource(Res.string.qr_scanner_no_access_alert_button_secondary),
        onDismiss = onClose,
        onSecondaryButtonClick = onClose,
        onPrimaryButtonClick = {
          NSURL.URLWithString(UIApplicationOpenSettingsURLString)
            ?.let { UIApplication.sharedApplication.openURL(it) }
            ?: run(onClose)
        }
      )
    )
  }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun UIKitScannerView(
  camera: AVCaptureDevice,
  onDataScanned: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val cameraScannerController = remember {
    CameraScannerController(
      camera = camera,
      onDataScanned = onDataScanned
    )
  }

  LaunchedEffect(Unit) {
    if (camera.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
      camera.lockForConfiguration(null)
      camera.setFocusMode(AVCaptureFocusModeContinuousAutoFocus)
      camera.unlockForConfiguration()
    }
  }

  UIKitView(
    modifier = modifier.fillMaxSize(),
    background = Color.Black,
    factory = {
      UIView().apply {
        cameraScannerController.setup(layer)
      }
    },
    onRelease = { cameraScannerController.dispose() },
    onResize = { view, rect ->
      CATransaction.apply {
        begin()
        setValue(true, kCATransactionDisableActions)
        view.layer.setFrame(rect)
        cameraScannerController.setFrame(rect)
        commit()
      }
    }
  )
}

@Suppress("UnusedPrivateClass")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class CameraScannerController(
  private val camera: AVCaptureDevice,
  private val onDataScanned: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
  private lateinit var cameraPreview: AVCaptureVideoPreviewLayer
  private lateinit var captureSession: AVCaptureSession

  fun setup(viewLayer: CALayer) {
    check(!::captureSession.isInitialized) {
      "setup(..) cannot be called twice on the same CameraScannerController"
    }
    captureSession = AVCaptureSession()

    val cameraInput = memScoped {
      val error = alloc<ObjCObjectVar<NSError?>>()
      AVCaptureDeviceInput(device = camera, error = error.ptr).also {
        if (error.value != null || !captureSession.canAddInput(it)) {
          log(LogLevel.Error) {
            "Failed to start camera session: ${error.value}"
          }
          return
        }
      }
    }

    captureSession.addInput(cameraInput)

    val metadataOutput = AVCaptureMetadataOutput()
    if (captureSession.canAddOutput(metadataOutput)) {
      captureSession.addOutput(metadataOutput)
      metadataOutput.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
      metadataOutput.setMetadataObjectTypes(metadataOutput.availableMetadataObjectTypes)
    } else {
      log(LogLevel.Error) { "Cannot add metadata output to capture session" }
      return
    }

    cameraPreview = AVCaptureVideoPreviewLayer(session = captureSession).apply {
      videoGravity = AVLayerVideoGravityResizeAspectFill
      frame = viewLayer.bounds
      viewLayer.addSublayer(this)
    }

    scope.launch { captureSession.startRunning() }
  }

  fun dispose() {
    if (::captureSession.isInitialized) {
      scope.launch {
        captureSession.dispose()
        scope.cancel()
      }
    } else {
      scope.cancel()
    }
  }

  fun setFrame(rect: CValue<CGRect>) {
    cameraPreview.setFrame(rect)
  }

  override fun captureOutput(
    output: AVCaptureOutput,
    didOutputMetadataObjects: List<*>,
    fromConnection: AVCaptureConnection,
  ) {
    scope.launch {
      didOutputMetadataObjects.firstOrNull()
        ?.let { it as? AVMetadataMachineReadableCodeObject }
        ?.takeIf { it.type == AVMetadataObjectTypeQRCode && !it.stringValue.isNullOrBlank() }
        ?.stringValue
        ?.run(onDataScanned)
    }
  }

  /** Remove all session configuration and stop it. */
  private fun AVCaptureSession.dispose() {
    beginConfiguration()
    inputs.filterIsInstance<AVCaptureInput>().forEach(::removeInput)
    outputs.filterIsInstance<AVCaptureOutput>().forEach(::removeOutput)
    commitConfiguration()
    stopRunning()
  }
}

package build.wallet.ui.app.qrcode

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview.Builder
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import build.wallet.logging.*
import build.wallet.statemachine.send.QrCodeScanBodyModel

@Composable
internal actual fun NativeQrCodeScanner(model: QrCodeScanBodyModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val hasCameraPermission by remember {
    mutableStateOf(
      value =
        ContextCompat.checkSelfPermission(
          context,
          permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    )
  }

  val previewView =
    remember {
      PreviewView(context)
    }

  val cameraProvider =
    remember {
      ProcessCameraProvider.getInstance(context).get()
    }

  DisposableEffect(cameraProvider) {
    onDispose {
      // when this composable leaves composition, close all instances of camera
      cameraProvider.unbindAll()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (hasCameraPermission) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          context.createQrCodeImageAnalysisUseCase(
            lifecycleOwner = lifecycleOwner,
            cameraProvider = cameraProvider,
            previewView = previewView,
            onQrCodeDetected = model.onQrCodeScanned
          )

          previewView
        }
      )
    }
  }
}

private fun Context.createQrCodeImageAnalysisUseCase(
  lifecycleOwner: LifecycleOwner,
  cameraProvider: ProcessCameraProvider,
  previewView: PreviewView,
  onQrCodeDetected: (String) -> Unit,
) {
  val preview =
    Builder()
      .build()
      .apply {
        setSurfaceProvider(previewView.surfaceProvider)
      }
  val selector =
    CameraSelector.Builder()
      .requireLensFacing(CameraSelector.LENS_FACING_BACK)
      .build()

  val imageAnalysis =
    ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()

  imageAnalysis.setAnalyzer(
    ContextCompat.getMainExecutor(this),
    QrCodeImageAnalyzer(
      onQrCodeDetected = onQrCodeDetected
    )
  )

  val useCaseGroup =
    UseCaseGroup.Builder()
      .addUseCase(preview)
      .addUseCase(imageAnalysis)
      .build()

  try {
    cameraProvider.bindToLifecycle(
      lifecycleOwner,
      selector,
      useCaseGroup
    )
  } catch (e: IllegalStateException) {
    logError(throwable = e) { "Unable to bind camera because ${e.localizedMessage}" }
  } catch (e: IllegalArgumentException) {
    logError(throwable = e) { "Unable to resolve camera because ${e.localizedMessage}" }
  }
}

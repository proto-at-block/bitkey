package build.wallet.ui.components.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.min
import build.wallet.ui.components.loading.LoadingIndicator
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.makeFromEncoded
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.QRCodeGenerator
import platform.CoreImage.createCGImage
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@Composable
actual fun QrCode(
  modifier: Modifier,
  data: String?,
  cellShape: CellShape,
) {
  BoxWithConstraints {
    val qrCodeImage by produceState<ImageBitmap?>(null, data, cellShape, constraints) {
      data ?: return@produceState
      val qrCodeSize = minOf(constraints.maxWidth, constraints.maxHeight)
      value = withContext(Dispatchers.Default) {
        createQRCodeUIImage(data, qrCodeSize)?.asImageBitmap()
      }
    }

    val qrCodeSizeDp = remember(constraints) { min(maxWidth, maxHeight) }
    when (val currentQrCodeImage = qrCodeImage) {
      null -> {
        Box(modifier = modifier.size(qrCodeSizeDp)) {
          // Show loading spinner while we are waiting for data
          LoadingIndicator(
            modifier =
              Modifier.size(qrCodeSizeDp / 4)
                .align(Alignment.Center)
          )
        }
      }
      else -> {
        Image(
          bitmap = currentQrCodeImage,
          contentDescription = null,
          modifier = Modifier.size(qrCodeSizeDp)
        )
      }
    }
  }
}

private fun UIImage.asImageBitmap(): ImageBitmap? {
  val imageData = UIImagePNGRepresentation(this) ?: return null
  return Image.makeFromEncoded(imageData).toComposeImageBitmap()
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun createQRCodeUIImage(
  data: String,
  qrCodeSize: Int,
): UIImage? {
  val dataAsByteArray = data.toByteArray()
  val dataAsNSData = dataAsByteArray.usePinned { pin ->
    NSData.create(
      bytes = pin.addressOf(0),
      length = dataAsByteArray.size.toULong()
    )
  }

  val qrCodeImage = CIFilter.QRCodeGenerator()
    .apply { setValue(value = dataAsNSData, forKey = "inputMessage") }
    .outputImage ?: return null
  val (scaleX, scaleY) = qrCodeImage.extent.useContents {
    Pair(
      qrCodeSize.toDouble() / size.width,
      qrCodeSize.toDouble() / size.height
    )
  }
  val scaleTransform = CGAffineTransformMakeScale(scaleX, scaleY)
  val transformedImage = qrCodeImage.imageByApplyingTransform(scaleTransform)

  return CIContext()
    .createCGImage(transformedImage, transformedImage.extent)
    .run(UIImage::imageWithCGImage)
}

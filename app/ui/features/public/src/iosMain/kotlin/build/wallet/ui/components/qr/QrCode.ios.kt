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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.min
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.theme.WalletTheme
import io.ktor.utils.io.core.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.makeFromEncoded
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreImage.*
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
  val color = WalletTheme.colors.foreground
  val backgroundColor = WalletTheme.colors.background
  BoxWithConstraints {
    val qrCodeImage by produceState<ImageBitmap?>(null, data, cellShape, constraints, color, backgroundColor) {
      data ?: return@produceState
      val qrCodeSize = minOf(constraints.maxWidth, constraints.maxHeight)
      value = withContext(Dispatchers.Default) {
        createQRCodeUIImage(
          data = data,
          qrCodeSize = qrCodeSize,
          color = color,
          backgroundColor = backgroundColor
        )?.asImageBitmap()
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

@OptIn(ExperimentalForeignApi::class)
private fun createQRCodeUIImage(
  data: String,
  qrCodeSize: Int,
  color: Color,
  backgroundColor: Color,
): UIImage? {
  val dataAsByteArray = data.toByteArray()
  val dataAsNSData = dataAsByteArray.usePinned { pin ->
    NSData.create(
      bytes = pin.addressOf(0),
      length = dataAsByteArray.size.toULong()
    )
  }

  val qrCodeFilter = CIFilter.QRCodeGenerator()
    .apply { setValue(value = dataAsNSData, forKey = "inputMessage") }

  val colorFilter = CIFilter.falseColorFilter()
    .apply {
      setValue(qrCodeFilter.outputImage, "inputImage")
      setValue(
        CIColor(
          red = color.red.toDouble(),
          green = color.green.toDouble(),
          blue = color.blue.toDouble()
        ),
        "inputColor0"
      )
      setValue(
        CIColor(
          red = backgroundColor.red.toDouble(),
          green = backgroundColor.green.toDouble(),
          blue = backgroundColor.blue.toDouble()
        ),
        "inputColor1"
      )
    }

  val qrCodeImage = colorFilter.outputImage ?: return null

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

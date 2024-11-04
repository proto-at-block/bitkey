package build.wallet.ui.app.qrcode

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

internal class QrCodeImageAnalyzer(
  private val onQrCodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {
  private val supportedImageFormats =
    listOf(
      ImageFormat.YUV_420_888,
      ImageFormat.YUV_422_888,
      ImageFormat.YUV_444_888
    )

  private val reader = QRCodeReader()

  @Suppress("SwallowedException")
  override fun analyze(image: ImageProxy) {
    if (supportedImageFormats.contains(image.format)) {
      val bytes = image.planes.first().buffer.toByteArray()

      val rotatedByteArray =
        rotateImageByteArray(
          array = bytes,
          width = image.width,
          height = image.height,
          rotationDegrees = image.imageInfo.rotationDegrees
        )

      val newWidth =
        when (image.imageInfo.rotationDegrees) {
          90, 270 -> image.height
          else -> image.width
        }
      val newHeight =
        when (image.imageInfo.rotationDegrees) {
          90, 270 -> image.width
          else -> image.height
        }

      val source =
        PlanarYUVLuminanceSource(
          rotatedByteArray,
          newWidth,
          newHeight,
          0,
          0,
          newWidth,
          newHeight,
          false
        )

      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

      try {
        val result = reader.decode(binaryBitmap)
        onQrCodeDetected(result.text)
      } catch (_: Exception) {
        // no-op ignore exception
        // swallow exception since these are fired during focus, positioning, etc
        // we don't log to prevent noisy logging
      } finally {
        image.close()
      }
    }
  }

  private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    return ByteArray(remaining()).also {
      get(it)
    }
  }

  private fun rotateImageByteArray(
    array: ByteArray,
    width: Int,
    height: Int,
    rotationDegrees: Int,
  ): ByteArray {
    val rotated = ByteArray(height * width)

    for (y in 0 until height) { // we scan the array by rows
      for (x in 0 until width) {
        when (rotationDegrees) {
          90 -> rotated[x * height + height - y - 1] = array[x + y * width]
          180 -> rotated[width * (height - y - 1) + width - x - 1] = array[x + y * width]
          270 -> rotated[y + x * height] = array[y * width + width - x - 1]
          else -> return array
        }
      }
    }

    return rotated
  }
}

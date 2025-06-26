package build.wallet.emergencyexitkit

import android.graphics.Bitmap
import android.graphics.Color
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream

@BitkeyInject(AppScope::class)
class EmergencyExitKitQrCodeGeneratorImpl : EmergencyExitKitQrCodeGenerator {
  override suspend fun imageBytes(
    width: Float,
    height: Float,
    contents: String,
  ): Result<ByteString, EmergencyExitKitQrCodeError> =
    generateQRCode(width, height, contents)
      .map { it.toByteString() }

  private suspend fun generateQRCode(
    width: Float,
    height: Float,
    contents: String,
  ): Result<Bitmap, EmergencyExitKitQrCodeError> =
    catchingResult {
      withContext(Dispatchers.Default) {
        val widthInt = width.toInt()
        val heightInt = height.toInt()

        val qrCodeWriter = QRCodeWriter()
        val bitMatrix =
          qrCodeWriter.encode(
            contents,
            BarcodeFormat.QR_CODE,
            widthInt,
            heightInt,
            mapOf(EncodeHintType.MARGIN to 0)
          )
        val bitmap = Bitmap.createBitmap(widthInt, heightInt, Bitmap.Config.ARGB_8888)
        for (x in 0 until widthInt) {
          for (y in 0 until heightInt) {
            // Only fill the black pixels and leave the rest transparent.
            if (bitMatrix[x, y]) {
              bitmap.setPixel(x, y, Color.BLACK)
            }
          }
        }
        bitmap
      }
    }
      .mapError { EmergencyExitKitQrCodeError(it) }
      .logFailure { "Failed to generate QR Code from string contents" }

  private suspend fun Bitmap.toByteString(): ByteString {
    return withContext(Dispatchers.IO) {
      val outputStream = ByteArrayOutputStream()
      this@toByteString.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
      outputStream.toByteArray().toByteString()
    }
  }
}

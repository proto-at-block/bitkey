package build.wallet.statemachine.qr

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

actual suspend fun String.toQrMatrix(): Result<QRMatrix, Error> {
  try {
    val matrix = Encoder.encode(
      this,
      ErrorCorrectionLevel.H,
      mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 0,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
      )
    ).matrix

    val width = matrix.width
    val height = matrix.height
    val flattenedData = BooleanArray(width * height) { index ->
      val row = index / width
      val col = index % width
      matrix.get(col, row) != 0.toByte()
    }

    return Ok(QRMatrix(width, flattenedData))
  } catch (e: WriterException) {
    return Err(
      Error(
        QrCodeGenerationException(
          message = "Failed to generate QR code: ${e.message}",
          cause = e
        )
      )
    )
  }
}

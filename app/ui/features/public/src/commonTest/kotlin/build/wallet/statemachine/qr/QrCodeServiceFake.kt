package build.wallet.statemachine.qr

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class QrCodeServiceFake : QrCodeService {
  private val defaultQrMatrix = QRMatrix(37, BooleanArray(37 * 37))

  var result: Result<QRMatrix, Error> = Ok(defaultQrMatrix)

  var lastGeneratedQrCodeData: String? = null

  override suspend fun generateQrCode(data: String): Result<QRMatrix, Error> {
    lastGeneratedQrCodeData = data
    return result
  }

  fun reset() {
    result = Ok(defaultQrMatrix)
    lastGeneratedQrCodeData = null
  }
}

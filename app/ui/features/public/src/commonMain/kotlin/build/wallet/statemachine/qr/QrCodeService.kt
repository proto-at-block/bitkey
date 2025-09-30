package build.wallet.statemachine.qr

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface QrCodeService {
  suspend fun generateQrCode(data: String): Result<QRMatrix, Error>
}

@BitkeyInject(ActivityScope::class)
class QrCodeServiceImpl : QrCodeService {
  override suspend fun generateQrCode(data: String): Result<QRMatrix, Error> {
    return withContext(Dispatchers.Default) {
      data.toQrMatrix()
    }
  }
}

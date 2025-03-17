package build.wallet.emergencyaccesskit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

@BitkeyInject(AppScope::class)
class EmergencyAccessKitQrCodeGeneratorImpl : EmergencyAccessKitQrCodeGenerator {
  // TODO("TODO: BKR-693, implement JVM version of this")
  override suspend fun imageBytes(
    width: Float,
    height: Float,
    contents: String,
  ): Result<ByteString, EmergencyAccessKitQrCodeError> = Ok(ByteString.EMPTY)
}

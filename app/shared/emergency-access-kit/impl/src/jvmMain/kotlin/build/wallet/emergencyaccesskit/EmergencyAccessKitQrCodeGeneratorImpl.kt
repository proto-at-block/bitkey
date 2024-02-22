package build.wallet.emergencyaccesskit

import com.github.michaelbull.result.Result
import okio.ByteString

actual class EmergencyAccessKitQrCodeGeneratorImpl actual constructor() : EmergencyAccessKitQrCodeGenerator {
  override suspend fun imageBytes(
    width: Float,
    height: Float,
    contents: String,
  ): Result<ByteString, EmergencyAccessKitQrCodeError> {
    TODO("TODO: BKR-693, implement JVM version of this")
  }
}

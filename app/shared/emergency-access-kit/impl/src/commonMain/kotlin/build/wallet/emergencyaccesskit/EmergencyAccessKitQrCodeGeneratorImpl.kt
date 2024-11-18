package build.wallet.emergencyaccesskit

import com.github.michaelbull.result.Result
import okio.ByteString

expect class EmergencyAccessKitQrCodeGeneratorImpl() : EmergencyAccessKitQrCodeGenerator {
  override suspend fun imageBytes(
    width: Float,
    height: Float,
    contents: String,
  ): Result<ByteString, EmergencyAccessKitQrCodeError>
}

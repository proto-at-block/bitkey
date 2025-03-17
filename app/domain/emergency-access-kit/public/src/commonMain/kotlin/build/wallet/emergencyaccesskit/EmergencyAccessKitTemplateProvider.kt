package build.wallet.emergencyaccesskit

import com.github.michaelbull.result.Result
import okio.ByteString

interface EmergencyAccessKitTemplateProvider {
  suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError>
}

data class EmergencyAccessKitTemplateUnavailableError(
  override val cause: Throwable?,
) : Error()

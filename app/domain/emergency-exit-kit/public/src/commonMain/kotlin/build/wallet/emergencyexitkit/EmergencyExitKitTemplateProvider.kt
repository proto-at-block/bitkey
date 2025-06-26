package build.wallet.emergencyexitkit

import com.github.michaelbull.result.Result
import okio.ByteString

interface EmergencyExitKitTemplateProvider {
  suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyExitKitTemplateUnavailableError>
}

data class EmergencyExitKitTemplateUnavailableError(
  override val cause: Throwable?,
) : Error()

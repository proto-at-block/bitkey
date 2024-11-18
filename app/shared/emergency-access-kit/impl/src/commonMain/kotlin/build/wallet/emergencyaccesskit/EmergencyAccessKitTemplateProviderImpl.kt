package build.wallet.emergencyaccesskit

import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.Result
import okio.ByteString

expect class EmergencyAccessKitTemplateProviderImpl(
  platformContext: PlatformContext,
) : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError>
}

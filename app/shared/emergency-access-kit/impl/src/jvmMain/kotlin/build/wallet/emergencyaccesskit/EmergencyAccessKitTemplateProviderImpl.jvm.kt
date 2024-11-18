package build.wallet.emergencyaccesskit

import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

actual class EmergencyAccessKitTemplateProviderImpl actual constructor(
  platformContext: PlatformContext,
) : EmergencyAccessKitTemplateProvider {
  actual override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> =
    Ok(ByteString.EMPTY)
}

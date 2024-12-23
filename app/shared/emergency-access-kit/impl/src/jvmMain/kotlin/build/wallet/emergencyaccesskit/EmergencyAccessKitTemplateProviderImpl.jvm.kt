package build.wallet.emergencyaccesskit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

@BitkeyInject(AppScope::class)
class EmergencyAccessKitTemplateProviderImpl : EmergencyAccessKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyAccessKitTemplateUnavailableError> =
    Ok(ByteString.EMPTY)
}

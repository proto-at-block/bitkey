package build.wallet.emergencyexitkit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

@BitkeyInject(AppScope::class)
class EmergencyExitKitTemplateProviderImpl : EmergencyExitKitTemplateProvider {
  override suspend fun pdfTemplateBytes(): Result<ByteString, EmergencyExitKitTemplateUnavailableError> =
    Ok(ByteString.EMPTY)
}

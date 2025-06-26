package build.wallet.emergencyexitkit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class EmergencyExitKitAppKeyParametersProviderImpl(
  private val payloadCreator: EmergencyExitPayloadCreator,
  private val emergencyExitKitPayloadDecoder: EmergencyExitKitPayloadDecoder,
) : EmergencyExitKitAppKeyParametersProvider {
  override suspend fun parameters(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<AppKeyParameters, Error> =
    payloadCreator
      .create(keybox, sealedCsek)
      .map { emergencyExitKitPayloadDecoder.encode(it) }
      .map { AppKeyParameters(appKeyCharacters = it, appKeyQRCodeText = it) }
}

package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class EmergencyAccessKitMobileKeyParametersProviderImpl(
  private val payloadCreator: EmergencyAccessPayloadCreator,
  private val emergencyAccessKitPayloadDecoder: EmergencyAccessKitPayloadDecoder,
) : EmergencyAccessKitMobileKeyParametersProvider {
  override suspend fun parameters(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<MobileKeyParameters, Error> =
    payloadCreator
      .create(keybox, sealedCsek)
      .map { emergencyAccessKitPayloadDecoder.encode(it) }
      .map { MobileKeyParameters(mobileKeyCharacters = it, mobileKeyQRCodeText = it) }
}

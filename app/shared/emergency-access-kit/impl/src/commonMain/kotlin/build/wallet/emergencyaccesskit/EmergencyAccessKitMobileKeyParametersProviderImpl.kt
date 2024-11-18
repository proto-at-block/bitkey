package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class EmergencyAccessKitMobileKeyParametersProviderImpl(
  private val payloadCreator: EmergencyAccessPayloadCreator,
  private val emergencyAccessKitPayloadDecoder: EmergencyAccessKitPayloadDecoderImpl,
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

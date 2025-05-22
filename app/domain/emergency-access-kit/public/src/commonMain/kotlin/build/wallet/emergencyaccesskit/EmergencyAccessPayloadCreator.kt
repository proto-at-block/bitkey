package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

interface EmergencyAccessPayloadCreator {
  /**
   * Creates an [EmergencyAccessKitPayload] backup from a [Keybox].
   *
   * @param keybox used to generate the Emergency Exit Kit payload.
   * @param sealedCsek the sealed CSEK to use to encrypt the backup. Expected that as this point,
   * unsealed [Csek] is persisted in [CsekDao]. If not, returns [CsekMissing] error.
   */
  suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyAccessKitPayload, EmergencyAccessPayloadCreatorError>

  sealed class EmergencyAccessPayloadCreatorError : Error() {
    /** Error describing that the CSEK is missing from the [CsekDao]. */
    data class CsekMissing(
      override val cause: Throwable? = null,
    ) : EmergencyAccessPayloadCreatorError()

    /** Error describing that the app private key was missing from the [AppPrivateKeyDao]. */
    data class AppPrivateKeyMissing(
      override val cause: Throwable? = null,
    ) : EmergencyAccessPayloadCreatorError()
  }
}

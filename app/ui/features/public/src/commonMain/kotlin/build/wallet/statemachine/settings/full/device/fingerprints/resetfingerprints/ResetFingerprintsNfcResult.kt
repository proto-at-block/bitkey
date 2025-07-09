package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.grants.GrantRequest

/**
 * Represents the possible outcomes of the NFC session specifically for fingerprint reset.
 */
sealed interface ResetFingerprintsNfcResult {
  data class GrantRequestRetrieved(val grantRequest: GrantRequest) : ResetFingerprintsNfcResult

  /** The grant was provided successfully. */
  object ProvideGrantSuccess : ResetFingerprintsNfcResult

  /** A firmware update is required to support fingerprint reset. */
  object FwUpRequired : ResetFingerprintsNfcResult
}

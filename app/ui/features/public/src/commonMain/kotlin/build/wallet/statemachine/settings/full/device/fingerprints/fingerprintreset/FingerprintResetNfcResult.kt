package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.grants.GrantRequest

/**
 * Represents the possible outcomes of the NFC session specifically for fingerprint reset.
 */
sealed interface FingerprintResetNfcResult {
  data class GrantRequestRetrieved(val grantRequest: GrantRequest) : FingerprintResetNfcResult

  /** The grant was provided successfully. */
  object ProvideGrantSuccess : FingerprintResetNfcResult

  /** The grant could not be provided. */
  object ProvideGrantFailed : FingerprintResetNfcResult

  /** A firmware update is required to support fingerprint reset. */
  object FwUpRequired : FingerprintResetNfcResult
}

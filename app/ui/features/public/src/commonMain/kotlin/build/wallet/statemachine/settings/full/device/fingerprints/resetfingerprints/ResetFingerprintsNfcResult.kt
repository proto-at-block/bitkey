package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.grants.GrantRequest

/**
 * Represents the possible outcomes of the NFC session specifically for fingerprint reset.
 */
sealed interface ResetFingerprintsNfcResult {
  data class GrantRequestRetrieved(val grantRequest: GrantRequest) : ResetFingerprintsNfcResult

  object ProvideGrantSuccess : ResetFingerprintsNfcResult

  object ProvideGrantFailure : ResetFingerprintsNfcResult
}

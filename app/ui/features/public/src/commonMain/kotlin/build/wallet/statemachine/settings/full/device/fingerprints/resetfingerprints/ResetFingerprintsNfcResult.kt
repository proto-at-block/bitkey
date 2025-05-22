package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

/**
 * Represents the possible outcomes of the NFC session specifically for fingerprint reset.
 */
sealed interface ResetFingerprintsNfcResult {
  object Success : ResetFingerprintsNfcResult
}

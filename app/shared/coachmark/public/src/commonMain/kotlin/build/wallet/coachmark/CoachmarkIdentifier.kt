package build.wallet.coachmark

/**
 * Coachmark identifiers
 */
enum class CoachmarkIdentifier(
  val string: String,
) {
  // Add new coachmark identifiers here
  HiddenBalanceCoachmark("hidden_balance_coachmark"),
  MultipleFingerprintsCoachmark("multiple_fingerprints_coachmark"),
  BiometricUnlockCoachmark("biometric_unlock_coachmark"),
}

package build.wallet.availability

/**
 * Result of age range verification for App Store Accountability Act compliance.
 */
sealed interface AgeRangeVerificationResult {
  /**
   * User is allowed to use the app (verified 18+, not in applicable jurisdiction, or fail-open).
   */
  data object Allowed : AgeRangeVerificationResult

  /**
   * User is denied access (verified as minor).
   */
  data object Denied : AgeRangeVerificationResult
}

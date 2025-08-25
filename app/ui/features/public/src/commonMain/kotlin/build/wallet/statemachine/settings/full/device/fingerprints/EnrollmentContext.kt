package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.grants.Grant

/**
 * Defines the context in which fingerprint enrollment is happening.
 * This affects behavior like whether to show instructions and whether to delete existing fingerprints.
 */
sealed interface EnrollmentContext {
  /** Adding a fingerprint after initial setup */
  data object AddingFingerprint : EnrollmentContext

  /** Enrolling a fingerprint as part of the fingerprint reset process */
  data class FingerprintReset(
    val grant: Grant,
  ) : EnrollmentContext
}

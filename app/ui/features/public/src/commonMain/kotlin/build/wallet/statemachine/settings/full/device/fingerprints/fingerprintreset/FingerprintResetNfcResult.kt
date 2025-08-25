package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.firmware.EnrolledFingerprints
import build.wallet.grants.GrantRequest

/**
 * Represents the possible outcomes when requesting a grant from the device for fingerprint reset.
 */
sealed interface FingerprintResetGrantRequestResult {
  /** The grant request was successful and the device is ready to begin fingerprint enrollment. */
  data class GrantRequestRetrieved(
    val grantRequest: GrantRequest,
  ) : FingerprintResetGrantRequestResult

  /** A firmware update is required to support fingerprint reset. */
  object FwUpRequired : FingerprintResetGrantRequestResult
}

/**
 * Represents the possible outcomes when providing a signed grant to the device for fingerprint reset.
 */
sealed interface FingerprintResetGrantProvisionResult {
  /** Grant has already been delivered and fingerprint enrollment is complete. */
  data class FingerprintResetComplete(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : FingerprintResetGrantProvisionResult

  /** The grant was provided successfully. */
  object ProvideGrantSuccess : FingerprintResetGrantProvisionResult

  /** The grant could not be provided. */
  object ProvideGrantFailed : FingerprintResetGrantProvisionResult
}

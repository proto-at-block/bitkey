package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

/**
 * Provides convenience APIs that incorporate business logic for interacting with fingerprint
 * apis from [NfcCommands].
 *
 * Namely, these APIs ensure that the hardware data and software state will
 * converge to a common end state; since fingerprint enrollment is an out-of-band process for the
 * app, either the app or hardware can de-sync its state.
 *
 * For example, the application could start a new enrollment, putting the hardware into an enrolling
 * state, then the user could back out of th enrollment on the app but still complete the enrollment
 * on the hardware. In this case the application will not know about the completed enrollment until
 * another fingerprint operation is attempted. These APIs help to clean up this mixed state, such as
 * by cancelling an enrollment, or rolling forward or backward a completed hardware enrollment.
 */
interface FingerprintNfcCommands {
  /**
   * Cleans up any pending state and then starts a new fingerprint enrollment.
   */
  suspend fun prepareForFingerprintEnrollment(
    commands: NfcCommands,
    session: NfcSession,
    enrolledFingerprints: EnrolledFingerprints,
    fingerprintToEnroll: FingerprintHandle,
  ): Boolean

  /**
   * Checks the current enrollment status, retrieving the latest enrollments if
   * enrollment has successfully completed.
   */
  suspend fun checkEnrollmentStatus(
    commands: NfcCommands,
    session: NfcSession,
    enrolledFingerprints: EnrolledFingerprints,
    fingerprintHandle: FingerprintHandle,
  ): EnrollmentStatusResult
}

sealed interface EnrollmentStatusResult {
  data object Unspecified : EnrollmentStatusResult

  data object Incomplete : EnrollmentStatusResult

  data class Complete(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : EnrollmentStatusResult
}

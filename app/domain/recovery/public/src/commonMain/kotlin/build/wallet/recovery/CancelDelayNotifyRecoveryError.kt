package build.wallet.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode

sealed class CancelDelayNotifyRecoveryError : Error() {
  /**
   * Canceling D&N recovery requires additional comms verification. Consumer should
   * initiate the comms verification flow and then retry the cancellation.
   */
  data class CommsVerificationRequiredError(
    override val cause: Error,
  ) : CancelDelayNotifyRecoveryError()

  /**
   * Corresponds to an error when canceling D&N recovery with f8e:
   * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
   * retry the recovery cancellation.
   * - due to some server error. In this case, we are unlikely to be able to cancel recovery.
   * - due to client error - e.g. bad input or serialization bug.
   */
  data class F8eCancelDelayNotifyError(
    val error: F8eError<CancelDelayNotifyRecoveryErrorCode>,
  ) : CancelDelayNotifyRecoveryError() {
    override val cause = error.error
  }

  /**
   * We successfully cleared recovery with f8e but failed to clear local database state:
   * - due to a corrupted database.
   * - due to some rare I/O error
   */
  data class LocalCancelDelayNotifyError(
    override val cause: Error,
  ) : CancelDelayNotifyRecoveryError()
}

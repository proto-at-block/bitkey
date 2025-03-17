package build.wallet.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode

sealed class CancelDelayNotifyRecoveryError : Error() {
  /**
   * Corresponds to an error when canceling D&N recovery with f8e:
   * - due to needing additional comms verification. In this case, we initiate the comms flow.
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

package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import com.github.michaelbull.result.Result

interface RecoveryCanceler {
  /**
   * Cancels in-progress recovery with f8e.
   *
   * @param [fullAccountId] which account currently has recovery in progress to cancel.
   */
  suspend fun cancel(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, RecoveryCancelerError>

  sealed class RecoveryCancelerError : Error() {
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
    ) : RecoveryCancelerError() {
      override val cause = error.error
    }

    /**
     * We successfully cleared recovery with f8e but failed to clear local database state:
     * - due to a corrupted database.
     * - due to some rare I/O error
     */
    data class FailedToClearRecoveryStateError(
      override val cause: DbError,
    ) : RecoveryCancelerError()
  }
}

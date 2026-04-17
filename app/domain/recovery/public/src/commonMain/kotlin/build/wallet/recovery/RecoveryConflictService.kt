package build.wallet.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.bitkey.f8e.FullAccountId
import com.github.michaelbull.result.Result

interface RecoveryConflictService {
  /**
   * Cancels a conflicting in-progress D&N recovery.
   * Builds an app-signed CancelConflictingRecovery action proof for W3 accounts.
   */
  suspend fun cancelRecoveryConflict(
    fullAccountId: FullAccountId,
  ): Result<Unit, RecoveryConflictServiceError>
}

sealed class RecoveryConflictServiceError : Error() {
  /**
   * F8e requires notification comms verification before cancellation can continue.
   */
  data class CommsVerificationRequired(
    override val cause: Error,
  ) : RecoveryConflictServiceError()

  data class F8eCancelRecoveryConflictError(
    val error: F8eError<CancelDelayNotifyRecoveryErrorCode>,
  ) : RecoveryConflictServiceError() {
    override val cause = error.error
  }

  data class LocalCancelRecoveryConflictError(
    override val cause: Error,
  ) : RecoveryConflictServiceError()
}

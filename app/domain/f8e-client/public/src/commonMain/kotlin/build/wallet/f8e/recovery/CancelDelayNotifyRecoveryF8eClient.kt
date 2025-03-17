package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

/**
 * Cancel delay + notify recovery.
 */
interface CancelDelayNotifyRecoveryF8eClient {
  /**
   * Request to cancel an active delay + notify recovery.
   */
  suspend fun cancel(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<CancelDelayNotifyRecoveryErrorCode>>
}

package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

/**
 * Domain service for managing Lost App + Cloud Delay & Notify recovery.
 *
 * TODO: move remaining domain operations here.
 */
interface LostAppAndCloudRecoveryService {
  /**
   * Cancels in progress D&N recovery using hardware proof of possession.
   */
  suspend fun cancelRecovery(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    hwProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, CancelDelayNotifyRecoveryError>
}

package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import com.github.michaelbull.result.Result

/**
 * Domain service for managing Lost Hardware Delay & Notify recovery.
 *
 * TODO: move remaining domain operations here.
 */
interface LostHardwareRecoveryService {
  /**
   * Cancels in progress D&N recovery using app proof of possession.
   */
  suspend fun cancelRecovery(
    accountId: FullAccountId,
  ): Result<Unit, CancelDelayNotifyRecoveryError>
}

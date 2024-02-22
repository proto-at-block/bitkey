package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface GetDelayNotifyRecoveryStatusService {
  /**
   * Retrieve current status of the delay and notify recovery.
   *
   * Returns `null` if there's no an active recovery in progress.
   */
  suspend fun getStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ServerRecovery?, NetworkingError>
}

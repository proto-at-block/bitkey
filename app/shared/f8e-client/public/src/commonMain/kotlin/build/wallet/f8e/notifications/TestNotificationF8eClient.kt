package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface TestNotificationF8eClient {
  /**
   * Get a test notification from f8e
   */
  suspend fun notification(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error>
}

package build.wallet.limit

import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Result

interface MobilePayDisabler {
  /**
   * Disables Mobile Pay.
   */
  suspend fun disable(account: FullAccount): Result<Unit, Unit>
}

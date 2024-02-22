package build.wallet.limit

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface MobilePayDisabler {
  /**
   * Disables Mobile Pay.
   */
  suspend fun disable(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, Unit>
}

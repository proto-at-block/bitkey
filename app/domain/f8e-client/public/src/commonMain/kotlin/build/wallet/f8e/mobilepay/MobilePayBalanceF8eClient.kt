package build.wallet.f8e.mobilepay

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.limit.MobilePayBalance
import com.github.michaelbull.result.Result

interface MobilePayBalanceF8eClient {
  /**
   * Asks the server to give us information about the user's configured mobile pay spending limit
   * and balance that the server will be willing to cosign for.
   */
  suspend fun getMobilePayBalance(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<MobilePayBalance, MobilePayBalanceFailure>
}

sealed interface MobilePayBalanceFailure {
  data class F8eError(val error: NetworkingError) : MobilePayBalanceFailure

  data object UnsupportedFiatCurrencyError : MobilePayBalanceFailure
}

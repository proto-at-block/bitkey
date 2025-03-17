package build.wallet.f8e.mobilepay

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.MobilePayErrorCode
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import build.wallet.limit.SpendingLimit
import com.github.michaelbull.result.Result

interface MobilePaySpendingLimitF8eClient {
  /**
   * Asks the server to set up and establish the giving spending limit.
   * Returns whether it was successful or not.
   */
  suspend fun setSpendingLimit(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    limit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError>

  /**
   * Disables the user's Mobile Pay. Returns 405, if unavailable.
   */
  suspend fun disableMobilePay(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, F8eError<MobilePayErrorCode>>
}

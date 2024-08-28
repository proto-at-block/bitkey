package build.wallet.limit

import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Service to manage Mobile Pay limit.
 */
interface MobilePayService {
  /**
   * Emits the latest [MobilePayData] for the currently active account.
   */
  val mobilePayData: StateFlow<MobilePayData?>

  /**
   * Sets a new Mobile Pay spending limit.
   *
   * The most recently active spending limit is updated locally and is accessible through [mobilePayData].
   */
  suspend fun setLimit(
    account: FullAccount,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error>

  /**
   * Disables Mobile Pay on f8e and marks local spending limit as inactive.
   *
   * The most recently active spending limit, if any, is preserved locally and is accessible through
   * [mobilePayData].
   */
  suspend fun disable(account: FullAccount): Result<Unit, Error>

  /**
   * Deletes local spending limits without disabling mobile pay on f8e.
   */
  suspend fun deleteLocal(): Result<Unit, Error>
}

package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
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

  /**
   * Signs a PSBT with the mobile pay keyset from f8e.
   */
  suspend fun signPsbtWithMobilePay(psbt: Psbt): Result<Psbt, Error>

  /**
   * Computes if the [transactionAmount] is above the spending limit
   *
   * @param transactionAmount - The amount to determine the status for
   */
  fun getDailySpendingLimitStatus(transactionAmount: BitcoinMoney): DailySpendingLimitStatus

  /**
   * Computes if the [transactionAmount] is above the spending limit
   *
   * @param transactionAmount - The amount to determine the status for
   */
  fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinTransactionSendAmount,
  ): DailySpendingLimitStatus
}

/**
 * Status of the daily spending limit based on the days sent transactions
 */
sealed class DailySpendingLimitStatus {
  /**
   * The status is RequiresHardware if there is no spending limit, or the days sent transactions with
   * the current transaction are above the active limit
   */
  data object RequiresHardware : DailySpendingLimitStatus()

  /**
   * The status is Mobile Pay Available if the days sent transactions with the current transaction
   * are above the below limit
   */
  data object MobilePayAvailable : DailySpendingLimitStatus()
}

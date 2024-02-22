package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.money.BitcoinMoney

/**
 * Use to determine if a given transaction is above the users daily spending limit
 */
interface MobilePaySpendingPolicy {
  /**
   * Computes if the [transactionAmount] is above the spending limit
   *
   * @param transactionAmount - The amount to determine the status for
   * @param latestTransactions - The existing transactions to use to calculate the
   * total daily amount spent
   */
  fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinMoney,
    latestTransactions: List<BitcoinTransaction>,
    mobilePayBalance: MobilePayBalance?,
  ): DailySpendingLimitStatus
}

/**
 * Status of the daily spending limit based on the days sent transactions
 */
sealed class DailySpendingLimitStatus(open val spendingLimit: SpendingLimit?) {
  /**
   * The status is RequiresHardware if there is no spending limit, or the days sent transactions with
   * the current transaction are above the active limit
   *
   * @property spendingLimit - The active spending limit, null if there is none
   */
  data class RequiresHardware(
    override val spendingLimit: SpendingLimit?,
  ) : DailySpendingLimitStatus(spendingLimit)

  /**
   * The status is Mobile Pay Available if the days sent transactions with the current transaction
   * are above the below limit
   *
   * @property spendingLimit - The current active spending limit
   */
  data class MobilePayAvailable(
    override val spendingLimit: SpendingLimit,
  ) : DailySpendingLimitStatus(spendingLimit)
}

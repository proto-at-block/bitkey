package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import kotlinx.datetime.TimeZone

/**
 * A object used for calculations for a customer's spending limit policy when building a transaction.
 *
 * A daily spending limit is set up by the customer in the app. They specify
 * the amount that can be spent without hardware approval on a daily (resetting
 * every day at 3AM in the set timezone) basis.
 */
interface MobilePayRemainingSpendingCalculator {
  /**
   * Returns remaining spending amount in BTC as per limit's timeframe.
   *
   * @param allTransactions - previous transactions used to calculate total spent amount, that
   * is bound by spending limit.
   * @param limitAmountInBtc - current daily spending limit for calculating remaining spending amount.
   * @param limitTimeZone - timezone to use for calculating
   */
  fun remainingSpendingAmount(
    allTransactions: List<BitcoinTransaction>,
    limitAmountInBtc: BitcoinMoney,
    limitTimeZone: TimeZone,
  ): Money
}

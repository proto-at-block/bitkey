package build.wallet.bitcoin.fees

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney

interface BitcoinTransactionBaseCalculator {
  /**
   * Calculates a "base" amount needed for this transaction to even be possible.
   *
   * 1. If exact, we add the user's desired `sendAmount` with the _slowest_ (and likely lowest) fee
   * amount.
   * 2. If sending all, we ensure that this number is minimally their total balance minus the
   * fastest (and likely highest) fee amount. If it is negative, the customer has insufficient funds.
   */
  fun minimumSatsRequiredForTransaction(
    walletBalance: BitcoinBalance,
    sendAmount: BitcoinTransactionSendAmount,
    fees: Map<EstimatedTransactionPriority, Fee>,
  ): BitcoinMoney
}

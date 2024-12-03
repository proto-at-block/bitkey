package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney

/**
 * Represents the details of a Bitcoin transaction.
 */
sealed interface TransactionDetails {
  val transferAmount: BitcoinMoney
  val feeAmount: BitcoinMoney

  /**
   * A regular send transaction.
   *
   * @property transferAmount Amount of bitcoin to send
   * @property feeAmount Amount of fees to offer for the transaction
   * @property estimatedTransactionPriority Selected transaction priority for this transaction.
   */
  data class Regular(
    override val transferAmount: BitcoinMoney,
    override val feeAmount: BitcoinMoney,
    val estimatedTransactionPriority: EstimatedTransactionPriority,
  ) : TransactionDetails

  /**
   * A speed-up transaction.
   *
   * @property transferAmount Amount of bitcoin to send
   * @property feeAmount Amount of fees being offered for the child transaction.
   * @property oldFeeAmount Amount of fees offered for the parent transaction.
   */
  data class SpeedUp(
    override val transferAmount: BitcoinMoney,
    override val feeAmount: BitcoinMoney,
    val oldFeeAmount: BitcoinMoney,
  ) : TransactionDetails
}

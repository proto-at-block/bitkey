package build.wallet.statemachine.send

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine responsible for rendering money details of a Bitcoin transaction (transfer, fee,
 * total amounts, as well as transaction speed). Handles currency conversion.
 */
interface TransactionDetailsCardUiStateMachine : StateMachine<TransactionDetailsCardUiProps, TransactionDetailsModel>

/**
 * @property [transferBitcoinAmount] - the actual amount of bitcoin that was sent in a transaction
 * excluding fee.
 * @property [feeBitcoinAmount] - the mining fee of a transaction.
 * @property estimatedTransactionPriority - the selected priority by the user
 * @property fiatCurrency: The fiat currency to convert BTC amounts to and from.
 */
data class TransactionDetailsCardUiProps(
  val transactionDetail: TransactionDetailType,
  val fiatCurrency: FiatCurrency,
  val exchangeRates: ImmutableList<ExchangeRate>?,
)

/**
 * Types of transactions to be displayed in the transaction details screen.
 */
sealed interface TransactionDetailType {
  val transferBitcoinAmount: BitcoinMoney
  val feeBitcoinAmount: BitcoinMoney

  /**
   * A regular send transaction.
   *
   * @property transferBitcoinAmount Amount of bitcoin to send
   * @property feeBitcoinAmount Amount of fees to offer for the transaction
   * @property estimatedTransactionPriority Selected transaction priority for this transaction.
   */
  data class Regular(
    override val transferBitcoinAmount: BitcoinMoney,
    override val feeBitcoinAmount: BitcoinMoney,
    val estimatedTransactionPriority: EstimatedTransactionPriority,
  ) : TransactionDetailType

  /**
   * A speed-up transaction.
   *
   * @property transferBitcoinAmount Amount of bitcoin to send
   * @property feeBitcoinAmount Amount of fees being offered for the child transaction.
   * @property oldFeeBitcoinAmount Amount of fees offered for the parent transaction.
   */
  data class SpeedUp(
    override val transferBitcoinAmount: BitcoinMoney,
    override val feeBitcoinAmount: BitcoinMoney,
    val oldFeeBitcoinAmount: BitcoinMoney,
  ) : TransactionDetailType
}

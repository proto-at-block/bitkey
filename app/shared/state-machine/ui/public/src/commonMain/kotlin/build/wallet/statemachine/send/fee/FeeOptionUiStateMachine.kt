package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList.FeeOption
import kotlinx.collections.immutable.ImmutableList

interface FeeOptionUiStateMachine : StateMachine<FeeOptionProps, FeeOption>

/**
 * Fee option props
 *
 * @property bitcoinBalance The user's wallet balance.
 * @property amount The amount of fees a user would be expected to pay for this option.
 * @property transactionAmount The amount that the user is trying to send (without fees).
 * @property selected Whether the user has selected this fee option.
 * @property estimatedTransactionPriority The priority level associated with this option.
 * @property fiatCurrency The fiat currency to use for the fee option.
 * @property showAllFeesEqualText If all the fee options have the same fee amount, show a label about it.
 * @property onClick Handler for when the user selects one of the fee options.
 * @constructor Create empty Fee option props
 */
data class FeeOptionProps(
  val bitcoinBalance: BitcoinBalance,
  val amount: BitcoinMoney,
  val transactionAmount: BitcoinMoney,
  val selected: Boolean,
  val estimatedTransactionPriority: EstimatedTransactionPriority,
  val fiatCurrency: FiatCurrency,
  val showAllFeesEqualText: Boolean = false,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onClick: () -> Unit,
)

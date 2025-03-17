package build.wallet.statemachine.money.amount

import build.wallet.amount.Amount
import build.wallet.money.Money
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that shows [Props.inputAmount] along with a converted amount in
 * [Props.secondaryDisplayAmountCurrency], using latest exchange rate.
 *
 * Used when the money amounts are actively being input by the customer, vs statically displayed.
 */
interface MoneyAmountEntryUiStateMachine : StateMachine<MoneyAmountEntryProps, MoneyAmountEntryModel>

/**
 * @property [inputAmount] - primary stable amount entered.
 * @property [secondaryAmount] - secondary amount converted from primary amount, null when there is
 * no secondary amount to display. For example, when the primary amount is [BitcoinMoney], and there
 * are no exchange rates available to convert to [FiatMoney].
 * @property [inputAmountMoney] - primary stable amount entered monetary value.
 * */
data class MoneyAmountEntryProps(
  val inputAmount: Amount,
  val secondaryAmount: Money?,
  val inputAmountMoney: Money,
)

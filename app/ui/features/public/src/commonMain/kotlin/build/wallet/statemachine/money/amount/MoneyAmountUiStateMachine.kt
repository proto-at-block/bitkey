package build.wallet.statemachine.money.amount

import build.wallet.money.Money
import build.wallet.money.currency.Currency
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that shows [Props.primaryMoney] along with a converted amount in
 * [Props.secondaryAmountCurrency], using latest exchange rate.
 */
interface MoneyAmountUiStateMachine : StateMachine<MoneyAmountUiProps, MoneyAmountModel>

/**
 * @property [primaryMoney] - primary stable amount entered.
 * @property [secondaryAmountCurrency] - currency to convert the secondary amount into, based on
 * the latest exchange rate.
 */
data class MoneyAmountUiProps(
  val primaryMoney: Money,
  val secondaryAmountCurrency: Currency,
)

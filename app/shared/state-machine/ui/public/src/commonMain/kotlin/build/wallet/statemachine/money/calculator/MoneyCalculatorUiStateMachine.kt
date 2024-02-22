package build.wallet.statemachine.money.calculator

import build.wallet.money.Money
import build.wallet.money.currency.Currency
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine that facilitates amount entry via keypad, handles money currency conversion and
 * will eventually handle entry amount swapping (right now uses Bitcoin amount in sats as primary
 * entry amount).
 */
interface MoneyCalculatorUiStateMachine : StateMachine<MoneyCalculatorUiProps, MoneyCalculatorModel>

/**
 * @property [inputAmountCurrency] - currency for the input amount, the amount shown
 * as primary and what the keypad acts on
 * @property [secondaryDisplayAmountCurrency] - currency for the secondary display
 * amount, shown below the input amount
 * @property [initialAmountInInputCurrency] - the initial amount in the input amount currency
 * @property [exchangeRates] - list of exchange rates to use for calculation, this is null when the
 * exchange rates are not available or are out of date due to the customer being offline or unable to
 * communicate with f8e
 */
data class MoneyCalculatorUiProps(
  val inputAmountCurrency: Currency,
  val secondaryDisplayAmountCurrency: Currency,
  val initialAmountInInputCurrency: Money,
  val exchangeRates: ImmutableList<ExchangeRate>?,
) {
  init {
    require(inputAmountCurrency != secondaryDisplayAmountCurrency)
  }
}

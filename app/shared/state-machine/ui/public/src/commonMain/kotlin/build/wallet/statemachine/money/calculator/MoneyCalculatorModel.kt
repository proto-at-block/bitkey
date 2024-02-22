package build.wallet.statemachine.money.calculator

import build.wallet.money.Money
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.ui.model.Model

/**
 * In itself this is not a UI model that should be rendered directly. Instead consumer gets
 * flexibility to render and arrange amount and keypad components as they wish.
 *
 * @property [primaryAmount] - latest primary entered amount. This value can be Fiat or Bitcoin Money.
 * This value is only BitcoinMoney if no exchange rates are available
 * @property [secondaryAmount] - latest secondary amount converted from primary amount. This value is
 * converted from [primaryAmount] using latest exchange rate. This value is null when there are no
 * exchange rates available to convert
 * @property [amountModel] - model that should be used for rendering the amount model containing
 * primary and secondary amount.
 * @property [keypadModel] - model that should be used for rendering the keypad used to edit amount.
 */
data class MoneyCalculatorModel(
  val primaryAmount: Money,
  val secondaryAmount: Money?,
  val amountModel: MoneyAmountEntryModel,
  val keypadModel: KeypadModel,
) : Model()

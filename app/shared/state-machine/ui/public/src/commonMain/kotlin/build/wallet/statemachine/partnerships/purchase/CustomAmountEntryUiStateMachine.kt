package build.wallet.statemachine.partnerships.purchase

import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for entering a custom purchase amount for partnerships flow.
 */
interface CustomAmountEntryUiStateMachine : StateMachine<CustomAmountEntryUiProps, ScreenModel>

data class CustomAmountEntryUiProps(
  val fiatCurrency: FiatCurrency,
  val minimumAmount: FiatMoney,
  val maximumAmount: FiatMoney,
  val onNext: (FiatMoney) -> Unit,
  val onBack: () -> Unit,
)

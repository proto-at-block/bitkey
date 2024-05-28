package build.wallet.statemachine.money.currency

import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface CurrencyPreferenceUiStateMachine : StateMachine<CurrencyPreferenceProps, ScreenModel>

/**
 * @param onBack: Callback for toolbar back button. If null, no back button is shown.
 */
data class CurrencyPreferenceProps(
  val onBack: () -> Unit,
  val btcDisplayAmount: BitcoinMoney,
)

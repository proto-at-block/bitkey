package build.wallet.statemachine.money.currency

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface AppearancePreferenceUiStateMachine : StateMachine<AppearancePreferenceProps, ScreenModel>

/**
 * @param onBack: Callback for toolbar back button. If null, no back button is shown.
 */
data class AppearancePreferenceProps(
  val onBack: () -> Unit,
)

package build.wallet.statemachine.settings.helpcenter

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for showing help center information
 */
interface HelpCenterUiStateMachine : StateMachine<HelpCenterUiProps, ScreenModel>

/**
 * Help Center props
 *
 * @property onBack - invoked once a back action has occurred
 */
data class HelpCenterUiProps(
  val onBack: () -> Unit,
)

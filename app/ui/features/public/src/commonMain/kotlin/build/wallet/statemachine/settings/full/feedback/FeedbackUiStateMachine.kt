package build.wallet.statemachine.settings.full.feedback

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for showing Feedback contact screen
 */
interface FeedbackUiStateMachine : StateMachine<FeedbackUiProps, ScreenModel>

/**
 * Feedback props
 *
 * @property onBack - invoked once a back action has occurred
 */
data class FeedbackUiProps(
  val account: Account? = null,
  val onBack: () -> Unit,
)

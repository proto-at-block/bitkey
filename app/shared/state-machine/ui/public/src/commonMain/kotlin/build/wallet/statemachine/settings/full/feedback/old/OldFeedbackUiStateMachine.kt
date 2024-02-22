package build.wallet.statemachine.settings.full.feedback.old

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface OldFeedbackUiStateMachine : StateMachine<OldFeedbackUiProps, ScreenModel>

data class OldFeedbackUiProps(
  val onBack: () -> Unit,
)

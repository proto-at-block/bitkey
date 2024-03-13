package build.wallet.statemachine.dev

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData

interface F8eCustomUrlStateMachine : StateMachine<F8eCustomUrlStateMachineProps, ScreenModel>

data class F8eCustomUrlStateMachineProps(
  val customUrl: String,
  val templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
  val onBack: () -> Unit,
)

package build.wallet.statemachine.dev.lightning

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.dev.lightning.LightningDebugMenuUiStateMachine.LightningDebugMenuUiProps

interface LightningDebugMenuUiStateMachine : StateMachine<LightningDebugMenuUiProps, ScreenModel> {
  data class LightningDebugMenuUiProps(
    val onBack: () -> Unit,
  )
}

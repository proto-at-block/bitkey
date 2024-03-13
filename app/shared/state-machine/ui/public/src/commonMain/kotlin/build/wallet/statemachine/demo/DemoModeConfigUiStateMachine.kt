package build.wallet.statemachine.demo

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData

interface DemoModeConfigUiStateMachine : StateMachine<DemoModeConfigUiProps, ScreenModel>

data class DemoModeConfigUiProps(
  val accountData: GettingStartedData,
  val onBack: () -> Unit,
)

package build.wallet.statemachine.demo

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData

interface DemoModeCodeEntryUiStateMachine : StateMachine<DemoCodeEntryUiProps, ScreenModel>

data class DemoCodeEntryUiProps(
  val accountData: GettingStartedData,
  val onCodeSuccess: () -> Unit,
  val onBack: () -> Unit,
)

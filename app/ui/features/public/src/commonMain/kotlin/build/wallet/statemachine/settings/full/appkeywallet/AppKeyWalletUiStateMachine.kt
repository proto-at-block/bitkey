package build.wallet.statemachine.settings.full.appkeywallet

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface AppKeyWalletUiStateMachine : StateMachine<AppKeyWalletUiProps, ScreenModel>

data class AppKeyWalletUiProps(
  val account: FullAccount,
  val onBack: () -> Unit,
)

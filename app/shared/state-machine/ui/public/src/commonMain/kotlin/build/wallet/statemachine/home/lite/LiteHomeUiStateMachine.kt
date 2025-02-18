package build.wallet.statemachine.home.lite

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for managing the home (money home + settings) experiences for a "lite" customer.
 */
interface LiteHomeUiStateMachine : StateMachine<LiteHomeUiProps, ScreenModel>

data class LiteHomeUiProps(
  val account: LiteAccount,
  val onUpgradeComplete: (FullAccount) -> Unit,
  val onAppDataDeleted: () -> Unit,
)

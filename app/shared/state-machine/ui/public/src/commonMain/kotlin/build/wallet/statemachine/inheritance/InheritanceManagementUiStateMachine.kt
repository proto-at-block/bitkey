package build.wallet.statemachine.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * This state machine manages the UI for adding and becoming a beneficiary for inheritance.
 */
interface InheritanceManagementUiStateMachine : StateMachine<InheritanceManagementUiProps, ScreenModel>

/**
 * @param onBack Callback to navigate back to the previous screen.
 */
data class InheritanceManagementUiProps(
  val account: FullAccount,
  val selectedTab: ManagingInheritanceTab,
  val onBack: () -> Unit,
  val onGoToUtxoConsolidation: () -> Unit,
)

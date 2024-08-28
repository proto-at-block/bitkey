package build.wallet.statemachine.recovery.socrec.view

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for viewing the details and actions on an established Protected Customer.
 */
interface ViewingProtectedCustomerUiStateMachine : StateMachine<ViewingProtectedCustomerProps, ScreenModel>

/**
 * @property screenModel: The screen model to show this detail bottom sheet on top of
 */
data class ViewingProtectedCustomerProps(
  val account: Account,
  val screenModel: ScreenModel,
  val protectedCustomer: ProtectedCustomer,
  val onHelpWithRecovery: () -> Unit,
  val onExit: () -> Unit,
)

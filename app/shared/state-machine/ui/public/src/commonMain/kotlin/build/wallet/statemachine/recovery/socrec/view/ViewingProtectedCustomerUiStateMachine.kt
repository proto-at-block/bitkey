package build.wallet.statemachine.recovery.socrec.view

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

/**
 * State machine for viewing the details and actions on an established Protected Customer.
 */
interface ViewingProtectedCustomerUiStateMachine : StateMachine<ViewingProtectedCustomerProps, ScreenModel>

/**
 * @property screenModel: The screen model to show this detail bottom sheet on top of
 */
data class ViewingProtectedCustomerProps(
  val screenModel: ScreenModel,
  val protectedCustomer: ProtectedCustomer,
  val onRemoveProtectedCustomer: suspend () -> Result<Unit, Error>,
  val onHelpWithRecovery: () -> Unit,
  val onExit: () -> Unit,
)

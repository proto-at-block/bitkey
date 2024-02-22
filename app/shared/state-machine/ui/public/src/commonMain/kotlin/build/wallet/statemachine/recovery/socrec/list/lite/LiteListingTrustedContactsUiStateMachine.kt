package build.wallet.statemachine.recovery.socrec.list.lite

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for the screen model to show the 'Trusted Contacts' list UI
 * for Lite Accounts
 */
interface LiteListingTrustedContactsUiStateMachine :
  StateMachine<LiteListingTrustedContactsUiProps, ScreenModel>

data class LiteListingTrustedContactsUiProps(
  val onExit: () -> Unit,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  val onRemoveProtectedCustomer: suspend (ProtectedCustomer) -> Result<Unit, Error>,
  val onHelpWithRecovery: (ProtectedCustomer) -> Unit,
  val onAcceptInvitePressed: () -> Unit,
)

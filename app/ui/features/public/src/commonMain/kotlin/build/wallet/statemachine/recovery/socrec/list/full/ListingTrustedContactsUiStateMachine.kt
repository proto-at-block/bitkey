package build.wallet.statemachine.recovery.socrec.list.full

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for adding a new Recovery Contact.
 */
interface ListingTrustedContactsUiStateMachine :
  StateMachine<ListingTrustedContactsUiProps, ScreenModel>

data class ListingTrustedContactsUiProps(
  val account: FullAccount,
  val onAddTCButtonPressed: () -> Unit,
  val onAcceptTrustedContactInvite: () -> Unit,
  val onExit: () -> Unit,
)

package build.wallet.statemachine.recovery.socrec.list.full

import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.recovery.socrec.SocRecFullAccountActions
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for adding a new Trusted Contact.
 */
interface ListingTrustedContactsUiStateMachine : StateMachine<ListingTrustedContactsUiProps, ScreenModel>

data class ListingTrustedContactsUiProps(
  val account: FullAccount,
  val relationships: SocRecRelationships,
  val onAddTCButtonPressed: () -> Unit,
  val onAcceptTrustedContactInvite: () -> Unit,
  val socRecFullAccountActions: SocRecFullAccountActions,
  val onExit: () -> Unit,
)
